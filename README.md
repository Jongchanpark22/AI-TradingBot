# AI-TradingBot — AI 필터링 기반 멀티전략 자동매매 플랫폼

검증된 룰 기반 전략들이 신호를 생성하고, 머신러닝 메타모델이 "지금 이 신호가 수익으로 이어질 확률"을 판단해 거르는 **메타라벨링(meta-labeling)** 구조의 자동매매 시스템입니다.

> **핵심 철학:** AI는 매매를 새로 만들지 않고 **거른다.**
> 거대한 예측 모델 한 방이 아니라, 검증된 룰 전략 + 작은 ML 필터 + 강한 리스크 관리의 조합으로 작은 우위를 꾸준히 수익화합니다.

---

## 비전

| 단계 | 목표 |
|---|---|
| **1단계 (현재)** | 업비트 암호화폐 자동매매 — 룰 전략 + AI 필터 |
| **2단계** | 전략 풀(pool) 운용 — AI가 상황별 최적 전략 선택 |
| **3단계** | 사용자가 자신만의 전략을 등록하는 멀티유저 전략 플랫폼 |
| **4단계** | 동일 파이프라인을 주식 차트로 확장 (코인에서 검증한 모델 복제) |

코인을 초기 모델로 두는 이유: 24시간장이라 학습 데이터가 빠르게 쌓여 ML 사이클을 빨리 돌릴 수 있습니다.

---

## 핵심 컨셉 — 메타라벨링

```
[1차 = 전략 풀]        매수 신호 생성 (방향 결정)
        ↓
[2차 = AI 메타모델]    "이 신호가 수익 날 확률" 판단 (거르기)
        ↓
   임계값 통과한 것만 → RiskManager → 주문
```

- **1차 모델 = 룰 전략들.** 각 전략이 독립적으로 매수 신호를 냅니다.
- **2차 모델 = LightGBM 메타모델.** `strategy_id`를 feature에 포함해 "어떤 전략이, 지금 이 레짐에서 낸 신호가 먹힐지"를 데이터가 자동으로 학습합니다.

---

## 시스템 아키텍처

두 개의 독립 프로세스가 HTTP로 통신합니다. AI 서버가 죽어도 거래 엔진은 룰 단독으로 계속 동작합니다.

```
┌────────────────────────────┐  HTTP /predict  ┌────────────────────────────┐
│   Java 거래 엔진 (:8080)   │ ──────────────> │   Python AI 서비스 (:8000) │
│  - 업비트 연동/WebSocket   │                 │  - FeatureSnapshot 수신     │
│  - 전략 신호 생성          │ <────────────── │  - LightGBM 추론            │
│  - 주문 / 리스크 / 청산    │  매수확률(0~1)  │  - 오프라인 재학습           │
│  - AiSignalGate (게이트)   │                 └────────────────────────────┘
└────────────┬───────────────┘
             │       공유 MySQL + Redis
             └──────────────────────────
```

Java는 `strategy_run_logs`에 판단 시점 feature를 기록하고, Python은 같은 DB를 읽어 학습합니다.
train-serve skew를 막기 위해 **Java가 계산한 지표값을 그대로** 학습에 사용합니다.

### AI 게이트 모드

설정 한 줄(`trading.ai.mode`)로 점진적으로 전환합니다.

| 모드 | 동작 |
|---|---|
| `RULE_ONLY` | ML 호출 없음. 기존 룰 엔진과 100% 동일 (기본값) |
| `SHADOW` | ML 예측을 로그에만 기록. 실거래 영향 없음 → 룰 vs ML 비교 |
| `ENSEMBLE` | 룰 BUY **AND** ML 확률 ≥ 임계값(0.6)일 때만 진입 |

ML 서버 장애·타임아웃(800ms) 시 ENSEMBLE도 룰 단독으로 자동 폴백합니다.

---

## 매매 파이프라인 (15분마다 실행)

