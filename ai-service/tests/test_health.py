from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health():
    res = client.get("/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "ok"
    assert data["service"] == "ai-service"


def test_recommend_placeholder():
    res = client.get("/recommend/1?limit=5")
    assert res.status_code == 200
    assert res.json()["items"] == []
