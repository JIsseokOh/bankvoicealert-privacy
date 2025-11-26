# 띵동 입금알리미

은행 입금 알림을 음성으로 안내하는 Android 앱

[![Play Store](https://img.shields.io/badge/Google%20Play-다운로드-green?style=flat-square&logo=google-play)](https://play.google.com/store/apps/details?id=com.family.bankvoicealert)

---

## 개요

- 은행 앱의 푸시 알림을 실시간 감지
- 입금 내역을 음성(TTS)으로 자동 안내
- 매출 집계 및 달력 기반 통계 제공

---

## 주요 기능

### 음성 알림
- 입금 감지 시 즉시 음성 안내
- 볼륨 및 음성 속도 조절 가능 (0.5x ~ 2.0x)
- 영어 은행명 자연스러운 발음 처리 (KB → 케이비, NH → 엔에이치)

### 매출 집계
- 일별/월별 입금 내역 자동 기록
- 달력 기반 매출 현황 확인
- 당일 및 월간 매출 합계 표시

### 백그라운드 동작
- 앱 종료 후에도 지속적 알림 감지
- 배터리 최적화 설정 지원
- 포그라운드 서비스로 안정적 동작

---

## 지원 은행

| 주요 은행 | 인터넷 은행 | 기타 |
|-----------|-------------|------|
| KB국민은행 | 카카오뱅크 | 새마을금고 |
| 신한은행 | 토스뱅크 | 우체국 |
| 우리은행 | 케이뱅크 | 수협은행 |
| 하나은행 | | 신협 |
| NH농협은행 | | 지역농협 |
| IBK기업은행 | | SC제일은행 |

> 입금 키워드가 포함된 모든 알림 지원

---

## 요구 권한

| 권한 | 용도 | 필수 여부 |
|------|------|-----------|
| 알림 접근 | 은행 앱 푸시 알림 감지 | 필수 |
| 포그라운드 서비스 | 백그라운드 동작 유지 | 필수 |
| 배터리 최적화 예외 | 안정적 서비스 동작 | 권장 |

---

## 기술 스택

- **Language**: Kotlin
- **Min SDK**: 21 (Android 5.0 Lollipop)
- **Target SDK**: 35 (Android 15)
- **Architecture**: Single Activity
- **TTS Engine**: Android Native TTS

---

## 빌드 환경

```
- Android Studio Hedgehog 이상
- Gradle 8.x
- JDK 11
```

---

## 설치 방법

### Google Play Store
[띵동 입금알리미 다운로드](https://play.google.com/store/apps/details?id=com.family.bankvoicealert)

### 직접 빌드
```bash
git clone https://github.com/JIsseokOh/bankvoicealert-privacy.git
cd bankvoicealert-privacy
./gradlew assembleRelease
```

---

## 프로젝트 구조

```
app/src/main/java/com/family/bankvoicealert/
├── MainActivity.kt          # 메인 화면 및 설정
├── BankNotificationService.kt   # 알림 감지 서비스
├── ForegroundService.kt     # 백그라운드 서비스
├── TTSManager.kt            # 음성 출력 관리
├── DepositDataManager.kt    # 입금 데이터 관리
├── UpdateChecker.kt         # 앱 업데이트 확인
└── DuplicateChecker.kt      # 중복 알림 방지
```

---

## 개인정보처리방침

[개인정보처리방침 보기](https://jisseokoh.github.io/bankvoicealert-privacy/privacy.html)

---

## 문의

- **Email**: tom00100218@gmail.com
- **Issue**: [GitHub Issues](https://github.com/JIsseokOh/bankvoicealert-privacy/issues)

---

## 라이선스

이 프로젝트는 비공개 라이선스입니다. 무단 복제 및 배포를 금지합니다.

Copyright © 2024-2025 JIsseokOh. All rights reserved.
