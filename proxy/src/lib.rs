//! fengni-mqttf-proxy — JNI bridge and encrypted MQTT proxy
//!
//! Provides native methods called from `FengniNative.java`:
//!   init(serverPubKeyHex, groupId)  → handle
//!   handshakeStep(handle, input) → nextMessage | null(done)
//!   encrypt(handle, plaintext) → wireBytes  ([2-byte len][ciphertext])
//!   decrypt(handle, wireBytes) → plaintext
//!   routeEncrypt(routePubKeyHex, groupId) → 66-byte routing header
//!   close(handle)

use fengni::crypto::{derive_key, encrypt};
use fengni::{HandshakeBuilder, KeyPair, TransportState};
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jbyteArray, jint, jlong};
use jni::JNIEnv;
use x25519_dalek::{PublicKey as X25519PublicKey, StaticSecret};

use std::collections::HashMap;
use std::sync::Mutex;

// ── Global session store ──

static SESSIONS: std::sync::LazyLock<Mutex<HashMap<u64, Session>>> =
    std::sync::LazyLock::new(|| Mutex::new(HashMap::new()));

static NEXT_ID: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(1);

struct Session {
    #[allow(dead_code)]
    identity: KeyPair,
    /// For handshake phase — None once handshake completes.
    handshake: Option<fengni::Handshake>,
    /// Set after handshake completes.
    transport: Option<TransportState>,
}

fn store_session(s: Session) -> u64 {
    let id = NEXT_ID.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
    SESSIONS.lock().unwrap().insert(id, s);
    id
}

// ── JNI helpers ──

fn jstring_to_string(env: &mut JNIEnv, jstr: &JString) -> Result<String, String> {
    env.get_string(jstr)
        .map(|s| s.into())
        .map_err(|e| format!("JNI get_string: {e}"))
}

fn bytes_to_java(env: &mut JNIEnv, data: &[u8]) -> Result<jbyteArray, String> {
    env.byte_array_from_slice(data)
        .map_err(|e| format!("JNI byte_array_from_slice: {e}"))
        .map(|arr| arr.into_raw())
}

fn java_to_bytes(env: &mut JNIEnv, arr: &JByteArray) -> Result<Vec<u8>, String> {
    env.convert_byte_array(arr)
        .map_err(|e| format!("JNI convert_byte_array: {e}"))
}

// ── Exported JNI functions ──

/// Initialize a new fengni client session with groupId (prologue for key isolation).
///
/// Returns an opaque session handle (u64). The caller must call `close(handle)` to free.
#[no_mangle]
pub extern "system" fn Java_com_fengni_mqttf_FengniNative_init(
    mut env: JNIEnv,
    _class: JClass,
    server_pub_hex: JString,
    group_id: jint,
) -> jlong {
    match init_impl(&mut env, &server_pub_hex, group_id) {
        Ok(handle) => handle as jlong,
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", e);
            0
        }
    }
}

fn init_impl(env: &mut JNIEnv, server_pub_hex: &JString, group_id: jint) -> Result<u64, String> {
    let hex_str = jstring_to_string(env, server_pub_hex)?;
    let hex_str = hex_str.trim();

    if hex_str.len() != 64 {
        return Err(format!(
            "server public key must be 64 hex chars, got {}",
            hex_str.len()
        ));
    }

    let mut server_pub = [0u8; 32];
    hex::decode_to_slice(hex_str, &mut server_pub).map_err(|e| format!("hex decode: {e}"))?;

    let mut identity = KeyPair::generate();
    identity.pin_peer(&server_pub);

    let prologue = (group_id as u16).to_be_bytes().to_vec();

    let handshake = HandshakeBuilder::initiator(identity.clone(), server_pub)
        .prologue(&prologue)
        .build();

    Ok(store_session(Session {
        identity,
        handshake: Some(handshake),
        transport: None,
    }))
}

/// Process a handshake message and return the next message to send.
///
/// Pass `null` / empty for the first call (initiator generates Hello).
/// Returns the next message to send, or `null` when the handshake is complete.
#[no_mangle]
pub extern "system" fn Java_com_fengni_mqttf_FengniNative_handshakeStep(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    input: JByteArray,
) -> jbyteArray {
    match handshake_step_impl(&mut env, handle as u64, &input) {
        Ok(Some(bytes)) => match bytes_to_java(&mut env, &bytes) {
            Ok(arr) => arr,
            Err(e) => {
                let _ = env.throw_new("java/lang/RuntimeException", e);
                std::ptr::null_mut()
            }
        },
        Ok(None) => std::ptr::null_mut(), // handshake complete
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", e);
            std::ptr::null_mut()
        }
    }
}

