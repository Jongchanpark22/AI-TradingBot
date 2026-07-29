"""
FeatureSnapshot → 학습/추론 공용 피처 벡터 변환 모듈.

train.py와 serve.py 양쪽에서 동일한 변환을 보장한다.
피처 순서(FEATURE_COLS)가 바뀌면 모델을 반드시 재학습해야 한다.
"""

import pandas as pd

# 학습/추론에 사용할 피처 컬럼 목록 (순서 고정 — 변경 금지)
FEATURE_COLS = [
    'closeVsEma26Pct',   # (close - ema26) / ema26 × 100
    'ema12_ratio',       # ema12 / close
    'ema26_ratio',       # ema26 / close
    'sma50_ratio',       # sma50 / close
    'macd_ratio',        # macd / close
    'macdHist_ratio',    # macdHist / close
    'rsi14',
    'adx14',
    'plusDi',
    'minusDi',
    'atr_ratio',         # atr14 / close (변동성 비율)
    'bb_upper_dist',     # (bbUpper - close) / close
    'bb_lower_dist',     # (close - bbLower) / close
    'bb_width',          # (bbUpper - bbLower) / close
    'volumeRatio',
    'donchian_dist',     # (donchianHigh20 - close) / close
    'supertrendDir',
    'trendScore',
    'momentumScore',
    'rsiScore',
    'candleScore',
    'volumeConfidence',
    'totalScore',
    'regime_enc',
    'ruleSignal_enc',
    # BTC 시장 맥락 피처 (Task 2)
    'marketRegime_enc',  # BTC 레짐 인코딩
    'marketTrend',       # BTC EMA12 > EMA26 (1/0)
    'marketReturn',      # BTC 최근 20봉 수익률 (%)
    'marketAboveMA',     # BTC > SMA50 (1/0)
]

# regime 인코딩: 추세 강도 순
REGIME_MAP = {
    'TRENDING_UP': 3,
    'NEUTRAL': 2,
    'RANGING': 1,
    'TRENDING_DOWN': 0,
}

# BTC 시장 레짐 인코딩 (알트 레짐과 동일 스케일)
MARKET_REGIME_MAP = {
    'TRENDING_UP': 3,
    'NEUTRAL': 2,
    'RANGING': 1,
    'TRENDING_DOWN': 0,
    'UNKNOWN': 1,  # 데이터 없음 → 중립으로 처리
}

# ruleSignal 인코딩
RULE_SIGNAL_MAP = {
    'STRONG_BUY': 2,
    'BUY': 1,
}


def build_features(snap: dict) -> pd.DataFrame:
    """
    FeatureSnapshot dict → 1행 DataFrame 변환.

    Args:
        snap: camelCase JSON dict (Spring FeatureSnapshot 직렬화 형태)

    Returns:
        FEATURE_COLS 컬럼 순서 정렬된 1행 DataFrame
    """
    close = snap['close']
    if close <= 0:
        raise ValueError(f"close 가격 이상: {close}")

    row = {
        # 이동평균 이격 (close 대비 비율 — 코인 간 가격 차이 제거)
        'closeVsEma26Pct': snap['closeVsEma26Pct'],
        'ema12_ratio':     snap['ema12'] / close,
        'ema26_ratio':     snap['ema26'] / close,
        'sma50_ratio':     snap['sma50'] / close,

        # MACD (close 대비 정규화)
        'macd_ratio':     snap['macd'] / close,
        'macdHist_ratio': snap['macdHist'] / close,

        # 모멘텀/추세 지표 (이미 정규화된 값)
        'rsi14':   snap['rsi14'],
        'adx14':   snap['adx14'],
        'plusDi':  snap['plusDi'],
        'minusDi': snap['minusDi'],

        # 변동성 지표 (close 대비 비율)
        'atr_ratio':      snap['atr14'] / close,
        'bb_upper_dist':  (snap['bbUpper'] - close) / close,
        'bb_lower_dist':  (close - snap['bbLower']) / close,
        'bb_width':       (snap['bbUpper'] - snap['bbLower']) / close,

        # 거래량
        'volumeRatio': snap['volumeRatio'],

        # 구조 지표
        'donchian_dist': (snap['donchianHigh20'] - close) / close,
        'supertrendDir': snap['supertrendDir'],

        # HybridSignalAnalyzer 점수 (-2~2 또는 0~4)
        'trendScore':      snap['trendScore'],
        'momentumScore':   snap['momentumScore'],
        'rsiScore':        snap['rsiScore'],
        'candleScore':     snap['candleScore'],
        'volumeConfidence': snap['volumeConfidence'],
        'totalScore':      snap['totalScore'],

        # 카테고리 인코딩
        'regime_enc':     REGIME_MAP.get(snap.get('regime', ''), 1),
        'ruleSignal_enc': RULE_SIGNAL_MAP.get(snap.get('ruleSignal', ''), 0),

        # BTC 시장 맥락 (없으면 중립/0으로 처리)
        'marketRegime_enc': MARKET_REGIME_MAP.get(snap.get('marketRegime', 'UNKNOWN'), 1),
        'marketTrend':      int(snap.get('marketTrend', 0)),
        'marketReturn':     float(snap.get('marketReturn', 0.0)),
        'marketAboveMA':    int(snap.get('marketAboveMA', 0)),
    }

    return pd.DataFrame([row])[FEATURE_COLS]
