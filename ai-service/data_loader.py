"""
MySQL에서 백테스트 ML 학습 데이터를 로드하는 모듈.

strategy_run_logs.feature_json (FeatureSnapshot)과
trade_history (entry_price, highest_price, atr_at_entry, profit_rate)를
signal_id로 조인하여 학습용 X, y를 반환한다.

라벨 정의:
    label = 1 if profit_rate > 0 else 0  (실제 수익 기준)

학습/평가 분리:
    모델 학습 타깃(y): profit_rate > 0 라벨
    임계값 표 승률/PF: 실제 profit_rate 기반 (동일)

BreakRetest 제외:
    전략별 성능에서 BreakRetest가 PF 0.57로 혼자 바닥 → 학습에서 제외.
"""

import json
import os

import pandas as pd
from sqlalchemy import create_engine, text

from features import build_features


def get_db_url() -> str:
    """환경변수 DB_URL 우선 — 없으면 로컬 개발 기본값."""
    return os.getenv(
        'DB_URL',
        'mysql+pymysql://root:01030418aa@localhost:3306/crypto_bot'
    )


def load_training_data(db_url: str = None, return_meta: bool = False) -> tuple:
    """
    백테스트 학습 데이터 로드.

    Args:
        db_url: SQLAlchemy DB URL. None이면 환경변수/기본값 사용.
        return_meta: True이면 (X, y, meta_df) 반환.
                     meta_df 컬럼: profit_rate, strategy_id, strategy_type.

    Returns:
        return_meta=False: (X, y)
        return_meta=True:  (X, y, meta_df)
    """
    if db_url is None:
        db_url = get_db_url()

    engine = create_engine(db_url, echo=False)

    # signal_id 기준 조인 — 거래로 이어진 신호만 라벨 계산 가능
    # BreakRetest 제외: PF 0.57로 성과 바닥 — 학습 데이터 오염 방지
    # atr_at_entry, highest_price, entry_price NULL/0 행 제외 — 오라벨 방지
    query = text("""
        SELECT
            s.feature_json,
            t.profit_rate,
            t.entry_price,
            t.highest_price,
            t.atr_at_entry
        FROM strategy_run_logs s
        INNER JOIN trade_history t ON t.signal_id = s.signal_id
        WHERE s.source = 'BACKTEST'
          AND s.feature_json IS NOT NULL
          AND t.atr_at_entry IS NOT NULL AND t.atr_at_entry > 0
          AND t.highest_price IS NOT NULL AND t.highest_price > 0
          AND t.entry_price  IS NOT NULL AND t.entry_price  > 0
          AND s.strategy_name NOT LIKE 'BreakRetest%'
        ORDER BY s.timestamp_ms
    """)

    with engine.connect() as conn:
        df = pd.read_sql(query, conn)

    # 라벨: 실제 수익 기준 (profit_rate > 0)
    df['label'] = (df['profit_rate'] > 0).astype(int)

    label_1 = int(df['label'].sum())
    label_0 = len(df) - label_1
    print(f"[DataLoader] 쿼리 결과: {len(df)}건 "
          f"(수익={label_1}, 손실={label_0}, 승률={label_1/len(df):.1%})")

    # feature_json 파싱 → 피처 엔지니어링
    rows = []
    labels = []
    metas = []
    skipped = 0

    for _, row in df.iterrows():
        try:
            snap = json.loads(row['feature_json'])
            feat = build_features(snap)
            rows.append(feat)
            labels.append(int(row['label']))
            metas.append({
                'profit_rate':   float(row['profit_rate']),
                'strategy_id':   snap.get('strategyId', 'UNKNOWN'),
                'strategy_type': snap.get('strategyType', 'UNKNOWN'),
            })
        except Exception as e:
            skipped += 1

    if skipped > 0:
        print(f"[DataLoader] 파싱 실패 {skipped}건 제외")

    X = pd.concat(rows, ignore_index=True)
    y = pd.Series(labels, name='label', dtype=int)

    print(f"[DataLoader] 피처 행렬: {X.shape}, 승률: {y.mean():.1%}")

    if return_meta:
        meta_df = pd.DataFrame(metas).reset_index(drop=True)
        return X, y, meta_df
    return X, y
