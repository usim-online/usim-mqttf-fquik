# 构建

## 前置条件

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 17 | 须显式设置 `JAVA_HOME` 环境变量 |
| Android SDK | compileSdk 34 | 通过 Android Studio 或命令行工具安装 |
| Rust 工具链 | 稳定版 | 可选，仅构建 proxy 时需要 |

## 安卓应用

确保 `JAVA_HOME` 已指向 JDK 17：

```bash
export JAVA_HOME=/path/to/jdk17
```

执行构建：

```bash
./gradlew :presentation:assembleRelease -x test
```

APK 输出路径：

```
presentation/build/outputs/apk/release/
```

签名需要提供 keystore 并在 `.gradle/` 或 `.gradlerc` 中配置签名信息。

## Rust 代理

仅当需要构建 `fengni-mqtt-proxy` 时才需要 Rust 工具链：

```bash
cd proxy && cargo build --release
```

二进制文件输出到：

```
proxy/target/release/fengni-mqtt-proxy
```
