# AdMob 실제 광고 설정 가이드

## 📋 현재 상태
현재 앱은 **테스트 광고 ID**를 사용하고 있습니다:
- **App ID (테스트)**: `ca-app-pub-3940256099942544~3347511713`
- **배너 광고 ID (테스트)**: `ca-app-pub-3940256099942544/6300978111`
- **네이티브 광고 ID (테스트)**: `ca-app-pub-3940256099942544/2247696110`

## 🚀 AdMob 계정 설정 (이미 있다면 2번으로)

### 1. AdMob 계정 생성
1. [AdMob 웹사이트](https://admob.google.com) 접속
2. Google 계정으로 로그인
3. 계정 정보 입력 및 약관 동의

### 2. 앱 등록
1. AdMob 대시보드에서 **앱 > 앱 추가** 클릭
2. 앱 정보 입력:
   - 플랫폼: Android
   - Google Play에 게시됨?: 예
   - Play Store URL 입력
3. 앱 이름: **띵동 입금알리미**

### 3. 광고 단위 생성

#### 배너 광고 생성
1. 앱 대시보드에서 **광고 단위 > 추가** 클릭
2. **배너** 선택
3. 광고 단위 이름: `BankAlert_Banner`
4. 생성 후 광고 단위 ID 복사

#### 네이티브 광고 생성
1. **광고 단위 > 추가** 클릭
2. **네이티브** 선택
3. 광고 단위 이름: `BankAlert_Native`
4. 생성 후 광고 단위 ID 복사

## 📝 코드 수정 방법

### Step 1: AdMob App ID 확인
AdMob 대시보드 > 앱 설정에서 **앱 ID** 확인
형식: `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`

### Step 2: AndroidManifest.xml 수정
```xml
<!-- 이 부분을 찾아서 -->
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-3940256099942544~3347511713"/>

<!-- 실제 App ID로 교체 -->
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-실제앱ID~숫자"/>
```

### Step 3: AdManager.kt 수정
```kotlin
class AdManager(private val activity: Activity) {

    // 테스트 ID를 실제 ID로 교체
    // private val TEST_BANNER_AD_ID = "ca-app-pub-3940256099942544/6300978111"
    // private val TEST_NATIVE_AD_ID = "ca-app-pub-3940256099942544/2247696110"

    // 실제 광고 ID (예시)
    private val BANNER_AD_ID = "ca-app-pub-실제발급받은ID/배너광고ID"
    private val NATIVE_AD_ID = "ca-app-pub-실제발급받은ID/네이티브광고ID"

    // loadBannerAd 함수에서
    fun loadBannerAd(adContainer: ViewGroup) {
        bannerAdView = AdView(activity).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BANNER_AD_ID  // 변경된 변수명 사용
        }
        // ... 나머지 코드
    }

    // loadNativeAd 함수에서
    fun loadNativeAd(adContainer: ViewGroup, onAdLoaded: () -> Unit = {}) {
        val adLoader = AdLoader.Builder(activity, NATIVE_AD_ID)  // 변경된 변수명 사용
        // ... 나머지 코드
    }
}
```

## 🔧 테스트 모드 설정 (중요!)

개발/테스트 중에는 실제 광고를 클릭하면 안 됩니다. **테스트 디바이스 설정**을 해야 합니다:

### AdManager.kt에 테스트 디바이스 추가
```kotlin
class AdManager(private val activity: Activity) {

    // 실제 광고 ID
    private val BANNER_AD_ID = "ca-app-pub-실제ID/배너ID"
    private val NATIVE_AD_ID = "ca-app-pub-실제ID/네이티브ID"

    // 테스트 디바이스 ID (로그캣에서 확인)
    private val TEST_DEVICE_IDS = listOf(
        AdRequest.DEVICE_ID_EMULATOR,  // 에뮬레이터
        "YOUR_TEST_DEVICE_ID_HERE"     // 실제 테스트 기기
    )

    init {
        // 테스트 디바이스 설정
        val configuration = RequestConfiguration.Builder()
            .setTestDeviceIds(TEST_DEVICE_IDS)
            .build()
        MobileAds.setRequestConfiguration(configuration)

        MobileAds.initialize(activity) {
            Log.d("AdMob", "AdMob initialized")
        }
    }

    fun loadBannerAd(adContainer: ViewGroup) {
        bannerAdView = AdView(activity).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BANNER_AD_ID
        }

        adContainer.addView(bannerAdView)

        // 광고 요청 생성
        val adRequest = AdRequest.Builder().build()
        bannerAdView?.loadAd(adRequest)

        // ... 리스너 코드
    }
}
```

## 📱 테스트 디바이스 ID 찾기

1. 앱 실행
2. Logcat에서 다음과 같은 메시지 확인:
```
Use RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList("33BE2250B43518CCDA7DE426D04EE231"))
```
3. 이 ID를 `TEST_DEVICE_IDS` 리스트에 추가

## ⚠️ 주의사항

### 1. 실제 광고 클릭 금지
- 자신의 광고를 클릭하면 계정이 정지될 수 있습니다
- 반드시 테스트 디바이스를 설정하세요

### 2. 광고 정책 준수
- 광고를 인위적으로 클릭하도록 유도하지 마세요
- 광고와 콘텐츠를 명확히 구분하세요
- 민감한 콘텐츠 근처에 광고 배치 금지

### 3. 단계별 배포
1. 먼저 내부 테스트 트랙에 배포
2. 테스트 디바이스에서 광고가 정상 표시되는지 확인
3. 문제 없으면 프로덕션 배포

## 🏗️ 빌드 환경별 설정

### BuildConfig를 활용한 환경 분리 (선택사항)
`app/build.gradle`:
```gradle
android {
    buildTypes {
        debug {
            buildConfigField "String", "BANNER_AD_ID", "\"ca-app-pub-3940256099942544/6300978111\""
            buildConfigField "String", "NATIVE_AD_ID", "\"ca-app-pub-3940256099942544/2247696110\""
        }
        release {
            buildConfigField "String", "BANNER_AD_ID", "\"ca-app-pub-실제ID/배너ID\""
            buildConfigField "String", "NATIVE_AD_ID", "\"ca-app-pub-실제ID/네이티브ID\""
        }
    }
}
```

AdManager.kt:
```kotlin
private val BANNER_AD_ID = BuildConfig.BANNER_AD_ID
private val NATIVE_AD_ID = BuildConfig.NATIVE_AD_ID
```

## 📊 수익 최적화 팁

1. **광고 배치**
   - 사용자 경험을 해치지 않는 위치에 배치
   - 콘텐츠와 자연스럽게 어울리도록 설정

2. **광고 빈도**
   - 너무 자주 표시하면 사용자 이탈
   - 적절한 간격으로 표시

3. **네이티브 광고 활용**
   - 앱 디자인과 어울리는 네이티브 광고 추천
   - 배너보다 수익률이 높음

## 체크리스트

- [ ] AdMob 계정 생성/로그인
- [ ] 앱 등록 완료
- [ ] 배너 광고 단위 생성
- [ ] 네이티브 광고 단위 생성
- [ ] AndroidManifest.xml에 실제 App ID 입력
- [ ] AdManager.kt에 실제 광고 ID 입력
- [ ] 테스트 디바이스 ID 설정
- [ ] 테스트 빌드로 광고 표시 확인
- [ ] 프로덕션 빌드 및 배포

---
생성일: 2025년 11월 6일