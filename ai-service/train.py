"""
XGBoost 메타-레이블링 모델 학습 스크립트.

사용법:
    cd ai-service
    python train.py

결과물:
    model/xgb_model.json  — XGBoost 모델
    model/meta.json       — 버전/성능 메타 정보

주의:
    교차검증은 TimeSeriesSplit(walk-forward) 사용. 랜덤 셔플 금지.
    최종 학습은 전체 데이터로 수행.
"""

import json
import os
from datetime import datetime

import numpy as np
import pandas as pd
import xgboost as xgb
from sklearn.metrics import roc_auc_score, classification_report
from sklearn.model_selection import TimeSeriesSplit

from data_loader import load_training_data

MODEL_DIR  = os.path.join(os.path.dirname(__file__), 'model')
MODEL_PATH = os.path.join(MODEL_DIR, 'xgb_model.json')
META_PATH  = os.path.join(MODEL_DIR, 'meta.json')

# 손익분기 승률 기준 (R:R ~1.6:1 기준)
BREAKEVEN_WIN_RATE = 0.39
# 임계값 필터 후 거래가 "충분히 남는다"는 기준 (OOS 풀 대비 비율)
MIN_TRADE_RATIO = 0.20


def _make_model(scale_pos_weight: float) -> xgb.XGBClassifier:
    """XGBClassifier 공용 파라미터 — 폴드마다 동일 설정."""
    return xgb.XGBClassifier(
        n_estimators=300,
        max_depth=4,
        learning_rate=0.05,
        subsample=0.8,
        colsample_bytree=0.8,
        min_child_weight=5,
        scale_pos_weight=scale_pos_weight,
        eval_metric='auc',
        random_state=42,
        n_jobs=-1,
    )


def walk_forward_oos(X: pd.DataFrame, y: pd.Series, meta_df: pd.DataFrame,
                     scale_pos_weight: float, n_splits: int = 5) -> tuple:
    """
    TimeSeriesSplit walk-forward CV — OOS 예측 풀 수집.

    Args:
        X:                 피처 행렬 (시간순 정렬)
        y:                 레이블 Series
        meta_df:           profit_rate, strategy_id, strategy_type 메타 DataFrame
        scale_pos_weight:  클래스 불균형 보정 비율
        n_splits:          폴드 수 (기본 5)

    Returns:
        (oos_df, auc_scores):
            oos_df     — 검증 폴드 OOS 레코드 DataFrame
            auc_scores — 폴드별 AUC numpy 배열
    """
    tscv = TimeSeriesSplit(n_splits=n_splits)
    records = []
    auc_scores = []

    for fold, (train_idx, test_idx) in enumerate(tscv.split(X), start=1):
        X_tr, X_te = X.iloc[train_idx], X.iloc[test_idx]
        y_tr, y_te = y.iloc[train_idx], y.iloc[test_idx]

        # 이 폴드 훈련 데이터로만 학습 (look-ahead 금지)
        fold_model = _make_model(scale_pos_weight)
        fold_model.fit(X_tr, y_tr)

        probs = fold_model.predict_proba(X_te)[:, 1]

        if len(set(y_te)) > 1:
            fold_auc = roc_auc_score(y_te, probs)
            auc_scores.append(fold_auc)
            print(f"  Fold {fold}: AUC={fold_auc:.4f}, 테스트={len(test_idx)}건")
        else:
            print(f"  Fold {fold}: 단일 클래스 — AUC 미계산, 테스트={len(test_idx)}건")

        for i, idx in enumerate(test_idx):
            records.append({
                'y_true':        int(y.iloc[idx]),
                'y_prob':        float(probs[i]),
                'profit_rate':   float(meta_df['profit_rate'].iloc[idx]),
                'strategy_id':   str(meta_df['strategy_id'].iloc[idx]),
                'strategy_type': str(meta_df['strategy_type'].iloc[idx]),
            })

    oos_df = pd.DataFrame(records).reset_index(drop=True)
    return oos_df, np.array(auc_scores)


def print_threshold_table(oos_df: pd.DataFrame) -> None:
    """
    OOS 기준 임계값별 수익성 분석 출력.

    profit_rate는 퍼센트 단위 (5.0 = +5%).
    손익분기 승률 ≈ 39% (R:R ~1.6:1 기준).
    """
    total_oos = len(oos_df)
    line = "=" * 80
    print(f"\n{line}")
    print(f"[OOS 임계값 분석]  총 OOS 거래 {total_oos:,}건")
    print(f"  손익분기 승률 ≈ {BREAKEVEN_WIN_RATE:.0%}  |  "
          f"목표: 승률 {BREAKEVEN_WIN_RATE:.0%}+ AND OOS 대비 비율 {MIN_TRADE_RATIO:.0%}+ 동시 충족")
    print(line)
    print(f"{'threshold':>10} {'거래수':>8} {'OOS비율%':>9} {'승률':>7} "
          f"{'평균PnL(%)':>11} {'추정PF':>8}  판정")
    print("-" * 80)

    for thresh in [0.50, 0.55, 0.60, 0.65, 0.70]:
        fil = oos_df[oos_df['y_prob'] >= thresh]
        n = len(fil)

        if n == 0:
            print(f"{thresh:>10.2f} {'0':>8} {'0.0':>9} {'-':>7} {'-':>11} {'-':>8}")
            continue

        win_rate = (fil['profit_rate'] > 0).mean()
        avg_pnl  = fil['profit_rate'].mean()
        pct      = n / total_oos * 100

        wins   = fil[fil['profit_rate'] > 0]['profit_rate'].sum()
        losses = abs(fil[fil['profit_rate'] <= 0]['profit_rate'].sum())
        pf     = wins / losses if losses > 0 else float('inf')

        # 손익분기 초과 AND 거래 비율 충분 → 사용 가능 후보
        viable = win_rate >= BREAKEVEN_WIN_RATE and pct >= MIN_TRADE_RATIO * 100
        marker = " ◀ VIABLE" if viable else ""

        print(f"{thresh:>10.2f} {n:>8,d} {pct:>9.1f} {win_rate:>7.1%} "
              f"{avg_pnl:>11.2f} {pf:>8.2f}{marker}")

    print(line)
    print("  ◀ VIABLE: 승률 39%+ AND OOS 비율 20%+ 동시 충족 → SHADOW 진행 가치 있음")
    print(f"  없으면 → feature/라벨 개선 필요\n")


