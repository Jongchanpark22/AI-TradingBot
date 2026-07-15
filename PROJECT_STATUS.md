# AI-TradingBot — Java 구현 완료 현황

> 작성일: 2026-07-15  
> 현재 브랜치: `feat/phase2-backtest-logging` (PR #16 오픈)  
> 전체 Java 파일: 92개 | 테스트: 11개 클래스, 91개 케이스 (전원 통과)

---

## 1. 기술 스택

| 영역 | 기술 |
|------|------|
| 언어 | Java 17 |
| 프레임워크 | Spring Boot 3.2.3 |
| ORM | Spring Data JPA (Hibernate) |
| DB | MySQL (ddl-auto: update) |
| 캐시 | Redis (Spring Cache, TTL 10분) |
| HTTP 클라이언트 | RestTemplate (동기), WebFlux WebClient (비동기 ML 호출) |
| WebSocket | OkHttp WebSocketClient (업비트 실시간 시세) |
| 인증 | JWT (업비트 API 서명), Spring Security |
| API 문서 | SpringDoc OpenAPI (Swagger UI `/swagger-ui.html`) |
| 빌드 | Gradle 8.7 |
| 테스트 | JUnit 5 |

---

## 2. Spring 아키텍처 패턴

### 전통적 MVC가 아닌 레이어드 아키텍처

이 프로젝트는 View가 없는 REST API 서버이므로, 순수 MVC보다 **레이어드 아키텍처** + 전략 패턴 조합으로 구성됩니다.

```
┌─────────────────────────────────────┐
│          REST Controller Layer       │  @RestController
│  AccountController, OrderController  │  HTTP 요청 수신, 응답 반환
│  PositionController, UpbitController │
│  TradeHistoryController              │
│  BacktestGenerationController (신규) │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│           Service Layer              │  @Service
│  AccountService, RiskService         │  비즈니스 로직, 트랜잭션 관리
│  HybridStrategyExecutor (@Scheduled) │  스케줄러 기반 자동 실행
│  StrategyRunLogService               │
│  BacktestDataGenerationService (신규)│
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│           Repository Layer           │  @Repository (JPA)
│  CandleRepository, TickerRepository  │  DB 접근 (Spring Data JPA)
│  PositionRepository, OrderRepository │
│  TradeHistoryRepository              │
│  StrategyRunLogRepository            │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│           Entity Layer               │  @Entity
│  Candle, Ticker, Position            │  JPA 엔티티 → MySQL 테이블
│  Order, TradeHistory, Account        │
│  StrategyRunLog, StrategyConfig      │
└─────────────────────────────────────┘
```

### 추가 패턴들

| 패턴 | 적용 위치 | 설명 |
|------|-----------|------|
| **전략 패턴** | `Strategy` 인터페이스 | `DonchianBreakoutStrategy`, `MeanReversionStrategy`가 구현. `StrategyRegistry`가 자동 수집 |
| **순수 함수 패턴** | `RiskManager`, `BacktestEngine` | 상태 없음, I/O 없음 → 테스트/파라미터 스위프 친화적 |
| **팩토리 패턴** | `FeatureSnapshotFactory` | 라이브와 백테스트 양쪽에서 동일 feature 빌드 보장 |
| **게이트 패턴** | `AiSignalGate` | RULE_ONLY / SHADOW / ENSEMBLE 세 모드로 ML 통합 여부 제어 |
| **스케줄러** | `HybridStrategyExecutor` | `@Scheduled(cron = "0 0/15 * * * *")` 15분마다 신호 실행 |
| **옵저버** | `PositionMonitor` + WebSocket | 업비트 실시간 시세 수신 → 포지션 스탑 감시 |
| **레이어 분리** | 캐시 서비스 (`common/cache`) | Redis 캐시 로직을 별도 서비스로 분리 |

---

## 3. 전체 패키지 구조 및 역할

```
com.example.cryptobot/
│
├── CryptoTradingBotApplication.java          # Spring Boot 진입점
│
├── account/                                   # 계좌 관리
│   ├── Account.java                           # 거래 계좌 엔티티
│   ├── AccountController.java                 # GET /api/accounts
│   ├── AccountRepository.java
│   ├── AccountService.java                    # getPrimaryAccount()
│   └── dto/AccountResponse.java
│
├── common/
│   ├── cache/                                 # Redis 캐시 서비스 (TTL 10분)
│   │   ├── CandleCacheService.java            # 캔들 데이터 캐시
│   │   ├── SignalCacheService.java            # 신호 결과 캐시
│   │   ├── TickerCacheService.java            # 시세 캐시
│   │   └── TradeLogCacheService.java          # 거래 로그 캐시
│   ├── config/
│   │   ├── AiWebClientConfig.java             # Python ML 서버용 WebClient 빈
│   │   ├── JwtTokenProvider.java              # 업비트 JWT 서명
│   │   ├── RedisConfig.java                   # Redis 연결 설정
│   │   ├── SchedulerConfig.java               # 스케줄러 스레드풀 설정
│   │   ├── SecurityConfig.java                # Spring Security (개발 환경 무인증)
│   │   └── SwaggerConfig.java                 # OpenAPI 설정
│   ├── entity/BaseEntity.java                 # createdAt, updatedAt 공통 필드
│   ├── exception/BusinessException.java
│   ├── exception/GlobalExceptionHandler.java  # @ControllerAdvice 전역 예외 처리
│   └── HealthController.java                  # GET /actuator/health
│
├── exchange/upbit/                            # 업비트 거래소 연동
│   ├── client/
│   │   ├── UpbitApiClient.java                # REST API (공개 + 인증 API)
│   │   └── UpbitWebSocketClient.java          # 실시간 시세 WebSocket
│   ├── config/
│   │   ├── UpbitApiProperties.java            # API URL, 키 설정
│   │   └── UpbitClientConfig.java             # RestTemplate 빈
│   ├── controller/UpbitController.java        # 수동 시세 조회 엔드포인트
│   ├── dto/                                   # 업비트 API 응답 DTO
│   │   ├── UpbitAccountDto.java
│   │   ├── UpbitCandleDto.java                # 캔들 데이터 (timestamp ms 포함)
│   │   ├── UpbitMarketDto.java
│   │   ├── UpbitOrderDto.java
│   │   └── UpbitTickerDto.java
│   └── service/
│       ├── UpbitAccountService.java           # 잔고 조회/동기화
│       ├── UpbitMarketService.java            # 캔들/시세 수집 + DB 저장
│       ├── UpbitOrderService.java             # 주문 체결 상태 추적
│       └── UpbitSyncService.java              # 계좌 데이터 주기적 동기화
│
├── market/
│   ├── candle/
│   │   ├── Candle.java                        # 분봉 엔티티 (1/5/15/30/60/240분)
│   │   └── CandleRepository.java
│   └── ticker/
│       ├── Ticker.java                        # 현재 시세 스냅샷 엔티티
│       └── TickerRepository.java
│
├── order/                                     # 주문 처리
│   ├── Order.java                             # 주문 엔티티 (PENDING/FILLED/CANCELLED)
│   ├── OrderController.java
│   ├── OrderRepository.java
│   ├── OrderService.java
│   └── OrderSyncService.java                  # 업비트 체결 상태 동기화
│
├── portfolio/                                 # 포지션 관리
│   ├── Position.java                          # 오픈 포지션 (signalId, ATR 스탑 포함)
│   ├── PositionController.java
│   ├── PositionRepository.java
│   └── PositionService.java
│
├── risk/                                      # 일일 리스크 기록
│   ├── RiskRecord.java                        # 일일 손익 기록 엔티티
│   ├── RiskRecordRepository.java
│   └── RiskService.java                       # validateBuy(): 일일 한도/최대 포지션 수 검증
│
├── strategy/
│   ├── ai/                                    # ML 연동 레이어 (Phase 0)
│   │   ├── AiSignalClient.java                # Python /predict 호출 (WebFlux)
│   │   ├── AiSignalGate.java                  # RULE_ONLY / SHADOW / ENSEMBLE 모드
│   │   ├── FeatureSnapshotFactory.java        # 공용 feature 빌더 (라이브/백테스트 동일)
│   │   └── dto/
│   │       ├── AiPredictionResponse.java      # buy_probability, model_version
│   │       └── FeatureSnapshot.java           # Python 계약 31개 필드
│   │
│   ├── backtest/                              # 백테스트 엔진
│   │   ├── BacktestEngine.java                # 순수 함수 백테스트 (RegimeRouter 기반)
│   │   ├── BacktestDataGenerationService.java # ML 학습 데이터 대량 생성 (Phase 2)
│   │   ├── BacktestGenerationController.java  # POST /admin/backtest/generate
│   │   ├── BacktestResult.java                # 결과 집계 record
│   │   ├── BacktestTrade.java                 # 개별 거래 record
│   │   └── HybridBacktestEngine.java          # HybridSignalAnalyzer 기반 로깅 백테스트
│   │
│   ├── core/                                  # 전략 공통 인프라
│   │   ├── Strategy.java                      # 전략 인터페이스: id(), type(), evaluate()
│   │   ├── StrategyConfig.java                # DB 전략 설정 엔티티 (구 Strategy JPA)
│   │   ├── StrategyRegistry.java              # @Component Strategy 빈 자동 수집
│   │   ├── StrategyRepository.java
│   │   ├── StrategyRunLog.java                # 신호 실행 로그 (featureJson, signalId, source)
│   │   ├── StrategyRunLogRepository.java
│   │   ├── StrategyRunLogService.java         # FeatureSnapshot → JSON + DB 저장
│   │   ├── StrategySignal.java                # 전략 신호 record (direction, entryPrice, atr)
│   │   └── StrategyType.java                  # TREND_FOLLOWING / MEAN_REVERSION / BREAKOUT / SCALPING
│   │
│   ├── hybrid/                                # 메인 매매 엔진
│   │   ├── HybridSignalAnalyzer.java          # 5개 신호 분석 (추세/모멘텀/RSI/거래량/캔들)
│   │   ├── HybridStrategyExecutor.java        # 15분 스케줄 실행 + AI 게이트
│   │   ├── TechnicalIndicatorCalculator.java  # EMA/SMA/MACD/RSI/볼린저 계산
│   │   └── TradeExecutionEngine.java          # 매수 주문 사이징 + 제출
│   │
│   ├── indicator/
│   │   └── Indicators.java                    # 순수 함수 지표 라이브러리
│   │                                          # ATR(Wilder), ADX/±DI, Bollinger,
│   │                                          # Donchian, Supertrend
│   │
│   ├── monitor/
│   │   └── PositionMonitor.java               # WebSocket 기반 실시간 스탑/트레일링 감시
│   │
│   ├── mtf/                                   # 다중 타임프레임 필터 (Phase 4)
│   │   ├── HigherTimeframeFilter.java
│   │   └── MultiTimeframeRouter.java          # 15분 신호를 1h/4h 상위 추세로 확인
│   │
│   ├── paper/
│   │   └── PaperBroker.java                   # 모의 거래 시뮬레이터
│   │
│   ├── portfolio/
│   │   └── CorrelationGuard.java              # 상관관계 높은 코인 동시 매수 방지
│   │
│   ├── range/
│   │   └── MeanReversionStrategy.java         # 볼린저 밴드 + RSI 평균 회귀 (Strategy 구현)
│   │
│   ├── regime/
│   │   ├── MarketRegime.java                  # TRENDING_UP / RANGING / NEUTRAL / TRENDING_DOWN
│   │   ├── RegimeClassifier.java              # ADX + 이동평균으로 레짐 판별
│   │   └── RegimeRouter.java                  # 레짐에 맞는 전략 선택 + 진입 계획
│   │
│   ├── risk/                                  # 순수 함수 리스크 매니저
│   │   ├── EntryPlan.java                     # 진입 계획 record (수량, 손절가, 목표가)
│   │   ├── ExchangeStopService.java           # 거래소 서버사이드 스탑 인터페이스
│   │   ├── InMemoryExchangeStopService.java   # 메모리 기반 구현
│   │   ├── RiskManager.java                   # planLong(), updateTrailing() — 순수 함수
│   │   ├── RiskParameters.java                # SL=2.5×ATR, TP=2R(=5×ATR), Trail=3×ATR
│   │   └── TrailingDecision.java              # 트레일링 스탑 결정 record
│   │
│   ├── scanner/
│   │   └── MarketScannerService.java          # 24h 거래대금/변동률 기준 코인 필터링
│   │
│   └── trend/
│       └── DonchianBreakoutStrategy.java      # Donchian 채널 돌파 전략 (Strategy 구현)
│
└── trade/                                     # 거래 이력
    ├── TradeHistory.java                      # 청산 기록 (pnl, exitType, signalId, source)
    ├── TradeHistoryController.java
    ├── TradeHistoryRepository.java
    └── TradeHistoryService.java               # record() — 손익 계산 + 저장
```

---

## 4. DB 테이블 구조 (JPA → MySQL)

| 테이블 | 엔티티 | 주요 컬럼 |
|--------|--------|-----------|
| `accounts` | Account | balance, currency, is_active |
| `candles` | Candle | symbol, period, timestamp, OHLCV |
| `tickers` | Ticker | symbol, current_price, change_24h |
| `orders` | Order | symbol, type, status, exchange_order_id |
| `positions` | Position | symbol, avg_buy_price, stop_loss, signal_id |
| `trade_history` | TradeHistory | pnl, exit_type, signal_id, **source** |
| `strategy_run_logs` | StrategyRunLog | signal_id, feature_json, **timestamp_ms**, **source** |
| `strategies` | StrategyConfig | name, config_type, enabled |
| `risk_records` | RiskRecord | daily_pnl, daily_loss_rate |

> **굵게** 표시된 컬럼은 최근 Phase 2에서 추가됨 (ddl-auto:update 자동 적용)

---

## 5. 구현 완료된 Phase 목록

### ✅ Phase 1 — 지표 라이브러리 (PR #4)
- `Indicators.java` 순수 함수 라이브러리
- ATR (Wilder 방식), ADX/±DI, Bollinger Band(20, 2σ), Donchian Channel, Supertrend(10, 3.0)
- 반환 타입: `AdxValue`, `BollingerBand`, `DonchianChannel`, `SupertrendPoint` record

### ✅ Phase 2 — 리스크 매니저 (PR #5)
- `RiskManager` (순수 함수) + `RiskParameters` record
- `planLong()`: ATR 기반 포지션 사이징, 손절가/목표가 산출
- `updateTrailing()`: 챈들리어 트레일링 스탑 + 1R 부분 청산 + 브레이크이븐
- 기본값: SL=2.5×ATR, TP=5.0×ATR, Trail=3.0×ATR

### ✅ Phase 3 — 레짐 라우터 (PR #6)
- `RegimeClassifier`: ADX > 25 → 추세장, Bollinger 폭 기준 횡보장 판별
- `RegimeRouter`: 레짐별 전략 선택 → `RegimeRouter.RoutedDecision` 반환
- `DonchianBreakoutStrategy`, `MeanReversionStrategy` (Strategy 인터페이스 구현)

### ✅ Phase 4 — 다중 타임프레임 확인 (PR #7)
- `MultiTimeframeRouter`: 15분봉 신호를 1h/4h 상위 추세로 검증
- `HigherTimeframeFilter`: 상위 타임프레임 EMA 기반 추세 판별

### ✅ Phase 5-6 — 백테스트 엔진 + 페이퍼 브로커 (PR #8)
- `BacktestEngine` (순수 함수): 과거 캔들 재현, RegimeRouter 기반 진입/청산
  - Look-ahead 금지 (다음 봉 시가 체결)
  - 스탑/TP 보수적 순서 (스탑 먼저)
  - 챈들리어 트레일링 → 실 거래와 동일 로직
- `PaperBroker`: 실거래 없이 모의 손익 추적
- `CorrelationGuard`: 상관관계 높은 코인 동시 진입 방지

### ✅ Phase 7 — 실시간 포지션 모니터 (PR #9)
- `PositionMonitor`: 업비트 WebSocket으로 실시간 시세 수신
- 스탑 히트 시 즉시 매도 주문 제출
- `MonitoredPosition` 내부 클래스로 포지션 상태 추적

### ✅ Phase 0 — AI 계측 레이어 (PR #12)
- `signalId` (UUID) 모든 신호에 발급 → Position → TradeHistory 전파
- `StrategyRunLog`에 `featureJson`, `mlBuyProb`, `mlModelVer` 저장
- `AiSignalGate`: 3가지 모드 (RULE_ONLY / SHADOW / ENSEMBLE)
  - ENSEMBLE 모드: ML 확률 < threshold → BUY 신호 차단
  - 페일세이프: ML 서버 다운 → 경고 로그 후 정상 진행

### ✅ Phase 1 (전략 모듈화) — Strategy 인터페이스 (PR #14)
- `Strategy` 인터페이스: `id()`, `type()`, `Optional<StrategySignal> evaluate(List<Candle>)`
- `StrategyRegistry`: 모든 `@Component Strategy` 빈 자동 수집
- `StrategySignal` record: direction, entryPrice, atr, strategyId, strategyType
- 기존 `Strategy.java` (JPA 엔티티) → `StrategyConfig.java`로 이름 변경 (테이블명 유지)

### 🔄 Phase 2 (ML 데이터 생성) — 현재 PR #16 오픈
**Task C** ✅ 완료
- `strategy_run_logs`에 `source VARCHAR(16) DEFAULT 'LIVE'`, `timestamp_ms BIGINT` 추가
- `trade_history`에 `source VARCHAR(16) DEFAULT 'LIVE'` 추가

**Task D** ✅ 완료
- `FeatureSnapshot` Python 계약 31개 필드로 재설계
- `FeatureSnapshotFactory`: 라이브/백테스트 공용 빌더 추출
- 신호 열거형 → 숫자 점수 인코딩 (trendScore, momentumScore 등)

**Task A** ✅ 완료
- `HybridBacktestEngine`: HybridSignalAnalyzer 기반 (라이브와 동일 신호 엔진)
- BUY 신호 → `strategy_run_logs(source='BACKTEST')` 저장
- 청산 → `trade_history(source='BACKTEST')` 에 동일 `signal_id`로 연결
- SL=2.5×ATR, TP=5.0×ATR (라이브와 완전히 일치)

**Task B** ✅ 완료
- `POST /admin/backtest/generate`: 심볼 목록 + 기간(개월수) 파라미터
- 업비트 공개 API 페이지네이션으로 24개월 캔들 일괄 수집
- `UpbitApiClient.getCandles(market, unit, count, to)` 오버로드 추가

---

## 6. 실시간 매매 플로우 (15분 스케줄 기준)

```
@Scheduled (15분마다)
    │
    ▼
MarketScannerService.scanTopCoins()
(거래대금 상위, 변동률 필터)
    │
    ▼ 각 코인
UpbitMarketService.getAndSaveCandles()  ← 업비트 공개 API
    │
    ▼
TechnicalIndicatorCalculator (EMA/MACD/RSI/ATR/Volume)
+ Indicators (ADX/Bollinger/Donchian/Supertrend)
    │
    ▼
RegimeClassifier.classify()
HybridSignalAnalyzer.generateTradeSignal()
(trend + momentum + rsi + volume + candle → BUY/NO_SIGNAL)
    │
    ▼
FeatureSnapshotFactory.build()
→ AiSignalGate.evaluate()
   ├── RULE_ONLY: ML 스킵, 룰 신호 그대로
   ├── SHADOW:    ML 호출 후 로그만 (신호 변경 없음)
   └── ENSEMBLE:  ML 확률 < 0.6 → BUY 차단
    │
    ▼
필터 체인
├── 레짐 필터: RANGING → BUY 차단
├── EMA 이격 필터: close > ema26 × 1.03 → 차단
├── 1시간봉 추세 필터 (MTF)
└── 4시간봉 추세 필터 (MTF)
    │
    ▼
StrategyRunLogService.save()           ← strategy_run_logs DB 저장
    │
    ▼ (BUY 통과 시)
TradeExecutionEngine.executeBySignal()
→ UpbitApiClient.createOrder()        ← 업비트 실거래 주문
    │
    ▼
Position 생성 (signalId 포함)
+ PositionMonitor.trackPosition()     ← WebSocket 실시간 감시 시작
```

---

## 7. 백테스트 → ML 학습 데이터 플로우

```
POST /admin/backtest/generate
    │
    ▼
BacktestDataGenerationService
    │
    ├── 업비트 공개 API (페이지네이션)
    │   └── 24개월 × 15분봉 캔들 수집
    │
    └── HybridBacktestEngine.run()
        │
        ▼ (각 봉, window=50)
        HybridSignalAnalyzer (라이브 동일 로직)
            │
            ▼ BUY 신호 발생 시
            signalId = UUID
            FeatureSnapshotFactory.build()
            strategy_run_logs (source='BACKTEST')
            │
            ▼ 다음 봉 시가 체결
            OpenPosition (SL=2.5×ATR, TP=5.0×ATR)
            │
            ▼ 청산 시 (스탑/TP/트레일링)
            trade_history (source='BACKTEST', 동일 signalId)

Python 학습 시 JOIN:
SELECT s.signal_id, s.feature_json, t.pnl, t.exit_type
FROM strategy_run_logs s
JOIN trade_history t ON t.signal_id = s.signal_id
WHERE s.source = 'BACKTEST' AND s.feature_json IS NOT NULL
```

---

## 8. Python ML 서비스가 받는 FeatureSnapshot 필드 (최종 확정)

```json
{
  "symbol":           "KRW-BTC",
  "timeframe":        "15분",
  "timestampMs":      1720000000000,
  "close":            85000000.0,
  "ema12":            84500000.0,
  "ema26":            83000000.0,
  "sma50":            80000000.0,
  "closeVsEma26Pct":  2.41,
  "macd":             1500.0,
  "macdSignal":       1200.0,
  "macdHist":         300.0,
  "rsi14":            58.3,
  "adx14":            28.5,
  "plusDi":           25.1,
  "minusDi":          18.4,
  "atr14":            1200000.0,
  "bbUpper":          88000000.0,
  "bbLower":          78000000.0,
  "volumeRatio":      1.8,
  "donchianHigh20":   87000000.0,
  "supertrendDir":    1,
  "regime":           "TRENDING_UP",
  "trendScore":       2,
  "momentumScore":    2,
  "rsiScore":         1,
  "candleScore":      1,
  "volumeConfidence": 4,
  "totalScore":       6,
  "ruleSignal":       "STRONG_BUY"
}
```

**점수 인코딩 규칙:**
- `trendScore`: STRONG_UPTREND=2 / UPTREND=1 / SIDEWAYS=0 / DOWNTREND=-1 / STRONG_DOWNTREND=-2
- `momentumScore`: STRONG_BUY=2 / BUY=1 / NEUTRAL=0 / SELL=-1 / STRONG_SELL=-2
- `rsiScore`: WEAK_BUY=1 / OVERBOUGHT=-1 / else=0
- `candleScore`: STRONG_BULLISH|HAMMER|CONSECUTIVE_BULLISH=1 / STRONG_BEARISH|SHOOTING_STAR=-1 / else=0
- `volumeConfidence`: VERY_LOW=0 / LOW=1 / NORMAL=2 / HIGH=3 / VERY_HIGH=4
- `supertrendDir`: 상승=1 / 하락=-1 / 미결정=0

**Python config.py 배수 (자바와 일치):**
- `SL_ATR_MULT = 2.5`
- `TP_ATR_MULT = 5.0`
- `TIME_BARRIER_BARS` — 자바 백테스트에는 없음 (end-of-data 강제 청산만 존재)

---

## 9. Java 구현 완료 여부 판단

### ✅ 완료된 것

| 영역 | 상태 | 비고 |
|------|------|------|
| 업비트 API 연동 (캔들/시세/주문) | ✅ | REST + WebSocket |
| 기술적 지표 라이브러리 | ✅ | ATR, ADX, BB, Donchian, Supertrend |
| 리스크 매니저 (ATR 기반) | ✅ | 순수 함수, 테스트 완료 |
| 레짐 분류기 + 라우터 | ✅ | ADX/이동평균 기반 |
| 다중 타임프레임 필터 | ✅ | 1h/4h 상위 추세 확인 |
| 하이브리드 신호 분석기 | ✅ | 5개 신호 → 복합 점수 |
| 15분 스케줄 자동 매매 | ✅ | @Scheduled |
| AI 게이트 (3모드) | ✅ | RULE_ONLY / SHADOW / ENSEMBLE |
| signal_id 추적 (신호→포지션→거래) | ✅ | UUID, DB 조인 가능 |
| 실시간 포지션 모니터 (WebSocket) | ✅ | 스탑/트레일링 실시간 |
| 전략 인터페이스 모듈화 | ✅ | Strategy, StrategyRegistry |
| 백테스트 엔진 (순수 함수) | ✅ | BacktestEngine, HybridBacktestEngine |
| ML 학습 데이터 생성 엔드포인트 | ✅ | POST /admin/backtest/generate |
| FeatureSnapshot Python 계약 정렬 | ✅ | 31개 필드 확정 |
| DB source/timestamp_ms 컬럼 | ✅ | 학습/실거래 구분 |
| 테스트 (91개 케이스) | ✅ | 전원 통과 |

### ⚠️ PR #16 머지 필요
- `feat/phase2-backtest-logging` 브랜치의 Task A/B/C/D가 아직 main 미반영
- PR 머지 후 `POST /admin/backtest/generate` 실행하여 데이터 생성 권장

### 🔲 Java 쪽 미구현 (선택 사항)
| 항목 | 설명 |
|------|------|
| ENSEMBLE 모드 실제 ON | 현재 application.yml에 `mode: RULE_ONLY` — Python 모델 완성 후 ENSEMBLE로 전환 |
| 다중 심볼 동시 처리 | 현재 순차 처리 — 추후 CompletableFuture/스레드풀 병렬화 가능 |
| Bithumb 연동 | 현재 업비트만 지원 |
| 알림 (Slack/텔레그램) | 구현 없음 |

---

## 10. 다음 단계 — Python ML 서비스

Java 백엔드 핵심 기능은 **완료**입니다.  
이제 Python `ai-service`가 해야 할 작업:

```
1. POST /admin/backtest/generate 실행
   → strategy_run_logs(BACKTEST) 수천 행 + trade_history 매칭
   → 완료 확인: SELECT COUNT(*) ... JOIN ... WHERE source='BACKTEST'

2. data_loader.py
   - MySQL에서 JOIN 쿼리 실행
   - feature_json 파싱 (31개 필드)
   - pnl > 0 → label=1, pnl <= 0 → label=0

3. train.py
   - LightGBM / XGBoost / sklearn 등으로 메타라벨링 모델 학습
   - Walk-forward CV로 AUC 검증
   - 모델 파일 저장 (.pkl 또는 ONNX)

4. serve.py (FastAPI, 포트 8000)
   - POST /predict
   - Request: FeatureSnapshot JSON (31개 필드)
   - Response: { "buy_probability": 0.73, "model_version": "v1" }
   - Java AiSignalClient가 이 API 호출

5. application.yml 변경
   - mode: RULE_ONLY → ENSEMBLE
   - buy-threshold: 0.6 (조정 가능)
```
