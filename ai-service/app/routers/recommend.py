"""Goi y mon (Diep). Hien la ban mau: tra ve danh sach rong.

Huong lam: dem cac mon hay duoc dat cung nhau (co-occurrence) + do tuong tu cosine
(scikit-learn) tu bang orders/order_items va user_item_interactions.
Neu nguoi dung moi (chua co lich su) thi tra ve mon ban chay.
"""
from fastapi import APIRouter

router = APIRouter(prefix="/recommend", tags=["recommend"])


@router.get("/{user_id}")
def recommend(user_id: int, limit: int = 10) -> dict:
    return {"user_id": user_id, "limit": limit, "items": []}
