# 架构

## 网络拓扑

```
                    fengni-mqtt-proxy
                    ┌─────────────────────────┐
                    │  Rust                    │
  :1883 加密入口 ◄──┤                         ├──► :1884 Mosquitto
                    │  Fengni 握手 + 加解密     │
                    └─────────────────────────┘
                              ▲
                              │ MQTTF 隧道
                  ┌───────────┼───────────┐
                  │           │           │
             Device A    Device B    Device C
```

所有客户端与 proxy 之间的流量经 Fengni 协议加密。MQTT 代理仅看到密文。

## 安卓模块

| 模块 | 职责 |
|------|------|
| `presentation` | 界面层——Compose UI、Activity、ViewModel |
| `domain` | 业务逻辑——用例、交互规则 |
| `data` | 数据层——Repository、数据源 |
| `common` | 公共工具——扩展函数、工具类 |
| `android-smsmms` | 遗留 MMS/SMS 处理 |
| `fengni-bridge` | MQTTF 集成——Fengni 协议绑定、MQTT 客户端 |

## 服务端组件

| 路径 | 说明 |
|------|------|
| `proxy/` | `fengni-mqtt-proxy` Rust 二进制 + JNI cdylib |
