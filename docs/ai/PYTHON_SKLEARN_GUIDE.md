# TÀI LIỆU HỌC PYTHON VÀ SCIKIT-LEARN CHO SERVICE AI
**Dự án:** Hệ thống giao đồ ăn nhanh Food Delivery Express  
**Người phụ trách:** Sầm Xuân Điệp (MSSV: 2380614940)  
**Phạm vi:** Service AI (FastAPI), Gợi ý món (scikit-learn), Dự báo doanh thu (statsmodels), Chatbot LLM  

---

## 1. TỔNG QUAN VỀ SERVICE AI TRONG HỆ THỐNG

### 1.1. Vai trò kiến trúc
- **Tại sao tách riêng AI Service bằng Python?**
  - Hệ sinh thái AI/ML và Data Science của Python (scikit-learn, pandas, numpy, statsmodels) phong phú và tối ưu hơn nhiều so với Java.
  - Phân tách trách nhiệm (Microservices pattern): Backend Spring Boot tập trung xử lý Transaction, Thanh toán, Bảo mật và Đơn hàng. Python FastAPI tập trung tính toán thuật toán gợi ý món và mô hình dữ liệu.
  - Khả năng mở rộng độc lập (Scale): AI Service có thể được cấp phát tài nguyên CPU/GPU riêng biệt qua Docker Compose mà không ảnh hưởng tới Spring Boot.

### 1.2. Luồng giao tiếp giữa Spring Boot và AI Service
1. **Spring Boot (Java 21):** Sử dụng `AiServiceClient` (dựa trên Spring `RestClient`) để gửi HTTP Request sang URL cấu hình sẵn (`app.ai-service-url`, mặc định `http://localhost:8000` hoặc container `http://ai-service:8000`).
2. **AI Service (FastAPI):** Nhận request, xử lý dữ liệu và trả về JSON chuẩn ISO.
3. **Cơ chế chịu lỗi (Fault Tolerance):** Nếu AI Service gặp sự cố hoặc tạm thời không phản hồi, `AiServiceClient` trả về trạng thái `DOWN` và kích hoạt phương án dự phòng (Fallback: ví dụ trả về danh sách món bán chạy nhất từ MySQL) thay vì làm dừng ứng dụng của khách hàng.

---

## 2. NỀN TẢNG FASTAPI VÀ CÁC THƯ VIỆN CỐT LÕI

### 2.1. FastAPI & Uvicorn
- **FastAPI:** Web framework hiệu năng cao dựa trên Starlette và Pydantic, hỗ trợ asynchronous (`async`/`await`), tự động sinh tài liệu Swagger UI (`/docs`).
- **Uvicorn:** ASGI Server chịu trách nhiệm chạy ứng dụng FastAPI.
- **Lệnh chạy cơ bản:**
  ```bash
  # Chạy trực tiếp từ thư mục ai-service
  uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
  ```

### 2.2. Kiểm tra sức khỏe (Health Check)
- Endpoint: `GET /health`
- Mục đích: Docker Compose và Spring Boot thăm dò định kỳ (health probe) để đảm bảo container đã sẵn sàng tiếp nhận request.
- Phản hồi:
  ```json
  {
    "status": "ok",
    "service": "ai-service"
  }
  ```

---

## 3. THUẬT TOÁN GỢI Ý MÓN BẰNG SCIKIT-LEARN (CHUẨN BỊ CHO TUẦN 7)

### 3.1. Bài toán đặt ra
Khách hàng mở app/web muốn nhận được danh sách món ăn gợi ý phù hợp với sở thích cá nhân ("Gợi ý cho bạn" trên trang chủ).

### 3.2. Phương pháp giải quyết: Lọc cộng tác dựa trên món ăn (Item-based Collaborative Filtering)
1. **Ma trận tương quan món ăn (Item Co-occurrence Matrix):**
   - Đếm tần suất 2 món ăn được đặt cùng nhau trong cùng một đơn hàng (`order_items`).
   - Ví dụ: Khách đặt `Cơm tấm sườn` rất thường xuyên gọi kèm `Trà đá` hoặc `Canh rong biển`.
2. **Độ tương tự Cosine (Cosine Similarity trong scikit-learn):**
   - Công thức: $\cos(\theta) = \frac{\mathbf{A} \cdot \mathbf{B}}{\|\mathbf{A}\| \|\mathbf{B}\|}$
   - Đo góc giữa 2 vector biểu diễn lịch sử đặt món của các món ăn. Giá trị từ 0 (không liên quan) đến 1 (hoàn toàn tương đồng).
   - Code minh họa với scikit-learn:
     ```python
     from sklearn.metrics.pairwise import cosine_similarity
     import numpy as np

     # Ma trận đặc trưng món ăn x người dùng (hoặc đặc trưng phân loại)
     item_matrix = np.array([
         [1, 0, 1, 1],  # Món A (Cơm sườn)
         [1, 1, 1, 0],  # Món B (Cơm bì)
         [0, 1, 0, 0],  # Món C (Trà sữa)
     ])

     # Tính độ tương tự giữa tất cả các cặp món
     similarity = cosine_similarity(item_matrix)
     # similarity[0][1] sẽ có độ tương đồng cao hơn similarity[0][2]
     ```
