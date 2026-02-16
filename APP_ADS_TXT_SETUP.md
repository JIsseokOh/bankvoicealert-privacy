# app-ads.txt 설정 가이드

## 📋 app-ads.txt 파일이 생성되었습니다!

### 파일 내용:
```
google.com, pub-8476619670449177, DIRECT, f08c47fec0942fa0
```

## 🌐 GitHub Pages로 호스팅하기

### Step 1: GitHub 저장소 설정

1. GitHub에서 새 저장소 생성 또는 기존 저장소 사용
   - 저장소 이름: `bankvoicealert` (또는 원하는 이름)
   - Public으로 설정 (무료 호스팅을 위해)

2. `docs` 폴더의 내용을 GitHub에 푸시:
```bash
cd BankVoiceAlert
git init
git add docs/app-ads.txt
git add docs/index.html
git commit -m "Add app-ads.txt for AdMob"
git remote add origin https://github.com/당신의유저명/bankvoicealert.git
git push -u origin main
```

### Step 2: GitHub Pages 활성화

1. GitHub 저장소 → Settings → Pages
2. Source: Deploy from a branch
3. Branch: main, 폴더: /docs
4. Save 클릭

### Step 3: 도메인 확인

GitHub Pages가 활성화되면:
- URL: `https://당신의유저명.github.io/bankvoicealert/`
- app-ads.txt 위치: `https://당신의유저명.github.io/bankvoicealert/app-ads.txt`

### Step 4: Google Play Console 업데이트

1. [Google Play Console](https://play.google.com/console) 접속
2. 앱 선택
3. 스토어 등록정보 → 앱 세부정보
4. 웹사이트 URL에 GitHub Pages URL 입력:
   ```
   https://당신의유저명.github.io/bankvoicealert
   ```
5. 저장

## 🔄 대체 방법: 무료 호스팅 서비스

GitHub이 어려우시다면:

### Netlify Drop (가장 쉬움)
1. [Netlify Drop](https://app.netlify.com/drop) 접속
2. `docs` 폴더를 드래그 앤 드롭
3. 생성된 URL 복사
4. Google Play Console에 URL 등록

### Vercel
1. [Vercel](https://vercel.com) 가입
2. 새 프로젝트 → docs 폴더 업로드
3. 자동 배포 URL 받기
4. Google Play Console에 등록

## ✅ 확인 방법

1. 브라우저에서 확인:
   ```
   https://당신의도메인/app-ads.txt
   ```
   위 주소로 접속했을 때 아래 내용이 보여야 함:
   ```
   google.com, pub-8476619670449177, DIRECT, f08c47fec0942fa0
   ```

2. AdMob에서 확인:
   - 24시간 후 AdMob 대시보드 확인
   - app-ads.txt 경고가 사라짐

## 📱 Google Play Console 웹사이트 설정

앱 정보에 웹사이트를 추가하는 방법:
1. Google Play Console → 앱 선택
2. 성장 → 스토어 등록정보 → 주요 스토어 등록정보
3. 연락처 세부정보 섹션
4. 웹사이트 필드에 GitHub Pages URL 입력
5. 저장 및 검토

## ⏱️ 소요 시간

- GitHub Pages 활성화: 10분
- Google 크롤링: 24-48시간
- AdMob 확인: 최대 24시간

## 🆘 문제 해결

### "app-ads.txt를 찾을 수 없음" 오류
- URL 끝에 슬래시(/) 없이 입력했는지 확인
- https:// 포함했는지 확인
- 파일이 정확히 `app-ads.txt`인지 확인 (대소문자 구분)

### "형식이 올바르지 않음" 오류
- 파일 내용이 정확한지 확인
- 공백이나 특수문자가 추가되지 않았는지 확인
- UTF-8 인코딩인지 확인

---
작성일: 2025년 11월 10일