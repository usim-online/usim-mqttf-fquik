---
layout: home

hero:
  name: Fquik
  text: Multi-device Encrypted Messaging Sync
  tagline: Based on Quik SMS · Powered by MQTTF (MQTT 5 + Fengni)
  actions:
    - theme: brand
      text: Get Started / 开始使用
      link: /guide/getting-started
    - theme: alt
      text: View on GitHub
      link: https://github.com/usim-online/usim-mqttf-fquik

features:
  - icon: 🔐
    title: End-to-end Encryption / 端到端加密
    details: All messages encrypted via Fengni protocol before leaving the device. No plaintext traverses the network at any point. / 所有消息在离设备前经 Fengni 协议加密，全程无明文传输。
  - icon: 📡
    title: MQTTF Protocol Stack / MQTTF 协议栈
    details: MQTT 5 for message transport + Fengni for encryption, replay protection, and traffic padding — a unified encrypted messaging stack. / MQTT 5 负责消息传输 + Fengni 负责加密、重放保护和流量填充——统一的加密消息协议栈。
  - icon: 📱
    title: Multi-device Sync / 多设备同步
    details: Devices sharing the same customer ID form a sync group. Messages appear instantly across all devices. / 共享相同 customer ID 的设备组成同步组，消息即时出现在所有设备上。
  - icon: 🛡️
    title: Traffic Analysis Resistance / 防流量分析
    details: Configurable padding randomizes packet lengths to prevent size-based behavior analysis. / 可配置填充随机化包长度，防止基于大小的行为分析。
  - icon: 🔗
    title: Device Identity & Isolation / 设备身份与隔离
    details: Each device uniquely identified by deviceId. Sync groups isolated by customerId — unauthorized devices cannot access group messages. / 每台设备由 deviceId 唯一标识，同步组由 customerId 隔离，未授权设备无法接入。
  - icon: 🔑
    title: Local Key Management / 本地密钥管理
    details: Encryption keys reside only on the device's local storage, never synced or uploaded. Loss is unrecoverable. / 加密密钥仅存储在设备本地，不同步不上传，丢失无法恢复。
---
