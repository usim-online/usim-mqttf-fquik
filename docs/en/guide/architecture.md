# Architecture

## Topology

```
                    fengni-mqtt-proxy (Rust)
                   ┌─────────────────────────┐
                   │  :1883 (encrypted)       │
                   │  Fengni protocol         │
                   │         │                │
                   │    decrypt/encrypt       │
                   │         │                │
                   │  :1884 ──► Mosquitto     │
                   └────┬────┬────┬───────────┘
                        │    │    │
                   MQTTF tunnel
                        │    │    │
                   ┌────┘    │    └────┐
                   ▼         ▼         ▼
              Device A   Device B   Device C
```

All client-to-proxy traffic is encrypted with the Fengni protocol. The Mosquitto broker only sees ciphertext.

## Android Modules

| Module | Responsibility |
|--------|---------------|
| `presentation` | UI layer — activities, fragments, Compose screens |
| `domain` | Business logic — use cases, domain models |
| `data` | Repository implementations, data sources |
| `common` | Shared utilities and extensions |
| `android-smsmms` | Legacy MMS/SMS sending and receiving |
| `fengni-bridge` | MQTTF integration — connects the app to `fengni-mqtt-proxy` |

## Server Component

The `proxy/` directory contains the `fengni-mqtt-proxy` Rust project. It builds both:

- A standalone binary (`fengni-mqtt-proxy`) for running as a network service
- A JNI `cdylib` for in-process embedding
