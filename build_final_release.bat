@echo off
echo ========================================
echo Building FINAL Release AAB for Play Store
echo ========================================
echo.
echo This will build AAB with all recent changes:
echo - Updated AndroidManifest.xml (removed round icon)
echo - Updated targetSdk 33
echo - Updated ProGuard rules
echo - All bug fixes
echo.
pause

echo.
echo Step 1: Stopping Gradle daemon...
call gradlew.bat --stop
timeout /t 2 /nobreak >nul

echo.
echo Step 2: Cleaning old builds...
rd /s /q app\build 2>nul
rd /s /q build 2>nul
call gradlew.bat clean

echo.
echo Step 3: Building Release AAB...
call gradlew.bat bundleRelease

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo SUCCESS! AAB Ready for Play Store!
    echo ========================================
    echo.
    echo AAB File Location:
    echo app\build\outputs\bundle\release\app-release.aab
    echo.
    echo File Details:
    for %%I in (app\build\outputs\bundle\release\app-release.aab) do echo Size: %%~zI bytes
    for %%I in (app\build\outputs\bundle\release\app-release.aab) do echo Date: %%~tI
    echo.
    echo Next Steps:
    echo 1. Upload this AAB to Google Play Console
    echo 2. Don't forget screenshots and 512x512 icon!
    echo.
) else (
    echo.
    echo Build failed. Please check errors above.
)

pause