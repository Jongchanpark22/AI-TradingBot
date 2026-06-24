# AI TradingBot — 업비트 암호화폐 자동매매 시스템

Spring Boot 기반의 업비트(Upbit) 암호화폐 자동매매 봇입니다.  
시장 레짐(추세/횡보)을 실시간 분류하고, 멀티타임프레임 필터 + 복합 기술적 지표를 통해 매수 신호를 생성하며, ATR 기반 동적 손절과 트레일링 스탑으로 포지션을 관리합니다.

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.3 |
| ORM | Spring Data JPA (Hibernate) |
| Database | MySQL |
| Cache | Redis (Spring Data Redis) |
| HTTP Client | Spring WebFlux, OkHttp3 |
| WebSocket | OkHttp3 (업비트 실시간 시세) |
| Security | Spring Security, JJWT 0.12.3 |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Gradle |
| Deploy | Docker, Docker Compose |

---

## 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                    HybridStrategyExecutor                   │
│                     (15분마다 스케줄 실행)                    │
│                                                             │
│  1. MarketScannerService → 후보 코인 선별 (최대 20개)        │
│  2. 업비트 API → 최신 캔들/티커 fetch → DB 저장              │
│  3. 지표 계산 (EMA, MACD, RSI, ATR, 거래량)                 │
│  4. RegimeClassifier → 시장 레짐 분류 (ADX 기반)            │
│  5. HybridSignalAnalyzer → 매수/매도 신호 생성              │
│  6. 멀티타임프레임 필터 (1H, 4H)                            │
│  7. EMA 이격 필터                                           │
│  8. RiskManager → ATR 기반 손절/목표가/포지션 크기          │
│  9. 업비트 REST API → 실제 주문 제출                        │
│ 10. PositionMonitor 등록 → WebSocket 실시간 모니터링        │
└─────────────────────────────────────────────────────────────┘
                              │
                    WebSocket (실시간 틱)
                              │
┌─────────────────────────────────────────────────────────────┐
│                      PositionMonitor                        │
│                   (~100ms 틱 단위 반응)                      │
│                                                             │
│  - 손절선 터치 → 즉시 시장가 전량 매도                       │
│  - +2R 도달 → 50% 부분청산 + 손절선 진입가로 이동            │
│  - 최고가 갱신 → 트레일링 스탑 상향 (Chandelier Exit)        │
└─────────────────────────────────────────────────────────────┘
```

---

## 전략 상세

### 1단계 — 시장 스캐너 (MarketScannerService)

15분마다 전체 KRW 마켓을 스캔하여 매매 후보 코인을 선별합니다.

**하드 필터 (1차 탈락 기준)**

| 조건 | 기준값 |
|---|---|
| 24h 거래대금 | 50억 KRW 이상 |
| 24h 변동률 | +0.5% ~ +7% |
| 거래 중단 코인 | 제외 |
| 투자 경고(CAUTION) 코인 | 제외 |

**점수 산출 (100점 만점)**

| 항목 | 배점 | 기준 |
|---|---|---|
| 거래대금 점수 | 40점 | 후보군 내 정규화 — 유동성 높은 코인 우대 |
| 트렌드 위치 점수 | 35점 | 현재가가 24h 범위의 55~85% 구간에 위치 (상승 추세 진행 중) |
| 모멘텀 점수 | 25점 | 변동률 +1%~+5% 구간 최고점 (+3% 근방이 만점) |

상위 20개 코인을 다음 단계로 전달합니다.

---

### 2단계 — 시장 레짐 분류 (RegimeClassifier)

ADX(14) 지표를 기반으로 현재 시장 상태를 분류합니다.

```
ADX < 20  → RANGING      (횡보장 — 신규 매수 차단)
ADX 20~25 → NEUTRAL      (불분명 — 초기 추세 포착 허용)
ADX > 25  → TRENDING
            +DI > -DI   → TRENDING_UP   (상승 추세 — 매수 허용)
            +DI < -DI   → TRENDING_DOWN (하락 추세 — 대기)
