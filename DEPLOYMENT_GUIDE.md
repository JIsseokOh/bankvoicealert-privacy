# Google Play Store 배포 가이드

## 사전 준비 사항

### 1. Google Play Console 계정 설정
1. [Google Play Console](https://play.google.com/console) 접속
2. 개발자 계정 등록 (일회성 등록비 $25)
3. 개발자 프로필 작성

### 2. 앱 서명 키 생성

#### 방법 1: 배치 파일 사용 (권장)
```bash
# 프로젝트 폴더로 이동
cd BankVoiceAlert

# keystore 생성 스크립트 실행
generate_keystore.bat
```

#### 방법 2: 수동 생성
```bash
keytool -genkey -v -keystore keystore/release-keystore.jks -alias bankvoicealert -keyalg RSA -keysize 2048 -validity 10000
```

**중요**: 생성한 keystore 파일과 비밀번호는 안전하게 보관하세요. 분실 시 앱 업데이트가 불가능합니다.

## 빌드 프로세스

### 1. 환경 변수 설정 (선택사항)
```bash
set KEYSTORE_PASSWORD=your-password
set KEY_ALIAS=bankvoicealert
set KEY_PASSWORD=your-key-password
```

### 2. Release AAB 파일 생성
```bash
# 프로젝트 폴더로 이동
cd BankVoiceAlert

# 빌드 스크립트 실행
build_release.bat
```

또는 수동으로:
```bash
gradlew clean
gradlew bundleRelease
```

생성된 AAB 파일 위치: `app/build/outputs/bundle/release/app-release.aab`

## Google Play Console 업로드 단계

### 1. 새 앱 만들기
1. Play Console 대시보드에서 "앱 만들기" 클릭
2. 앱 이름: "은행 음성 알림"
3. 기본 언어: 한국어
4. 앱 또는 게임: 앱
5. 무료 또는 유료: 무료

### 2. 앱 설정 완료

#### 앱 정보
- 앱 이름: 은행 음성 알림
- 간단한 설명: 은행 입출금 문자를 음성으로 읽어주는 편리한 알림 앱
- 자세한 설명: (store_listing.md 파일 참조)

#### 그래픽 자산
- 앱 아이콘: 512x512 PNG
- 스크린샷: 최소 2개 (1080x1920 권장)
  - 메인 화면
  - 설정 화면
  - 알림 기록 화면

#### 카테고리 및 태그
- 카테고리: 금융 또는 도구
- 태그: 은행, 알림, 음성, TTS, 입출금

#### 연락처 정보
- 이메일 주소: (필수)
- 웹사이트: (선택)
- 개인정보처리방침: privacy_policy.html을 웹에 호스팅 후 URL 입력

### 3. 콘텐츠 등급 설정
1. 콘텐츠 등급 질문서 작성
2. 예상 등급: 전체이용가

### 4. 가격 및 배포
1. 국가/지역 선택: 대한민국
2. 가격: 무료

### 5. 앱 콘텐츠
1. 개인정보처리방침 URL 입력 (필수)
2. 광고 포함 여부 선택
3. 앱 액세스 권한 설명

### 6. AAB 업로드
1. "프로덕션" 탭 선택
2. "새 출시 만들기" 클릭
3. AAB 파일 업로드
4. 출시 이름 및 노트 작성
5. 검토 후 "프로덕션 트랙으로 출시 시작"

## 심사 및 게시

### 예상 소요 시간
- 첫 심사: 2-7일
- 업데이트: 2-24시간

### 심사 거부 시 대응
1. 거부 사유 확인
2. 문제 해결
3. 재제출

## 체크리스트

### 앱 준비
- [ ] Keystore 생성 완료
- [ ] AAB 파일 빌드 완료
- [ ] 앱 아이콘 (512x512) 준비
- [ ] 스크린샷 (최소 2개) 준비
- [ ] 개인정보처리방침 작성 및 호스팅

### Play Console
- [ ] 개발자 계정 등록
- [ ] 앱 생성
- [ ] 스토어 등록정보 작성
- [ ] 콘텐츠 등급 설정
- [ ] 가격 및 배포 설정
- [ ] AAB 업로드
- [ ] 심사 제출

## 유용한 팁

1. **테스트 트랙 활용**: 프로덕션 출시 전 내부/비공개 테스트 트랙 사용
2. **단계별 출시**: 일부 사용자에게만 먼저 출시하여 피드백 수집
3. **업데이트 노트**: 각 업데이트마다 변경사항 명확히 기재
4. **ASO 최적화**: 앱 이름과 설명에 주요 키워드 포함
5. **사용자 피드백**: 리뷰에 적극적으로 응답

## 문제 해결

### 빌드 실패
- Gradle 버전 확인
- Android SDK 업데이트
- 종속성 충돌 해결

### 심사 거부
- 권한 사용 이유 명확히 설명
- 개인정보처리방침 업데이트
- 앱 설명 개선

### 서명 문제
- Keystore 경로 확인
- 비밀번호 확인
- 별칭(alias) 확인

## 참고 자료
- [Android 개발자 문서](https://developer.android.com/distribute)
- [Play Console 도움말](https://support.google.com/googleplay/android-developer)
- [앱 서명 가이드](https://developer.android.com/studio/publish/app-signing)