fn handshake_step_impl(
    env: &mut JNIEnv,
    handle: u64,
    input: &JByteArray,
) -> Result<Option<Vec<u8>>, String> {
    let input_bytes = if input.is_null() {
        vec![]
    } else {
        java_to_bytes(env, input)?
    };

    let mut sessions = SESSIONS.lock().map_err(|e| format!("lock: {e}"))?;
    let session = sessions
        .get_mut(&handle)
        .ok_or_else(|| format!("invalid handle: {handle}"))?;

    match &mut session.handshake {
        None => {
            // Handshake already complete — this shouldn't happen
            Ok(None)
        }
        Some(ref mut hs) => {
            if input_bytes.is_empty() {
                // First step: initiator generates Hello
                hs.send_hello()
                    .map(Some)
                    .map_err(|e| format!("send_hello: {e}"))
            } else {
                // Process incoming handshake message
                let result = hs
                    .handle_message(&input_bytes)
                    .map_err(|e| format!("handle_message: {e}"))?;

                if hs.state().is_completed() {
                    // Handshake done — extract transport
                    let hs = session.handshake.take().unwrap();
                    session.transport = Some(
                        hs.into_transport()
                            .map_err(|e| format!("into_transport: {e}"))?,
                    );
                }
                Ok(result)
            }
        }
    }
}