```

RANGING 레짐에서는 BUY/STRONG_BUY 신호를 차단합니다. NEUTRAL은 추세 초입을 포착하기 위해 허용합니다.

---

### 3단계 — 멀티타임프레임 필터

15분봉에서 신호가 나와도 상위 타임프레임이 하락 추세이면 차단합니다.

```
1시간봉: EMA12 > EMA26 → 상승추세 → 통과
4시간봉: EMA12 > EMA26 → 상승추세 → 통과
둘 중 하나라도 하락추세 → 매수 신호 차단
```

---

### 4단계 — 복합 신호 분석 (HybridSignalAnalyzer)

5가지 지표를 조합하여 매수/매도 신호를 생성합니다.

#### 추세 신호 (EMA)

| 조건 | 신호 | 점수 |
|---|---|---|
| EMA12 > EMA26 > SMA50 | STRONG_UPTREND | +2 |
| EMA12 > EMA26 | UPTREND | +1 |
| EMA12 < EMA26 < SMA50 | STRONG_DOWNTREND | -2 |
| EMA12 < EMA26 | DOWNTREND | -1 |

#### 모멘텀 신호 (MACD 12-26-9)

| 조건 | 신호 | 점수 |
|---|---|---|
| MACD > 시그널 > 0 + 히스토그램 골든크로스 | STRONG_BUY | +2 |
| MACD > 시그널 > 0 + 히스토그램 증가 | BUY | +1 |
| MACD < 시그널 < 0 + 데드크로스 | STRONG_SELL | -2 |
| MACD < 시그널 < 0 | SELL | -1 |

MACD 위에 있어도 히스토그램이 감소하면 NEUTRAL 처리(늦은 진입 차단).

#### RSI 신호 (RSI 14)

| 구간 | 신호 | 점수 |
|---|---|---|
| 50~65 | WEAK_BUY | +1 (모멘텀 빌드업 구간) |
| > 70 | OVERBOUGHT | -1 |
| < 35 | OVERSOLD | 0 (하락 위험 — 점수 없음) |

#### 거래량 신호

| 현재 거래량 / 20MA | 신호 |
|---|---|
| 1.5배 이상 | VERY_HIGH_CONFIDENCE |
| 1.3배 이상 | HIGH_CONFIDENCE |
| 1.0배 이상 | NORMAL |
| 0.8배 이상 | LOW_CONFIDENCE |
| 0.8배 미만 | VERY_LOW_CONFIDENCE → 즉시 차단 |

#### 캔들 패턴 신호

| 패턴 | 조건 | 점수 |
|---|---|---|
| Hammer | 하락 꼬리 > 전체 범위의 50%, 양봉 | +1 |
| Shooting Star | 상승 꼬리 > 전체 범위의 50%, 음봉 | -1 |
| Strong Bullish | 몸통 > 꼬리 2배, 양봉 | +1 |
| Consecutive Bullish | 연속 2봉 양봉 | +1 |
| Strong Bearish | 몸통 > 꼬리 2배, 음봉 | -1 |

#### 최종 신호 결정 (매수 필수 조건: 추세↑ + 모멘텀↑ + RSI 과매수 아님)

| 신호 | 조건 | 신뢰도 |
|---|---|---|
| STRONG_BUY | 총점 ≥ 5 + VERY_HIGH 거래량 | 95% |
| BUY | 총점 ≥ 4 + HIGH 이상 거래량 | 80% |
| STRONG_SELL | 매도점수 ≥ 4 + VERY_HIGH 거래량 | 95% |
| SELL | 매도점수 ≥ 3 + HIGH 이상 거래량 | 80% |

---

### 5단계 — EMA 이격 필터

현재가가 EMA26 대비 3% 초과 이격 시 고점 추격을 차단합니다.

```
현재가 > EMA26 × 1.03 → NO_SIGNAL (고점 추격 차단)
```

---

### 6단계 — 레짐 기반 전략 라우팅 (RegimeRouter)

레짐에 따라 서로 다른 진입 전략을 선택합니다.

#### 추세장 전략: DonchianBreakoutStrategy

터틀 트레이딩에서 영감을 받은 Donchian 채널 돌파 전략입니다.

- **조건 1** — Donchian 채널 돌파: 현재 종가 > 직전 20봉 최고가
- **조건 2** — Supertrend 상향: Supertrend(10, 3.0) 방향이 상승
- **조건 3** — 거래량 확인: 현재 거래량 ≥ 20MA × 1.3

세 조건을 모두 만족할 때만 진입. 사이징과 손절은 RiskManager에 위임합니다.

#### 횡보장 전략: MeanReversionStrategy

볼린저 밴드 + RSI 평균회귀 전략입니다.

- **조건 1** — 볼린저 밴드 하단 터치: 최저가 ≤ BB(20, 2σ) 하단
- **조건 2** — RSI 과매도: RSI(14) < 30
- **조건 3** — 반전 캔들: 종가 > 시가 (양봉)

세 조건을 모두 만족할 때만 진입합니다.

---

### 7단계 — 포지션 크기 및 손절/목표가 결정 (RiskManager)

ATR 기반으로 변동성에 맞게 동적으로 결정합니다.

```
손절가      = 진입가 - 2.5 × ATR(14)    [안전 한도: 2%~10%]
목표가      = 진입가 + 5.0 × ATR(14)    [손익비 2:1]
포지션 크기 = (자산 × riskPerTrade) / 손절 폭
```

기본 설정값 (application.yml):

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| riskPerTrade | 10% | 1회 기본 투자 비율 |
| maxOrderPercent | 15% | STRONG_BUY 시 최대 투자 비율 |
| minOrderAmount | 20,000 KRW | 최소 주문 금액 |
| maxOpenPositions | 3 | 최대 동시 보유 코인 수 |
| maxDailyLoss | 10% | 일일 손실 한도 (킬 스위치) |

---

### 8단계 — 실시간 포지션 모니터링 (PositionMonitor)

업비트 WebSocket으로 실시간 틱(~100ms)을 수신하여 포지션을 관리합니다.

#### 처리 흐름

```
매 틱 수신
    ↓
