@echo off
echo ========================================
echo Building with API Level 34
echo ========================================
echo.
echo Using API 34 (Android 14) - Stable and supported by Google Play
echo.

set SOURCE_DIR=%CD%
set DEST_DIR=C:\temp\BankVoiceAlert_API34

echo Step 1: Creating clean temp directory...
if exist "%DEST_DIR%" (
    echo Removing old temp directory...
    rd /s /q "%DEST_DIR%"
)
mkdir "%DEST_DIR%"

echo.
echo Step 2: Copying project files...
xcopy /E /I /Y /Q "%SOURCE_DIR%\*" "%DEST_DIR%\" >nul 2>&1

echo.
echo Step 3: Moving to temp directory...
cd /d "%DEST_DIR%"

echo.
echo Step 4: Deep cleaning build environment...
if exist ".gradle" rd /s /q .gradle
if exist "app\build" rd /s /q app\build  
if exist "build" rd /s /q build
call gradlew.bat --stop >nul 2>&1
timeout /t 2 /nobreak >nul

echo.
echo Step 5: Building Release AAB with API 34...
call gradlew.bat clean
call gradlew.bat bundleRelease

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo SUCCESS! API 34 AAB Built Successfully!
    echo ========================================
    echo.
    echo Copying AAB back to original directory...
    if not exist "%SOURCE_DIR%\app\build\outputs\bundle\release" (
        mkdir "%SOURCE_DIR%\app\build\outputs\bundle\release"
    )
    copy /Y "%DEST_DIR%\app\build\outputs\bundle\release\app-release.aab" "%SOURCE_DIR%\app\build\outputs\bundle\release\"
    
    echo.
    echo ========================================
    echo READY FOR GOOGLE PLAY CONSOLE!
    echo ========================================
    echo.
    echo AAB File Location:
    echo %SOURCE_DIR%\app\build\outputs\bundle\release\app-release.aab
    echo.
    echo Specifications:
    echo - Target API: 34 (Android 14)
    echo - Min API: 21 (Android 5.0)
    echo - Version: 1.0.0
    echo.
    for %%I in (%SOURCE_DIR%\app\build\outputs\bundle\release\app-release.aab) do echo File Size: %%~zI bytes
    for %%I in (%SOURCE_DIR%\app\build\outputs\bundle\release\app-release.aab) do echo Build Time: %%~tI
    echo.
    echo Next Step: Upload this AAB to Google Play Console
    echo.
) else (
    echo.
    echo Build failed. Checking for issues...
    echo.
    echo Possible solutions:
    echo 1. Run: sdkmanager "platforms;android-34"
    echo 2. Open Android Studio and sync project
    echo 3. Try running the script again
)

cd /d "%SOURCE_DIR%"
pause