<#
.SYNOPSIS
    Multi-group fengni deployment generator.
.DESCRIPTION
    Interactive script that generates everything needed to deploy fengni-mqtt-proxy
    in multi-group mode (encrypted routing headers) and its client devices:

      Server side:
        - route.identity          X25519 routing private key (32-byte hex)
        - group-<N>.identity      X25519 group identity private keys
        - proxy-config.json       multi-group proxy configuration

      Device side (per device):
        - fengni.key              AES-256 key for config decryption
        - fengni.conf             AES-256-GCM encrypted config (all fields filled)

    If fengni-mqtt-proxy is found on PATH or via --proxy-bin, public keys
    are derived automatically. Otherwise they must be provided manually.

    Legacy single-group mode is still supported: run without adding groups
    to generate a traditional allowlist.json instead.
.NOTES
    Requires PowerShell 7+ (pwsh.exe) for AES-GCM support.
#>

$ErrorActionPreference = "Stop"

# ── Check runtime ──────────────────────────────────────────────
if ($PSVersionTable.PSVersion.Major -lt 7) {
    Write-Host "ERROR: This script requires PowerShell 7+." -ForegroundColor Red
    exit 1
}
if (-not ([System.Security.Cryptography.AesGcm]::IsSupported)) {
    Write-Host "ERROR: AES-GCM is not supported on this .NET runtime." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  fengni Deployment Generator (Multi-Group)"         -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# ── 1/7  Output directory ──────────────────────────────────────
$defaultDir = Join-Path $PSScriptRoot "output"
$outDir = Read-Host "1/7  Output directory [$defaultDir]"
if ($outDir -eq "") { $outDir = $defaultDir }
$outDir = [IO.Path]::GetFullPath($outDir)
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# ── 2/7  Server connection ─────────────────────────────────────
$remoteHost = Read-Host "2/7  Remote host (e.g. broker.example.com)"
while ($remoteHost -eq "") { $remoteHost = Read-Host "     (required) Remote host" }

$portStr = Read-Host "     Remote port [443]"
if ($portStr -eq "") { $portStr = "443" }
$remotePort = [int]$portStr

# ── 3/7  Customer ID ──────────────────────────────────────────
$customerId = Read-Host "3/7  Customer ID (e.g. cust-001)"
while ($customerId -eq "") { $customerId = Read-Host "     (required) Customer ID" }

# ── 4/7  Padding ──────────────────────────────────────────────
$paddingStr = Read-Host "4/7  Padding max bytes [64]"
if ($paddingStr -eq "") { $paddingStr = "64" }
$paddingMax = [int]$paddingStr

# ── 5/7  Proxy binary (for public key derivation) ─────────────
$proxyBin = Read-Host "5/7  fengni-mqtt-proxy path (Enter to skip auto-derive)"
if ($proxyBin -ne "" -and -not (Test-Path $proxyBin)) {
    Write-Host "     WARNING: $($proxyBin) not found, will prompt for public keys" -ForegroundColor Yellow
    $proxyBin = ""
}

# ── 6/7  Routing identity ─────────────────────────────────────
$routeIdentityInput = Read-Host "6/7  Route identity file path [route.identity]"
if ($routeIdentityInput -eq "") { $routeIdentityInput = "route.identity" }

$routeIdentityLeaf = Split-Path $routeIdentityInput -Leaf
$routeIdentityFile = Join-Path $outDir $routeIdentityLeaf

if (Test-Path $routeIdentityInput) {
    Copy-Item $routeIdentityInput $routeIdentityFile -Force
    Write-Host ""
    Write-Host "  Reusing existing route identity: $routeIdentityInput" -ForegroundColor Green
} else {
    $routeKeyBytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($routeKeyBytes)
    $routeKeyHex = -join ($routeKeyBytes | ForEach-Object { $_.ToString("x2") })
    [IO.File]::WriteAllText($routeIdentityFile, $routeKeyHex, [Text.Encoding]::ASCII)
    Write-Host ""
    Write-Host "  Generated new route identity: $routeIdentityFile" -ForegroundColor Green
}

# Derive route public key
$routePubKey = ""
if ($proxyBin -ne "") {
    try {
        $result = & $proxyBin --show-route-pubkey $routeIdentityFile 2>$null
        if ($result -and $result.Trim().Length -eq 64) {
            $routePubKey = $result.Trim()
            Write-Host "  Route public key: $($routePubKey.Substring(0,16))..." -ForegroundColor Green
        } else {
            Write-Host "  WARNING: --show-route-pubkey returned unexpected output" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  WARNING: --show-route-pubkey failed: $_" -ForegroundColor Yellow
    }
}

if ($routePubKey -eq "") {
    Write-Host ""
    Write-Host "  Cannot auto-derive route public key. Run manually:" -ForegroundColor Yellow
    Write-Host "    fengni-mqtt-proxy --show-route-pubkey $routeIdentityFile" -ForegroundColor Gray
    Write-Host ""
    $routePubKey = Read-Host "  Route public key (64 hex chars)"
    while ($routePubKey.Length -ne 64) {
        Write-Host "     Must be exactly 64 hex characters, got $($routePubKey.Length)" -ForegroundColor Yellow
        $routePubKey = Read-Host "     Route public key"
    }
}

# ── 7/7  Backend address ──────────────────────────────────────
$backendStr = Read-Host "7/7  Mosquitto backend address [127.0.0.1:1884]"
if ($backendStr -eq "") { $backendStr = "127.0.0.1:1884" }

# ── Collect groups ─────────────────────────────────────────────
$groups = @()
$allDeviceConfigs = @{}
$groupNum = 0

Write-Host ""
Write-Host "  Add groups (group_id 0 to finish):" -ForegroundColor White
Write-Host ""

while ($true) {
    $groupNum++
    $groupIdStr = Read-Host "  Group $groupNum ID (Enter to finish)"
    if ($groupIdStr -eq "") { break }
    $groupId = [int]$groupIdStr
    if ($groupId -le 0) { break }
    if ($groupId -gt 65535) {
        Write-Host "     group_id must be 1-65535" -ForegroundColor Yellow
        continue
    }
    # Check for duplicate group_id
    if ($groups | Where-Object { $_.groupId -eq $groupId }) {
        Write-Host "     group_id $groupId already exists" -ForegroundColor Yellow
        continue
    }

    # Group identity
    $groupIdentityInput = Read-Host "     Group $groupId identity file path [group-$groupId.identity]"
    if ($groupIdentityInput -eq "") { $groupIdentityInput = "group-$groupId.identity" }
    $groupIdentityLeaf = Split-Path $groupIdentityInput -Leaf
    $groupIdentityFile = Join-Path $outDir $groupIdentityLeaf

    if (Test-Path $groupIdentityInput) {
        Copy-Item $groupIdentityInput $groupIdentityFile -Force
        Write-Host "     Reusing existing identity: $groupIdentityInput" -ForegroundColor Green
    } else {
        $privKeyBytes = [byte[]]::new(32)
        [Security.Cryptography.RandomNumberGenerator]::Fill($privKeyBytes)
        $privKeyHex = -join ($privKeyBytes | ForEach-Object { $_.ToString("x2") })
        [IO.File]::WriteAllText($groupIdentityFile, $privKeyHex, [Text.Encoding]::ASCII)
        Write-Host "     Generated new identity: $groupIdentityFile" -ForegroundColor Green
    }

    # Derive server public key for this group
    $serverPubKey = ""
    if ($proxyBin -ne "") {
        try {
            $result = & $proxyBin --show-pubkey $groupIdentityFile 2>$null
            if ($result -and $result.Trim().Length -eq 64) {
                $serverPubKey = $result.Trim()
                Write-Host "     Server public key: $($serverPubKey.Substring(0,16))..." -ForegroundColor Green
            } else {
                Write-Host "     WARNING: --show-pubkey returned unexpected output" -ForegroundColor Yellow
            }
        } catch {
            Write-Host "     WARNING: --show-pubkey failed: $_" -ForegroundColor Yellow
        }
    }

    if ($serverPubKey -eq "") {
        $serverPubKey = Read-Host "     Group $groupId server public key (64 hex chars)"
        while ($serverPubKey.Length -ne 64) {
            Write-Host "        Must be exactly 64 hex characters" -ForegroundColor Yellow
            $serverPubKey = Read-Host "        Server public key"
        }
    }

    # Collect devices for this group
    $devices = @{}
    $devNum = 0
    Write-Host ""
    Write-Host "     Add devices for group $groupId (empty ID to finish):" -ForegroundColor White

    while ($true) {
        $devNum++
        $deviceId = Read-Host "     Device $devNum ID (Enter to finish)"
        if ($deviceId -eq "") { break }

        $hmacKeyBytes = [byte[]]::new(32)
        [Security.Cryptography.RandomNumberGenerator]::Fill($hmacKeyBytes)
        $hmacKey = -join ($hmacKeyBytes | ForEach-Object { $_.ToString("x2") })

        $enabledStr = Read-Host "     Enabled? [Y/n]"
        $enabled = ($enabledStr -ne "n" -and $enabledStr -ne "N")

        $devices[$deviceId] = @{
            hmacKey = $hmacKey
            enabled = $enabled
        }

        # Save device config data for later fengni.conf generation
        $allDeviceConfigs[$deviceId] = @{
            groupId       = $groupId
            serverPubKey  = $serverPubKey
            hmacKey       = $hmacKey
        }

        Write-Host "     Added: $deviceId (HMAC: $($hmacKey.Substring(0,8))...)" -ForegroundColor Green
    }

    if ($devices.Count -eq 0) {
        Write-Host "     No devices in group $groupId, skipping group" -ForegroundColor Yellow
        continue
    }

    $groups += @{
        groupId      = $groupId
        identityLeaf = $groupIdentityLeaf
        serverPubKey = $serverPubKey
        devices      = $devices
    }
    Write-Host ""
}

if ($groups.Count -eq 0) {
    Write-Host "No groups added. Aborting." -ForegroundColor Red
    exit 1
}

# ── Generate proxy-config.json ─────────────────────────────────
$proxyConfigGroups = @()
foreach ($g in $groups) {
    $deviceEntries = @{}
    foreach ($key in $g.devices.Keys) {
        $deviceEntries[$key] = @{
            hmac_key = $g.devices[$key].hmacKey
            enabled  = $g.devices[$key].enabled
        }
    }
    $proxyConfigGroups += @{
        group_id      = $g.groupId
        identity_path = $g.identityLeaf
        padding_max   = $paddingMax
        devices       = $deviceEntries
    }
}

$proxyConfig = @{
    route_identity_path    = $routeIdentityLeaf
    backend                = $backendStr
    groups                 = $proxyConfigGroups
}

$proxyConfigJson = $proxyConfig | ConvertTo-Json -Depth 4
$proxyConfigFile = Join-Path $outDir "proxy-config.json"
[IO.File]::WriteAllText($proxyConfigFile, $proxyConfigJson, [Text.UTF8Encoding]::new($false))

Write-Host "──────────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host "  Proxy config : $proxyConfigFile" -ForegroundColor Green
Write-Host "  Route ident  : $routeIdentityFile" -ForegroundColor Green
foreach ($g in $groups) {
    $gFile = Join-Path $outDir $g.identityLeaf
    Write-Host "  Group $($g.groupId) ident : $gFile" -ForegroundColor Green
}
Write-Host ""

# ── Generate per-device fengni.key + fengni.conf ───────────────
foreach ($devKey in $allDeviceConfigs.Keys) {
    $devConf = $allDeviceConfigs[$devKey]
    $devDir = Join-Path $outDir $devKey
    New-Item -ItemType Directory -Force -Path $devDir | Out-Null

    # AES-256 key for config file encryption
    $keyBytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($keyBytes)
    $keyHex = -join ($keyBytes | ForEach-Object { $_.ToString("x2") })

    # Plaintext JSON (all fields filled, including routePubKey and groupId)
    $plaintext = @{
        remoteHost   = $remoteHost
        remotePort   = $remotePort
        serverPubKey = $devConf.serverPubKey
        routePubKey  = $routePubKey
        groupId      = $devConf.groupId
        customerId   = $customerId
        deviceId     = $devKey
        hmacKey      = $devConf.hmacKey
        paddingMax   = $paddingMax
    } | ConvertTo-Json -Compress

    $plainBytes = [Text.Encoding]::UTF8.GetBytes($plaintext)

    # AES-256-GCM encrypt
    $nonce      = [byte[]]::new(12)
    [Security.Cryptography.RandomNumberGenerator]::Fill($nonce)
    $tag        = [byte[]]::new(16)
    $ciphertext = [byte[]]::new($plainBytes.Length)
    $aes = [System.Security.Cryptography.AesGcm]::new($keyBytes)
    try {
        $aes.Encrypt($nonce, $plainBytes, $ciphertext, $tag)
    } finally {
        $aes.Dispose()
    }
    $encrypted = $nonce + $ciphertext + $tag

    $devKeyFile  = Join-Path $devDir "fengni.key"
    $devConfFile = Join-Path $devDir "fengni.conf"
    [IO.File]::WriteAllText($devKeyFile, $keyHex, [Text.Encoding]::ASCII)
    [IO.File]::WriteAllBytes($devConfFile, $encrypted)

    Write-Host "  Device '$devKey' (group $($devConf.groupId)):" -ForegroundColor White
    Write-Host "    Key  : $devKeyFile" -ForegroundColor Gray
    Write-Host "    Conf : $devConfFile" -ForegroundColor Gray
}

# ── Summary ────────────────────────────────────────────────────
Write-Host ""
Write-Host "════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Done. $($groups.Count) group(s), $($allDeviceConfigs.Count) device(s)" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Server deployment (copy to proxy server):" -ForegroundColor White
Write-Host "    $proxyConfigFile" -ForegroundColor Gray
foreach ($g in $groups) {
    $gFile = Join-Path $outDir $g.identityLeaf
    Write-Host "    $gFile" -ForegroundColor Gray
}
Write-Host "    $routeIdentityFile" -ForegroundColor Gray
Write-Host "    fengni-mqtt-proxy --proxy-config proxy-config.json --backend $($backendStr)" -ForegroundColor Gray
Write-Host ""
Write-Host "  Device deployment (per device):" -ForegroundColor White
Write-Host "    adb push <deviceId>/fengni.key /sdcard/Android/data/<app>/files/fengni/" -ForegroundColor Gray
Write-Host "    adb push <deviceId>/fengni.conf /sdcard/Android/data/<app>/files/fengni/" -ForegroundColor Gray
Write-Host "════════════════════════════════════════════════════" -ForegroundColor Cyan
