# MainActivity.kt SMS 권한 제거 가이드

## MainActivity.kt에서 제거해야 할 부분

### 1. SMS 권한 요청 함수 제거
`requestSmsPermission()` 함수 전체를 제거하거나 주석 처리하세요:

```kotlin
// 이 함수를 제거 또는 주석 처리
/*
private fun requestSmsPermission() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
        != PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
        != PackageManager.PERMISSION_GRANTED) {

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
            100
        )
    }
}
*/
```

### 2. SMS 권한 요청 호출 제거
MainActivity의 onCreate() 또는 다른 곳에서 `requestSmsPermission()` 호출하는 부분을 제거하세요:

```kotlin
// 이 줄을 제거
// requestSmsPermission()
```

### 3. SMS 권한 관련 import 문 제거 (선택사항)
사용하지 않는 import 문을 정리하세요:

```kotlin
// SMS 관련 권한 상수를 사용하지 않으면 제거 가능
// import android.Manifest
```

### 4. onRequestPermissionsResult에서 SMS 권한 처리 제거
권한 결과 처리 함수에서 SMS 관련 처리를 제거하세요:

```kotlin
override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)

    when (requestCode) {
        100 -> {
            // SMS 권한 처리 코드 제거
            // 이 부분을 제거하거나 주석 처리
        }
        // 다른 권한 처리는 유지
    }
}
```

## UI에서 SMS 관련 설정 제거

### 1. SMS 백업 토글 제거
만약 UI에 SMS 백업 기능 토글이 있다면 제거하세요:

```kotlin
// SMS 백업 관련 UI 요소 제거
// binding.smsBackupSwitch.visibility = View.GONE
```

### 2. SMS 관련 설명 텍스트 제거
SMS 기능 관련 안내 문구가 있다면 수정하세요.

## 완전 제거 체크리스트

- [ ] AndroidManifest.xml에서 SMS 권한 제거
- [ ] AndroidManifest.xml에서 SmsReceiver 제거
- [ ] SmsReceiver.kt 파일 삭제
- [ ] MainActivity.kt에서 requestSmsPermission() 함수 제거
- [ ] MainActivity.kt에서 requestSmsPermission() 호출 제거
- [ ] onRequestPermissionsResult()에서 SMS 권한 처리 제거
- [ ] UI에서 SMS 관련 설정 제거
- [ ] strings.xml에서 SMS 관련 문자열 제거 (선택사항)

## 알림 리스너만으로 충분한 이유

BankNotificationService (알림 리스너)만으로도:
1. 대부분의 은행 앱은 입금 시 알림을 발송합니다
2. 알림 내용에서 입금액을 추출할 수 있습니다
3. SMS 권한 없이도 동일한 기능을 제공할 수 있습니다
4. Google Play 정책 위반 없이 앱을 게시할 수 있습니다

## 테스트 방법

1. SMS 권한 제거 후 앱 빌드
2. 설정 > 앱 > 띵동 입금알리미 > 권한 확인
3. SMS 권한이 표시되지 않는지 확인
4. 은행 앱 알림으로 입금 알림이 정상 작동하는지 테스트