# Build

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| JDK | 17 | Set `JAVA_HOME` to the JDK 17 installation path |
| Android SDK | compileSdk 34 | Install via Android Studio or `sdkmanager` |
| Rust toolchain | stable | Optional, only needed to build the proxy |

## Android APK

```sh
JAVA_HOME=/path/to/jdk17 ./gradlew :presentation:assembleRelease -x test
```

The signed APK is output to:

```
presentation/build/outputs/apk/release/
```

### Signing

Release builds require a keystore. Configure it in one of:

- `gradle.properties` or project-level `gradle.properties`
- `~/.gradle/gradle.properties`

Required properties:

```properties
RELEASE_STORE_FILE=/path/to/keystore.jks
RELEASE_STORE_PASSWORD=*****
RELEASE_KEY_ALIAS=your-alias
RELEASE_KEY_PASSWORD=*****
```

## Rust Proxy

```sh
cd proxy && cargo build --release
```

The binary is output to:

```
proxy/target/release/fengni-mqtt-proxy
```

The Rust toolchain is optional. If you only need the Android app and already have a pre-built proxy binary, you can skip this step.
