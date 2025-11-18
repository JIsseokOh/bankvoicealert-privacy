# 앱 강제 업데이트 시스템 가이드

## 개요
이 문서는 BankVoiceAlert 앱의 강제 업데이트 시스템을 설명합니다. 이 시스템을 통해 이전 버전 사용자들이 새로운 버전으로 업데이트하도록 강제할 수 있습니다.

## 시스템 구성

### 1. UpdateChecker.kt
- 앱 시작 시 버전을 체크하는 클래스
- GitHub Pages에 호스팅된 `version.json` 파일을 읽어와 현재 앱 버전과 비교
- 강제 업데이트가 필요한 경우 다이얼로그를 표시

### 2. version.json
- GitHub Pages에 호스팅되는 버전 정보 파일
- 위치: `docs/version.json`
- URL: https://jisseokoh.github.io/bankvoicealert-privacy/version.json

### 3. MainActivity 통합
- 앱 시작 시 자동으로 버전 체크 수행
- 네트워크 오류 시에도 앱은 정상적으로 실행

## 강제 업데이트 설정 방법

### 1. version.json 파일 수정
`docs/version.json` 파일을 다음과 같이 수정합니다:

```json
{
  "latestVersionCode": 54,           // 최신 버전 코드
  "latestVersionName": "1.4.0",      // 최신 버전 이름
  "minimumVersionCode": 53,          // 최소 허용 버전 코드 (이것보다 낮으면 강제 업데이트)
  "updateMessage": "중요한 보안 업데이트가 있습니다.\n앱을 계속 사용하려면 업데이트가 필요합니다.",
  "playStoreUrl": "https://play.google.com/store/apps/details?id=com.family.bankvoicealert"
}
```

### 2. 강제 업데이트 시나리오

#### 시나리오 1: 특정 버전 이하 모두 강제 업데이트
예: 버전 코드 50 이하의 모든 앱을 강제 업데이트하려면
```json
{
  "latestVersionCode": 54,
  "minimumVersionCode": 51,  // 50 이하는 모두 강제 업데이트
  ...
}
```

#### 시나리오 2: 선택적 업데이트
예: 최신 버전은 54이지만 버전 50 이상은 선택적 업데이트
```json
{
  "latestVersionCode": 54,
  "minimumVersionCode": 50,  // 49 이하만 강제 업데이트
  ...
}
```

#### 시나리오 3: 모든 이전 버전 강제 업데이트
```json
{
  "latestVersionCode": 54,
  "minimumVersionCode": 54,  // 현재 버전과 동일 = 모든 이전 버전 강제
  ...
}
```

## 배포 프로세스

### 1. 새 버전 앱 출시 준비
1. `app/build.gradle`에서 versionCode와 versionName 업데이트
2. 앱 빌드 및 테스트
3. Google Play Store에 업로드

### 2. GitHub Pages 업데이트
1. `docs/version.json` 파일 수정
2. Git commit 및 push
```bash
cd BankVoiceAlert
git add docs/version.json
git commit -m "Update version info for force update"
git push origin main
```

### 3. 확인
- GitHub Pages가 업데이트되면 (보통 1-2분 소요) 기존 사용자들이 앱을 실행할 때 업데이트 알림을 받게 됨
- 브라우저에서 직접 확인: https://jisseokoh.github.io/bankvoicealert-privacy/version.json

## 동작 방식

### 강제 업데이트 플로우
1. 사용자가 앱 실행
2. UpdateChecker가 GitHub Pages의 version.json 다운로드
3. 현재 버전 코드와 minimumVersionCode 비교
4. 현재 버전 < minimumVersionCode인 경우:
   - 강제 업데이트 다이얼로그 표시 (취소 불가)
   - "업데이트" 버튼만 표시
   - 업데이트 버튼 클릭 시 Play Store로 이동
   - 다이얼로그 닫으면 앱 종료

### 선택적 업데이트 플로우
1. 현재 버전 < latestVersionCode이지만 >= minimumVersionCode인 경우:
   - 선택적 업데이트 다이얼로그 표시
   - "업데이트"와 "나중에" 버튼 표시
   - 사용자가 "나중에"를 선택하면 앱 정상 사용 가능

## 주의사항

1. **네트워크 오류 처리**: 네트워크 오류 시 앱이 정상적으로 실행되도록 설정됨
2. **캐싱**: 브라우저 캐싱으로 인해 업데이트가 즉시 반영되지 않을 수 있음
3. **테스트**: 실제 배포 전 반드시 테스트 환경에서 확인
4. **버전 코드 관리**: versionCode는 항상 증가해야 함 (감소 불가)

## 테스트 방법

### 1. 로컬 테스트
1. `UpdateChecker.kt`의 `getCurrentVersionCode()` 메소드를 임시로 수정하여 낮은 버전 반환
2. 앱 실행하여 강제 업데이트 다이얼로그 확인
3. 테스트 후 코드 원복

### 2. 실제 테스트
1. 이전 버전 APK 설치
2. `docs/version.json` 업데이트 및 GitHub에 push
3. 앱 실행하여 업데이트 알림 확인

## 문제 해결

### Q: 업데이트 알림이 표시되지 않음
A: 다음을 확인하세요:
- 인터넷 연결 상태
- GitHub Pages URL이 올바른지
- version.json 파일 형식이 올바른지
- Logcat에서 UpdateChecker 관련 에러 로그 확인

### Q: 강제 업데이트 후에도 이전 버전 사용자가 있음
A: 다음 경우일 수 있습니다:
- 오프라인 상태에서 앱 실행
- APK 파일로 직접 설치한 사용자
- 자동 업데이트를 비활성화한 사용자

## 향후 개선 사항
1. Firebase Remote Config 연동 (더 빠른 업데이트 반영)
2. 업데이트 통계 수집
3. A/B 테스트를 통한 점진적 롤아웃
4. 업데이트 메시지 다국어 지원