# Automated Screenshot Capture Pipeline for VahakVigil
# Usage: .\presentation-assets\capture_screenshots.ps1
# Requires an attached device/emulator via adb.

$ErrorActionPreference = "Stop"

$adbCandidates = @(
    "C:\Users\DELL\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    "adb"
)

$adbPath = $null
foreach ($cand in $adbCandidates) {
    if (Test-Path $cand) {
        $adbPath = $cand
        break
    }
    if (Get-Command $cand -ErrorAction SilentlyContinue) {
        $adbPath = $cand
        break
    }
}

if (-not $adbPath) {
    Write-Error "adb.exe not found. Please install Android Platform Tools or set Android SDK path."
    exit 1
}

Write-Host "Using ADB: $adbPath" -ForegroundColor Cyan

# Check connected device
$devices = & $adbPath devices | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "List of" }
if (-not $devices) {
    Write-Host "`n[!] No Android device or emulator currently connected." -ForegroundColor Yellow
    Write-Host "Please connect your Samsung Galaxy A55 via USB with USB Debugging enabled, or launch an emulator." -ForegroundColor Yellow
    Write-Host "Then re-run this script to capture 100% authentic live screenshots directly from the device.`n"
    exit 0
}

Write-Host "Connected Device Found: $devices" -ForegroundColor Green

$screensDir = "presentation-assets/screens"
if (-not (Test-Path $screensDir)) {
    New-Item -ItemType Directory -Force -Path $screensDir | Out-Null
}

$apkPath = "app/build/outputs/apk/debug/app-debug.apk"
if (Test-Path $apkPath) {
    Write-Host "Installing latest debug APK to device..." -ForegroundColor Cyan
    & $adbPath install -r -d $apkPath
}

# Wake device and unlock
Write-Host "Waking device..."
& $adbPath shell input keyevent 224 # KEYCODE_WAKEUP
& $adbPath shell input keyevent 82  # KEYCODE_MENU (unlock)
Start-Sleep -Seconds 1

# Launch App
Write-Host "Launching VahakVigil..."
& $adbPath shell am force-stop com.manish.vahakvigil
& $adbPath shell am start -n com.manish.vahakvigil/.MainActivity
Start-Sleep -Seconds 3 # Wait for startup animation to complete

$wmSize = & $adbPath shell wm size
$width = 1080
$height = 2400
if ($wmSize -match "(\d+)x(\d+)") {
    $width = [int]$matches[1]
    $height = [int]$matches[2]
}
Write-Host "Detected Screen Resolution: ${width}x${height}" -ForegroundColor Cyan

# 1. Home Screen
Write-Host "Capturing 1. Home Screen..." -ForegroundColor Green
& $adbPath exec-out screencap -p > "$screensDir/home.png"
Start-Sleep -Milliseconds 600

# 2. Live Ride (Tap 'Start ride')
Write-Host "Capturing 2. Live Ride & Map..." -ForegroundColor Green
# Start ride button is near 55% vertical height
$startBtnX = [int]($width * 0.5)
$startBtnY = [int]($height * 0.54)
& $adbPath shell input tap $startBtnX $startBtnY
Start-Sleep -Seconds 2
& $adbPath exec-out screencap -p > "$screensDir/live-ride.png"

# Also capture Sensor Dashboard telemetry
Write-Host "Capturing 10. Sensor Dashboard..." -ForegroundColor Green
& $adbPath exec-out screencap -p > "$screensDir/sensor-dashboard.png"

# Stop ride
& $adbPath shell input tap $startBtnX $startBtnY
Start-Sleep -Seconds 1

# Quick Access Grid coordinates:
# Medical Profile is top-left card
# Emergency Contacts is top-right card
# Ride History is bottom-left card
# Settings is bottom-right card
$qaTopY = [int]($height * 0.72)
$qaBottomY = [int]($height * 0.83)
$qaLeftX = [int]($width * 0.28)
$qaRightX = [int]($width * 0.72)

# 6. Medical Profile
Write-Host "Capturing 6. Medical Profile..." -ForegroundColor Green
& $adbPath shell input tap $qaLeftX $qaTopY
Start-Sleep -Seconds 1
& $adbPath exec-out screencap -p > "$screensDir/medical-profile.png"
& $adbPath shell input keyevent 4 # Back
Start-Sleep -Milliseconds 700

# 7. Emergency Contacts
Write-Host "Capturing 7. Emergency Contacts..." -ForegroundColor Green
& $adbPath shell input tap $qaRightX $qaTopY
Start-Sleep -Seconds 1
& $adbPath exec-out screencap -p > "$screensDir/emergency-contacts.png"
& $adbPath shell input keyevent 4 # Back
Start-Sleep -Milliseconds 700

# 8. Ride History
Write-Host "Capturing 8. Ride History..." -ForegroundColor Green
& $adbPath shell input tap $qaLeftX $qaBottomY
Start-Sleep -Seconds 1
& $adbPath exec-out screencap -p > "$screensDir/ride-history.png"
& $adbPath shell input keyevent 4 # Back
Start-Sleep -Milliseconds 700

# 9. Settings
Write-Host "Capturing 9. Settings..." -ForegroundColor Green
& $adbPath shell input tap $qaRightX $qaBottomY
Start-Sleep -Seconds 1
& $adbPath exec-out screencap -p > "$screensDir/settings.png"
& $adbPath shell input keyevent 4 # Back
Start-Sleep -Milliseconds 700

# 5. Profile (top header avatar icon)
Write-Host "Capturing 5. Profile..." -ForegroundColor Green
$avatarX = [int]($width * 0.88)
$avatarY = [int]($height * 0.08)
& $adbPath shell input tap $avatarX $avatarY
Start-Sleep -Seconds 1
& $adbPath exec-out screencap -p > "$screensDir/profile.png"
& $adbPath shell input keyevent 4 # Back
Start-Sleep -Milliseconds 700

# Run mockups and contact sheet generators
Write-Host "`nRunning Mockup Generator..." -ForegroundColor Cyan
python presentation-assets/generate_mockups.py

Write-Host "Running Contact Sheet Generator..." -ForegroundColor Cyan
python presentation-assets/generate_contact_sheet.py

Write-Host "`n[OK] Screenshot capture and presentation packaging complete!" -ForegroundColor Green
