import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.recommender import (
    RecommendationEngine,
    RecommendationRequest,
    engine,
)

client = TestClient(app)


@pytest.fixture(autouse=True)
def reset_global_engine():
    yield
    # Khoi phuc trang thai rong sau moi test de khong anh huong test_health.py
    with engine._lock:
        engine._version = None
        engine._item_ids = []
        engine._item_index = {}
        engine._active_items = {}
        engine._cosine_matrix = None
        engine._co_occ_matrix = None
        engine._popular_items = []
        engine._user_profiles = {}
        engine._user_ordered_items = {}
        engine._basket_count = 0


def _sample_snapshot(version: str = "v1") -> dict:
    return {
        "snapshotVersion": version,
        "items": [
            {"itemId": 1, "restaurantId": 1, "name": "Phở bò tái", "price": 55000, "cuisineNames": ["Phở"], "isAvailable": True},
            {"itemId": 2, "restaurantId": 1, "name": "Quẩy giòn", "price": 10000, "cuisineNames": ["Phở"], "isAvailable": True},
            {"itemId": 3, "restaurantId": 1, "name": "Trà đá", "price": 5000, "cuisineNames": ["Phở"], "isAvailable": True},
            {"itemId": 4, "restaurantId": 2, "name": "Gà rán giòn", "price": 45000, "cuisineNames": ["Gà rán"], "isAvailable": True},
            {"itemId": 5, "restaurantId": 2, "name": "Khoai tây chiên", "price": 25000, "cuisineNames": ["Gà rán"], "isAvailable": True},
            {"itemId": 6, "restaurantId": 2, "name": "Pepsi vừa", "price": 18000, "cuisineNames": ["Gà rán"], "isAvailable": True},
            {"itemId": 7, "restaurantId": 1, "name": "Món ngừng bán", "price": 50000, "cuisineNames": ["Phở"], "isAvailable": False},
        ],
        "interactions": [
            {"userId": 10, "itemId": 1, "weight": 5.0, "type": "ORDER"},
            {"userId": 10, "itemId": 2, "weight": 5.0, "type": "ORDER"},
            {"userId": 11, "itemId": 1, "weight": 5.0, "type": "ORDER"},
            {"userId": 11, "itemId": 3, "weight": 3.0, "type": "ADD_TO_CART"},
            {"userId": 12, "itemId": 4, "weight": 5.0, "type": "ORDER"},
            {"userId": 12, "itemId": 5, "weight": 5.0, "type": "ORDER"},
            {"userId": 13, "itemId": 4, "weight": 5.0, "type": "ORDER"},
            {"userId": 13, "itemId": 6, "weight": 5.0, "type": "ORDER"},
        ],
        "orderBaskets": [
            {"orderId": 1001, "userId": 10, "itemIds": [1, 2], "isSimulated": True},
            {"orderId": 1002, "userId": 11, "itemIds": [1, 2, 3], "isSimulated": True},
            {"orderId": 1003, "userId": 12, "itemIds": [4, 5], "isSimulated": True},
            {"orderId": 1004, "userId": 13, "itemIds": [4, 5, 6], "isSimulated": True},
        ],
        "popularItems": [
            {"itemId": 4, "orderCount": 50, "quantitySold": 120},
            {"itemId": 1, "orderCount": 40, "quantitySold": 80},
            {"itemId": 7, "orderCount": 99, "quantitySold": 200},  # unavailable -> must be filtered out
            {"itemId": 5, "orderCount": 30, "quantitySold": 60},
            {"itemId": 2, "orderCount": 25, "quantitySold": 40},
        ],
    }


def test_recommendations_needs_snapshot_when_cold():
    local_engine = RecommendationEngine()
    res = local_engine.recommend(RecommendationRequest(userId=1, limit=5))
    assert res.needsSnapshot is True
    assert res.strategy == "POPULARITY"
    assert res.recommendations == []


def test_sync_and_personalized_differs_between_users():
    sync_res = client.post("/recommendations/sync", json=_sample_snapshot("v-test-1"))
    assert sync_res.status_code == 200
    assert sync_res.json()["ready"] is True
    assert sync_res.json()["activeItemCount"] == 6  # item 7 unavailable excluded

    # User A thích Phở bò tái (itemId=1) -> gợi ý Quẩy (2), Trà đá (3)
    res_pho = client.post(
        "/recommendations",
        json={
            "userId": 201,
            "limit": 5,
            "snapshotVersion": "v-test-1",
            "userInteractions": [{"itemId": 1, "weight": 5.0, "type": "ORDER"}],
        },
    )
    assert res_pho.status_code == 200
    data_pho = res_pho.json()
    assert data_pho["strategy"] == "PERSONALIZED"
    assert data_pho["needsSnapshot"] is False
    pho_ids = [r["itemId"] for r in data_pho["recommendations"]]
    assert 2 in pho_ids
    assert 3 in pho_ids
    assert 1 not in pho_ids
    assert 7 not in pho_ids

    # User B thích Gà rán (itemId=4) -> gợi ý Khoai tây chiên (5), Pepsi (6)
    res_chicken = client.post(
        "/recommendations",
        json={
            "userId": 202,
            "limit": 5,
            "snapshotVersion": "v-test-1",
            "userInteractions": [{"itemId": 4, "weight": 5.0, "type": "ORDER"}],
        },
    )
    assert res_chicken.status_code == 200
    data_chicken = res_chicken.json()
    assert data_chicken["strategy"] == "PERSONALIZED"
    chicken_ids = [r["itemId"] for r in data_chicken["recommendations"]]
    assert 5 in chicken_ids
    assert 6 in chicken_ids
    assert pho_ids != chicken_ids


def test_cold_start_new_user_returns_popularity_and_skips_unavailable():
    client.post("/recommendations/sync", json=_sample_snapshot("v-test-2"))
    res = client.post(
        "/recommendations",
        json={
            "userId": 9999,
            "limit": 5,
            "snapshotVersion": "v-test-2",
            "userInteractions": [],
        },
    )
    assert res.status_code == 200
    data = res.json()
    assert data["strategy"] == "POPULARITY"
    ids = [r["itemId"] for r in data["recommendations"]]
    assert ids[0] == 4  # Gà rán giòn (highest valid popularity)
    assert 7 not in ids  # Unavailable item must not appear


def test_exclude_cart_items_and_restaurant_filter():
    client.post("/recommendations/sync", json=_sample_snapshot("v-test-3"))
    res = client.post(
        "/recommendations",
        json={
            "userId": 201,
            "limit": 5,
            "restaurantId": 1,
            "excludeItemIds": [2],  # Quẩy đang trong giỏ
            "snapshotVersion": "v-test-3",
            "userInteractions": [{"itemId": 1, "weight": 5.0, "type": "ORDER"}],
        },
    )
    assert res.status_code == 200
    ids = [r["itemId"] for r in res.json()["recommendations"]]
    assert 2 not in ids
    assert 3 in ids
    assert all(iid in {1, 2, 3} for iid in ids)
