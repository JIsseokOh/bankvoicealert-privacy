@echo off
echo ========================================
echo Java 8용 구형 Gradle 설정
echo ========================================
echo.
echo 경고: 이 방법은 매우 오래된 Android 버전을 사용합니다.
echo 권장: Java 11을 설치하는 것이 더 나은 선택입니다.
echo.
echo 계속하시겠습니까? (Y/N)
set /p CONTINUE=선택: 

if /I "%CONTINUE%" NEQ "Y" (
    echo 취소되었습니다.
    pause
    exit /b
)

echo.
echo 구형 Android Gradle Plugin 설정 중...

REM 매우 오래된 버전으로 다운그레이드 (Java 8 지원하는 마지막 버전)
echo Android Gradle Plugin 4.2.2와 Gradle 6.9로 설정합니다...

REM build.gradle 수정
powershell -Command "(Get-Content 'build.gradle') -replace 'version ''7.0.4''', 'version ''4.2.2''' | Set-Content 'build.gradle'"
powershell -Command "(Get-Content 'build.gradle') -replace 'version ''1.6.21''', 'version ''1.5.21''' | Set-Content 'build.gradle'"

REM gradle-wrapper.properties 수정
powershell -Command "(Get-Content 'gradle\wrapper\gradle-wrapper.properties') -replace 'gradle-7.2-bin.zip', 'gradle-6.9-bin.zip' | Set-Content 'gradle\wrapper\gradle-wrapper.properties'"

REM app/build.gradle의 compileSdk, targetSdk 수정
powershell -Command "(Get-Content 'app\build.gradle') -replace 'compileSdk 33', 'compileSdk 31' | Set-Content 'app\build.gradle'"
powershell -Command "(Get-Content 'app\build.gradle') -replace 'targetSdk 33', 'targetSdk 31' | Set-Content 'app\build.gradle'"

echo.
echo 완료! 이제 build_release.bat를 실행해보세요.
echo.
echo 주의: 이 설정은 2021년 버전의 Android 도구를 사용합니다.
echo 최신 기능을 사용하려면 Java 11 설치를 권장합니다.
pause