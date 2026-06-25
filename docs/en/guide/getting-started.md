# Introduction

Fquik is a multi-device encrypted messaging sync application built on [Quik SMS](https://github.com/octoshrimpy/quik). It replaces the Android SMS delivery pipeline with **MQTTF** — a protocol stack combining **MQTT 5** for message transport and the [**Fengni**](https://github.com/linkbitflower/fengni-v1) protocol for end-to-end encryption.

Devices sharing the same `customerId` form a sync group — messages composed on one device appear instantly on all others.

## What is MQTTF?

**MQTTF** = **MQTT 5** + **Fengni** — an encrypted messaging protocol stack:

- **MQTT 5** handles message routing, pub/sub, and QoS guarantees
- [**Fengni**](https://github.com/linkbitflower/fengni-v1) provides end-to-end encryption, replay protection, and traffic padding

The `fengni-mqtt-proxy` sits between clients and a plaintext MQTT broker (e.g. Mosquitto), performing Fengni handshakes and encrypting/decrypting all traffic. Clients never communicate in plaintext — even the MQTT broker only sees ciphertext.

## Known Issues

| # | Issue | Severity |
|---|-------|----------|
| 1 | App online status and message sending have Realm synchronization issues causing UI inconsistencies | Medium |
| 2 | Transparent proxy does not persist history — no offline message storage or replay across reconnections | Medium |
| 3 | App has not been modified for non-text content delivery — MMS, media, and other non-SMS payloads are not adapted | Low |

> Fquik serves as a demonstration of the Fengni protocol's capabilities and application scenarios.