최고가(highestPriceSinceEntry) 업데이트
    ↓
RiskManager.updateTrailing() 호출
    ↓
현재가 ≤ 손절선?  → 시장가 전량 매도 (즉시)
+2R 도달?        → 시장가 50% 부분청산 + 손절선 → 진입가(본전)
최고가 갱신?     → Chandelier Exit 계산 → 손절선 상향
                  (highestPrice - 3 × ATR, 단조 증가)
```

#### Chandelier Exit (트레일링 스탑)

```
트레일링 손절 = 포지션 진입 이후 최고가 - 3 × ATR
              (손절선은 올라가기만 하고 내려가지 않음)
ATR 데이터 없을 경우 → 최고가 대비 3% 폴백 적용
```

서버 재시작 시 DB에서 OPEN 상태 포지션을 로드하여 모니터링을 재개합니다.

---

### 매도 정책 요약

| 상황 | 동작 |
|---|---|
| 손절선 터치 | WebSocket 틱 감지 → 즉시 시장가 전량 매도 |
| +2R 도달 | 50% 부분청산 + 손절선 진입가로 이동 |
| Chandelier 손절선 터치 | 즉시 시장가 전량 매도 |
| STRONG_SELL 신호 (bearishScore≥4 + VERY_HIGH 거래량) | 즉시 매도 |
| SELL 신호 | 무시 (15분봉 노이즈 — 트레일링 스탑에 위임) |

---

### 포트폴리오 상관관계 가드 (CorrelationGuard)

BTC, ETH, SOL 등 높은 상관관계의 코인들을 동시에 보유하면 실질적으로 1개 포지션을 3배 크기로 운용하는 것과 같습니다. 이를 방지합니다.

```
후보 코인의 최근 50봉 log-return과
기존 보유 코인들의 log-return의 피어슨 상관계수 계산
|상관계수| > 0.7 → 신규 진입 차단
```

---

## 백테스트 엔진 (BacktestEngine)

단일 심볼에 대해 역사적 캔들 데이터를 바 단위로 재현합니다.

- **진입**: 다음 봉 시가에 체결 (룩어헤드 없음)
- **손절/목표 체크**: 봉 내부 고/저가 기준 (같은 봉에서 손절·목표 동시 터치 시 손절 우선 — 보수적 평가)
- **트레일링**: 실제 운영과 동일한 `RiskManager.updateTrailing()` 사용
- **순수 함수**: 상태 없음, I/O 없음 → 파라미터 스윕 가능

---

## 데이터베이스 ERD

```
USERS ||--o{ ACCOUNTS : has
ACCOUNTS ||--o{ ORDERS : places
ACCOUNTS ||--o{ POSITIONS : has
ACCOUNTS ||--o{ RISK_RECORDS : generates
ACCOUNTS ||--o{ STRATEGIES : executes
```

### 주요 테이블

| 테이블 | 설명 |
|---|---|
| `users` | 사용자 계정 |
| `accounts` | 거래소 API 키 연동 정보 |
| `positions` | 현재 보유 포지션 (손절가, 목표가, ATR 등 리스크 상태 포함) |
| `orders` | 주문 이력 |
| `candles` | 캔들 데이터 (15분, 1시간, 4시간 등 다중 주기) |
| `tickers` | 최신 시세 스냅샷 |
| `risk_records` | 일일 손익/킬스위치 상태 기록 |
| `strategy_run_logs` | 매 스케줄 실행 결과 로그 (신호, 신뢰도, 차단 이유) |
| `trade_history` | 체결된 거래 이력 (진입/청산가, PnL, ATR, 청산 유형) |

`positions` 테이블에 저장되는 리스크 관리 상태:

| 컬럼 | 설명 |
|---|---|
| `initial_stop_loss` | 진입 시 최초 손절가 |
| `current_stop_loss` | 현재 손절가 (트레일링으로 갱신) |
| `take_profit_price` | 목표가 |
| `highest_price_since_entry` | 진입 후 최고가 (Chandelier Exit 계산용) |
| `atr_at_entry` | 진입 시 ATR 값 |
| `partial_exit_done` | 부분청산 완료 여부 |

---

## 빠른 시작

### 사전 요구사항

- Java 17
- MySQL 8.0+
- Redis
- 업비트 API 키

### 환경 변수 설정

```bash
export LOCAL_DB_URL=jdbc:mysql://localhost:3306/trading_bot
export LOCAL_DB_USERNAME=your_db_user
export LOCAL_DB_PASSWORD=your_db_password
export UPBIT_ACCESS_KEY=your_upbit_access_key
export UPBIT_SECRET_KEY=your_upbit_secret_key
```

### 빌드 및 실행

```bash
# 빌드
./gradlew build

# 로컬 실행
./gradlew bootRun

# Docker로 실행
docker-compose up
```

### API 문서

서버 실행 후 `http://localhost:8080/api/swagger-ui.html` 에서 Swagger UI를 확인할 수 있습니다.

---

## 주요 설정 (application.yml)

```yaml
trading:
  scheduler:
    enabled: true             # false: 전략 스케줄러 중지

  scanner:
    min-trade-price-24h: 5000000000   # 최소 24h 거래대금 (50억 KRW)
    max-coins: 20                     # 스캔 결과 최대 코인 수
    min-change-rate: 0.005            # 최소 24h 변동률 (+0.5%)
    max-change-rate: 0.07             # 최대 24h 변동률 (+7%)

  risk:
    stop-loss-percent: 5.0            # ATR 없을 때 폴백 손절 비율
    take-profit-percent: 10.0         # ATR 없을 때 폴백 목표 비율
    max-daily-loss: 10.0              # 일일 최대 손실 한도 (킬 스위치)
    risk-per-trade: 0.10              # 1회 기본 투자 비율 (총자산의 10%)
    max-order-percent: 0.15           # STRONG_BUY 시 최대 투자 비율 (15%)
    min-order-amount: 20000           # 최소 주문 금액 (KRW)
    max-open-positions: 3             # 최대 동시 보유 코인 수
```

---

## 테스트

```bash
./gradlew test
```

---

## 핵심 클래스 맵

| 클래스 | 역할 |
|---|---|
| `HybridStrategyExecutor` | 전략 진입점, 15분 스케줄러 |
| `MarketScannerService` | 매매 후보 코인 선별 |
| `RegimeClassifier` | ADX 기반 시장 레짐 분류 |
| `RegimeRouter` | 레짐에 따른 전략 선택 |
| `HybridSignalAnalyzer` | 5개 지표 복합 신호 분석 |
| `TechnicalIndicatorCalculator` | EMA, MACD, RSI, 볼린저 밴드 계산 |
| `DonchianBreakoutStrategy` | 추세장 진입 전략 |
| `MeanReversionStrategy` | 횡보장 진입 전략 |
| `RiskManager` | ATR 기반 손절/목표가/포지션 크기 결정 |
| `PositionMonitor` | WebSocket 실시간 포지션 관리 |
| `CorrelationGuard` | 포트폴리오 상관관계 필터 |
| `BacktestEngine` | 전략 백테스트 |
| `Indicators` | ADX, Supertrend, Donchian, OBV 등 지표 라이브러리 |
