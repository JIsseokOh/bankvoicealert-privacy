@echo off
echo ========================================
echo Building with API Level 35
echo ========================================
echo.
echo Google Play requires API level 35 or higher
echo Building AAB with updated API level...
echo.

set SOURCE_DIR=%CD%
set DEST_DIR=C:\temp\BankVoiceAlert_API35

echo Step 1: Creating temp directory...
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
echo Step 4: Cleaning build cache...
if exist ".gradle" rd /s /q .gradle
if exist "app\build" rd /s /q app\build
if exist "build" rd /s /q build
call gradlew.bat --stop >nul 2>&1

echo.
echo Step 5: Building Release AAB with API 35...
call gradlew.bat clean
call gradlew.bat bundleRelease

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo SUCCESS! API 35 AAB Built!
    echo ========================================
    echo.
    echo Copying AAB back to original directory...
    if not exist "%SOURCE_DIR%\app\build\outputs\bundle\release" (
        mkdir "%SOURCE_DIR%\app\build\outputs\bundle\release"
    )
    copy /Y "%DEST_DIR%\app\build\outputs\bundle\release\app-release.aab" "%SOURCE_DIR%\app\build\outputs\bundle\release\"
    
    echo.
    echo ========================================
    echo READY FOR GOOGLE PLAY!
    echo ========================================
    echo AAB Location:
    echo %SOURCE_DIR%\app\build\outputs\bundle\release\app-release.aab
    echo.
    echo API Level: 35 (Latest)
    echo.
    for %%I in (%SOURCE_DIR%\app\build\outputs\bundle\release\app-release.aab) do echo File Size: %%~zI bytes
    for %%I in (%SOURCE_DIR%\app\build\outputs\bundle\release\app-release.aab) do echo Build Date: %%~tI
    echo.
    echo Upload this file to Google Play Console!
) else (
    echo.
    echo Build failed. 
    echo Android SDK 35 might not be installed.
    echo The build will auto-download it, try running again.
)

cd /d "%SOURCE_DIR%"
pause