# 运行

## 1. 启动 Mosquitto

启动 MQTT 代理，监听端口 1884：

```bash
mosquitto -p 1884
```

## 2. 启动 fengni-mqtt-proxy

```bash
./fengni-mqtt-proxy \
  --listen 0.0.0.0:1883 \
  --backend 127.0.0.1:1884 \
  --identity <密钥文件>
```

proxy 启动后会打印 `serverPubKey`，记下此值——后续配置需要使用。

## 3. 部署设备配置

将 `fengni.key` 和 `fengni.conf` 部署到 Android 设备：

```
Android/data/<包名>/files/fengni/
```

使用 `generate-fengni-deployment.ps1` 脚本生成部署文件：

```powershell
./generate-fengni-deployment.ps1
```

## 4. 验证

按以下检查项确认系统运行正常：

- [ ] 握手成功——proxy 日志无握手错误
- [ ] 订阅成功——MQTT 订阅已建立
- [ ] 无错误——应用日志无异常
- [ ] 会话显示——消息列表正确展示
- [ ] 通知到达——新消息触发系统通知
