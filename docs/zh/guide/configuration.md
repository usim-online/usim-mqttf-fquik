# 配置

## FengniConfig 字段

| 字段 | 说明 |
|------|------|
| `customerId` | 同步组共享标识，相同值的设备属于同一同步组 |
| `deviceId` | 设备唯一标识，区分组内不同设备 |
| `serverPubKey` | 代理身份公钥，客户端用于验证 proxy |
| `remoteHost` | 代理服务器地址 |
| `remotePort` | 代理服务器端口 |
| `routePubKey` | 多组路由公钥 |
| `groupId` | 消息路由组标识 |
| `hmacKey` | 消息认证密钥，用于验证消息完整性 |
| `paddingMax` | 抗流量分析填充上限，随机化包长度 |

## 同步组

相同 `customerId` + `serverPubKey` 但不同 `deviceId` 的设备组成同步组。组内任一设备发送的消息会即时同步到其他设备。

## 生成配置

使用 `generate-fengni-deployment.ps1` 生成 `fengni.key` 和 `fengni.conf`：

```powershell
./generate-fengni-deployment.ps1
```

该脚本会自动生成所有必要字段，无需手动编辑配置文件。