/// Encrypt plaintext for sending.
///
/// Returns `[2-byte len (u16 BE)][fengni AEAD ciphertext]`.
#[no_mangle]
pub extern "system" fn Java_com_fengni_mqttf_FengniNative_encrypt(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    plaintext: JByteArray,
) -> jbyteArray {
    match encrypt_impl(&mut env, handle as u64, &plaintext) {
        Ok(wire_bytes) => match bytes_to_java(&mut env, &wire_bytes) {
            Ok(arr) => arr,
            Err(e) => {
                let _ = env.throw_new("java/lang/RuntimeException", e);
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", e);
            std::ptr::null_mut()
        }
    }
}

fn encrypt_impl(
    env: &mut JNIEnv,
    handle: u64,
    plaintext_arr: &JByteArray,
) -> Result<Vec<u8>, String> {
    let pt = java_to_bytes(env, plaintext_arr)?;

    let sessions = SESSIONS.lock().map_err(|e| format!("lock: {e}"))?;
    let session = sessions
        .get(&handle)
        .ok_or_else(|| format!("invalid handle: {handle}"))?;

    let transport = session.transport.as_ref().ok_or("handshake not complete")?;

    let ct = transport.send(&pt).map_err(|e| format!("encrypt: {e}"))?;

    // Wire format: [2-byte len][ciphertext]
    let len = ct.len() as u16;
    let mut wire = Vec::with_capacity(2 + ct.len());
    wire.extend_from_slice(&len.to_be_bytes());
    wire.extend_from_slice(&ct);
    Ok(wire)
}

/// Decrypt ciphertext received from the wire.
///
/// Expects `[2-byte len (u16 BE)][fengni AEAD ciphertext]`.
/// Returns plaintext.
#[no_mangle]
pub extern "system" fn Java_com_fengni_mqttf_FengniNative_decrypt(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    wire_data: JByteArray,
) -> jbyteArray {
    match decrypt_impl(&mut env, handle as u64, &wire_data) {
        Ok(plaintext) => match bytes_to_java(&mut env, &plaintext) {
            Ok(arr) => arr,
            Err(e) => {
                let _ = env.throw_new("java/lang/RuntimeException", e);
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", e);
            std::ptr::null_mut()
        }
    }
}

fn decrypt_impl(env: &mut JNIEnv, handle: u64, wire_arr: &JByteArray) -> Result<Vec<u8>, String> {
    let wire = java_to_bytes(env, wire_arr)?;

    if wire.len() < 2 {
        return Err("wire data too short".into());
    }

    let data_len = u16::from_be_bytes([wire[0], wire[1]]) as usize;
    if wire.len() < 2 + data_len {
        return Err(format!(
            "truncated frame: expected {} bytes, got {}",
            2 + data_len,
            wire.len()
        ));
    }

    let ct = &wire[2..2 + data_len];

    let sessions = SESSIONS.lock().map_err(|e| format!("lock: {e}"))?;
    let session = sessions
        .get(&handle)
        .ok_or_else(|| format!("invalid handle: {handle}"))?;

    let transport = session.transport.as_ref().ok_or("handshake not complete")?;

    transport.recv(ct).map_err(|e| format!("decrypt: {e}"))
}

/// Generate an encrypted routing header for multi-group proxy.
///
/// Returns a 66-byte array: [2B len=64][32B ephemeral_pub][12B nonce][18B ct+tag][2B zero_padding]
#[no_mangle]
pub extern "system" fn Java_com_fengni_mqttf_FengniNative_routeEncrypt(
    mut env: JNIEnv,
    _class: JClass,
    route_pub_hex: JString,
    group_id: jint,
) -> jbyteArray {
    match route_encrypt_impl(&mut env, &route_pub_hex, group_id) {
        Ok(data) => match bytes_to_java(&mut env, &data) {
            Ok(arr) => arr,
            Err(e) => {
                let _ = env.throw_new("java/lang/RuntimeException", e);
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", e);
            std::ptr::null_mut()
        }
    }
}

fn route_encrypt_impl(
    env: &mut JNIEnv,
    route_pub_hex: &JString,
    group_id: jint,
) -> Result<Vec<u8>, String> {
    // 1. Parse routePubKeyHex → [u8; 32]
    let hex_str = jstring_to_string(env, route_pub_hex)?;
    let hex_str = hex_str.trim();
    if hex_str.len() != 64 {
        return Err(format!(
            "route pub key must be 64 hex chars, got {}",
            hex_str.len()
        ));
    }
    let mut route_pub_bytes = [0u8; 32];
    hex::decode_to_slice(hex_str, &mut route_pub_bytes)
        .map_err(|e| format!("route pub key hex decode: {e}"))?;

    // 2. Generate ephemeral X25519 key pair (per-connection, never reused)
    let ephemeral_sk = StaticSecret::random();
    let ephemeral_pk = X25519PublicKey::from(&ephemeral_sk);

    // 3. DH(ephemeral_sk, route_pub) → shared_secret
    let route_pub = X25519PublicKey::from(route_pub_bytes);
    let shared = ephemeral_sk.diffie_hellman(&route_pub);

    // 4. HKDF(shared, "fengni-routing-v1") → routing_key
    let routing_key = derive_key(shared.as_bytes(), b"fengni-routing-v1")
        .map_err(|e| format!("routing key derivation: {e}"))?;

    // 5. Generate random nonce
    let nonce: [u8; 12] = rand::random();

    // 6. ChaCha20-Poly1305 encrypt(group_id.to_be_bytes())
    let gid_bytes = (group_id as u16).to_be_bytes();
    let ciphertext = encrypt(&routing_key, &nonce, &gid_bytes)
        .map_err(|e| format!("routing header encrypt: {e}"))?;
    // ciphertext = 2B encrypted_group_id + 16B tag = 18 bytes

    // 7. Assemble [2B len=64][32B ephemeral_pub][12B nonce][18B ct+tag][2B zero_padding]
    let total_payload: usize = 32 + 12 + ciphertext.len() + 2; // = 64
    let mut buf = Vec::with_capacity(2 + total_payload);
    buf.extend_from_slice(&(total_payload as u16).to_be_bytes()); // 2B len=64
    buf.extend_from_slice(ephemeral_pk.as_bytes());               // 32B pub
    buf.extend_from_slice(&nonce);                                 // 12B nonce
    buf.extend_from_slice(&ciphertext);                            // 18B ct+tag
    buf.extend_from_slice(&[0u8; 2]);                              // 2B zero padding

    Ok(buf) // 66 bytes
}

/// Free the session.
#[no_mangle]
pub extern "system" fn Java_com_fengni_mqttf_FengniNative_close(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    SESSIONS.lock().ok().map(|mut m| m.remove(&(handle as u64)));
}
