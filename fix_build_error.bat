@echo off
echo ========================================
echo Fixing Build Access Error
echo ========================================
echo.

echo Stopping Gradle daemon...
call gradlew.bat --stop

echo.
echo Cleaning build folders...
rd /s /q app\build 2>nul
rd /s /q build 2>nul
rd /s /q .gradle 2>nul

echo.
echo Waiting for processes to release files...
timeout /t 3 /nobreak >nul

echo.
echo Creating fresh build directories...
mkdir app\build
mkdir build

echo.
echo Rebuilding APK...
call gradlew.bat clean
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
    echo To install on phone:
    echo 1. Send APK to yourself via email/KakaoTalk
    echo 2. Or upload to Google Drive
    echo 3. Open on phone and install
    echo.
) else (
    echo.
    echo Build failed. Try running as Administrator.
)

pause