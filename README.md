<div align="center">

# Fquik

**多设备加密消息同步应用** / Multi-device encrypted messaging sync app

Based on [Quik SMS](https://github.com/octoshrimpy/quik) · Powered by [MQTTF](https://github.com/linkbitflower/fengni-v1) (MQTT 5 + Fengni)

[![GitHub stars](https://img.shields.io/github/stars/usim-online/usim-mqttf-fquik?style=social)](https://github.com/usim-online/usim-mqttf-fquik/stargazers)
[![License](https://img.shields.io/github/license/usim-online/usim-mqttf-fquik?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green?style=flat-square)]()
[![Protocol](https://img.shields.io/badge/protocol-MQTTF-blue?style=flat-square)](https://github.com/linkbitflower/fengni-v1)

</div>

---

## What is Fquik? / 什么是 Fquik？

**EN**: Fquik is a multi-device encrypted messaging sync application built on [Quik SMS](https://github.com/octoshrimpy/quik). It replaces the Android SMS delivery pipeline with MQTTF — a protocol stack combining **MQTT 5** for message transport and the [**Fengni**](https://github.com/linkbitflower/fengni-v1) protocol for end-to-end encryption. Devices sharing the same `customerId` form a sync group — messages composed on one device appear instantly on all others.

**中文**: Fquik 是一款基于 [Quik SMS](https://github.com/octoshrimpy/quik) 改造的多设备加密消息同步应用。它用 MQTTF 协议栈替换了 Android SMS 投递管道——**MQTT 5** 负责消息传输，[**Fengni**](https://github.com/linkbitflower/fengni-v1) 协议负责端到端加密。共享相同 `customerId` 的设备组成同步组，在一台设备上发送的消息会即时出现在所有其他设备上。

## What is MQTTF? / 什么是 MQTTF？

**MQTTF** = **MQTT 5** + **Fengni** — an encrypted messaging protocol stack:

- **MQTT 5** handles message routing, pub/sub, and QoS guarantees
- [**Fengni**](https://github.com/linkbitflower/fengni-v1) provides end-to-end encryption, replay protection, and traffic padding

The `fengni-mqtt-proxy` sits between clients and a plaintext MQTT broker (e.g. Mosquitto), performing Fengni handshakes and encrypting/decrypting all traffic. Clients never communicate in plaintext — even the MQTT broker only sees ciphertext.

**MQTTF** = **MQTT 5** + **Fengni** — 加密消息协议栈：

- **MQTT 5** 负责消息路由、发布/订阅和 QoS 保障
- [**Fengni**](https://github.com/linkbitflower/fengni-v1) 提供端到端加密、重放保护和流量填充

`fengni-mqtt-proxy` 部署在客户端与明文 MQTT 代理（如 Mosquitto）之间，执行 Fengni 握手并加解密所有流量。客户端绝不以明文通信——即使是 MQTT 代理也只能看到密文。

## Architecture / 架构

```
                    ┌──────────────────────┐
                    │  fengni-mqtt-proxy   │
                    │  (Rust, server-side) │
                    │  :1883 ← encrypted   │
                    │  :1884 → Mosquitto   │
                    └──────┬───────────────┘
                           │ MQTTF tunnel
              ┌────────────┼────────────┐
              ▼            ▼            ▼
         ┌─────────┐ ┌─────────┐ ┌─────────┐
         │ Device A│ │ Device B│ │ Device C│
         │ (SIM)   │ │ (no SIM)│ │ (no SIM)│
         └─────────┘ └─────────┘ └─────────┘
```

### Android App Modules / 应用模块

| Module | Role |
|--------|------|
| `presentation` | UI layer — Activities, Fragments, ViewModels |
| `domain` | Business logic — Interactors, Repository interfaces, Realm models |
| `data` | Data layer — Repository implementations, QkTransaction send pipeline |
| `common` | Shared utilities — TelephonyCompat, extensions |
| `android-smsmms` | MMS/SMS library (legacy, retained for compatibility) |
| `fengni-bridge` | MQTTF integration — MQTT foreground service, message sync, config management |

### Server Component / 服务端组件

| Component | Role |
|-----------|------|
| `proxy/` | `fengni-mqtt-proxy` — Rust binary that decrypts MQTTF client traffic and forwards to a plaintext MQTT broker. Also compiles as a JNI library (`cdylib`) for direct use on Android. |

## Security / 安全

- **End-to-end encryption / 端到端加密**: All messages are encrypted via the Fengni protocol before leaving the device. Config files are additionally protected with AES-256-GCM. No plaintext traverses the network at any point.
- **Traffic analysis resistance / 防流量分析**: `paddingMax` randomizes packet lengths to prevent size-based behavior analysis.
- **Device identity & isolation / 设备身份与隔离**: Each device is uniquely identified by `deviceId`. Sync groups are isolated by `customerId` — unauthorized devices cannot access group messages.
- **Key management / 密钥管理**: `fengni.key` resides only on the device's local storage and is never synced or uploaded. Loss of this key makes the configuration unrecoverable.

## Build / 构建

### Prerequisites / 前置条件

- JDK 17 (must set `JAVA_HOME` explicitly if system default differs / 若系统默认不是 JDK 17 须显式设置)
- Android SDK with compileSdk 34
- Rust toolchain (for proxy, optional / 仅 proxy 需要，可选)

### Android App / 安卓应用

```bash
JAVA_HOME=/path/to/jdk17 ./gradlew :presentation:assembleRelease -x test
```

The release APK is output to `presentation/build/outputs/apk/release/`.

Signing requires a keystore and a `.gradle/.gradlerc` file (excluded from version control):

```properties
keyAlias=<alias>
storePassword=<password>
keyPassword=<password>
```

### Rust Proxy / Rust 代理

```bash
cd proxy
cargo build --release
```

The binary is `proxy/target/release/fengni-mqtt-proxy`.

## Running / 运行

### 1. Start Mosquitto (plaintext broker / 明文代理)

```bash
mosquitto -p 1884 -v
```

### 2. Start fengni-mqtt-proxy (encrypted gateway / 加密网关)

```bash
fengni-mqtt-proxy \
  --listen 0.0.0.0:1883 \
  --backend 127.0.0.1:1884 \
  --identity <identity-key-file>
```

The proxy prints its server public key on startup — this is the `serverPubKey` value for client config.

### 3. Configure the Android app / 配置安卓应用

Deploy two files to the app's external storage directory (`Android/data/<package>/files/fengni/`):

- `fengni.key` — AES-256 key (hex-encoded, 32 bytes) for config decryption
- `fengni.conf` — AES-256-GCM encrypted config containing `customerId`, `deviceId`, `serverPubKey`, and connection details

Use `fengni-bridge/tools/generate-fengni-deployment.ps1` to generate these files.

### 4. Verify / 验证

1. Proxy console shows "Connection from ..." — Fengni handshake succeeded / 握手成功
2. Mosquitto `-v` output shows SUBSCRIBE — MQTT subscription active / 订阅成功
3. No "error" in proxy console — encryption/decryption working / 加解密正常
4. App displays conversations — Realm write + UI refresh working / 数据写入与刷新正常
5. Push notifications arrive — notification chain complete (requires `POST_NOTIFICATIONS` permission) / 通知链路完整

## Configuration / 配置

Each device has a `FengniConfig` containing:

| Field | Description |
|-------|-------------|
| `customerId` | Shared across all devices in a sync group / 同步组内所有设备共享 |
| `deviceId` | Unique per device / 每台设备唯一 |
| `serverPubKey` | Proxy identity public key (derived from `--identity` private key) / 代理身份公钥 |
| `remoteHost` / `remotePort` | fengni-mqtt-proxy listen address / 代理监听地址 |
| `routePubKey` | Routing public key for multi-group deployments / 多组路由公钥 |
| `groupId` | Group identifier for multi-group routing / 多组路由标识 |
| `hmacKey` | HMAC key for message authentication / 消息认证密钥 |
| `paddingMax` | Maximum padding size for traffic analysis resistance / 抗流量分析最大填充 |

Devices with the same `customerId` and `serverPubKey` but different `deviceId`s form a sync group. All messages flow through the MQTT broker.

## Message Flow / 消息流

**Incoming (SIM device / SIM 设备接收短信):**
```
SMS ContentProvider → ContentObserver → FengniMqttService.forwardIncomingSms()
  → FengniMessageStore.insert() → MQTT publish → FengniSyncRepository.syncMessage()
  → Realm write → notification
```

**Incoming (remote device / 远程设备接收):**
```
MQTT subscriber → FengniMqttService.messageLoop()
  → parse topic → FengniMessageStore.insert() → FengniSyncRepository.syncMessage()
  → Realm write → notification (incoming topic only)
```

**Outgoing (user sends / 用户发送):**
```
ComposeActivity → QkTransaction.sendSmsMessage() (fengni:// URI)
  → FengniMqttService.enqueueOutgoing() → MQTT publish
  → markSent / markFailed → Realm update
```

## Known Issues / 已知问题

| # | Issue / 问题 | Severity / 严重性 |
|---|-------------|-------------------|
| 1 | App online status and message sending have Realm synchronization issues causing UI inconsistencies / App 在线状态、消息发送等存在 Realm 同步问题导致 UI 异常 | Medium / 中 |
| 2 | Transparent proxy does not persist history — no offline message storage or replay across reconnections / 透明代理不存储历史记录——无离线消息持久化或重连回放 | Medium / 中 |
| 3 | App has not been modified for non-text content delivery — MMS, media, and other non-SMS payloads are not adapted / App 未对非文本内容发送进行改造——MMS、媒体等非 SMS 载荷未适配 | Low / 低 |

> Fquik serves as a demonstration of the Fengni protocol's capabilities and application scenarios. The above issues reflect its current status as a proof-of-concept implementation.
>
> Fquik 作为 Fengni 协议先进性与应用场景的 Demo 存在，以上问题反映了其当前作为概念验证实现的状态。

## License / 许可证

[GPL-3.0](LICENSE)

---

<div align="center">

Copyright © 2026 [USIM Online](https://github.com/usim-online)

</div>
