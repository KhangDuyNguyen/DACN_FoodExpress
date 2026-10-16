# ai-service (Python FastAPI)

Chay local:

```bash
cd ai-service
python -m venv .venv
.venv\Scripts\activate          # Windows (macOS/Linux: source .venv/bin/activate)
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Mo http://localhost:8000/docs de thu API. Chay test: `pytest` (can `pip install pytest`).

Chay bang Docker: `docker compose up -d ai-service` (o thu muc goc).