```
1. MarketScannerService     KRW 전체 마켓 스캔 → 후보 코인 최대 20개 선별
2. UpbitMarketService       최신 캔들/티커 fetch → DB 저장 (Redis 캐시 TTL 10분)
3. 지표 계산                EMA(12/26), SMA(50), MACD(12-26-9), RSI(14), ATR(14), 거래량비
4. RegimeClassifier         ADX(14) 기반 시장 레짐 분류
5. HybridSignalAnalyzer     5개 지표 복합 채점 → 매수/매도 신호 생성
6. AiSignalGate             FeatureSnapshot → Python /predict → RULE_ONLY/SHADOW/ENSEMBLE 처리
7. 레짐 필터                RANGING이면 BUY 차단
8. 멀티타임프레임 필터       1H EMA 하락추세이면 차단 → 4H EMA 하락추세이면 추가 차단
9. EMA 이격 필터            현재가 > EMA26 × 1.03이면 고점 추격 차단
10. RegimeRouter            레짐에 맞는 진입 전략 선택 (DonchianBreakout / MeanReversion)
11. RiskManager             ATR 기반 손절가·목표가·포지션 크기 결정
12. UpbitApiClient          시장가/지정가 주문 제출
13. PositionMonitor 등록    WebSocket 실시간 모니터링 시작
```

---

## 전략 상세

### 시장 스캐너 (MarketScannerService)

**하드 필터 (1차 탈락)**

| 조건 | 기준값 |
|---|---|
| 24h 거래대금 | 50억 KRW 이상 |
| 24h 변동률 | +0.5% ~ +7% |
| 거래 중단 / 투자경고(CAUTION) | 제외 |

**점수 산출 (100점 만점) — 상위 20개 선택**

| 항목 | 배점 | 기준 |
|---|---|---|
| 거래대금 점수 | 40점 | 후보군 내 정규화 (유동성 우대) |
| 트렌드 위치 점수 | 35점 | 현재가가 24h 범위의 55~85% 구간 |
| 모멘텀 점수 | 25점 | 변동률 +1%~+5% 구간 (≈+3%이 만점) |

---

### 시장 레짐 분류 (RegimeClassifier)

```
ADX < 20        → RANGING       횡보장 — BUY/STRONG_BUY 차단
ADX 20 ~ 25     → NEUTRAL       불분명 — 초기 추세 포착 허용
ADX > 25, +DI > -DI  → TRENDING_UP    상승 추세 — 매수 허용
ADX > 25, +DI < -DI  → TRENDING_DOWN  하락 추세 — 대기
```

---

### 복합 신호 분석 (HybridSignalAnalyzer)

#### 점수 체계

| 지표 | 신호 조건 | 점수 |
|---|---|---|
| **추세 (EMA)** | EMA12 > EMA26 > SMA50 | +2 |
| | EMA12 > EMA26 | +1 |
| | EMA12 < EMA26 < SMA50 | -2 |
| | EMA12 < EMA26 | -1 |
| **모멘텀 (MACD)** | MACD > Signal > 0 + 히스토그램 골든크로스 | +2 |
| | MACD > Signal > 0 + 히스토그램 증가 | +1 |
| | MACD < Signal < 0 + 데드크로스 | -2 |
| | MACD < Signal < 0 | -1 |
| **RSI (14)** | 50~65 (모멘텀 빌드업) | +1 |
| | > 70 (과매수) | -1 |
| | < 35 (과매도, 하락 위험) | 0 |
| **캔들 패턴** | Hammer / Strong Bullish / 연속 2봉 양봉 | +1 |
| | Shooting Star / Strong Bearish | -1 |

히스토그램 감소 시 NEUTRAL 처리 — 늦은 진입 차단.
거래량 비율 < 0.8× MA20이면 즉시 차단.

#### 최종 신호 결정

| 신호 | 조건 |
|---|---|
| `STRONG_BUY` | 총점 ≥ 5 + 거래량 VERY_HIGH (≥1.5×) |
| `BUY` | 총점 ≥ 4 + 거래량 HIGH 이상 (≥1.3×) |
| `STRONG_SELL` | 매도점수 ≥ 4 + 거래량 VERY_HIGH |
| `SELL` | 매도점수 ≥ 3 + 거래량 HIGH 이상 |

---

### 레짐 기반 전략 라우팅 (RegimeRouter)

전략은 신호(`StrategySignal`)만 반환하고, 포지션 사이징은 `RegimeRouter`가 `RiskManager`에 위임합니다.

