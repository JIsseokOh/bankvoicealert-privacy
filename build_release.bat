@echo off
echo Building release AAB for Google Play Store
echo.

REM Set environment variables if not already set
if "%KEYSTORE_PASSWORD%"=="" (
    set /p KEYSTORE_PASSWORD=Enter keystore password: 
)
if "%KEY_ALIAS%"=="" (
    set /p KEY_ALIAS=Enter key alias: 
)
if "%KEY_PASSWORD%"=="" (
    set /p KEY_PASSWORD=Enter key password: 
)

echo Cleaning previous builds...
call gradlew.bat clean

echo.
echo Building release AAB file...
call gradlew.bat bundleRelease

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Build successful!
    echo AAB file location: app\build\outputs\bundle\release\app-release.aab
    echo.
    echo You can also build APK with: gradlew.bat assembleRelease
    echo APK file location: app\build\outputs\apk\release\app-release.apk
) else (
    echo.
    echo Build failed. Please check the error messages above.
)

pause