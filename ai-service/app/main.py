"""Service AI (FastAPI). Spring Boot goi service nay qua HTTP (mac dinh http://localhost:8000).

Nguoi phu trach: Diep (goi y mon, chatbot, du bao), Duong (phan tich danh gia).
Cac module them vao thu muc app/ roi include_router o day.
"""
import logging
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.routers import recommend

# Cau hinh log co ban
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("ai-service")

app = FastAPI(
    title="Food Delivery AI Service",
    description="Service AI xu ly goi y mon an, chatbot va du bao nhu cau",
    version="0.1.0",
)

# Global Exception Handler de xu ly loi va khong lam crash server
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error("Loi he thong khi xu ly request %s: %s", request.url.path, str(exc), exc_info=True)
    return JSONResponse(
        status_code=500,
        content={"status": "error", "message": "Loi may chu noi bo", "detail": str(exc)},
    )


# Dang ky cac router
app.include_router(recommend.router)


@app.get("/health")
def health() -> dict:
    """Endpoint kiem tra tinh trang hoat dong cua AI service."""
    logger.info("Health check endpoint duoc goi")
    return {
        "status": "ok",
        "service": "ai-service",
    }