3. **Chiến lược giải quyết vấn đề Cold Start (Người dùng mới):**
   - Nếu người dùng chưa có bất kỳ lịch sử xem món hay đặt hàng nào (`user_item_interactions` rỗng), hệ thống sẽ **Fallback sang danh sách Món bán chạy nhất** (Popularity-based recommendation) lấy từ thống kê đơn hàng.

---

## 4. DỰ BÁO DOANH THU BẰNG STATSMODELS (CHUẨN BỊ CHO TUẦN 8)

### 4.1. Bài toán đặt ra
Chủ cửa hàng cần biết xu hướng doanh thu dự kiến trong 7 ngày tới để chủ động chuẩn bị nguyên vật liệu và nhân sự.

### 4.2. Phương pháp giải quyết: Mô hình làm mịn hàm mũ Holt-Winters (Exponential Smoothing)
- Thuộc thư viện `statsmodels.tsa.holtwinters`.
- Ưu điểm: Phù hợp với chuỗi thời gian có xu hướng (trend) và tính chu kỳ tuần (seasonality - ví dụ cuối tuần doanh thu luôn tăng vọt).
- Code minh họa:
  ```python
  from statsmodels.tsa.holtwinters import ExponentialSmoothing
  import pandas as pd

  # Chuỗi doanh thu 30 ngày gần nhất
  # dates = pd.date_range(start='2026-10-01', periods=30)
  # series = pd.Series(revenue_data, index=dates)

  # Huấn luyện mô hình với chu kỳ 7 ngày (1 tuần)
  model = ExponentialSmoothing(
      series,
      trend='add',
      seasonal='add',
      seasonal_periods=7
  ).fit()

  # Dự báo 7 ngày tiếp theo
  forecast_7_days = model.forecast(7)
  ```
- Kết quả được lưu vào bảng `demand_forecasts` trong MySQL và hiển thị lên biểu đồ Recharts tại Dashboard cửa hàng.

---

## 5. BỘ CÂU HỎI VẤN ĐÁP THƯỜNG GẶP KHI BẢO VỆ ĐỒ ÁN

### Câu hỏi 1: Tại sao em lại tách service AI ra chạy riêng bằng Python FastAPI mà không viết luôn trong Spring Boot?
**Trả lời:**  
Dạ thưa Thầy/Cô, việc tách service AI thành một microservice riêng bằng Python FastAPI có 3 lý do kỹ thuật chính:
1. **Thư viện chuyên dụng:** Các thư viện Machine Learning và phân tích chuỗi thời gian mạnh mẽ như `scikit-learn`, `pandas`, `statsmodels` đều được tối ưu sâu trên nền Python với các thư viện tính toán C/C++ (NumPy).
2. **Kiến trúc tách biệt (Decoupling):** Spring Boot chịu trách nhiệm xử lý nghiệp vụ chính (giao dịch, đặt món, thanh toán VNPay/MoMo) đòi hỏi tính nhất quán ACID. Các tác vụ tính toán AI có tính chất phân tích dữ liệu chuyên sâu được cô lập, tránh tiêu tốn tài nguyên CPU/RAM ảnh hưởng đến luồng đặt hàng.
3. **Mở rộng linh hoạt (Scalability):** Service AI được đóng gói Docker riêng, trong tương lai khi lượng truy cập tăng có thể scale số lượng container hoặc triển khai trên server có card đồ họa GPU mà không cần thay đổi code của backend Spring Boot.

### Câu hỏi 2: Khi AI Service bị lỗi hoặc mất mạng, hệ thống của em xử lý thế nào? Có làm khách hàng không đặt được món không?
**Trả lời:**  
Dạ không ạ. Trong backend Spring Boot, em đã thiết kế lớp `AiServiceClient` sử dụng Spring `RestClient` có cấu hình timeout rõ ràng (3 giây) và bọc khối `try-catch` an toàn. Nếu AI service tạm ngưng hoặc trả về lỗi, hệ thống sẽ tự động kích hoạt cơ chế **Fallback (dự phòng)**:
- Ở trang chủ, thay vì gợi ý cá nhân hóa từ AI, hệ thống sẽ tự động chuyển sang hiển thị danh sách **các món bán chạy nhất** từ database MySQL.
- Khách hàng vẫn xem menu, thêm vào giỏ và đặt hàng hoàn toàn bình thường mà không thấy lỗi.

### Câu hỏi 3: Trong Tuần 2 này em đã làm được những gì cho phần AI?
**Trả lời:**  
Dạ thưa Thầy/Cô, trong Tuần 2 em đã hoàn thành:
1. Xây dựng cấu trúc service FastAPI chuẩn với thư mục `app/` và router riêng.
2. Xây dựng endpoint kiểm tra sức khỏe `GET /health` trả về trạng thái HTTP 200 và JSON `{"status": "ok", "service": "ai-service"}`.
3. Viết unit test tự động bằng `pytest` đạt 100% pass.
4. Cấu hình Dockerfile và tích hợp `ai-service` vào `docker-compose.yml` có Docker Healthcheck tự động thăm dò.
5. Tạo client `AiServiceClient` và controller `/api/ai/health` trong Spring Boot để kiểm chứng việc Spring Boot gọi sang service AI thành công.
