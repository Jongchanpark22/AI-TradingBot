# Crypto Trading Bot

스프링 부트 기반의 암호화폐 자동매매 봇 프로젝트입니다.  
업비트/빗썸 같은 국내 거래소 연동을 목표로 하며, 로컬 환경에서 개발/실행하고 이후 백테스트, 모의투자, 실거래까지 확장하는 것을 목표로 합니다.

## 프로젝트 목표

이 프로젝트의 핵심 목표는 다음과 같습니다.

- 거래소 API 연동
- 실시간 시세 수집
- 전략 기반 매매 신호 생성
- 리스크 관리
- 주문 실행 및 체결 추적
- 백테스트 / 모의투자 지원
- 향후 AI 기반 판단 보조 기능 확장

처음부터 AI가 직접 주문을 내리는 구조보다는,  
**안정적인 거래 엔진과 리스크 관리 구조를 먼저 만들고** 이후 AI를 보조 모듈로 붙이는 방향으로 개발합니다.

---

## 기술 스택

- Java 17
- Spring Boot
- Spring Web / WebClient
- Spring Data JPA
- MySQL 또는 PostgreSQL
- Redis
- Docker / Docker Compose
- Gradle

---

## 주요 기능 예정

### 1. 거래소 연동
- 업비트 API 연동
- 빗썸 API 연동
- 시세 조회
- 잔고 조회
- 주문 / 취소 / 체결 조회

### 2. 마켓 데이터 처리
- 캔들 데이터 수집
- 호가 / 현재가 조회
- 실시간 데이터 처리(WebSocket 확장 예정)

### 3. 전략 엔진
- 이동평균 기반 전략
- RSI 기반 전략
- 거래량 기반 전략
- 전략별 신호 생성

### 4. 리스크 관리
- 1회 최대 주문 금액 제한
- 1일 최대 손실 제한
- 손절 / 익절
- 중복 주문 방지
- 변동성 과열 구간 진입 제한

### 5. 주문 실행
- 주문 요청 생성
- 거래소 전송
- 주문 상태 추적
- 체결 반영
- 포지션 및 손익 갱신

### 6. 확장 예정
- 백테스트
- 모의투자(Paper Trading)
- Discord / Telegram 알림
- AI 기반 시장 요약 및 보조 판단

---

## 프로젝트 구조

예상 패키지 구조는 아래와 같습니다.

```text
src/main/java/com/example/cryptobot
├── common
│   ├── config
│   ├── exception
│   ├── util
│   └── logging
├── exchange
│   ├── core
│   ├── upbit
│   └── bithumb
├── market
│   ├── candle
│   ├── ticker
│   └── websocket
├── strategy
│   ├── core
│   ├── momentum
│   ├── meanreversion
│   └── ai
├── risk
├── execution
├── order
├── account
├── portfolio
├── backtest
├── papertrade
├── alert
└── admin
