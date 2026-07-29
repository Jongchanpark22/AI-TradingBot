FROM python:3.11-slim

WORKDIR /app

# 의존성 먼저 설치 (레이어 캐싱 — 소스 변경 시 재설치 방지)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 소스 복사
COPY . .

# 모델 디렉토리 생성 (볼륨 마운트 없을 때 빈 폴더 보장)
RUN mkdir -p /app/model

EXPOSE 8000

CMD ["uvicorn", "serve:app", "--host", "0.0.0.0", "--port", "8000"]
