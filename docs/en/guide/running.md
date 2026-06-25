# Running

This guide walks through starting the full Fquik stack end-to-end.

## Step 1: Start Mosquitto

Start the Mosquitto broker on port 1884:

```sh
mosquitto -p 1884
```

Verify it is listening:

```sh
mosquitto_sub -h 127.0.0.1 -p 1884 -t "test"
```

## Step 2: Start fengni-mqtt-proxy

```sh
./fengni-mqtt-proxy \
  --listen 0.0.0.0:1883 \
  --backend 127.0.0.1:1884 \
  --identity <key-file>
```

On startup the proxy prints its **serverPubKey**. Record this value — it is required in the Android configuration.

The proxy listens on port 1883 for encrypted Fengni connections and forwards decrypted traffic to Mosquitto on port 1884.

## Step 3: Deploy Configuration to the Device

The Android app needs two files placed in its private storage:

```
Android/data/<package>/files/fengni/fengni.key
Android/data/<package>/files/fengni/fengni.conf
```

Use the deployment script to generate both:

```powershell
./generate-fengni-deployment.ps1
```

Then push the files to the device via `adb push` or a file manager.

## Step 4: Verify

Open the app on each device in the sync group and confirm:

1. The MQTT handshake completes without errors
2. Subscriptions are established
3. A test message sent from one device appears on the others
4. Conversations populate in the UI
5. Notifications arrive for incoming messages

If the handshake fails, double-check that `serverPubKey` in `fengni.conf` matches the key printed by the proxy on startup.
