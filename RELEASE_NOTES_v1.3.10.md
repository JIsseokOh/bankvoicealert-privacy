# 릴리즈 노트 - v1.3.10

## 버전 정보
- **버전 코드**: 41
- **버전 이름**: 1.3.10
- **빌드 날짜**: 2025년 11월 6일
- **AAB 파일 위치**: `BankVoiceAlert/app/build/outputs/bundle/release/app-release.aab`

## 주요 변경 사항

### 🔒 Google Play 권한 정책 준수
1. **SMS 권한 제거**
   - `RECEIVE_SMS` 권한 제거
   - `READ_SMS` 권한 제거
   - SmsReceiver 컴포넌트 비활성화

2. **미사용 권한 제거**
   - `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 권한 제거

### 🎯 기능 변경
- 이제 알림 리스너 서비스(BankNotificationService)만을 사용하여 은행 입금 알림 처리
- SMS 백업 기능 임시 비활성화 (Google Play 정책 준수를 위함)

## Google Play Console 업로드 체크리스트

### ✅ 완료된 작업
- [x] AndroidManifest.xml에서 SMS 권한 제거
- [x] AndroidManifest.xml에서 미사용 권한 제거
- [x] SmsReceiver 컴포넌트 비활성화
- [x] 버전 코드 증가 (40 → 41)
- [x] 버전 이름 업데이트 (1.3.9 → 1.3.10)
- [x] 프로젝트 클린 빌드
- [x] 새 AAB 파일 생성

### 📤 업로드 전 확인 사항
1. **AAB 파일 확인**
   - 파일 크기: 약 7.5MB
   - 서명: release-keystore.jks로 서명됨

2. **Google Play Console 업로드 시**
   - 권한 선언 양식 업데이트 불필요 (SMS 권한 제거됨)
   - 릴리즈 노트에 권한 변경 사항 명시

3. **릴리즈 노트 제안**
   ```
   • Google Play 정책 준수를 위한 업데이트
   • 알림 리스너를 통한 더 안정적인 입금 알림
   • 성능 개선 및 버그 수정
   ```

## 주의 사항

⚠️ **중요**: 이 버전은 SMS 권한을 제거했으므로:
- 은행 SMS를 직접 읽지 않음
- 오직 은행 앱의 알림을 통해서만 입금을 감지
- 대부분의 은행 앱이 알림을 발송하므로 기능상 문제 없음

## 백업 정보
- 원본 AndroidManifest.xml 백업: `AndroidManifest_backup.xml`
- 이전 버전으로 롤백 필요 시 백업 파일 사용 가능

## 다음 단계
1. Google Play Console에 AAB 파일 업로드
2. 내부 테스트 트랙에서 먼저 테스트
3. 문제 없을 시 프로덕션 릴리즈

---
**파일 생성 완료**: 2025년 11월 6일 15:32