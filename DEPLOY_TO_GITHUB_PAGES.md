# GitHub Pages로 개인정보처리방침 호스팅하기

## 방법 1: GitHub Pages 사용 (무료, 가장 쉬움)

### 1단계: GitHub 저장소 생성
1. [GitHub.com](https://github.com) 로그인
2. 우측 상단 '+' → 'New repository' 클릭
3. Repository name: `bankvoicealert-privacy` (원하는 이름)
4. Public 선택 (무료 호스팅은 Public만 가능)
5. 'Create repository' 클릭

### 2단계: 파일 업로드
1. 생성된 저장소에서 'uploading an existing file' 클릭
2. `docs` 폴더 전체를 드래그 앤 드롭
3. Commit message: "Add privacy policy"
4. 'Commit changes' 클릭

### 3단계: GitHub Pages 활성화
1. 저장소에서 Settings 탭 클릭
2. 왼쪽 메뉴에서 'Pages' 클릭
3. Source: Deploy from a branch
4. Branch: main, 폴더: /docs 선택
5. Save 클릭

### 4단계: URL 확인 (5-10분 후)
- URL 형식: `https://[GitHub사용자명].github.io/bankvoicealert-privacy/`
- 예시: `https://yourname.github.io/bankvoicealert-privacy/`

---

## 방법 2: 명령어로 배포 (Git 설치 필요)

```bash
# Git 초기화 및 파일 추가
cd BankVoiceAlert
git init
git add docs/
git commit -m "Add privacy policy"

# GitHub 저장소 연결 (저장소 생성 후)
git remote add origin https://github.com/[사용자명]/bankvoicealert-privacy.git
git branch -M main
git push -u origin main
```

---

## 방법 3: Google Sites 사용 (대안)

1. [Google Sites](https://sites.google.com) 접속
2. '+' 버튼으로 새 사이트 생성
3. 제목: "은행 입금 알림 개인정보처리방침"
4. privacy_policy.html 내용 복사하여 붙여넣기
5. 우측 상단 '게시' 클릭
6. URL 설정: `bankvoicealert-privacy` (또는 원하는 이름)
7. 게시된 URL: `https://sites.google.com/view/bankvoicealert-privacy`

---

## 방법 4: Netlify Drop (가장 빠름)

1. [Netlify Drop](https://app.netlify.com/drop) 접속
2. `docs` 폴더를 브라우저로 드래그 앤 드롭
3. 자동으로 URL 생성됨
4. 무료 계정 생성 시 영구 보관

생성된 URL 예시: `https://amazing-site-123.netlify.app`

---

## Google Play Console에 URL 등록

호스팅 완료 후:
1. Google Play Console → 앱 콘텐츠
2. 개인정보처리방침 URL 입력
3. 저장

## 추천 순서
1. **GitHub Pages** - 개발자에게 가장 적합
2. **Netlify Drop** - 가장 빠르고 간단
3. **Google Sites** - 비개발자도 쉽게 사용