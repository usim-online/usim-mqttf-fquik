# 消息流

## 接收（SIM 设备）

```
SMS ContentProvider
  → ContentObserver
  → FengniMqttService.forwardIncomingSms()
  → FengniMessageStore.insert()
  → MQTT publish
  → FengniSyncRepository.syncMessage()
  → Realm 写入
  → 通知
```

SIM 设备通过 Android SMS ContentProvider 捕获短信，经 Fengni 加密后通过 MQTT 发布到同步组。

## 接收（远程设备）

```
MQTT 订阅者
  → FengniMqttService.messageLoop()
  → 解析 topic
  → FengniMessageStore.insert()
  → FengniSyncRepository.syncMessage()
  → Realm 写入
  → 通知（仅 incoming）
```

远程设备通过 MQTT 订阅接收加密消息，解密后写入本地存储并触发通知。

## 发送

```
ComposeActivity
  → QkTransaction.sendSmsMessage()（fengni:// URI）
  → FengniMqttService.enqueueOutgoing()
  → MQTT publish
  → markSent / markFailed
  → Realm 更新
```

发送流程使用 `fengni://` URI 方案，消息经 Fengni 加密后通过 MQTT 发布，随后根据结果更新消息状态。

## 关键组件

| 组件 | 职责 |
|------|------|
| `FengniMqttService` | MQTT 连接管理、消息收发调度 |
| `FengniSyncRepository` | 同步组消息同步逻辑 |
| `FengniMessageStore` | 消息持久化与查询 |
| `FengniMessageMapper` | 消息格式转换 |
| `QkTransaction` | 发送事务管理，状态追踪 |
