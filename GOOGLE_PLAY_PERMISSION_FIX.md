# Google Play 권한 정책 위반 해결 가이드

## 문제 진단
앱이 Google Play에서 거부된 이유는 민감한 권한을 사용하면서 적절한 권한 선언 양식을 제출하지 않았기 때문입니다.

## 발견된 민감한 권한

### 1. SMS 권한 (가장 중요)
- `android.permission.RECEIVE_SMS`
- `android.permission.READ_SMS`

**문제점**: SMS 권한은 Google Play에서 매우 제한적인 권한으로, 특정 핵심 기능(예: 기본 SMS/통화 앱, 백업 앱 등)에만 허용됩니다.

### 2. REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (미사용 권한)
- AndroidManifest.xml에 선언되어 있지만 실제로 사용되지 않음

## 즉시 해결 방법

### 옵션 1: SMS 권한 제거 (권장)
SMS 권한은 Google Play 정책상 매우 제한적이므로, 알림 리스너 서비스로만 입금 알림을 처리하는 것이 좋습니다.

1. **AndroidManifest.xml 수정**:
```xml
<!-- 이 권한들을 제거하세요 -->
<!-- <uses-permission android:name="android.permission.RECEIVE_SMS" /> -->
<!-- <uses-permission android:name="android.permission.READ_SMS" /> -->

<!-- SMS 수신기도 제거하세요 -->
<!-- <receiver android:name=".SmsReceiver" ... > -->
```

2. **코드에서 SMS 기능 비활성화**:
- SmsReceiver.kt 파일 삭제 또는 비활성화
- MainActivity에서 SMS 권한 요청 코드 제거

### 옵션 2: SMS 권한 유지하고 Google Play Console에서 양식 제출
SMS 권한을 반드시 사용해야 한다면:

1. **Google Play Console에 접속**
2. **앱 콘텐츠 > 권한 선언** 섹션으로 이동
3. **SMS 및 통화 로그 권한 선언** 양식 작성:
   - 핵심 기능 카테고리 선택: "금융 거래 및 보안"
   - 사용 목적 상세 설명:
     ```
     은행 SMS 입금 알림을 읽어서 음성으로 알려주는 핵심 기능을 위해 SMS 권한이 필요합니다.
     사용자가 은행 입금 SMS를 받으면 즉시 음성으로 "입금확인, XX원"을 안내합니다.
     개인 SMS는 읽지 않으며, 오직 은행 입금 메시지만 감지합니다.
     ```
   - 비디오 데모 제출 (필수): 앱이 SMS를 읽고 음성으로 알림하는 과정을 보여주는 비디오

### 옵션 3: 미사용 권한 제거
**REQUEST_IGNORE_BATTERY_OPTIMIZATIONS** 권한이 실제로 사용되지 않으므로 제거하세요:

```xml
<!-- AndroidManifest.xml에서 이 줄을 제거 -->
<!-- <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" /> -->
```

## 권장 해결 순서

1. **즉시 조치** (1일 이내):
   - AndroidManifest.xml에서 미사용 권한(REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) 제거
   - SMS 권한 제거 또는 유지 결정

2. **SMS 권한을 제거하는 경우**:
   - AndroidManifest.xml에서 SMS 권한 및 SmsReceiver 제거
   - 알림 리스너 서비스만으로 기능 구현
   - 새 버전 빌드 후 업로드

3. **SMS 권한을 유지하는 경우**:
   - Google Play Console에서 SMS 권한 양식 제출
   - 비디오 데모 준비 및 업로드
   - 심사 대기 (보통 7-14일 소요)

## 알림 리스너만 사용하는 대체 방안

SMS 대신 알림 리스너 서비스(BankNotificationService)만 사용하면:
- 권한 정책 위반 없음
- 더 빠른 앱 승인
- 대부분의 은행 앱이 알림을 발송하므로 충분히 기능 구현 가능

## 체크리스트

- [ ] AndroidManifest.xml에서 불필요한 권한 제거
- [ ] SMS 권한 사용 여부 결정
- [ ] SMS 제거 시: 관련 코드 및 권한 제거
- [ ] SMS 유지 시: Google Play Console 권한 양식 제출
- [ ] 버전 코드 증가 (versionCode +1)
- [ ] 새 APK/AAB 빌드
- [ ] Google Play Console에 업로드

## 추가 참고 자료

- [Google Play SMS 및 통화 로그 정책](https://support.google.com/googleplay/android-developer/answer/10208820)
- [권한 선언 양식 가이드](https://support.google.com/googleplay/android-developer/answer/9214102)
- [민감한 권한 대체 방법](https://developer.android.com/privacy-and-security/permissions/minimize-permission-requests)

## 주의사항

1. **비디오 데모 품질**: SMS 권한 양식 제출 시 비디오는 명확하게 기능을 보여줘야 함
2. **정확한 설명**: 권한 사용 목적을 정확하고 상세하게 설명
3. **핵심 기능 증명**: SMS 읽기가 앱의 핵심 기능임을 명확히 증명
4. **대체 수단 없음**: 다른 방법으로는 구현할 수 없음을 설명

## 결론

**가장 빠른 해결책**: SMS 권한을 제거하고 알림 리스너 서비스만 사용하는 것입니다.
이렇게 하면 권한 정책 위반 없이 빠르게 앱을 게시할 수 있습니다.

SMS 권한이 꼭 필요하다면, Google Play Console에서 상세한 권한 양식을 제출하고
비디오 데모를 준비해야 하며, 승인까지 시간이 걸릴 수 있습니다.