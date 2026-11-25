"""Engine goi y mon an (MVP) cho FoodExpress.

Ket hop hai huong thuat toan tren du lieu tuong tac va don hang hop le:
1. Item Co-occurrence: dem tan suat cac mon thuong duoc dat cung nhau trong mot don
   (chuan hoa theo chi so Ochiai de tranh mon phu gia re tien lan at mon chinh).
2. Item-based Cosine Similarity (scikit-learn): xay dung ma tran User-Item co trong so
   hanh vi (VIEW < ADD_TO_CART < FAVORITE < ORDER) va tinh do tuong dong cosine giua cac mon.

Ho tro Cold Start (nguoi dung moi chua co lich su) bang danh sach mon ban chay (POPULARITY).
"""
from __future__ import annotations

import logging
import threading
from collections import defaultdict
from typing import Literal

import numpy as np
import pandas as pd
from pydantic import BaseModel, Field
from scipy.sparse import csr_matrix
from sklearn.metrics.pairwise import cosine_similarity

logger = logging.getLogger("ai-service.recommender")

# Trong so hanh vi nguoi dung (de dang dieu chinh theo nghiep vu)
INTERACTION_WEIGHTS: dict[str, float] = {
    "VIEW": 1.0,
    "ADD_TO_CART": 3.0,
    "FAVORITE": 3.5,
    "ORDER": 5.0,
}

# Ty le ket hop giua Cosine Similarity va Co-occurrence
COSINE_BLEND_WEIGHT = 0.60
CO_OCCURRENCE_BLEND_WEIGHT = 0.40


class ActiveItem(BaseModel):
    itemId: int
    restaurantId: int
    name: str
    price: float = 0.0
    cuisineNames: list[str] = Field(default_factory=list)
    isAvailable: bool = True


class UserInteractionRecord(BaseModel):
    userId: int
    itemId: int
    weight: float = Field(gt=0)
    type: str | None = None


class OrderBasket(BaseModel):
    orderId: int | None = None
    userId: int | None = None
    itemIds: list[int] = Field(default_factory=list)
    isSimulated: bool = False


class PopularItemRecord(BaseModel):
    itemId: int
    orderCount: int = 0
    quantitySold: int = 0


class SnapshotPayload(BaseModel):
    snapshotVersion: str
    items: list[ActiveItem] = Field(default_factory=list)
    interactions: list[UserInteractionRecord] = Field(default_factory=list)
    orderBaskets: list[OrderBasket] = Field(default_factory=list)
    popularItems: list[PopularItemRecord] = Field(default_factory=list)


class UserHistorySignal(BaseModel):
    itemId: int
    weight: float = Field(default=1.0, gt=0)
    type: str = "VIEW"


class RecommendationRequest(BaseModel):
    userId: int | None = None
    limit: int = Field(default=10, ge=1, le=50)
    restaurantId: int | None = None
    excludeItemIds: list[int] = Field(default_factory=list)
    excludeOrdered: bool = True
    userInteractions: list[UserHistorySignal] = Field(default_factory=list)
    snapshotVersion: str | None = None
    snapshot: SnapshotPayload | None = None


class RecommendedItemOut(BaseModel):
    itemId: int
    score: float
    reason: str
    cosineScore: float = 0.0
    coOccurrenceScore: float = 0.0


class RecommendationResponse(BaseModel):
    recommendations: list[RecommendedItemOut] = Field(default_factory=list)
    strategy: Literal["PERSONALIZED", "POPULARITY"]
    needsSnapshot: bool = False
    snapshotVersion: str | None = None
    message: str | None = None


