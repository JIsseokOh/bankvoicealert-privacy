@echo off
chcp 65001 >nul
echo ========================================
echo Java 11 Installation Guide
echo ========================================
echo.
echo Android app build requires Java 11.
echo.
echo Please choose one option to install Java 11:
echo.
echo [Option 1] Adoptium Temurin 11 (Recommended)
echo Download: https://adoptium.net/temurin/releases/?version=11
echo - Download Windows x64 MSI installer
echo - Check "Set JAVA_HOME variable" during installation
echo.
echo [Option 2] Amazon Corretto 11
echo Download: https://corretto.aws/downloads/latest/amazon-corretto-11-x64-windows-jdk.msi
echo.
echo [Option 3] Oracle JDK 11
echo Download: https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html
echo (Oracle account required)
echo.
echo ========================================
echo After installation:
echo ========================================
echo 1. Open new command prompt
echo 2. Run: java -version
echo 3. Should display "openjdk version 11" or "java version 11"
echo.
echo ========================================
echo Environment Variables (if manual setup needed):
echo ========================================
echo 1. System Properties - Advanced - Environment Variables
echo 2. JAVA_HOME = C:\Program Files\Eclipse Adoptium\jdk-11.x.x
echo 3. Add to Path: %%JAVA_HOME%%\bin
echo.
pause

echo.
echo Checking Java 11 installation...
java -version 2>&1 | findstr /i "version" | findstr /i "11"
if %ERRORLEVEL% EQU 0 (
    echo.
    echo Java 11 is installed!
    java -version
    echo.
    echo You can now run build_release.bat
) else (
    echo.
    echo Java 11 not detected.
    echo Please install Java 11 following the guide above.
)
pause