@echo off
echo ========================================
echo Building Test APK for Installation
echo ========================================
echo.

echo Building release APK...
call gradlew.bat assembleRelease

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo BUILD SUCCESS!
    echo ========================================
    echo.
    echo APK Location:
    echo app\build\outputs\apk\release\app-release.apk
    echo.
    echo How to install on your phone:
    echo.
    echo [Method 1: Email/Cloud]
    echo 1. Send app-release.apk to yourself via email or upload to Google Drive
    echo 2. Open on phone and install
    echo.
    echo [Method 2: USB Cable]
    echo 1. Connect phone via USB
    echo 2. Enable Developer Options and USB Debugging
    echo 3. Run: adb install app\build\outputs\apk\release\app-release.apk
    echo.
    echo [Method 3: QR Code]
    echo Upload APK to WeTransfer or similar, get link, create QR code
    echo.
) else (
    echo.
    echo Build failed. Check errors above.
)

pause