def print_strategy_table(oos_df: pd.DataFrame) -> None:
    """OOS 기준 전략별 성능 출력 (임계값 0.50 기준 전체 포함)."""
    if 'strategy_id' not in oos_df.columns:
        return

    strategies = oos_df['strategy_id'].unique()
    if len(strategies) <= 1 and strategies[0] == 'UNKNOWN':
        print("[전략별 분석] strategy_id 데이터 없음 — 건너뜀\n")
        return

    line = "=" * 78
    print(f"{line}")
    print("[OOS 전략별 성능]  (임계값 0.50, 전체 포함)")
    print(line)
    print(f"{'전략':>32} {'거래수':>7} {'승률':>7} {'평균PnL(%)':>11} {'추정PF':>8}")
    print("-" * 78)

    for sid, grp in oos_df.groupby('strategy_id', sort=True):
        n        = len(grp)
        win_rate = (grp['profit_rate'] > 0).mean()
        avg_pnl  = grp['profit_rate'].mean()
        wins     = grp[grp['profit_rate'] > 0]['profit_rate'].sum()
        losses   = abs(grp[grp['profit_rate'] <= 0]['profit_rate'].sum())
        pf       = wins / losses if losses > 0 else float('inf')
        print(f"{str(sid):>32} {n:>7,d} {win_rate:>7.1%} {avg_pnl:>11.2f} {pf:>8.2f}")

    print(line + "\n")


def train():
    os.makedirs(MODEL_DIR, exist_ok=True)

    # 학습 데이터 로드 (profit_rate + strategy 메타 포함)
    X, y, meta_df = load_training_data(return_meta=True)

    # 클래스 불균형 보정 (패 건수 / 승 건수)
    neg = int((y == 0).sum())
    pos = int((y == 1).sum())
    scale_pos_weight = neg / pos
    print(f"[Train] 클래스 비율 — 승: {pos}, 패: {neg}, scale_pos_weight: {scale_pos_weight:.2f}")

    # ---- Walk-forward OOS (TimeSeriesSplit, 랜덤 셔플 없음) ----
    print("[Train] Walk-forward CV (TimeSeriesSplit, n_splits=5) — 시간순 유지")
    print("  ※ 이전 StratifiedKFold(shuffle=True)와 AUC 수치가 다를 수 있음 (정상)")
    oos_df, auc_scores = walk_forward_oos(X, y, meta_df, scale_pos_weight, n_splits=5)

    if len(auc_scores) > 0:
        cv_mean = auc_scores.mean()
        cv_std  = auc_scores.std()
        print(f"[Train] Walk-forward CV AUC: {cv_mean:.4f} ± {cv_std:.4f}")
        if cv_mean < 0.52:
            print("[Train] 경고: AUC < 0.52 — 모델이 랜덤 수준입니다. 데이터를 더 수집하거나 피처를 검토하세요.")
    else:
        cv_mean, cv_std = 0.0, 0.0
        print("[Train] 경고: AUC 계산 불가 (폴드마다 단일 클래스)")

    # ---- OOS 임계값 분석 ----
    print_threshold_table(oos_df)

    # ---- 전략별 OOS 성능 ----
    print_strategy_table(oos_df)

    # ---- 전체 데이터로 최종 모델 학습 ----
    model = _make_model(scale_pos_weight)
    model.fit(X, y)

    # 학습 데이터 기준 성능 (in-sample — 참고용, 과적합 수치이므로 신뢰 불가)
    y_prob = model.predict_proba(X)[:, 1]
    y_pred = model.predict(X)
    train_auc = roc_auc_score(y, y_prob)
    print(f"[Train] 전체(in-sample) AUC: {train_auc:.4f}  ← 과적합 수치, 무시")
    print(classification_report(y, y_pred, target_names=['패(0)', '승(1)']))

    # 피처 중요도 상위 10개 출력
    importances = sorted(
        zip(X.columns, model.feature_importances_),
        key=lambda x: x[1],
        reverse=True,
    )
    print("[Train] 피처 중요도 Top 10:")
    for feat, imp in importances[:10]:
        print(f"  {feat:25s}: {imp:.4f}")

    # 모델 저장
    model.save_model(MODEL_PATH)
    print(f"[Train] 모델 저장: {MODEL_PATH}")

    # 메타 정보 저장 (serve.py에서 버전 읽기)
    version = datetime.now().strftime("v%Y%m%d_%H%M")
    meta = {
        "version":      version,
        "trained_at":   datetime.now().isoformat(),
        "n_samples":    len(X),
        "n_features":   X.shape[1],
        "win_rate":     float(y.mean()),
        "cv_auc_mean":  float(cv_mean),
        "cv_auc_std":   float(cv_std),
        "train_auc":    float(train_auc),
        "features":     list(X.columns),
    }
    with open(META_PATH, 'w', encoding='utf-8') as f:
        json.dump(meta, f, indent=2, ensure_ascii=False)

    print(f"[Train] 완료 — 버전: {version}, Walk-forward CV AUC: {cv_mean:.4f}")


if __name__ == '__main__':
    train()
