# Introduction / 简介

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

## Known Issues / 已知问题

| # | Issue / 问题 | Severity |
|---|-------------|----------|
| 1 | App online status and message sending have Realm synchronization issues causing UI inconsistencies / App 在线状态、消息发送等存在 Realm 同步问题导致 UI 异常 | Medium |
| 2 | Transparent proxy does not persist history — no offline message storage or replay across reconnections / 透明代理不存储历史记录——无离线消息持久化或重连回放 | Medium |
| 3 | App has not been modified for non-text content delivery — MMS, media, and other non-SMS payloads are not adapted / App 未对非文本内容发送进行改造——MMS、媒体等非 SMS 载荷未适配 | Low |

> Fquik serves as a demonstration of the Fengni protocol's capabilities and application scenarios. / Fquik 作为 Fengni 协议先进性与应用场景的 Demo 存在。
