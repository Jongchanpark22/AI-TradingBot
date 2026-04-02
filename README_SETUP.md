# Crypto Trading Bot - 개발 가이드

## 빠른 시작 (Quick Start)

### 1. 환경 설정

#### 필수 요구사항
- Java 17+
- Gradle 8.5+
- Docker & Docker Compose
- MySQL 8.0+
- Redis 7+

#### 설치

```bash
# 저장소 클론
git clone <repository-url>
cd AI-TradingBot

# 의존성 설치 및 빌드
./gradlew clean build -x test
```

### 2. 로컬 개발 환경 실행

#### Docker Compose로 MySQL + Redis 실행

```bash
docker-compose up -d
```

이 명령어는 다음을 실행합니다:
- MySQL 8.0 (포트: 3306)
- Redis 7 (포트: 6379)

#### 데이터베이스 초기화

```bash
# MySQL 접속
docker exec -it crypto-trading-bot-mysql mysql -u bot_user -p
# 비밀번호: password

# 또는 직접 연결
mysql -h 127.0.0.1 -u bot_user -p crypto_bot
```

### 3. 애플리케이션 실행

```bash
# 개발 환경에서 실행
./gradlew bootRun

# 또는 빌드된 JAR 실행
java -jar build/libs/crypto-trading-bot-0.0.1-SNAPSHOT.jar
```

애플리케이션이 시작되면 `http://localhost:8080/api/health`에 접속하여 상태를 확인할 수 있습니다.

### 4. API 테스트

```bash
# 헬스 체크
curl http://localhost:8080/api/health

# 계정 조회 (예시)
curl http://localhost:8080/api/accounts/1

# 주문 조회 (예시)
curl http://localhost:8080/api/orders/account/1
```

---

## 프로젝트 구조 상세

### 엔티티 (Entity) 설계

이미 생성된 주요 엔티티:
- **User**: 사용자 정보
- **Account**: 거래소 계정 (Upbit, Bithumb)
- **Order**: 주문 기록
- **Position**: 보유 포지션
- **Candle**: 캔들 데이터
- **Ticker**: 현재가 정보
- **Strategy**: 거래 전략 설정
- **RiskRecord**: 일일 리스크 기록

### 패키지 구조

```text
src/main/java/com/example/cryptobot/
├── common/
│   ├── config/           # Spring 설정 클래스
│   │   ├── JpaConfig
│   │   ├── RedisConfig
│   │   └── HttpClientConfig
│   ├── exception/        # 예외 처리
│   │   ├── BusinessException
│   │   └── GlobalExceptionHandler
│   ├── entity/           # 기본 엔티티
│   │   └── BaseEntity
│   ├── util/             # 유틸리티
│   └── logging/          # 로깅
├── exchange/             # 거래소 연동
│   ├── core/             # 추상 인터페이스
│   ├── upbit/            # 업비트 API
│   └── bithumb/          # 빗썸 API
├── market/               # 시장 데이터
│   ├── candle/           # 캔들 데이터
│   ├── ticker/           # 호가 정보
│   └── websocket/        # 실시간 데이터
├── account/              # 계정 관리
│   ├── User (엔티티)
│   ├── Account (엔티티)
│   ├── UserRepository
│   ├── AccountRepository
│   ├── AccountService
│   └── AccountController
├── order/                # 주문 관리
│   ├── Order (엔티티)
│   ├── OrderRepository
│   ├── OrderService
│   └── OrderController
├── portfolio/            # 포지션 관리
│   ├── Position (엔티티)
│   ├── PositionRepository
│   ├── PositionService
│   └── PositionController
├── strategy/             # 거래 전략
│   ├── core/
│   │   ├── Strategy (엔티티)
│   │   └── StrategyRepository
│   ├── momentum/          # 모멘텀 전략
│   ├── meanreversion/    # 평균회귀 전략
│   └── ai/               # AI 기반 전략
├── risk/                 # 리스크 관리
│   ├── RiskRecord (엔티티)
│   └── RiskRecordRepository
├── execution/            # 실행 엔진
├── backtest/             # 백테스트
├── papertrade/           # 모의투자
├── alert/                # 알림
├── admin/                # 관리자 기능
└── CryptoTradingBotApplication (메인)
```