```java
interface Strategy {
    String id();                                      // 로그/ML feature에 기록되는 전략 식별자
    StrategyType type();                              // TREND_FOLLOWING / MEAN_REVERSION / BREAKOUT / SCALPING
    Optional<StrategySignal> evaluate(List<Candle>); // 신호 없으면 empty
}
// @Component만 달면 StrategyRegistry에 자동 등록
```

#### DonchianBreakoutStrategy (type: BREAKOUT) — 추세장

터틀 트레이딩 기반 Donchian 채널 돌파 전략.

| 조건 | 내용 |
|---|---|
| Donchian 돌파 | 현재 종가 > 직전 20봉 최고가 |
| Supertrend 상향 | Supertrend(10, 3.0) 방향 상승 |
| 거래량 확인 | 현재 거래량 ≥ 20MA × 1.3 |

#### MeanReversionStrategy (type: MEAN_REVERSION) — 횡보장

볼린저 밴드 + RSI 평균회귀 전략.

| 조건 | 내용 |
|---|---|
| BB 하단 터치 | 최저가 ≤ BB(20, 2σ) 하단 |
| RSI 과매도 | RSI(14) < 30 |
| 반전 캔들 | 종가 > 시가 (양봉) |

---

### 포지션 크기 및 손절/목표가 (RiskManager)

```
손절가      = 진입가 - 2.5 × ATR(14)    (안전 한도 2%~10%)
목표가      = 진입가 + 5.0 × ATR(14)    (손익비 2:1)
포지션 크기 = (자산 × risk-per-trade) / 손절 폭
```

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| risk-per-trade | 10% | 1회 기본 투자 비율 |
| max-order-percent | 15% | STRONG_BUY 시 최대 투자 비율 |
| min-order-amount | 20,000 KRW | 최소 주문 금액 |
| max-open-positions | 3 | 최대 동시 보유 코인 수 |
| max-daily-loss | 10% | 일일 손실 한도 (킬 스위치) |

---

### 실시간 포지션 모니터링 (PositionMonitor)

업비트 WebSocket으로 실시간 틱(~100ms)을 수신합니다.

```
매 틱
  ↓
highestPriceSinceEntry 업데이트
  ↓
RiskManager.updateTrailing() 호출
  │
  ├─ 현재가 ≤ 손절선       → 시장가 전량 매도 (즉시)
  ├─ +2R 도달              → 50% 부분청산 + 손절선을 진입가(본전)로 이동
  └─ 최고가 갱신           → Chandelier Exit 계산 → 손절선 상향 (단조 증가)
                              트레일링 손절 = 최고가 - 3 × ATR
```

서버 재시작 시 DB에서 OPEN 포지션을 로드해 모니터링을 자동 재개합니다.

### 매도 정책 요약

| 상황 | 동작 |
|---|---|
| 손절선 터치 | WebSocket 틱 감지 → 즉시 시장가 전량 매도 |
| +2R 도달 | 50% 부분청산 + 손절선 → 진입가(본전) |
| Chandelier 트레일링 손절 터치 | 즉시 시장가 전량 매도 |
| STRONG_SELL (매도점수 ≥ 4 + VERY_HIGH 거래량) | 즉시 매도 |
| SELL 신호 | 무시 — 15분봉 노이즈, 트레일링 스탑에 위임 |

---

## 안전 불변식

- AI는 매수를 **막을 뿐, 새로 만들지 않는다.** 룰이 BUY 아니면 AI와 무관하게 진입 없음.
- 손절·익절·사이징·청산은 **RiskManager / PositionMonitor 전담.** AI 관여 금지.
- ML 서버 장애 시 → 봇은 룰 단독으로 멈춤 없이 계속.
- 모든 평가는 진입 여부와 무관하게 `strategy_run_logs`에 기록 (학습 데이터 보존).

---

## AI 메타모델 — 학습 방식

