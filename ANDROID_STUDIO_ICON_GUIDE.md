# Android Studio에서 앱 아이콘 생성하기

## 📱 완벽한 앱 아이콘 만들기

### 1단계: Android Studio 열기
1. Android Studio 실행
2. `BankVoiceAlert` 프로젝트 열기
3. 프로젝트가 완전히 로드될 때까지 대기

### 2단계: Image Asset 도구 실행
1. 좌측 프로젝트 탐색기에서 `app` 폴더 우클릭
2. **New** → **Image Asset** 선택

### 3단계: 아이콘 타입 설정
- **Icon Type**: `Launcher Icons (Adaptive and Legacy)` 선택

### 4단계: Foreground Layer 설정
1. **Asset Type**: `Image` 선택
2. **Path**: 찾아보기 클릭
3. `app/src/main/res/drawable/ic_launcher_foreground.xml` 선택
4. **또는 Clip Art 사용**:
   - Asset Type: `Clip Art`
   - Clip Art 아이콘 클릭
   - 검색: "volume" 또는 "speaker"
   - 스피커 아이콘 선택
   - Color: `#FFFFFF` (흰색)

### 5단계: Background Layer 설정
1. **Asset Type**: `Color` 선택
2. **Color**: `#2196F3` (Material Blue) 입력

### 6단계: Options 설정
- ✅ **Generate Round Icon**: 체크
- ✅ **Generate Legacy Icon**: 체크
- **Legacy Icon**:
  - Shape: `Circle` 또는 `Square`
  - Background Color: `#2196F3`

### 7단계: 미리보기 확인
- 각 디바이스별 미리보기 확인
- Round Icon 미리보기 확인
- Google Play Store 아이콘 확인

### 8단계: 생성
1. **Next** 클릭
2. 생성될 파일 목록 확인
3. **Finish** 클릭

### 9단계: 512x512 아이콘 확인
- 위치: `app/src/main/ic_launcher-playstore.png`
- 이 파일이 Play Store 업로드용 아이콘입니다

---

## 🎨 커스텀 디자인 추가하기 (선택)

### Dollar Sign 추가하기
1. Image Asset 창에서
2. Foreground Layer에 두 번째 레이어 추가 가능
3. 또는 Photoshop/GIMP에서:
   - 512x512 캔버스
   - 파란색 배경 (#2196F3)
   - 흰색 스피커 아이콘
   - 금색 $ 기호 (#FFC107) 오버레이
   - PNG로 저장
   - Image Asset에서 이 PNG 사용

---

## ✅ 생성 완료 후 확인

### 생성된 파일들:
```
app/src/main/
├── res/
│   ├── mipmap-hdpi/
│   │   ├── ic_launcher.png ✓
│   │   └── ic_launcher_round.png ✓
│   ├── mipmap-mdpi/
│   │   ├── ic_launcher.png ✓
│   │   └── ic_launcher_round.png ✓
│   ├── mipmap-xhdpi/
│   │   ├── ic_launcher.png ✓
│   │   └── ic_launcher_round.png ✓
│   ├── mipmap-xxhdpi/
│   │   ├── ic_launcher.png ✓
│   │   └── ic_launcher_round.png ✓
│   ├── mipmap-xxxhdpi/
│   │   ├── ic_launcher.png ✓
│   │   └── ic_launcher_round.png ✓
│   └── mipmap-anydpi-v26/
│       ├── ic_launcher.xml ✓
│       └── ic_launcher_round.xml ✓
└── ic_launcher-playstore.png ✓ (512x512)
```

---

## 🚀 새 아이콘으로 앱 빌드

1. Android Studio 터미널 열기 (하단 Terminal 탭)
2. 명령어 실행:
```bash
./gradlew clean
./gradlew assembleRelease
```

또는 명령 프롬프트에서:
```bash
cd BankVoiceAlert
build_test_apk.bat
```

---

## 💡 추가 팁

### 더 나은 아이콘을 위한 제안:
1. **Material Design 가이드라인 준수**
   - Safe zone 유지
   - 그림자 효과 적절히 사용
   
2. **색상 조합**
   - Primary: #2196F3 (파란색)
   - Accent: #FFC107 (금색)
   - Background: #FFFFFF (흰색)

3. **아이콘 테스트**
   - 다양한 배경에서 테스트
   - 작은 크기에서도 인식 가능한지 확인

### Play Store용 추가 자료:
- Feature Graphic: 1024x500 PNG
- Promo Graphic: 180x120 PNG (선택)
- TV Banner: 1280x720 PNG (TV 앱인 경우)