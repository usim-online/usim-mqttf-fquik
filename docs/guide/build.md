# Build / 构建

## Prerequisites / 前置条件

- JDK 17 (must set `JAVA_HOME` explicitly if system default differs / 若系统默认不是 JDK 17 须显式设置)
- Android SDK with compileSdk 34
- Rust toolchain (for proxy, optional / 仅 proxy 需要，可选)

## Android App / 安卓应用

```bash
JAVA_HOME=/path/to/jdk17 ./gradlew :presentation:assembleRelease -x test
```

The release APK is output to `presentation/build/outputs/apk/release/`.

Signing requires a keystore and a `.gradle/.gradlerc` file (excluded from version control):

```properties
keyAlias=<alias>
storePassword=<password>
keyPassword=<password>
```

## Rust Proxy / Rust 代理

```bash
cd proxy
cargo build --release
```

The binary is `proxy/target/release/fengni-mqtt-proxy`.