---

## 개발 가이드

### 1. 새로운 기능 추가

#### 엔티티 추가 예시
```java
// 1. Entity 생성
@Entity
@Table(name = "example_table")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExampleEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // ... 필드
}

// 2. Repository 생성
@Repository
public interface ExampleRepository extends JpaRepository<ExampleEntity, Long> {
    // 커스텀 쿼리 메소드
}

// 3. Service 생성
@Service
@RequiredArgsConstructor
@Transactional
public class ExampleService {
    private final ExampleRepository exampleRepository;
    // ... 비즈니스 로직
}

// 4. Controller 생성
@RestController
@RequestMapping("/examples")
@RequiredArgsConstructor
public class ExampleController {
    private final ExampleService exampleService;
    // ... API 엔드포인트
}
```

### 2. 거래소 API 연동 예시
```java
// exchange/upbit/UpbitExchangeService.java
@Service
@RequiredArgsConstructor
public class UpbitExchangeService {
    private final WebClient webClient;
    
    public TickerData getCurrentPrice(String symbol) {
        // Upbit API 호출 로직
    }
    
    public Order placeOrder(Order order) {
        // 주문 생성 로직
    }
}
```

### 3. 전략 구현 예시
```java
// strategy/momentum/MomentumStrategy.java
@Service
@RequiredArgsConstructor
public class MomentumStrategy {
    private final CandleRepository candleRepository;
    
    public TradeSignal generateSignal(String symbol, Candle.CandlePeriod period) {
        // 기술적 분석을 통한 거래 신호 생성
    }
}
```

---

## 데이터베이스 스키마

### 주요 테이블

#### users
- id: PK
- username: 사용자명
- email: 이메일
- role: 역할 (ADMIN, USER)
- is_active: 활성 여부

#### accounts
- id: PK
- user_id: FK
- exchange_type: 거래소 타입 (UPBIT, BITHUMB)
- api_key: API 키
- secret_key: 시크릿 키
- total_balance: 총 잔고
- available_balance: 가용 잔고
- locked_balance: 주문 대기 잔고

#### orders
- id: PK
- account_id: FK
- symbol: 코인 심볼
- type: 주문 타입 (LIMIT, MARKET)
- side: 매매 방향 (BUY, SELL)
- price: 주문 가격
- quantity: 주문 수량
- filled_quantity: 체결 수량
- status: 주문 상태 (PENDING, PARTIALLY_FILLED, FILLED, CANCELLED)

#### positions
- id: PK
- account_id: FK
- symbol: 코인 심볼
- quantity: 보유 수량
- avg_buy_price: 평균 매입가
- current_price: 현재가
- unrealized_profit: 미실현 손익
- status: 포지션 상태 (OPEN, CLOSED, PARTIAL)

---

## 다음 단계

### 즉시 구현 예정
- [ ] Upbit API 연동 구현
- [ ] Bithumb API 연동 구현
- [ ] 거래 신호 생성 로직
- [ ] 주문 실행 엔진
- [ ] 포지션 추적 및 손익 계산

### 중기 계획
- [ ] 웹소켓 실시간 데이터 처리
- [ ] 백테스트 엔진
- [ ] 모의투자(Paper Trading)
- [ ] 리스크 관리 고도화

### 장기 계획
- [ ] AI 기반 신호 생성
- [ ] Discord/Telegram 알림
- [ ] 대시보드 UI
- [ ] 클라우드 배포

---

## 문제 해결

### 데이터베이스 연결 오류
```bash
# MySQL 상태 확인
docker-compose ps

# MySQL 로그 확인
docker-compose logs mysql

# 재시작
docker-compose restart mysql
```

### Gradle 빌드 오류
```bash
# 캐시 삭제 후 재빌드
./gradlew clean build -x test
```

### 포트 충돌
```bash
# 포트 변경 (application.yml)
server:
  port: 8081
```

---

## 기여 가이드

1. Feature 브랜치 생성
2. 커밋 작성
3. Pull Request 생성
4. 리뷰 및 머지

---

## 라이선스

MIT License