- **라벨링**: Triple-Barrier — 익절선(+2×ATR) / 손절선(-2.5×ATR) / 시간(N봉) 중 먼저 닿은 것으로 성공(1)/실패(0) 부여.
- **Feature**: EMA/MACD/RSI/ADX/ATR/거래량비/볼린저 + `strategy_id` / `strategy_type` + 레짐.
- **모델**: LightGBM 분류기 → 출력 = 매수 확률(0~1).
- **검증**: walk-forward / purged cross-validation (시간 순서 유지, 정보 누수 차단).
- **평가지표**: 수수료·슬리피지 차감 후 손익 / 샤프 / MDD / 정밀도. 정확도는 보조 지표.
- **데이터 소스**: ① BacktestEngine으로 과거 신호 대량 생성, ② SHADOW 모드 실시간 누적.

> 현실 목표: 방향 정확도 ~52–55%. 손익비 2:1과 결합해 수수료 차감 후 우위가 남는 것이 목표.

---

## 포트폴리오 상관관계 가드 (CorrelationGuard)

```
후보 코인의 최근 50봉 log-return ↔ 보유 코인들의 log-return
피어슨 상관계수 |r| > 0.7 → 신규 진입 차단
```

BTC / ETH / SOL처럼 동조화된 코인 동시 보유 = 1개 포지션 3배 크기 → 방지.

---

## 백테스트 엔진 (BacktestEngine)

- **진입**: 다음 봉 시가에 체결 (룩어헤드 없음)
- **손절/목표 체크**: 봉 내부 고/저가 기준, 같은 봉에서 동시 터치 시 손절 우선 (보수적 평가)
- **트레일링**: 실제 운영과 동일한 `RiskManager.updateTrailing()` 사용
- **순수 함수**: 상태 없음, I/O 없음 → 파라미터 스윕 가능

페이퍼 트레이딩은 `PaperBroker`로 실행합니다 (실제 주문 없이 가상 체결).

---

## 기술 스택

| 영역 | 기술 |
|---|---|
| 거래 엔진 | Java 17, Spring Boot 3.2, JPA/Hibernate, WebFlux, OkHttp3 |
| AI 서비스 | Python 3.11, LightGBM, pandas, scikit-learn, FastAPI |
| 캐시 | Redis (캔들·티커·신호·거래로그 TTL 10분) |
| DB | MySQL 8 |
| 보안 | Spring Security, JJWT 0.12.3 |
| API 문서 | SpringDoc OpenAPI (Swagger UI) |
| 배포 | Docker, Docker Compose, Oracle Cloud Always Free |

---

## 데이터베이스 주요 테이블

| 테이블 | 설명 |
|---|---|
| `users` | 사용자 계정 |
| `accounts` | 거래소 API 키 연동 정보 |
| `positions` | 보유 포지션 (손절가, 목표가, ATR, `signal_id`) |
| `orders` | 주문 이력 |
| `candles` | 캔들 데이터 (15분 / 1시간 / 4시간 다중 주기) |
| `tickers` | 최신 시세 스냅샷 |
| `risk_records` | 일일 손익 / 킬 스위치 상태 |
| `strategy_run_logs` | 매 실행 결과 로그 — feature JSON, ML 확률, `signal_id` 포함 |
| `trade_history` | 체결 이력 (진입/청산가, PnL, ATR, `signal_id`로 신호 연결) |
| `strategies` | 전략 설정 엔티티 (StrategyConfig) |

**signal_id 흐름:** BUY 신호 생성 시 UUID 발급 → `positions.signal_id` 저장 → 청산 시 `trade_history.signal_id`로 전달.
이 ID로 신호 → 포지션 → 손익을 연결해 ML 라벨링 데이터를 구성합니다.

---

## 핵심 클래스 맵

