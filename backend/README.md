# backend (Spring Boot)

Java 21, Spring Boot 3.5, Maven, MySQL + Flyway, SQL thuần.

## Chạy

```bash
docker compose up -d mysql      # ở thư mục gốc repo
cd backend
mvn spring-boot:run
```

- Health: http://localhost:8080/api/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- Cấu hình: `src/main/resources/application.yml` (mặc định khớp với `docker-compose.yml`).

Lần chạy đầu Flyway tạo 41 bảng từ `src/main/resources/db/migration/V1__init.sql`.

## Cấu trúc

```
src/main/java/vn/hutech/fooddelivery/
├── config/        SecurityConfig, OpenApiConfig
├── common/        ApiError, GlobalExceptionHandler, NotFoundException, Haversine
├── health/        GET /api/health
└── <tinh-nang>/   auth, user, cart, order, payment, restaurant, menu, review,
                   driver, assignment, tracking, notification,
                   promotion, admin, report, support, ai
```

Mỗi package tính năng có `package-info.java` ghi người phụ trách. Thêm code theo luồng Controller → Service → Repository (xem [docs/CONVENTIONS.md](../docs/CONVENTIONS.md)).

## Việc cần làm đầu tiên

- Khang: JWT + phân quyền trong `SecurityConfig` (hiện đang cho phép mọi request).
- Mọi người: thêm API của mình, thử bằng Swagger UI.
