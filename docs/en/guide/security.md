# Security

## End-to-End Encryption

All message traffic is encrypted end-to-end using the [Fengni](https://github.com/linkbitflower/fengni-v1) protocol. No plaintext messages traverse the network — even the Mosquitto broker only sees ciphertext.

## Traffic Analysis Resistance

The `paddingMax` configuration field controls packet padding. Each outgoing packet is padded with a random number of bytes (up to `paddingMax`), which randomizes packet lengths and makes size-based traffic analysis significantly harder.

## Device Identity and Isolation

- Each device has a unique `deviceId` within its sync group
- The `customerId` isolates groups — devices in different groups cannot see each other's traffic
- A device can only belong to one sync group at a time

## Key Management

- `fengni.key` is stored locally on the device and **never synced** to other devices or servers
- Loss of `fengni.key` is **unrecoverable** — the device must be re-provisioned with a new key pair
- The proxy's `serverPubKey` is distributed out-of-band (printed on startup) and manually configured in `fengni.conf`
