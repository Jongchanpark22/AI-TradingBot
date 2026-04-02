# 🚀 로컬 환경에서 실행하기

## 사전 요구사항

- Java 17 이상
- MySQL 8.0 (로컬 설치)
- Redis (로컬 설치)
- Gradle (자동으로 처리됨)

## MySQL 설정

### 1. MySQL 데이터베이스 생성

```bash
mysql -u root -p

# MySQL 프롬프트에서:
CREATE DATABASE crypto_bot;
CREATE USER 'bot_user'@'localhost' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON crypto_bot.* TO 'bot_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

### 2. MySQL 실행 확인

```bash
mysql -h localhost -u bot_user -p crypto_bot
# 비밀번호: password
```

## Redis 설정

### 1. Redis 시작 (Mac with Homebrew)

```bash
brew services start redis
```

### 2. Redis 연결 확인

```bash
redis-cli
127.0.0.1:6379> ping
# PONG 응답이 나오면 정상
> exit
```

## 애플리케이션 실행

### 1단계: 빌드

```bash
cd /Users/parkjongchan/IdeaProjects/AI-TradingBot
./gradlew clean build -x test
```

### 2단계: 애플리케이션 시작

```bash
./gradlew bootRun
```

또는

```bash
java -jar build/libs/crypto-trading-bot-0.0.1-SNAPSHOT.jar
```

### 3단계: 애플리케이션 확인

```bash
curl http://localhost:8080/api/health
```

응답 예시:
```json
{
  "status": "UP",
  "message": "Crypto Trading Bot is running"
}
```

## 데이터베이스 테이블 자동 생성

Spring Boot가 시작될 때 `hibernate.ddl-auto: create-drop` 설정으로 자동으로 테이블을 생성합니다.

생성되는 테이블:
- users
- accounts
- orders
- positions
- candles
- tickers
- strategies
- risk_records

## API 엔드포인트

### 헬스 체크
```bash
GET http://localhost:8080/api/health
```

### 계정 조회
```bash
GET http://localhost:8080/api/accounts/{accountId}
GET http://localhost:8080/api/accounts/user/{userId}
```

### 주문 조회
```bash
GET http://localhost:8080/api/orders/{orderId}
GET http://localhost:8080/api/orders/account/{accountId}
```

### 포지션 조회
```bash
GET http://localhost:8080/api/positions/account/{accountId}
```

## 트러블슈팅

### MySQL 연결 오류
```
com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure
```

**해결책:**
```bash
# MySQL 상태 확인
mysql -u root -p

# 또는 Mac에서
brew services restart mysql-server
```

### Redis 연결 오류
```
org.springframework.data.redis.connection.jedis.JedisConnectionFactory
```

**해결책:**
```bash
# Redis 상태 확인
redis-cli ping

# 또는 Mac에서
brew services restart redis
```

### 포트 충돌 (8080)
```
Address already in use
```

**해결책:**
application.yml에서 포트 변경:
```yaml
server:
  port: 8081
```

## 로그 레벨 조정

application.yml에서 로그 레벨을 조정할 수 있습니다:

```yaml
logging:
  level:
    root: INFO
    com.example.cryptobot: DEBUG              # 프로젝트 로그
    org.springframework.web: DEBUG             # Spring Web 로그
    org.hibernate.SQL: DEBUG                   # SQL 로그
    org.hibernate.type.descriptor.sql: TRACE   # SQL 파라미터 로그
```

## 애플리케이션 중지

터미널에서 `Ctrl + C`를 눌러서 중지합니다.

---

**이제 로컬에서만 실행되고 Docker 의존성이 없습니다!** 🎉

