# BankVoiceAlert — Real-Time Deposit Voice Notification

[![Google Play](https://img.shields.io/badge/Google%20Play-Download-brightgreen?style=for-the-badge&logo=google-play)](https://play.google.com/store/apps/details?id=com.family.bankvoicealert)
[![Android](https://img.shields.io/badge/Android-5.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)

> An intelligent voice notification service designed for small business owners in South Korea, providing instant audible alerts for incoming bank deposits.

---

## Overview

**BankVoiceAlert** (띵동 입금알리미) automatically detects deposit notifications from banking apps and announces the deposited amount via text-to-speech — enabling merchants to confirm transactions hands-free, even in noisy environments.

The app leverages a dual TTS engine architecture: high-quality pre-generated cloud voices for common amounts, with seamless local TTS fallback for all other values.

---

## Key Features

### Instant Voice Alerts
- Automatic detection of deposit push notifications from banking apps
- Real-time TTS announcement of deposited amounts
- Adjustable volume (0–100%) and speech rate (0.5x–2.0x)
- Natural pronunciation mapping for English-abbreviated bank names (e.g., KB, NH, IBK)

### Revenue Tracking
- Automatic daily and monthly deposit logging
- Calendar-based revenue dashboard
- Daily and monthly aggregate totals
- Individual transaction management

### Persistent Background Operation
- Continuous 24/7 monitoring via foreground service
- Automatic service recovery on termination
- Battery optimization bypass support

### Popup Notifications
- On-screen deposit confirmation overlay
- Configurable popup display toggle

---

## Supported Financial Institutions

| Commercial Banks | Digital Banks | Specialized Institutions |
|:-----------------|:-------------|:-------------------------|
| KB Kookmin | KakaoBank | Saemaul Geumgo |
| Shinhan | Toss Bank | Korea Post |
| Woori | K bank | Suhyup Bank |
| Hana | | Shinhyup |
| NH Nonghyup | | Regional Nonghyup |
| IBK Industrial | | SC First Bank |

> Supports all notifications containing deposit-related keywords.

---

## Requirements

| Requirement | Details |
|:------------|:--------|
| Platform | Android 5.0 (API 21) or higher |
| Notification Access | Required for detecting bank push notifications |
| Foreground Service | Required for persistent background monitoring |
| Display Over Other Apps | Optional, for popup deposit alerts |

---

## Documentation

- [Privacy Policy](https://jisseokoh.github.io/bankvoicealert-privacy/privacy.html)
- [Landing Page](https://jisseokoh.github.io/bankvoicealert-privacy/)

---

## Contact

| Channel | Link |
|:--------|:-----|
| Email | tom00100218@gmail.com |
| KakaoTalk | [Open Chat](https://open.kakao.com/o/sxro90Th) |
| Issues | [GitHub Issues](https://github.com/JIsseokOh/bankvoicealert-privacy/issues) |

---

## License

Copyright 2024-2026 JIsseokOh. All rights reserved.

This software is proprietary. Unauthorized copying, modification, distribution, or use of this software, in whole or in part, is strictly prohibited without prior written consent from the copyright holder.
