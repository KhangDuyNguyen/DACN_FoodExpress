"""Service AI (FastAPI). Spring Boot goi service nay qua HTTP (mac dinh http://localhost:8000).

Nguoi phu trach: Diep (goi y mon, chatbot, du bao), Dung (phan tich danh gia).
Cac module them vao thu muc app/ roi include_router o day.
"""
from fastapi import FastAPI

from app.routers import recommend

app = FastAPI(title="Food Delivery AI Service", version="0.1.0")
app.include_router(recommend.router)


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}
