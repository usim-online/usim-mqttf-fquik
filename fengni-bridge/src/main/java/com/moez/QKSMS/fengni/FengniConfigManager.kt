package com.moez.QKSMS.fengni

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the fengni configuration lifecycle:
 * - Import encrypted config + key file from external storage
 * - AES-256-GCM decrypt using hex-encoded key file
 * - Cache decrypted values in SharedPreferences (never write plaintext to disk)
 * - hmacKey is further encrypted via Android Keystore before storage
 * - Provide typed access to MQTT connection parameters
 */
@Singleton
class FengniConfigManager @Inject constructor(
    private val context: Context
) {
    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns the current fengni config, or null if not yet imported.
     * Checks SharedPreferences first; if absent, attempts file import.
     */
    fun getConfig(): FengniConfig? {
        if (isConfigured()) {
            return loadFromPrefs()
        }
        return tryImportFromDefaultPath()
    }

    /**
     * Import configuration from user-provided URIs (content:// or file://).
     * Decrypts with the key file, stores to SharedPreferences. Never writes plaintext to disk.
     *
     * @param configFile encrypted config file
     * @param keyFile hex-encoded 32-byte AES-256 key file
     * @return decoded config, or null on failure
     */
    fun importConfig(configFile: File, keyFile: File): FengniConfig? {
        return try {
            val keyHex = keyFile.readText().trim()
            if (keyHex.length != 64) {
                Timber.e("Invalid key length: ${keyHex.length}, expected 64 hex chars")
                return null
            }
            val keyBytes = hexToBytes(keyHex)

            val encrypted = configFile.readBytes()
            val plaintext = decryptAesGcm(keyBytes, encrypted) ?: return null

            val json = JSONObject(plaintext)
            val config = FengniConfig(
                remoteHost = json.getString("remoteHost"),
                remotePort = json.getInt("remotePort"),
                serverPubKey = json.getString("serverPubKey"),
                routePubKey = json.getString("routePubKey"),
                groupId = json.getInt("groupId"),
                customerId = json.getString("customerId"),
                deviceId = json.getString("deviceId"),
                hmacKey = json.optString("hmacKey", ""),
                paddingMax = json.optInt("paddingMax", 64)
            )

            saveToPrefs(config)
            Timber.i("Config imported successfully: ${config.remoteHost}:${config.remotePort}")
            config
        } catch (e: Exception) {
            Timber.e(e, "Failed to import config")
            null
        }
    }

    fun isConfigured(): Boolean {
        return prefs.getBoolean(KEY_CONFIGURED, false)
    }

    // ── SharedPreferences persistence ──

    private fun loadFromPrefs(): FengniConfig? {
        val hmacKey = loadHmacKeyFromKeystore()
        return FengniConfig(
            remoteHost = prefs.getString(KEY_HOST, null) ?: return null,
            remotePort = prefs.getInt(KEY_PORT, 0).takeIf { it > 0 } ?: return null,
            serverPubKey = prefs.getString(KEY_PUBKEY, null) ?: return null,
            routePubKey = prefs.getString(KEY_ROUTE_PUBKEY, null) ?: return null,
            groupId = prefs.getInt(KEY_GROUP_ID, 0).takeIf { it > 0 } ?: return null,
            customerId = prefs.getString(KEY_CUSTOMER, null) ?: return null,
            deviceId = prefs.getString(KEY_DEVICE, null) ?: return null,
            hmacKey = hmacKey,
            paddingMax = prefs.getInt(KEY_PADDING_MAX, 64)
        )
    }

    private fun saveToPrefs(config: FengniConfig) {
        prefs.edit()
            .putString(KEY_HOST, config.remoteHost)
            .putInt(KEY_PORT, config.remotePort)
            .putString(KEY_PUBKEY, config.serverPubKey)
            .putString(KEY_ROUTE_PUBKEY, config.routePubKey)
            .putInt(KEY_GROUP_ID, config.groupId)
            .putString(KEY_CUSTOMER, config.customerId)
            .putString(KEY_DEVICE, config.deviceId)
            .putInt(KEY_PADDING_MAX, config.paddingMax)
            .putBoolean(KEY_CONFIGURED, true)
            .apply()

        // Encrypt and store hmacKey via Android Keystore
        if (config.hmacKey.isNotEmpty()) {
            saveHmacKeyToKeystore(config.hmacKey)
        }
    }

    // ── Android Keystore hmacKey encryption ──

    /**
     * Encrypt hmacKey with Android Keystore AES-256-GCM and store as Base64 in SharedPreferences.
     * Storage format: Base64(iv_12bytes + ciphertext + tag_16bytes)
     */
    private fun saveHmacKeyToKeystore(hmacKeyHex: String) {
        try {
            val secretKey = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance(KESTORE_CIPHER_TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encrypted = cipher.doFinal(hmacKeyHex.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv
            val combined = iv + encrypted
            val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
            prefs.edit().putString(KEY_HMAC_ENCRYPTED, encoded).apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to encrypt hmacKey with Keystore")
        }
    }

    /**
     * Decrypt hmacKey from SharedPreferences using Android Keystore.
     * Returns empty string if not stored or decryption fails (backward compatible).
     */
    private fun loadHmacKeyFromKeystore(): String {
        val encoded = prefs.getString(KEY_HMAC_ENCRYPTED, null) ?: return ""
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < KESTORE_IV_LEN + 1) return ""
            val iv = combined.copyOfRange(0, KESTORE_IV_LEN)
            val ciphertext = combined.copyOfRange(KESTORE_IV_LEN, combined.size)

            val secretKey = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance(KESTORE_CIPHER_TRANSFORM)
            val spec = GCMParameterSpec(KESTORE_TAG_LEN_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decrypt hmacKey from Keystore")
            ""
        }
    }

    /**
     * Get or create the Android Keystore AES key used for hmacKey encryption.
     */
    private fun getOrCreateKeystoreKey(): java.security.Key {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null)
            return (entry as KeyStore.SecretKeyEntry).secretKey
        }

        // Create new key
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    // ── Default path import ──

    private fun tryImportFromDefaultPath(): FengniConfig? {
        val dir = File(context.getExternalFilesDir(null), "fengni")
        val configFile = File(dir, "fengni.conf")
        val keyFile = File(dir, "fengni.key")

        if (!configFile.exists() || !keyFile.exists()) {
            Timber.w("Config files not found at ${dir.absolutePath}")
            return null
        }

        Timber.i("Found config files, importing...")
        return importConfig(configFile, keyFile)
    }

    // ── AES-256-GCM (config file encryption) ──

    companion object {
        private const val PREFS_NAME = "fengni_config"
        private const val KEY_CONFIGURED = "configured"
        private const val KEY_HOST = "remoteHost"
        private const val KEY_PORT = "remotePort"
        private const val KEY_PUBKEY = "serverPubKey"
        private const val KEY_ROUTE_PUBKEY = "routePubKey"
        private const val KEY_GROUP_ID = "groupId"
        private const val KEY_CUSTOMER = "customerId"
        private const val KEY_DEVICE = "deviceId"
        private const val KEY_HMAC_ENCRYPTED = "hmac_encrypted"
        private const val KEY_PADDING_MAX = "paddingMax"

        // Android Keystore constants
        private const val KEYSTORE_ALIAS = "fengni_hmac_key_encryption"
        private const val KESTORE_CIPHER_TRANSFORM = "AES/GCM/NoPadding"
        private const val KESTORE_IV_LEN = 12
        private const val KESTORE_TAG_LEN_BITS = 128

        // Config file encryption constants
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_NONCE_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128 // bits

        fun decryptAesGcm(keyBytes: ByteArray, encrypted: ByteArray): String? {
            return try {
                if (encrypted.size < GCM_NONCE_LENGTH + 1) {
                    Timber.e("Encrypted data too short: ${encrypted.size} bytes")
                    return null
                }

                val nonce = encrypted.copyOfRange(0, GCM_NONCE_LENGTH)
                val ciphertext = encrypted.copyOfRange(GCM_NONCE_LENGTH, encrypted.size)

                val keySpec = SecretKeySpec(keyBytes, "AES")
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)

                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
                String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            } catch (e: Exception) {
                Timber.e(e, "AES-GCM decryption failed")
                null
            }
        }

        fun encryptAesGcm(keyBytes: ByteArray, plaintext: ByteArray): ByteArray? {
            return try {
                val nonce = java.security.SecureRandom().apply { nextBytes(ByteArray(GCM_NONCE_LENGTH)) }
                    .let { it.generateSeed(GCM_NONCE_LENGTH) }

                val keySpec = SecretKeySpec(keyBytes, "AES")
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)

                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
                val ciphertext = cipher.doFinal(plaintext)

                nonce + ciphertext
            } catch (e: Exception) {
                Timber.e(e, "AES-GCM encryption failed")
                null
            }
        }

        private fun hexToBytes(hex: String): ByteArray {
            return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }
}
