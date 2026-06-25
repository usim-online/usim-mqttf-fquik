# Message Flow

## Incoming (SIM Device)

When the device has a SIM and receives an SMS:

```
SMS ContentProvider
  → ContentObserver
  → FengniMqttService.forwardIncomingSms()
  → FengniMessageStore.insert()
  → MQTT publish
  → FengniSyncRepository.syncMessage()
  → Realm write
  → notification
```

The SMS is read from the system `ContentProvider`, persisted locally, published to the MQTT topic for the sync group, and a notification is posted.

## Incoming (Remote Device)

When a device receives a synced message from MQTT:

```
MQTT subscriber
  → FengniMqttService.messageLoop()
  → parse topic
  → FengniMessageStore.insert()
  → FengniSyncRepository.syncMessage()
  → Realm write
  → notification (incoming only)
```

Remote devices do not re-publish the message. Notifications are generated for incoming messages only.

## Outgoing

When the user sends a message:

```
ComposeActivity
  → QkTransaction.sendSmsMessage() (fengni:// URI)
  → FengniMqttService.enqueueOutgoing()
  → MQTT publish
  → markSent / markFailed
  → Realm update
```

The message is enqueued for MQTT delivery. On success the message is marked as sent; on failure it is marked as failed. The Realm database is updated accordingly.

## Key Components

| Component | Role |
|-----------|------|
| `FengniMqttService` | Manages MQTT connection, publishes outgoing messages, subscribes to incoming topics |
| `FengniSyncRepository` | Orchestrates message synchronization between MQTT and local storage |
| `FengniMessageStore` | Inserts and queries messages in the local data layer |
| `FengniMessageMapper` | Maps between domain models and MQTT message format |
| `QkTransaction` | Handles send operations using `fengni://` URI scheme |
