# Message Flow / 消息流

## Incoming (SIM device receives cellular SMS) / SIM 设备接收短信

```
SMS ContentProvider → ContentObserver → FengniMqttService.forwardIncomingSms()
  → FengniMessageStore.insert() → MQTT publish → FengniSyncRepository.syncMessage()
  → Realm write → notification
```

## Incoming (remote device receives via MQTT) / 远程设备接收

```
MQTT subscriber → FengniMqttService.messageLoop()
  → parse topic → FengniMessageStore.insert() → FengniSyncRepository.syncMessage()
  → Realm write → notification (incoming topic only)
```

## Outgoing (user sends a message) / 用户发送

```
ComposeActivity → QkTransaction.sendSmsMessage() (fengni:// URI)
  → FengniMqttService.enqueueOutgoing() → MQTT publish
  → markSent / markFailed → Realm update
```

## Key Components / 关键组件

| Component | Role / 职责 |
|-----------|------------|
| `FengniMqttService` | Foreground service managing MQTT lifecycle, connection loop with exponential backoff, and message dispatch / 管理 MQTT 生命周期的前台服务 |
| `FengniSyncRepository` | Replaces original SyncRepositoryImpl — reads from FengniMessageStore instead of Android ContentProvider / 替代原始 SyncRepositoryImpl |
| `FengniMessageStore` | In-memory ConcurrentHashMap persisted to `fengni_messages.json` / 内存 ConcurrentHashMap 持久化到 JSON |
| `FengniMessageMapper` | Converts FengniMessage → Realm Message/Conversation/Recipient / 格式转换 |
| `QkTransaction` | Send pipeline using `fengni://` URI scheme to bypass ContentProvider-based SMS send / 使用 fengni:// URI 绕过 ContentProvider 发送管道 |
