@echo off
echo ========================================
echo Fixing Monetization Build Error
echo ========================================
echo.

echo Step 1: Cleaning everything...
call gradlew.bat --stop
timeout /t 2 /nobreak >nul
rd /s /q app\build 2>nul
rd /s /q build 2>nul
rd /s /q .gradle 2>nul

echo.
echo Step 2: Syncing dependencies...
call gradlew.bat dependencies --refresh-dependencies

echo.
echo Step 3: Building with detailed error output...
call gradlew.bat clean
call gradlew.bat bundleRelease --stacktrace

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================
    echo Build failed. Common solutions:
    echo ========================================
    echo 1. Check if AdMob App ID is valid in AndroidManifest.xml
    echo 2. Ensure all dependencies are compatible
    echo 3. Try building without monetization first
    echo.
    echo Attempting debug build to identify issues...
    call gradlew.bat assembleDebug --info
)

pause