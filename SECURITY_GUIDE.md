# 🔐 Security 설정 가이드

## 📌 개요

Spring Security를 통한 API 보안 설정이 완료되었습니다.

- ✅ CSRF 보호 비활성화 (REST API)
- ✅ 세션 비활성화 (JWT 사용)
- ✅ CORS 설정
- ✅ 엔드포인트별 인증 설정
- ✅ JWT 토큰 생성/검증

---

## 🔑 공개 API (인증 불필요)

### 헬스 체크
```
GET /api/health
```

### 시세 정보
```
GET /api/tickers
GET /api/tickers/{symbol}
```

### 캔들 데이터
```
GET /api/candles
GET /api/candles/{symbol}
```

### Swagger UI
```
GET /api/swagger-ui.html
GET /api/v3/api-docs
```

---

## 🔒 보안 API (인증 필요)

### 계정 관리
```
POST   /api/accounts           - 계정 생성
GET    /api/accounts           - 계정 목록
GET    /api/accounts/{id}      - 계정 상세
PUT    /api/accounts/{id}      - 계정 수정
DELETE /api/accounts/{id}      - 계정 삭제
```

### 주문 관리
```
POST   /api/orders             - 주문 생성
GET    /api/orders             - 주문 목록
GET    /api/orders/{id}        - 주문 상세
PUT    /api/orders/{id}        - 주문 수정
DELETE /api/orders/{id}        - 주문 취소
```

### 포지션 관리
```
GET    /api/positions          - 포지션 목록
GET    /api/positions/{id}     - 포지션 상세
PUT    /api/positions/{id}     - 포지션 수정
```

### 전략 관리
```
POST   /api/strategies         - 전략 생성
GET    /api/strategies         - 전략 목록
GET    /api/strategies/{id}    - 전략 상세
PUT    /api/strategies/{id}    - 전략 수정
DELETE /api/strategies/{id}    - 전략 삭제
```

---

## 🎫 JWT 토큰 사용

### 1️⃣ 토큰 생성
```bash
curl -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user",
    "password": "password"
  }'

# 응답
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 86400
}
```

### 2️⃣ 토큰으로 API 호출
```bash
curl -X GET "http://localhost:8080/api/accounts" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### 3️⃣ Swagger UI에서 사용
1. Swagger UI 접속: http://localhost:8080/api/swagger-ui.html
2. "Authorize" 버튼 클릭
3. 토큰 입력: `Bearer {token}`
4. API 테스트

---

## 🛠️ 설정 파일

### SecurityConfig.java
```java
- CSRF 비활성화
- 세션 정책: STATELESS (JWT)
- 엔드포인트별 인증 설정
- CORS 설정
```

### JwtTokenProvider.java
```java
- JWT 토큰 생성
- JWT 토큰 검증
- 토큰에서 정보 추출
```

### application.yml
```yaml
jwt:
  secret: JWT 서명 키 (환경변수 또는 기본값)
  expiration: 토큰 만료 시간 (기본: 24시간)
```

---

## 🌐 CORS 설정

### 허용 출처
```
http://localhost:3000       # React 개발 서버
http://localhost:8080       # Spring Boot 서버
http://127.0.0.1:3000
http://127.0.0.1:8080
```

### 허용 메서드
```
GET, POST, PUT, DELETE, PATCH, OPTIONS
```

### 허용 헤더
```
Content-Type
Authorization
X-Requested-With
Accept
Origin
```

---

## 🔑 JWT 설정

### 환경 변수 설정 (프로덕션)

```bash
# macOS/Linux
export JWT_SECRET="your-long-secret-key-minimum-256-bits"
export UPBIT_ACCESS_KEY="your-upbit-access-key"
export UPBIT_SECRET_KEY="your-upbit-secret-key"
export LOCAL_DB_URL="jdbc:mysql://localhost:3306/crypto_bot"
export LOCAL_DB_USERNAME="root"
export LOCAL_DB_PASSWORD="password"
```

### 로컬 개발 (application.yml 기본값)

```yaml
jwt:
  secret: your-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm-encryption
  expiration: 86400000  # 24시간
```

---

## ⚙️ 시큐리티 동작 흐름

```
1. 클라이언트 요청
   ↓
2. SecurityFilterChain 통과
   ↓
3. 엔드포인트 확인
   ├─ 공개 API → 통과
   └─ 보안 API → 4번으로
   ↓
4. Authorization 헤더 확인
   ├─ 없음 → 403 Forbidden
   ├─ 형식 오류 → 401 Unauthorized
   └─ 유효 → 5번으로
   ↓
5. JWT 토큰 검증
   ├─ 무효 → 401 Unauthorized
   └─ 유효 → 6번으로
   ↓
6. 요청 처리 및 응답 반환
```

---

## 🧪 테스트

### 1. 공개 API 테스트 (인증 불필요)
```bash
curl -X GET "http://localhost:8080/api/health"
```

### 2. 보안 API 테스트 (토큰 필요)
```bash
# 토큰 없이 호출
curl -X GET "http://localhost:8080/api/accounts"
# 결과: 403 Forbidden

# 토큰으로 호출
curl -X GET "http://localhost:8080/api/accounts" \
  -H "Authorization: Bearer {token}"
# 결과: 200 OK
```

---

## 📝 주의사항

### 개발 환경
```
✅ CORS 모든 로컬 출처 허용
✅ CSRF 비활성화
✅ Swagger UI 공개
```

### 프로덕션 환경
```
❌ CORS 특정 도메인만 허용
❌ JWT_SECRET 복잡한 값 사용
❌ Swagger UI 비활성화 고려
✅ HTTPS 필수
✅ 환경 변수로 보안 정보 관리
```

---

## 🚀 다음 단계

1. **로그인 API 구현**
   ```java
   @PostMapping("/auth/login")
   public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
       // 검증 로직
       String token = jwtTokenProvider.generateToken(username);
       return ResponseEntity.ok(new LoginResponse(token));
   }
   ```

2. **JWT 필터 추가** (선택사항)
   ```java
   - JwtAuthenticationFilter 구현
   - SecurityConfig에 필터 등록
   ```

3. **권한 관리** (선택사항)
   ```java
   - Role 기반 접근 제어 (RBAC)
   - @PreAuthorize 애노테이션 사용
   ```


