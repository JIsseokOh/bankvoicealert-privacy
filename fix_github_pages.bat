@echo off
chcp 65001 >nul
echo ========================================
echo GitHub Pages 404 Error Fix
echo ========================================
echo.

echo Checking current status...
git status

echo.
echo Pulling latest changes...
git pull origin main

echo.
echo Verifying docs folder exists...
if not exist "docs" (
    echo Creating docs folder...
    mkdir docs
)

echo.
echo Verifying index.html exists...
if not exist "docs\index.html" (
    echo Copying privacy policy to docs/index.html...
    copy privacy_policy.html docs\index.html
)

echo.
echo Adding files to git...
git add docs/index.html
git add -A

echo.
echo Creating commit...
git commit -m "Fix GitHub Pages deployment - add docs/index.html"

echo.
echo Pushing to GitHub...
git push origin main

echo.
echo ========================================
echo IMPORTANT: Now check these settings:
echo ========================================
echo.
echo 1. Go to: https://github.com/jisseokoh/bankvoicealert-privacy/settings/pages
echo.
echo 2. Make sure these are selected:
echo    - Source: Deploy from a branch
echo    - Branch: main
echo    - Folder: /docs
echo    - Click Save if needed
echo.
echo 3. Wait 2-5 minutes
echo.
echo 4. Check: https://jisseokoh.github.io/bankvoicealert-privacy/
echo.
echo If still 404, check:
echo - Is repository PUBLIC? (Settings - General - Danger Zone)
echo - Is there a green checkmark next to latest commit?
echo.
pause