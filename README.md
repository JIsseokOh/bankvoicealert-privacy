# 띵동 입금알리미

[![Google Play](https://img.shields.io/badge/Google%20Play-Download-brightgreen?style=for-the-badge&logo=google-play)](https://play.google.com/store/apps/details?id=com.family.bankvoicealert)
[![Android](https://img.shields.io/badge/Android-5.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)

> 소상공인을 위한 실시간 입금 음성 알림 서비스

---

## 핵심 기능

### 실시간 음성 알림
- 은행 앱 푸시 알림 자동 감지
- 입금 금액 즉시 음성(TTS) 안내
- 볼륨 조절: 0% ~ 100%
- 음성 속도 조절: 0.5x ~ 2.0x
- 영문 은행명 자연스러운 발음 처리 (KB → 케이비, NH → 엔에이치)

### 매출 집계
- 일별/월별 입금 내역 자동 기록
- 달력 기반 매출 현황 조회
- 당일 매출 합계 표시
- 월간 매출 합계 표시
- 개별 입금 내역 삭제 기능

### 백그라운드 동작
- 앱 종료 후에도 24시간 지속 감지
- 포그라운드 서비스로 안정적 동작
- 배터리 최적화 예외 설정 지원
- 서비스 자동 재시작

---

## 지원 은행

| 시중은행 | 인터넷전문은행 | 특수은행 |
|:--------:|:--------------:|:--------:|
| KB국민 | 카카오뱅크 | 새마을금고 |
| 신한 | 토스뱅크 | 우체국 |
| 우리 | 케이뱅크 | 수협 |
| 하나 | - | 신협 |
| NH농협 | - | 지역농협 |
| IBK기업 | - | SC제일 |

> "입금" 키워드가 포함된 모든 알림 지원

---

## 요구 권한

| 권한 | 용도 | 필수 |
|:-----|:-----|:----:|
| 알림 접근 | 은행 앱 푸시 알림 감지 | O |
| 포그라운드 서비스 | 백그라운드 지속 실행 | O |
| 배터리 최적화 예외 | 24시간 무제한 실행 | - |

---

## 기술 사양

| 항목 | 상세 |
|:-----|:-----|
| 언어 | Kotlin |
| Min SDK | 21 (Android 5.0) |
| Target SDK | 35 (Android 15) |
| 아키텍처 | Single Activity |
| TTS 엔진 | Android Native TTS |
| 광고 | Google AdMob |
| 결제 | Google Play Billing |

---

## 프로젝트 구조

```
com.family.bankvoicealert/
├── MainActivity.kt              # 메인 UI 및 설정
├── BankNotificationService.kt   # 알림 리스너 서비스
├── ForegroundService.kt         # 백그라운드 유지 서비스
├── TTSManager.kt                # 음성 출력 관리
├── DepositDataManager.kt        # 입금 데이터 관리
├── UpdateChecker.kt             # 앱 업데이트 확인
├── DuplicateChecker.kt          # 중복 알림 방지
└── AdManager.kt                 # 광고 관리
```

---

## 문서

- [개인정보처리방침](https://jisseokoh.github.io/bankvoicealert-privacy/privacy.html)
- [랜딩 페이지](https://jisseokoh.github.io/bankvoicealert-privacy/)

---

## 연락처

| 채널 | 링크 |
|:-----|:-----|
| 이메일 | tom00100218@gmail.com |
| 카카오톡 | [오픈채팅](https://open.kakao.com/o/sxro90Th) |
| 이슈 | [GitHub Issues](https://github.com/JIsseokOh/bankvoicealert-privacy/issues) |

---

## 라이선스

Copyright 2024-2025 JIsseokOh. All rights reserved.

이 프로젝트는 비공개 라이선스입니다. 무단 복제 및 배포를 금지합니다.