class RecommendationEngine:
    """Bo may tinh toan va cache ma tran goi y trong bo nho."""

    def __init__(self) -> None:
        self._lock = threading.RLock()
        self._version: str | None = None
        self._item_ids: list[int] = []
        self._item_index: dict[int, int] = {}
        self._active_items: dict[int, ActiveItem] = {}
        self._cosine_matrix: np.ndarray | None = None
        self._co_occ_matrix: np.ndarray | None = None
        self._popular_items: list[PopularItemRecord] = []
        self._user_profiles: dict[int, dict[int, float]] = {}
        self._user_ordered_items: dict[int, set[int]] = {}
        self._basket_count: int = 0

    def is_ready(self) -> bool:
        with self._lock:
            return self._version is not None

    def status_summary(self) -> dict:
        with self._lock:
            return {
                "ready": self._version is not None,
                "snapshotVersion": self._version,
                "activeItemCount": len(self._item_ids),
                "userProfileCount": len(self._user_profiles),
                "basketCount": self._basket_count,
            }

    def fit_snapshot(self, snapshot: SnapshotPayload) -> dict:
        """Xay dung va luu cache ma tran Cosine Similarity va Co-occurrence."""
        active_items = {
            item.itemId: item
            for item in snapshot.items
            if item.isAvailable
        }
        item_ids = sorted(active_items.keys())
        item_index = {item_id: idx for idx, item_id in enumerate(item_ids)}
        n_items = len(item_ids)

        # 1. Ma tran User-Item co trong so (ket hop user_item_interactions + don hang lich su)
        user_item_weights: dict[int, dict[int, float]] = defaultdict(lambda: defaultdict(float))
        user_ordered_items: dict[int, set[int]] = defaultdict(set)

        for rec in snapshot.interactions:
            if rec.itemId not in item_index:
                continue
            user_item_weights[rec.userId][rec.itemId] += float(rec.weight)
            if rec.type and rec.type.upper() == "ORDER":
                user_ordered_items[rec.userId].add(rec.itemId)

        # 2. Ma tran Co-occurrence tu cac gio don hang hop le (bo qua don huy)
        co_counts = np.zeros((n_items, n_items), dtype=np.float64)
        item_freq = np.zeros(n_items, dtype=np.float64)
        basket_count = 0

        for basket in snapshot.orderBaskets:
            valid_unique_ids = sorted({iid for iid in basket.itemIds if iid in item_index})
            if not valid_unique_ids:
                continue
            basket_count += 1
            if basket.userId is not None:
                for iid in valid_unique_ids:
                    # Chi bo sung trong so don hang neu chua co hoac tang tin hieu dat mon
                    if iid not in user_ordered_items[basket.userId]:
                        user_item_weights[basket.userId][iid] += INTERACTION_WEIGHTS["ORDER"]
                    user_ordered_items[basket.userId].add(iid)

            indices = [item_index[iid] for iid in valid_unique_ids]
            for idx in indices:
                item_freq[idx] += 1.0
            if len(indices) >= 2:
                for i_pos in range(len(indices)):
                    idx_a = indices[i_pos]
                    for j_pos in range(i_pos + 1, len(indices)):
                        idx_b = indices[j_pos]
                        co_counts[idx_a, idx_b] += 1.0
                        co_counts[idx_b, idx_a] += 1.0

        # Chuan hoa Co-occurrence bang Ochiai: C(i,j) / sqrt(freq(i) * freq(j))
        co_occ_matrix = np.zeros((n_items, n_items), dtype=np.float64)
        if n_items > 0:
            denom = np.sqrt(np.outer(item_freq, item_freq))
            np.divide(co_counts, denom, out=co_occ_matrix, where=denom > 0)
            np.fill_diagonal(co_occ_matrix, 0.0)

        # 3. Tinh Item-Item Cosine Similarity tu ma tran User-Item bang pandas + scikit-learn
        cosine_matrix = np.zeros((n_items, n_items), dtype=np.float64)
        if n_items > 0 and user_item_weights:
            rows_data = []
            for uid, items_map in user_item_weights.items():
                for iid, w in items_map.items():
                    if w > 0:
                        # Giam nhe anh huong lap lai qua nhieu lan xem bang log1p
                        smoothed = float(np.log1p(w))
                        rows_data.append((uid, item_index[iid], smoothed))

            if rows_data:
                df = pd.DataFrame(rows_data, columns=["user_id", "item_idx", "weight"])
                unique_users = df["user_id"].unique()
                user_to_row = {uid: r for r, uid in enumerate(unique_users)}
                row_indices = df["user_id"].map(user_to_row).to_numpy()
                col_indices = df["item_idx"].to_numpy()
                values = df["weight"].to_numpy(dtype=np.float64)

                # Ma tran thua (n_users x n_items), chuyen vi thanh (n_items x n_users) de tinh cosine giua cac mon
                user_item_sparse = csr_matrix(
                    (values, (row_indices, col_indices)),
                    shape=(len(unique_users), n_items),
                )
                cosine_matrix = cosine_similarity(user_item_sparse.T)
                np.fill_diagonal(cosine_matrix, 0.0)
                cosine_matrix = np.clip(cosine_matrix, 0.0, 1.0)

        # Sap xep mon ban chay (loai mon het hang)
        popular_items = [
            p for p in snapshot.popularItems if p.itemId in item_index
        ]
        popular_items.sort(key=lambda x: (x.quantitySold, x.orderCount), reverse=True)

        with self._lock:
            self._version = snapshot.snapshotVersion
            self._item_ids = item_ids
            self._item_index = item_index
            self._active_items = active_items
            self._cosine_matrix = cosine_matrix
            self._co_occ_matrix = co_occ_matrix
            self._popular_items = popular_items
            self._user_profiles = {uid: dict(mp) for uid, mp in user_item_weights.items()}
            self._user_ordered_items = {uid: set(st) for uid, st in user_ordered_items.items()}
            self._basket_count = basket_count

        logger.info(
            "Updated recommendation snapshot version=%s items=%d users=%d baskets=%d",
            snapshot.snapshotVersion,
            n_items,
            len(user_item_weights),
            basket_count,
        )
        return self.status_summary()

    def recommend(self, req: RecommendationRequest) -> RecommendationResponse:
        """Tinh goi y mon cho nguoi dung dua tren snapshot da cache."""
        if req.snapshot is not None:
            with self._lock:
                if self._version != req.snapshot.snapshotVersion:
                    self.fit_snapshot(req.snapshot)

        with self._lock:
            if self._version is None:
                return RecommendationResponse(
                    recommendations=[],
                    strategy="POPULARITY",
                    needsSnapshot=True,
                    snapshotVersion=None,
                    message="AI Service chưa nhận dữ liệu khởi tạo, kích hoạt fallback từ backend",
                )

            needs_snapshot = (
                req.snapshotVersion is not None and req.snapshotVersion != self._version
            )
            n_items = len(self._item_ids)
            if n_items == 0:
                return RecommendationResponse(
                    recommendations=[],
                    strategy="POPULARITY",
                    needsSnapshot=needs_snapshot,
                    snapshotVersion=self._version,
                    message="Hiện chưa có món ăn khả dụng trong hệ thống",
                )

            # Tap hop tin hieu hanh vi cua user (ket hop request + cache neu can)
            user_signals: dict[int, float] = {}
            ordered_items: set[int] = set()

            if req.userInteractions:
                for sig in req.userInteractions:
                    if sig.itemId in self._item_index:
                        user_signals[sig.itemId] = user_signals.get(sig.itemId, 0.0) + float(sig.weight)
                        if sig.type and sig.type.upper() == "ORDER":
                            ordered_items.add(sig.itemId)
            elif req.userId is not None and req.userId in self._user_profiles:
                for iid, w in self._user_profiles[req.userId].items():
                    if iid in self._item_index:
                        user_signals[iid] = float(w)
                ordered_items = set(self._user_ordered_items.get(req.userId, set()))

            excluded_ids = set(req.excludeItemIds)
            if req.excludeOrdered:
                excluded_ids.update(ordered_items)

            # Cold Start: chua co lich su tuong tac hop le
            if not user_signals:
                popular = self._build_popularity_recommendations(
                    limit=req.limit,
                    restaurant_id=req.restaurantId,
                    excluded_ids=excluded_ids,
                )
                return RecommendationResponse(
                    recommendations=popular,
                    strategy="POPULARITY",
                    needsSnapshot=needs_snapshot,
                    snapshotVersion=self._version,
                    message="Món bán chạy nổi bật cho người dùng mới" if popular else "Chưa có dữ liệu món bán chạy",
                )

            # Tinh diem ca nhan hoa bang Cosine Similarity + Item Co-occurrence
            user_vec = np.zeros(n_items, dtype=np.float64)
            source_items: list[tuple[int, int, float]] = []
            for iid, weight in user_signals.items():
                idx = self._item_index[iid]
                smoothed_w = float(np.log1p(max(weight, 0.0)))
                user_vec[idx] = smoothed_w
                source_items.append((iid, idx, smoothed_w))

            total_w = float(user_vec.sum())
            if total_w <= 0:
                popular = self._build_popularity_recommendations(
                    limit=req.limit,
                    restaurant_id=req.restaurantId,
                    excluded_ids=excluded_ids,
                )
                return RecommendationResponse(
                    recommendations=popular,
                    strategy="POPULARITY",
                    needsSnapshot=needs_snapshot,
                    snapshotVersion=self._version,
                    message="Món bán chạy nổi bật",
                )

            assert self._cosine_matrix is not None and self._co_occ_matrix is not None
            cosine_scores = (user_vec @ self._cosine_matrix) / total_w
            co_occ_scores = (user_vec @ self._co_occ_matrix) / total_w

            # Chuan hoa ve [0, 1] theo max score neu > 0 de phoi tron cong bang
            max_cos = float(cosine_scores.max()) if cosine_scores.size > 0 else 0.0
            max_co = float(co_occ_scores.max()) if co_occ_scores.size > 0 else 0.0
            norm_cos = cosine_scores / max_cos if max_cos > 0 else cosine_scores
            norm_co = co_occ_scores / max_co if max_co > 0 else co_occ_scores

            candidates: list[RecommendedItemOut] = []
            for idx, item_id in enumerate(self._item_ids):
                if item_id in excluded_ids or item_id in user_signals:
                    continue
                item_meta = self._active_items[item_id]
                if req.restaurantId is not None and item_meta.restaurantId != req.restaurantId:
                    continue

                c_score = float(norm_cos[idx])
                co_score = float(norm_co[idx])
                combined = COSINE_BLEND_WEIGHT * c_score + CO_OCCURRENCE_BLEND_WEIGHT * co_score
                if combined <= 0.0:
                    continue

                reason = self._explain_reason(
                    candidate_idx=idx,
                    source_items=source_items,
                    c_score=c_score,
                    co_score=co_score,
                )
                candidates.append(
                    RecommendedItemOut(
                        itemId=item_id,
                        score=round(min(combined, 1.0), 4),
                        reason=reason,
                        cosineScore=round(c_score, 4),
                        coOccurrenceScore=round(co_score, 4),
                    )
                )

            candidates.sort(key=lambda r: r.score, reverse=True)
            selected = candidates[: req.limit]

            if not selected:
                # Khong co mon tuong dong nao (vd user moi tuong tac mon chua co co-occurrence) -> fallback POPULARITY
                popular = self._build_popularity_recommendations(
                    limit=req.limit,
                    restaurant_id=req.restaurantId,
                    excluded_ids=excluded_ids,
                )
                return RecommendationResponse(
                    recommendations=popular,
                    strategy="POPULARITY",
                    needsSnapshot=needs_snapshot,
                    snapshotVersion=self._version,
                    message="Chưa đủ dữ liệu tương đồng, hiển thị món bán chạy",
                )

            return RecommendationResponse(
                recommendations=selected,
                strategy="PERSONALIZED",
                needsSnapshot=needs_snapshot,
                snapshotVersion=self._version,
                message="Gợi ý cá nhân hóa dựa trên sở thích của bạn",
            )

    def _explain_reason(
        self,
        candidate_idx: int,
        source_items: list[tuple[int, int, float]],
        c_score: float,
        co_score: float,
    ) -> str:
        assert self._cosine_matrix is not None and self._co_occ_matrix is not None
        if co_score >= c_score and co_score > 0:
            best_src_id = max(
                source_items,
                key=lambda s: s[2] * float(self._co_occ_matrix[s[1], candidate_idx]),
            )[0]
            src_name = self._active_items[best_src_id].name
            return f"Thường được đặt cùng với {src_name}"

        best_src_id = max(
            source_items,
            key=lambda s: s[2] * float(self._cosine_matrix[s[1], candidate_idx]),
        )[0]
        src_name = self._active_items[best_src_id].name
        return f"Phù hợp với gu thưởng thức từ {src_name}"

    def _build_popularity_recommendations(
        self,
        limit: int,
        restaurant_id: int | None,
        excluded_ids: set[int],
    ) -> list[RecommendedItemOut]:
        filtered: list[PopularItemRecord] = []
        for pop in self._popular_items:
            if pop.itemId in excluded_ids:
                continue
            item = self._active_items.get(pop.itemId)
            if item is None or not item.isAvailable:
                continue
            if restaurant_id is not None and item.restaurantId != restaurant_id:
                continue
            filtered.append(pop)

        if filtered:
            max_qty = max(p.quantitySold for p in filtered) or 1
            return [
                RecommendedItemOut(
                    itemId=p.itemId,
                    score=round(max(0.1, p.quantitySold / max_qty), 4),
                    reason="Món bán chạy được nhiều khách yêu thích",
                )
                for p in filtered[:limit]
            ]

        # Fallback cuoi cung neu chua co don hang nao trong he thong: lay danh sach mon dang mo ban
        fallback_items = [
            item
            for item in self._active_items.values()
            if item.isAvailable
            and item.itemId not in excluded_ids
            and (restaurant_id is None or item.restaurantId == restaurant_id)
        ]
        fallback_items.sort(key=lambda x: x.itemId)
        return [
            RecommendedItemOut(
                itemId=item.itemId,
                score=0.5,
                reason="Món nổi bật tại quán",
            )
            for item in fallback_items[:limit]
        ]


engine = RecommendationEngine()

