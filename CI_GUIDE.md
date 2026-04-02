# 🚀 CI/CD 파이프라인 가이드

## 📌 현재 구축 상황

### ✅ CI (Continuous Integration) - 완료
- GitHub Actions 자동 빌드 & 테스트
- Push/PR 시 자동 실행

### ⏳ CD (Continuous Deployment) - 나중
- 배포 대상 인스턴스 결정 후
- 환경 설정 완료 후
- 배포 파이프라인 추가

---

## 🔧 CI 파이프라인 구조

### `.github/workflows/ci.yml`

```
Push/PR
  ↓
Build Job (빌드 + 테스트)
  ├─ 코드 체크아웃
  ├─ Java 17 설치
  ├─ Gradle 빌드
  └─ 테스트 결과 리포트
  ↓
Code Quality Job (코드 품질)
  ├─ SpotBugs (버그 검사)
  └─ Checkstyle (스타일 검사)
  ↓
Status Job (최종 판정)
  └─ 성공/실패 확인
```

---

## ✅ CI 파이프라인 기능

### 1. Build Job
- Java 17 컴파일
- Gradle 빌드
- Unit 테스트 실행
- 테스트 리포트 생성

### 2. Code Quality Job
- 잠재적 버그 검사 (SpotBugs)
- 코드 스타일 검사 (Checkstyle)

### 3. Status Job
- 모든 Job 성공 여부 확인
- PR에 체크마크 표시

---

## 📊 실행 예상 시간

```
첫 실행:   2-3분 (환경 설정)
이후 실행: 1-2분 (캐시 활용)
```

---

## 🎯 GitHub에서 확인하는 방법

1. Repository → "Actions" 탭
2. 최근 workflow 클릭
3. Build Job 상세 로그 확인

---

## ⏳ CD 파이프라인 (나중)

배포 대상/환경 설정 후:
- Docker 이미지 빌드
- 레지스트리 푸시
- 인스턴스 배포
- 헬스 체크

