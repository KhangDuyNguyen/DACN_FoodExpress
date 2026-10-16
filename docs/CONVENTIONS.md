# Quy ước làm việc

## Git

- Nhánh `main` luôn chạy được. Không push thẳng lên `main`, làm trên nhánh riêng rồi mở Pull Request.
- Tên nhánh: `feature/<ten>-<tinh-nang>`, ví dụ `feature/an-driver-offers`, `fix/duong-menu-price`.
- Tên commit: `feat: ...`, `fix: ...`, `docs: ...`, `refactor: ...`. Ví dụ `feat: api nhan don cho tai xe`.
- Commit nhỏ và đều trong tuần. Commit log là bằng chứng đóng góp cá nhân khi thầy vấn đáp.
- Mỗi Pull Request có ít nhất 1 người trong nhóm xem qua trước khi gộp.
- Trước khi làm: `git pull origin main`. Gặp xung đột thì hỏi nhóm, không ghi đè code người khác.

## Backend (Spring Boot)

- Mỗi tính năng là một package trong `vn.hutech.fooddelivery` (xem `package-info.java` để biết ai phụ trách).
- Luồng chuẩn: `Controller` (nhận request) → `Service` (nghiệp vụ) → `Repository` (SQL thuần) → MySQL.
- Đường dẫn bắt đầu bằng `/api`. Dữ liệu JSON, tên trường dạng camelCase, thời gian theo chuẩn ISO-8601.
- Lỗi trả về theo `ApiError` (có sẵn trong `common`). Không trả stack trace cho client.
- Phân quyền theo `users.role`: `/api/admin/**` (ADMIN), `/api/merchant/**` (MERCHANT), `/api/driver/**` (DRIVER).
- Mọi API mới phải thử được trong Swagger UI.

### SQL thuần: luôn truyền tham số, không nối chuỗi

```java
@Repository
public class OrderRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public OrderRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<OrderRow> findById(long id) {
        String sql = """
            SELECT id, code, customer_id, restaurant_id, status, total_amount
            FROM orders WHERE id = :id
            """;
        return jdbc.query(sql, Map.of("id", id), (rs, i) -> new OrderRow(
                rs.getLong("id"), rs.getString("code"), rs.getLong("customer_id"),
                rs.getLong("restaurant_id"), rs.getString("status"), rs.getBigDecimal("total_amount")
        )).stream().findFirst();
    }
}
```

Nối chuỗi vào câu SQL (`"... WHERE id = " + id`) bị cấm vì dễ bị SQL injection.

## Database (Flyway)

- `V1__init.sql` là bản gốc 41 bảng. **Không sửa V1 sau khi cả nhóm đã chạy.**
- Cần thêm hoặc đổi bảng thì tạo file mới trong `backend/src/main/resources/db/migration/`, đặt tên theo ngày để tránh trùng số giữa các người: `V20261026_01__them_cot_abc.sql`.
- Báo nhóm khi đổi database. Mỗi người pull code rồi chạy lại backend là tự cập nhật.
- Muốn làm lại database sạch: `docker compose down -v` rồi `docker compose up -d mysql`.

## Web (React)

- Web có 3 khu trong 1 project: khách (`/`), cửa hàng (`/merchant`), admin (`/admin`). Mỗi khu có thư mục `pages/` riêng, xem README trong từng thư mục để biết ai làm trang nào.
- Gọi API qua `src/api/client.ts` (đã gắn JWT). Dev dùng Vite proxy nên không cần cấu hình CORS.
- Dữ liệu từ server dùng TanStack Query, không tự `useEffect` + `fetch` lung tung.

## Android

- Làm trong package riêng của mình, không sửa file của người khác khi không cần.
- Emulator gọi backend qua `http://10.0.2.2:8080/api/`.

## Bảo mật

- Không commit `.env`, API key, mật khẩu, file khóa (đã chặn trong `.gitignore`).
- Key LLM, VNPay, MoMo, Firebase chỉ để trong biến môi trường, không để trong code phía client (web/app).
- Mật khẩu người dùng chỉ lưu dạng băm (BCrypt).
