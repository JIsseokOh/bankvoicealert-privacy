@echo off
echo ========================================
echo 강제 업데이트 테스트 스크립트
echo ========================================
echo.

echo 현재 앱 버전 정보:
echo - versionCode: 53
echo - versionName: 1.3.22
echo.

echo 테스트 시나리오를 선택하세요:
echo 1. 강제 업데이트 테스트 (minimumVersionCode를 54로 설정)
echo 2. 선택적 업데이트 테스트 (minimumVersionCode를 50으로 설정)
echo 3. 업데이트 불필요 테스트 (latestVersionCode를 53으로 설정)
echo.

set /p choice="선택 (1-3): "

if "%choice%"=="1" goto force_update
if "%choice%"=="2" goto optional_update
if "%choice%"=="3" goto no_update
goto end

:force_update
echo.
echo 강제 업데이트 설정 중...
echo docs/version.json 파일을 다음과 같이 수정하세요:
echo {
echo   "latestVersionCode": 54,
echo   "latestVersionName": "1.4.0",
echo   "minimumVersionCode": 54,
echo   "updateMessage": "중요한 보안 업데이트가 있습니다.\n앱을 계속 사용하려면 업데이트가 필요합니다.",
echo   "playStoreUrl": "https://play.google.com/store/apps/details?id=com.family.bankvoicealert"
echo }
echo.
echo 수정 후 다음 명령을 실행하세요:
echo git add docs/version.json
echo git commit -m "Test force update"
echo git push origin main
goto end

:optional_update
echo.
echo 선택적 업데이트 설정 중...
echo docs/version.json 파일을 다음과 같이 수정하세요:
echo {
echo   "latestVersionCode": 54,
echo   "latestVersionName": "1.4.0",
echo   "minimumVersionCode": 50,
echo   "updateMessage": "새로운 기능이 추가되었습니다.\n업데이트를 권장합니다.",
echo   "playStoreUrl": "https://play.google.com/store/apps/details?id=com.family.bankvoicealert"
echo }
echo.
echo 수정 후 다음 명령을 실행하세요:
echo git add docs/version.json
echo git commit -m "Test optional update"
echo git push origin main
goto end

:no_update
echo.
echo 업데이트 불필요 설정 중...
echo docs/version.json 파일을 다음과 같이 수정하세요:
echo {
echo   "latestVersionCode": 53,
echo   "latestVersionName": "1.3.22",
echo   "minimumVersionCode": 50,
echo   "updateMessage": "최신 버전을 사용 중입니다.",
echo   "playStoreUrl": "https://play.google.com/store/apps/details?id=com.family.bankvoicealert"
echo }
echo.
echo 수정 후 다음 명령을 실행하세요:
echo git add docs/version.json
echo git commit -m "Test no update needed"
echo git push origin main
goto end

:end
echo.
echo ========================================
echo 테스트 방법:
echo 1. 위의 설정을 적용하고 GitHub에 push
echo 2. 1-2분 후 앱을 실행하여 업데이트 알림 확인
echo 3. Logcat에서 "UpdateChecker" 태그로 로그 확인
echo ========================================
pause