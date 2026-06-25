# Running / 运行

## 1. Start Mosquitto (plaintext broker / 明文代理)

```bash
mosquitto -p 1884 -v
```

## 2. Start fengni-mqtt-proxy (encrypted gateway / 加密网关)

```bash
fengni-mqtt-proxy \
  --listen 0.0.0.0:1883 \
  --backend 127.0.0.1:1884 \
  --identity <identity-key-file>
```

The proxy prints its server public key on startup — this is the `serverPubKey` value for client config.

## 3. Configure the Android app / 配置安卓应用

Deploy two files to the app's external storage directory (`Android/data/<package>/files/fengni/`):

- `fengni.key` — AES-256 key (hex-encoded, 32 bytes) for config decryption
- `fengni.conf` — AES-256-GCM encrypted config containing `customerId`, `deviceId`, `serverPubKey`, and connection details

Use `fengni-bridge/tools/generate-fengni-deployment.ps1` to generate these files.

## 4. Verify / 验证

1. Proxy console shows "Connection from ..." — Fengni handshake succeeded / 握手成功
2. Mosquitto `-v` output shows SUBSCRIBE — MQTT subscription active / 订阅成功
3. No "error" in proxy console — encryption/decryption working / 加解密正常
4. App displays conversations — Realm write + UI refresh working / 数据写入与刷新正常
5. Push notifications arrive — notification chain complete (requires `POST_NOTIFICATIONS` permission) / 通知链路完整
