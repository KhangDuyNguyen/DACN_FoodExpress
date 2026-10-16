from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health():
    res = client.get("/health")
    assert res.status_code == 200
    assert res.json() == {"status": "ok"}


def test_recommend_placeholder():
    res = client.get("/recommend/1?limit=5")
    assert res.status_code == 200
    assert res.json()["items"] == []
