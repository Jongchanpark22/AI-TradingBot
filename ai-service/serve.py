"""
FastAPI ML 예측 서버.

Spring Boot AiSignalClient → POST /predict → XGBoost 매수 확률 반환.
모델 파일이 없으면 503 반환 (train.py 먼저 실행 필요).

실행:
    uvicorn serve:app --host 0.0.0.0 --port 8000
"""

import json
import os
from contextlib import asynccontextmanager

import xgboost as xgb
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from features import build_features

MODEL_DIR  = os.path.join(os.path.dirname(__file__), 'model')
MODEL_PATH = os.path.join(MODEL_DIR, 'xgb_model.json')
META_PATH  = os.path.join(MODEL_DIR, 'meta.json')

# 서버 시작 시 한 번만 로드하는 전역 모델
_model: xgb.XGBClassifier | None = None
_model_version: str = "unknown"


@asynccontextmanager
async def lifespan(app: FastAPI):
    """서버 시작 시 모델 로드 — 없으면 경고만 출력하고 계속 실행."""
    global _model, _model_version

    if os.path.exists(MODEL_PATH):
        _model = xgb.XGBClassifier()
        _model.load_model(MODEL_PATH)

        if os.path.exists(META_PATH):
            with open(META_PATH, 'r', encoding='utf-8') as f:
                meta = json.load(f)
            _model_version = meta.get('version', 'unknown')
            print(f"[Serve] 모델 로드 완료 — 버전: {_model_version}, "
                  f"CV AUC: {meta.get('cv_auc_mean', 'N/A')}")
    else:
        print(f"[Serve] 경고: 모델 없음 ({MODEL_PATH}) — train.py 실행 후 재시작하세요.")

    yield  # 서버 실행


app = FastAPI(title="AI Trading ML Server", version="1.0.0", lifespan=lifespan)


# Spring Boot FeatureSnapshot과 1:1 대응 (camelCase)
class FeatureSnapshot(BaseModel):
    symbol: str
    timeframe: str
    timestampMs: int
    close: float
    ema12: float
    ema26: float
    sma50: float
    closeVsEma26Pct: float
    macd: float
    macdSignal: float
    macdHist: float
    rsi14: float
    adx14: float
    plusDi: float
    minusDi: float
    atr14: float
    bbUpper: float
    bbLower: float
    volumeRatio: float
    donchianHigh20: float
    supertrendDir: int
    regime: str
    trendScore: int
    momentumScore: int
    rsiScore: int
    candleScore: int
    volumeConfidence: int
    totalScore: int
    ruleSignal: str


class PredictionResponse(BaseModel):
    buy_probability: float
    model_version: str


@app.post("/predict", response_model=PredictionResponse)
def predict(snap: FeatureSnapshot):
    """
    매수 성공 확률 예측.

    Spring Boot AiSignalClient.predict()에서 호출된다.
    모델 미로드 시 503 반환 → Spring은 null로 처리하고 룰 단독으로 동작.
    """
    if _model is None:
        raise HTTPException(status_code=503, detail="모델 미로드 — train.py 실행 필요")

    try:
        snap_dict = snap.model_dump()
        X = build_features(snap_dict)
        prob = float(_model.predict_proba(X)[0][1])
        return PredictionResponse(buy_probability=prob, model_version=_model_version)

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"예측 실패: {e}")


@app.get("/health")
def health():
    """Docker healthcheck 및 Spring Boot actuator 연동용."""
    return {
        "status": "ok",
        "model_loaded": _model is not None,
        "model_version": _model_version,
    }
