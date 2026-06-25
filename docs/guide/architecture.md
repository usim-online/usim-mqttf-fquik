# Architecture / 架构

## Topology / 拓扑

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

## Android App Modules / 应用模块

| Module | Role / 职责 |
|--------|------------|
| `presentation` | UI layer — Activities, Fragments, ViewModels / 界面层 |
| `domain` | Business logic — Interactors, Repository interfaces, Realm models / 业务逻辑层 |
| `data` | Data layer — Repository implementations, QkTransaction send pipeline / 数据层 |
| `common` | Shared utilities — TelephonyCompat, extensions / 公共工具 |
| `android-smsmms` | MMS/SMS library (legacy, retained for compatibility) / MMS/SMS 库（遗留，保留兼容） |
| `fengni-bridge` | MQTTF integration — MQTT foreground service, message sync, config management / MQTTF 集成 |

## Server Component / 服务端组件

| Component | Role / 职责 |
|-----------|------------|
| `proxy/` | `fengni-mqtt-proxy` — Rust binary that decrypts MQTTF client traffic and forwards to a plaintext MQTT broker. Also compiles as a JNI library (`cdylib`) for direct use on Android. / Rust 二进制，解密 MQTTF 客户端流量并转发到明文 MQTT 代理，也可编译为 JNI 库直接在 Android 上使用。 |
