# Configuration / 配置

Each device has a `FengniConfig` containing:

| Field | Description / 描述 |
|-------|-------------------|
| `customerId` | Shared across all devices in a sync group / 同步组内所有设备共享 |
| `deviceId` | Unique per device / 每台设备唯一 |
| `serverPubKey` | Proxy identity public key (derived from `--identity` private key) / 代理身份公钥 |
| `remoteHost` / `remotePort` | fengni-mqtt-proxy listen address / 代理监听地址 |
| `routePubKey` | Routing public key for multi-group deployments / 多组路由公钥 |
| `groupId` | Group identifier for multi-group routing / 多组路由标识 |
| `hmacKey` | HMAC key for message authentication / 消息认证密钥 |
| `paddingMax` | Maximum padding size for traffic analysis resistance / 抗流量分析最大填充 |

Devices with the same `customerId` and `serverPubKey` but different `deviceId`s form a sync group. All messages flow through the MQTT broker.

共享相同 `customerId` 和 `serverPubKey` 但 `deviceId` 不同的设备组成同步组，所有消息经由 MQTT 代理流转。

## Config Generation / 配置生成

Use the deployment generator to create config files for all devices:

```powershell
pwsh fengni-bridge/tools/generate-fengni-deployment.ps1
```

This produces per-device `fengni.key` + `fengni.conf` pairs, along with server-side `proxy-config.json` and identity files.

使用部署生成器为所有设备创建配置文件，产出每台设备的 `fengni.key` + `fengni.conf` 对，以及服务端的 `proxy-config.json` 和 identity 文件。
