# Security / 安全

- **End-to-end encryption / 端到端加密**: All messages are encrypted via the Fengni protocol before leaving the device. Config files are additionally protected with AES-256-GCM. No plaintext traverses the network at any point. / 所有消息在离设备前经 Fengni 协议加密，配置文件额外使用 AES-256-GCM 保护，全程无明文传输。

- **Traffic analysis resistance / 防流量分析**: `paddingMax` randomizes packet lengths to prevent size-based behavior analysis. / `paddingMax` 随机化包长度，防止基于大小的行为分析。

- **Device identity & isolation / 设备身份与隔离**: Each device is uniquely identified by `deviceId`. Sync groups are isolated by `customerId` — unauthorized devices cannot access group messages. / 每台设备由 `deviceId` 唯一标识，同步组由 `customerId` 隔离，未授权设备无法接入同组消息。

- **Key management / 密钥管理**: `fengni.key` resides only on the device's local storage and is never synced or uploaded. Loss of this key makes the configuration unrecoverable. / `fengni.key` 仅存储在设备本地，不同步不上传，丢失后无法恢复配置。