| 클래스 | 역할 |
|---|---|
| `HybridStrategyExecutor` | 전략 진입점, 15분 스케줄러 |
| `MarketScannerService` | 매매 후보 코인 선별 |
| `RegimeClassifier` | ADX 기반 시장 레짐 분류 |
| `HybridSignalAnalyzer` | 5개 지표 복합 신호 채점 |
| `AiSignalGate` | ML 게이트 (RULE_ONLY / SHADOW / ENSEMBLE) |
| `AiSignalClient` | Python AI 서버 HTTP 클라이언트 (800ms 타임아웃) |
| `FeatureSnapshot` | 지표 스냅샷 — Python으로 전송 + DB 저장 |
| `Strategy` | 전략 공통 인터페이스 (`id` / `type` / `evaluate`) |
| `StrategyRegistry` | `@Component` Strategy 빈 자동 수집 |
| `StrategyRunLogService` | feature 직렬화 + strategy_run_logs 저장 |
| `DonchianBreakoutStrategy` | 추세장 진입 전략 (BREAKOUT) |
| `MeanReversionStrategy` | 횡보장 진입 전략 (MEAN_REVERSION) |
| `RegimeRouter` | 레짐 → 전략 선택 → RiskManager 위임 |
| `RiskManager` | ATR 기반 손절/목표가/포지션 크기 |
| `PositionMonitor` | WebSocket 실시간 포지션 관리 |
| `CorrelationGuard` | 포트폴리오 상관관계 필터 |
| `BacktestEngine` | 전략 백테스트 (순수 함수) |
| `PaperBroker` | 페이퍼 트레이딩 가상 체결 |
| `Indicators` | ADX, Supertrend, Donchian, Bollinger, RSI 등 지표 라이브러리 |

---

## 로드맵

- [x] **Phase 0 — 데이터 계측**: `signal_id` 추적, FeatureSnapshot 로깅, AiSignalGate 인프라 (`RULE_ONLY`).
- [x] **Phase 1 — 전략 모듈화**: `Strategy` 인터페이스 + `StrategyRegistry` + `StrategyType` 지문 태깅.
- [ ] **Phase 2 — AI 활성화**: Python 메타모델 학습/서빙 → `SHADOW` 검증 → `ENSEMBLE` 승급.
- [ ] **Phase 3 — 플랫폼화**: 사용자 커스텀 전략, 콜드스타트 자동 백테스트, 전략 마켓플레이스.
- [ ] **Phase 4 — 주식 확장**: KIS/Alpaca 어댑터, 파이프라인 재사용, 자산군별 모델 학습.

---

## 빠른 시작

### 환경 변수

```bash
export LOCAL_DB_URL=jdbc:mysql://localhost:3306/trading_bot
export LOCAL_DB_USERNAME=your_db_user
export LOCAL_DB_PASSWORD=your_db_password
export UPBIT_ACCESS_KEY=your_upbit_access_key
export UPBIT_SECRET_KEY=your_upbit_secret_key
```

### 실행

```bash
./gradlew build
./gradlew bootRun       # 로컬 실행
docker-compose up       # Java + MySQL + Redis 통합 실행
```

Swagger UI: `http://localhost:8080/api/swagger-ui.html`

---

## 주요 설정 (application.yml)

```yaml
trading:
  scheduler:
    enabled: true

  ai:
    base-url: http://localhost:8000  # Python ML 서버
    timeout-ms: 800
    mode: RULE_ONLY                  # RULE_ONLY | SHADOW | ENSEMBLE
    buy-threshold: 0.6               # ENSEMBLE 모드 매수 최소 확률

  scanner:
    min-trade-price-24h: 5000000000  # 최소 24h 거래대금 (50억 KRW)
    max-coins: 20
    min-change-rate: 0.005
    max-change-rate: 0.07

  risk:
    risk-per-trade: 0.10             # 1회 기본 투자 비율
    max-order-percent: 0.15          # STRONG_BUY 최대 투자 비율
    min-order-amount: 20000          # 최소 주문 금액 (KRW)
    max-open-positions: 3
    max-daily-loss: 10.0             # 일일 손실 한도 (킬 스위치)
    stop-loss-percent: 5.0           # ATR 미사용 시 폴백 손절 비율
    take-profit-percent: 10.0        # ATR 미사용 시 폴백 목표 비율
```

---

## 배포

1. **Oracle Cloud Always Free 인스턴스 생성** (ARM, 고정 공인 IP 1개 무료).
2. **업비트 API 키에 고정 IP 등록** (주문 권한 키는 공인 IP 필수).
3. **Java 엔진 배포** (`mode=RULE_ONLY`로 기존 동작 유지).
4. **Python AI 서비스 배포** → `SHADOW`로 데이터 검증 후 `ENSEMBLE` 승급.

---

## ⚠️ 면책

본 프로젝트는 개인 연구/학습 목적입니다. 자동매매는 원금 손실 위험이 있으며, 백테스트 성과가 실거래 수익을 보장하지 않습니다.
