"""Router AI goi y mon an (Diep).

Cung cap:
- POST /recommendations/sync: Spring Boot dong bo snapshot du lieu tuong tac & don hang de AI Service tinh & cache ma tran.
- POST /recommendations: Tinh goi y mon cho user (PERSONALIZED bang Co-occurrence + Cosine Similarity, hoac POPULARITY cho Cold Start).
- GET /recommendations/status: Kiem tra trang thai cache cua model goi y.
- GET /recommend/{user_id}: Endpoint GET don gian (giu tuong thich tu tuan 2).
"""
from fastapi import APIRouter

from app.recommender import (
    INTERACTION_WEIGHTS,
    RecommendationRequest,
    RecommendationResponse,
    SnapshotPayload,
    engine,
)

router = APIRouter(tags=["recommend"])


@router.post("/recommendations/sync")
def sync_snapshot(payload: SnapshotPayload) -> dict:
    """Nhan snapshot du lieu tu Spring Boot, xay dung va cache ma tran goi y."""
    summary = engine.fit_snapshot(payload)
    return {
        "status": "ok",
        "weights": INTERACTION_WEIGHTS,
        **summary,
    }


@router.get("/recommendations/status")
def recommendation_status() -> dict:
    """Trang thai cache ma tran goi y hien tai."""
    return {
        "weights": INTERACTION_WEIGHTS,
        **engine.status_summary(),
    }


@router.post("/recommendations", response_model=RecommendationResponse)
def get_recommendations(req: RecommendationRequest) -> RecommendationResponse:
    """Tra ve danh sach mon goi y cho nguoi dung."""
    return engine.recommend(req)


@router.get("/recommend/{user_id}")
def recommend_legacy(user_id: int, limit: int = 10) -> dict:
    """Endpoint tuong thich tu tuan 2: goi y nhanh theo user_id tu cache hien tai."""
    if not engine.is_ready():
        return {"user_id": user_id, "limit": limit, "items": []}
    resp = engine.recommend(RecommendationRequest(userId=user_id, limit=limit))
    return {
        "user_id": user_id,
        "limit": limit,
        "strategy": resp.strategy,
        "items": [r.model_dump() for r in resp.recommendations],
    }
