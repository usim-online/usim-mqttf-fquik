---
layout: home

hero:
  name: Fquik
  text: 多设备加密消息同步
  tagline: 基于 Quik SMS · 驱动于 MQTTF（MQTT 5 + Fengni）
  actions:
    - theme: brand
      text: 开始使用
      link: /zh/guide/getting-started
    - theme: alt
      text: GitHub
      link: https://github.com/usim-online/usim-mqttf-fquik

features:
  - icon: 🔐
    title: 端到端加密
    details: 所有消息在离设备前经 Fengni 协议加密，全程无明文传输。
  - icon: 📡
    title: MQTTF 协议
    details: MQTT 5 负责传输 + Fengni 负责加密、重放保护和流量填充。
  - icon: 📱
    title: 多设备同步
    details: 共享相同 customer ID 的设备组成同步组，消息即时出现在所有设备上。
  - icon: 🛡️
    title: 防流量分析
    details: 可配置填充随机化包长度，防止基于大小的行为分析。
  - icon: 🔗
    title: 设备隔离
    details: 每台设备唯一标识，同步组相互隔离，未授权设备无法接入。
  - icon: 🔑
    title: 本地密钥管理
    details: 密钥仅存储在设备本地，不同步不上传，丢失无法恢复。
---
