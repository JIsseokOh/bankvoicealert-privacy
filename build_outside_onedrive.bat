@echo off
echo ========================================
echo Building Outside OneDrive
echo ========================================
echo.
echo OneDrive sync issues detected. 
echo Copying project to C:\temp for clean build...
echo.

set SOURCE_DIR=%CD%
set DEST_DIR=C:\temp\BankVoiceAlert

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

echo.
echo Step 5: Building Release AAB...
call gradlew.bat clean
call gradlew.bat bundleRelease

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo SUCCESS! AAB Built Successfully!
    echo ========================================
    echo.
    echo AAB Location:
    echo %DEST_DIR%\app\build\outputs\bundle\release\app-release.aab
    echo.
    echo Copying AAB back to original directory...
    if not exist "%SOURCE_DIR%\app\build\outputs\bundle\release" (
        mkdir "%SOURCE_DIR%\app\build\outputs\bundle\release"
    )
    copy /Y "%DEST_DIR%\app\build\outputs\bundle\release\app-release.aab" "%SOURCE_DIR%\app\build\outputs\bundle\release\"
    
    echo.
    echo FINAL AAB Location:
    echo %SOURCE_DIR%\app\build\outputs\bundle\release\app-release.aab
    echo.
    echo File Details:
    for %%I in (%SOURCE_DIR%\app\build\outputs\bundle\release\app-release.aab) do echo Size: %%~zI bytes
    for %%I in (%SOURCE_DIR%\app\build\outputs\bundle\release\app-release.aab) do echo Date: %%~tI
    echo.
    echo Ready for Google Play Store upload!
) else (
    echo.
    echo Build failed. Check errors above.
)

cd /d "%SOURCE_DIR%"
pause