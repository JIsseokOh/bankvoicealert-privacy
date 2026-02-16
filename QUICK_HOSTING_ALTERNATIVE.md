# 🚀 app-ads.txt 빠른 호스팅 방법 (GitHub 없이)

## 방법 1: Netlify Drop (가장 쉬움) ⭐

1. **파일 준비**
   - `docs` 폴더를 바탕화면에 복사

2. **Netlify Drop 사용**
   - [https://app.netlify.com/drop](https://app.netlify.com/drop) 접속
   - `docs` 폴더를 브라우저로 드래그 & 드롭
   - 자동으로 URL 생성됨 (예: `https://amazing-site-123.netlify.app`)

3. **확인**
   - `https://amazing-site-123.netlify.app/app-ads.txt` 접속
   - 내용 확인

4. **Google Play Console 업데이트**
   - 생성된 URL을 웹사이트로 등록

## 방법 2: Surge.sh (명령줄)

```bash
# surge 설치
npm install -g surge

# docs 폴더로 이동
cd BankVoiceAlert/docs

# 배포 (이메일 입력 필요)
surge

# 도메인 선택 또는 자동 생성
# 예: https://your-site.surge.sh
```

## 방법 3: Firebase Hosting (Google 계정)

1. **Firebase Console**
   - [https://console.firebase.google.com](https://console.firebase.google.com)
   - 새 프로젝트 생성

2. **Firebase CLI 설치**
```bash
npm install -g firebase-tools
firebase login
firebase init hosting
```

3. **배포**
```bash
firebase deploy
```

## 방법 4: 000webhost (무료 호스팅)

1. [https://www.000webhost.com](https://www.000webhost.com) 가입
2. 새 사이트 생성
3. File Manager에서 `public_html` 폴더에 파일 업로드
4. URL 확인

## 방법 5: GitHub Gist (초간단)

1. [https://gist.github.com](https://gist.github.com) 접속
2. 파일명: `app-ads.txt`
3. 내용 붙여넣기:
```
google.com, pub-8476619670449177, DIRECT, f08c47fec0942fa0
```
4. Create secret gist 클릭
5. Raw 버튼 클릭하여 직접 URL 획득

⚠️ **주의**: Gist는 임시 방편입니다. 정식 웹사이트를 권장합니다.

## ✅ 체크리스트

어떤 방법을 선택하든:
- [ ] app-ads.txt 파일이 접근 가능한지 확인
- [ ] Google Play Console에 URL 등록
- [ ] 24시간 후 AdMob 확인

## 🎯 추천 순서

1. **Netlify Drop** - 가장 쉽고 빠름
2. **Surge.sh** - 명령줄 익숙하면 편함
3. **Firebase** - Google 서비스 통합 좋음
4. **000webhost** - 완전 무료
5. **GitHub Pages** - 개발자에게 최적

---
선택한 방법으로 URL을 받으면 Google Play Console에 등록하세요!