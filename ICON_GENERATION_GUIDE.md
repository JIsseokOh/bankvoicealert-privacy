# 앱 아이콘 생성 가이드

## 현재 문제
- 벡터 드로어블만 있고 PNG 아이콘이 없어서 일부 기기에서 기본 Android 아이콘이 표시됨

## 해결 방법

### 옵션 1: Android Studio 사용 (가장 쉬움)
1. Android Studio에서 프로젝트 열기
2. `app` 폴더 우클릭
3. New → Image Asset
4. Icon Type: **Launcher Icons (Adaptive and Legacy)**
5. Foreground Layer:
   - Asset Type: Clip Art
   - Clip Art: Speaker 아이콘 선택
   - Color: #FFC107 (금색)
6. Background Layer:
   - Asset Type: Color
   - Color: #2196F3 (파란색)
7. Options:
   - Generate Round Icon: ✓
   - Generate Legacy Icon: ✓
8. Finish

### 옵션 2: 온라인 도구
1. https://icon.kitchen 접속
2. 아이콘 디자인:
   - Background: 파란색 (#2196F3)
   - Icon: Speaker + $ 텍스트
3. Download for Android
4. 압축 해제 후 mipmap 폴더들을 프로젝트에 복사

### 옵션 3: Figma/Canva로 직접 디자인
1. 512x512 캔버스 생성
2. 파란색 배경
3. 흰색 스피커 아이콘 + 금색 $ 기호
4. PNG로 내보내기
5. 각 해상도별로 리사이즈:
   - mdpi: 48x48
   - hdpi: 72x72
   - xhdpi: 96x96
   - xxhdpi: 144x144
   - xxxhdpi: 192x192

## 필요한 파일들
```
app/src/main/res/
├── mipmap-mdpi/
│   ├── ic_launcher.png (48x48)
│   └── ic_launcher_round.png (48x48)
├── mipmap-hdpi/
│   ├── ic_launcher.png (72x72)
│   └── ic_launcher_round.png (72x72)
├── mipmap-xhdpi/
│   ├── ic_launcher.png (96x96)
│   └── ic_launcher_round.png (96x96)
├── mipmap-xxhdpi/
│   ├── ic_launcher.png (144x144)
│   └── ic_launcher_round.png (144x144)
└── mipmap-xxxhdpi/
    ├── ic_launcher.png (192x192)
    └── ic_launcher_round.png (192x192)
```

## 아이콘 적용 후
```bash
cd BankVoiceAlert
build_test_apk.bat
```

새 APK를 빌드하여 설치하면 새 아이콘이 표시됩니다.