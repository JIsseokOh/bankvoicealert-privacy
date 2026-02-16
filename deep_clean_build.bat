@echo off
echo ========================================
echo Deep Clean and Rebuild
echo ========================================
echo.

echo Stopping all Gradle processes...
call gradlew.bat --stop
taskkill /f /im java.exe 2>nul
taskkill /f /im javaw.exe 2>nul
timeout /t 2 /nobreak >nul

echo.
echo Removing ALL build artifacts...
rd /s /q app\build 2>nul
rd /s /q build 2>nul
rd /s /q .gradle 2>nul
rd /s /q "%USERPROFILE%\.gradle\caches\transforms-3" 2>nul
rd /s /q "%USERPROFILE%\.gradle\caches\build-cache-1" 2>nul

echo.
echo Clearing Gradle cache...
call gradlew.bat cleanBuildCache 2>nul

echo.
echo Waiting for file locks to release...
timeout /t 3 /nobreak >nul

echo.
echo Starting fresh build...
call gradlew.bat clean

echo.
echo Building Release APK...
call gradlew.bat assembleRelease

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo SUCCESS! APK Built Successfully
    echo ========================================
    echo.
    echo APK Location:
    echo app\build\outputs\apk\release\app-release.apk
    echo.
    echo To install:
    echo 1. Send to phone via KakaoTalk/Email
    echo 2. Install on phone
    echo.
) else (
    echo.
    echo Build failed. Trying alternative method...
    echo.
    call gradlew.bat assembleDebug
    if %ERRORLEVEL% EQU 0 (
        echo Debug APK built successfully!
        echo Location: app\build\outputs\apk\debug\app-debug.apk
    )
)

pause