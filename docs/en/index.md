---
layout: home

hero:
  name: Fquik
  text: Encrypted Multi-device Messaging
  tagline: Based on Quik SMS · Powered by MQTTF (MQTT 5 + Fengni)
  actions:
    - theme: brand
      text: Get Started
      link: /en/guide/getting-started
    - theme: alt
      text: GitHub
      link: https://github.com/usim-online/usim-mqttf-fquik

features:
  - icon: 🔐
    title: End-to-end Encryption
    details: All messages encrypted via Fengni protocol before leaving the device. No plaintext traverses the network.
  - icon: 📡
    title: MQTTF Protocol
    details: MQTT 5 for transport + Fengni for encryption, replay protection, and traffic padding.
  - icon: 📱
    title: Multi-device Sync
    details: Devices sharing the same customer ID form a sync group. Messages appear instantly across all devices.
  - icon: 🛡️
    title: Traffic Analysis Resistance
    details: Configurable padding randomizes packet lengths to prevent size-based behavior analysis.
  - icon: 🔗
    title: Device Isolation
    details: Each device uniquely identified. Sync groups isolated — unauthorized devices cannot access messages.
  - icon: 🔑
    title: Local Key Management
    details: Keys reside only on device local storage, never synced or uploaded. Loss is unrecoverable.
---
