# Configuration

Fquik is configured through the `FengniConfig` file deployed to each device. This page documents every field and explains how devices form sync groups.

## FengniConfig Fields

| Field | Description |
|-------|-------------|
| `customerId` | Shared identifier for the sync group. All devices with the same `customerId` belong to the same group. |
| `deviceId` | Unique identifier per device. Must differ across devices within the same sync group. |
| `serverPubKey` | The public key of the `fengni-mqtt-proxy` identity. Printed by the proxy on startup. |
| `remoteHost` | Hostname or IP address of the proxy. |
| `remotePort` | Port the proxy listens on (default `1883`). |
| `routePubKey` | Public key for multi-group routing. Used when forwarding messages across different sync groups. |
| `groupId` | Logical group identifier within the sync group. |
| `hmacKey` | Key used for message authentication. |
| `paddingMax` | Maximum padding bytes added to each packet. Randomizes packet lengths to resist traffic analysis. |

## Sync Groups

Devices with the same `customerId` and `serverPubKey` but different `deviceId` values form a **sync group**. Messages published by any member are delivered to all other members.

```
customerId: "alice"   deviceId: "phone"   ─┐
customerId: "alice"   deviceId: "tablet"  ─┤── sync group
customerId: "alice"   deviceId: "laptop"  ─┘

customerId: "bob"     deviceId: "phone"   ─── different sync group
```

A device cannot belong to more than one sync group. To change groups, update `customerId` and redeploy the configuration.

## Config Generation

Use the provided PowerShell script to generate deployment files:

```powershell
./generate-fengni-deployment.ps1
```

The script produces `fengni.key` and `fengni.conf` ready for deployment to the device.
