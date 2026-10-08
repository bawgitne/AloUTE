# CHECKPOINT PHASE 6: DATABASE SYNC, PAGE CONTROLLERS VS UI, DOCKER & DEVOPS

> **Chịu trách nhiệm chính**: Nguyễn Duy Ngọc (DevOps/DB) & Phạm Minh Quân (UI Integration)  
> **Trạng thái**: 🟢 **HOÀN THÀNH (95%)**  
> **Mức độ rủi ro**: Rất thấp (Đã trang bị đầy đủ Docker, docker-compose, script khởi tạo CSDL PostgreSQL và liên kết toàn bộ SSR Pages)  

---

## 1. CÁC TẦNG ĐÃ ĐỐI SOÁT & HOÀN TẤT

### 1.1. Giải pháp CSDL Thực tế: PostgreSQL Container với Stored Procedures & Triggers
- Đã tạo `current/deploy/postgres/init-db.sh`:
  - Khởi tạo tuần tự theo đúng thứ tự phụ thuộc:
    1. `/db/schema.sql`: Bảng chính, ràng buộc khóa ngoại, chỉ mục.
    2. `/db/procedures.sql`: Toàn bộ các stored procedures (`sp_get_visible_post`, `sp_list_public_posts`, `sp_create_post`, ...).
    3. `/db/triggers.sql`: Các trigger tự động cập nhật bộ đếm bài viết, lượt like, reply.
    4. `/db/data-seed.sql`: Dữ liệu mẫu ban đầu.
- Đã tạo `application-prod.yml` cấu hình kết nối chuẩn tới CSDL PostgreSQL: `jdbc:postgresql://postgres:5432/aloutedb`.
- H2 in-memory vẫn giữ nguyên cho unit test cục bộ nhanh (`mvn test` vượt qua 25/25 tests).

### 1.2. Kết nối Đồng bộ Toàn bộ Page Controller (SSR) và Giao diện Thymeleaf
Đã liên kết hoàn chỉnh tất cả các màn hình Thymeleaf với Backend Controller:

| Route người dùng | Template tương ứng | Page Controller | Trạng thái |
| :--- | :--- | :--- | :--- |
| `GET /feed` | `templates/feed/index.html` | `FeedPageController.java` | ✅ Đã inject `PostService`, nạp danh sách `posts` và `page` vào model. |
| `GET /posts/{id}` | `templates/post/detail.html` | `PostPageController.java` | ✅ Đã bổ sung endpoint chi tiết bài viết, nạp bài viết và replies. |
| `GET /messages` | `templates/messaging/index.html` | `MessagingPageController.java` | ✅ Đã tạo Controller, nạp danh sách hội thoại của user. |
| `GET /notifications` | `templates/notification/index.html` | `NotificationPageController.java` | ✅ Đã tạo Controller, nạp danh sách thông báo của user. |
| `GET /communities` | `templates/community/index.html` | `CommunityPageController.java` | ✅ Đã tạo Controller, nạp danh sách cộng đồng và chi tiết cộng đồng. |

### 1.3. Đóng gói DevOps & Reverse Proxy
- **`Dockerfile` Multi-Stage Build**:
  - Stage 1 (Build): `maven:3.9-eclipse-temurin-17` tải dependencies và đóng gói `app.jar` bằng `mvn clean package -DskipTests`.
  - Stage 2 (Runtime): `eclipse-temurin:17-jre` nhẹ, an toàn, chạy với user không đặc quyền `aloute`.
- **`docker-compose.yml`**:
  - Service `postgres`: PostgreSQL 16 Alpine, cấu hình volume bền vững `postgres_data`, tự động nạp toàn bộ stored procedures qua `init-db.sh`, kiểm tra sức khỏe `pg_isready`.
  - Service `app`: Khởi động container Spring Boot AloUTE khi `postgres` đã healthy, profile `prod`.
  - Service `nginx`: Nginx Alpine, cấu hình reverse proxy cổng 80/443, chuyển tiếp Header chuẩn (`Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`), hỗ trợ WebSocket STOMP Upgrade tại `/ws`.
- **`.dockerignore`**: Đã tạo để loại bỏ các thư mục rác, cache, build artifacts khỏi build context.
- **Biên dịch & Kiểm thử**: Mã nguồn biên dịch thành công 100% với Maven Java 21, toàn bộ 25 bài kiểm thử đơn vị và tích hợp chạy thành công không có lỗi.
