@echo off
chcp 65001 >nul
echo ========================================
echo GitHub Pages Privacy Policy Deployment
echo ========================================
echo.

echo Before running this script:
echo 1. Create new repository on GitHub.com
echo 2. Repository name: bankvoicealert-privacy (recommended)
echo 3. Set as Public
echo.
pause

set /p GITHUB_USERNAME=Enter GitHub username: 
set /p REPO_NAME=Enter repository name (default: bankvoicealert-privacy): 

if "%REPO_NAME%"=="" set REPO_NAME=bankvoicealert-privacy

echo.
echo Initializing Git...
git init

echo.
echo Adding files...
git add docs/
git add PRIVACY_POLICY_URL.txt

echo.
echo Creating commit...
git commit -m "Add privacy policy for Bank Voice Alert app"

echo.
echo Connecting to GitHub repository...
git branch -M main
git remote add origin https://github.com/%GITHUB_USERNAME%/%REPO_NAME%.git

echo.
echo Uploading files...
git push -u origin main

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo SUCCESS! Follow these steps:
    echo ========================================
    echo.
    echo 1. Go to GitHub repository:
    echo    https://github.com/%GITHUB_USERNAME%/%REPO_NAME%
    echo.
    echo 2. Click Settings tab
    echo 3. Click 'Pages' in left menu
    echo 4. Source: Deploy from a branch
    echo 5. Branch: main, folder: /docs
    echo 6. Click Save
    echo.
    echo 7. After 5-10 minutes, check URL:
    echo    https://%GITHUB_USERNAME%.github.io/%REPO_NAME%/
    echo.
    echo Use this URL in Google Play Console!
) else (
    echo.
    echo Error occurred.
    echo Check if Git is installed.
    echo Check if GitHub repository is created.
)

pause