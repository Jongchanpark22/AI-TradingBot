# 🔍 Swagger API 문서 가이드

## 📌 접근 방법

### Swagger UI 접속
```
http://localhost:8080/api/swagger-ui.html
```

### OpenAPI JSON 스키마
```
http://localhost:8080/api/v3/api-docs
```

---

## 🚀 사용 방법

### 1️⃣ 서버 시작
```bash
./gradlew bootRun
```

### 2️⃣ Swagger UI 열기
```
브라우저 → http://localhost:8080/api/swagger-ui.html
```

### 3️⃣ API 문서 보기
- 모든 엔드포인트가 자동으로 문서화됨
- 각 API의 요청/응답 형식 확인 가능
- 직접 테스트 가능 (Try it out 버튼)

---

## 📚 API 카테고리

### 계정 API
```
GET  /api/accounts           - 계정 정보 조회
POST /api/accounts           - 계정 생성
GET  /api/accounts/{id}      - 계정 상세 조회
PUT  /api/accounts/{id}      - 계정 수정
```

### 시세 API
```
GET  /api/tickers            - 시세 목록
GET  /api/tickers/{symbol}   - 특정 코인 시세
```

### 캔들 API
```
GET  /api/candles            - 캔들 목록
GET  /api/candles/{symbol}   - 특정 코인 캔들
```

### 주문 API
```
GET  /api/orders             - 주문 목록
POST /api/orders             - 주문 생성
GET  /api/orders/{id}        - 주문 상세
PUT  /api/orders/{id}        - 주문 수정
DELETE /api/orders/{id}      - 주문 취소
```

### 포지션 API
```
GET  /api/positions          - 포지션 목록
GET  /api/positions/{id}     - 포지션 상세
PUT  /api/positions/{id}     - 포지션 수정
```

### 전략 API
```
GET  /api/strategies         - 전략 목록
POST /api/strategies         - 전략 생성
GET  /api/strategies/{id}    - 전략 상세
PUT  /api/strategies/{id}    - 전략 수정
DELETE /api/strategies/{id}  - 전략 삭제
```

---

## 🧪 테스트 예시

### 1. 계정 정보 조회
```bash
curl -X GET "http://localhost:8080/api/accounts" \
  -H "Content-Type: application/json"
```

### 2. 시세 조회
```bash
curl -X GET "http://localhost:8080/api/tickers?symbol=BTC-KRW" \
  -H "Content-Type: application/json"
```

### 3. 주문 생성
```bash
curl -X POST "http://localhost:8080/api/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": 1,
    "symbol": "BTC-KRW",
    "type": "LIMIT",
    "side": "BUY",
    "price": 50000000,
    "quantity": 0.01
  }'
```

---

## 💡 Swagger UI 주요 기능

### Try it out
- 각 API 옆의 "Try it out" 버튼 클릭
- 파라미터 입력
- "Execute" 버튼으로 실제 요청 전송
- 응답 결과 확인

### 모델 스키마
- 각 DTO의 필드 정보 확인
- 필드 타입과 필수 여부 확인
- 예시 값 확인

### HTTP 상태 코드
- 각 API의 응답 상태 코드
- 에러 응답 형식
- 상태 코드별 설명

---

## 📊 설정 정보

### Application 이름
```
crypto-trading-bot
```

### 기본 URL
```
http://localhost:8080/api
```

### 버전
```
1.0.0
```

### 라이선스
```
MIT License
```

---

## 🔍 API 응답 형식

### 성공 응답 (200)
```json
{
  "data": {...},
  "message": "성공",
  "timestamp": "2024-04-03T10:30:00"
}
```

### 에러 응답 (4xx, 5xx)
```json
{
  "error": "ERROR_CODE",
  "message": "에러 메시지",
  "timestamp": "2024-04-03T10:30:00"
}
```

---

## 🚀 개발 팁

### 1. 자동 문서 생성
- Controller의 `@GetMapping`, `@PostMapping` 등 자동 인식
- `@Operation`, `@Parameter` 애노테이션으로 상세 문서 작성 가능

### 2. DTO 문서화
```java
@Schema(description = "계정 정보")
public class Account {
    @Schema(description = "계정 ID", example = "1")
    private Long id;
    
    @Schema(description = "계정명", example = "My Account")
    private String name;
}
```

### 3. API 설명 추가
```java
@Operation(summary = "계정 조회", description = "특정 계정의 정보를 조회합니다")
@GetMapping("/{id}")
public ResponseEntity<Account> getAccount(@PathVariable Long id) {
    // ...
}
```

---

## 📝 참고

- Swagger UI는 개발 환경에서만 활성화 권장
- 프로덕션 환경에서는 보안상 비활성화 고려
- API 변경 시 자동으로 문서 업데이트됨


