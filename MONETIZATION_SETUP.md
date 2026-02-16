# 💰 수익화 설정 가이드

## 구현 완료된 사항
- ✅ AdMob 라이브러리 추가
- ✅ Google Play Billing 라이브러리 추가
- ✅ SubscriptionManager.kt - 구독 관리
- ✅ AdManager.kt - 광고 관리

## 설정 필요 사항

### 1. Google AdMob 설정
1. **[AdMob](https://admob.google.com) 가입**
2. **앱 추가**
   - 앱 이름: 은행 입금 알림
   - 플랫폼: Android
3. **앱 ID 받기** (예: ca-app-pub-1234567890123456~1234567890)
4. **광고 단위 생성**
   - 형식: 전면 광고
   - 이름: App Launch Interstitial
5. **AndroidManifest.xml 수정**
   - `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`를 실제 앱 ID로 교체
6. **AdManager.kt 수정**
   - TEST_AD_UNIT_ID를 실제 광고 단위 ID로 교체

### 2. Google Play Console 구독 설정

#### Play Console에서:
1. **수익 창출 → 제품 → 구독**
2. **구독 만들기**
   - 제품 ID: `premium_monthly_2000`
   - 이름: 프리미엄 구독
   - 설명: 광고 없는 프리미엄 경험
3. **기본 플랜 추가**
   - 이름: 월간 구독
   - 가격: ₩2,000/월
   - 자동 갱신: 예
4. **활성화**

### 3. MainActivity 수정 필요
```kotlin
// MainActivity.kt에 추가할 코드

class MainActivity : AppCompatActivity() {
    private lateinit var adManager: AdManager
    private lateinit var subscriptionManager: SubscriptionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 광고 및 구독 초기화
        adManager = AdManager(this)
        subscriptionManager = SubscriptionManager(this)
        
        // 프리미엄 사용자가 아닌 경우 광고 표시
        if (!subscriptionManager.isPremiumUser()) {
            adManager.showAdIfAvailable {
                // 광고 닫힌 후 실행할 코드
                initApp()
            }
        } else {
            initApp()
        }
    }
    
    // 구독 버튼 클릭 시
    private fun onSubscribeClick() {
        subscriptionManager.launchBillingFlow()
    }
}
```

### 4. 레이아웃 수정 필요
activity_main.xml에 구독 버튼 추가:
```xml
<Button
    android:id="@+id/subscribeButton"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="🌟 광고 제거 (월 2,000원)"
    android:backgroundTint="#FFD700"/>
```

## 테스트 방법

### AdMob 테스트:
- 현재 테스트 광고 ID 사용 중
- 실제 배포 시 실제 광고 ID로 변경

### 구독 테스트:
1. Play Console → 라이선스 테스트 계정 추가
2. 테스트 계정으로 앱 설치 및 구독 테스트
3. 테스트 구독은 5분 후 자동 취소

## 수익 예상
- 무료 사용자: 앱 실행 시 전면 광고 (예상 eCPM: $1-3)
- 프리미엄 사용자: 월 2,000원
- 예상 전환율: 2-5%

## 주의사항
1. **광고 정책**: 광고를 너무 자주 표시하면 사용자 이탈
2. **구독 가격**: 2,000원은 합리적인 가격
3. **테스트**: 실제 배포 전 충분한 테스트 필요
4. **환불 정책**: Play Console에서 환불 정책 명시

## 다음 단계
1. AdMob 계정 생성 및 광고 단위 생성
2. Play Console에서 구독 상품 생성
3. MainActivity.kt 수정하여 광고/구독 로직 통합
4. 테스트 후 배포