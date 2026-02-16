@echo off
echo ========================================
echo Cleaning and Rebuilding Project
echo ========================================
echo.

echo Cleaning Gradle cache...
call gradlew.bat clean

echo.
echo Stopping Gradle daemon...
call gradlew.bat --stop

echo.
echo Deleting build directories...
if exist ".gradle" rmdir /s /q .gradle
if exist "app\build" rmdir /s /q app\build
if exist "build" rmdir /s /q build

echo.
echo Rebuilding project...
call gradlew.bat bundleRelease

pause