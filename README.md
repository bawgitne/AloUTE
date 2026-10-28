# AloUTE — Nền tảng Mạng Xã Hội Học Đường (HCMUTE)

**AloUTE** là nền tảng mạng xã hội học đường toàn diện được thiết kế và phát triển chuyên biệt cho cộng đồng sinh viên, giảng viên và các câu lạc bộ tại **Trường Đại học Sư phạm Kỹ thuật TP.HCM (HCMUTE)**.

Dự án cung cấp một không gian số hiện đại, an toàn và trực quan giúp người dùng dễ dàng kết nối, chia sẻ thông tin học tập, tham gia thảo luận theo chủ đề, quản lý cộng đồng, nhắn tin thời gian thực và tương tác đa phương tiện.

Hệ thống được phát triển theo mô hình lai (Hybrid):
- **Web Platform**: Server-Side Rendering (SSR) tối ưu hiệu năng và SEO với **Thymeleaf + Bootstrap 5 + Vanilla JavaScript**.
- **RESTful API**: Chuẩn hóa toàn bộ endpoint cho các ứng dụng ngoại vi.
- **Mobile Client**: Ứng dụng di động đa nền tảng viết bằng **Flutter** trong thư mục [`aloute_mobile/`](aloute_mobile/).

---

## 1. Giới thiệu tổng quan

AloUTE giải quyết nhu cầu trao đổi thông tin, tương tác học thuật và hoạt động phong trào trong môi trường đại học. Hệ thống hướng tới việc thay thế các nhóm phân tán trên mạng xã hội thương mại bằng một nền tảng chuyên biệt, bảo mật thông tin nội bộ và tích hợp sâu với quy trình quản lý học sinh - sinh viên.

Các mục tiêu kỹ thuật chính:
- Kiến trúc phân lớp chuẩn hóa, dễ mở rộng và bảo trì.
- Phân quyền chặt chẽ dựa trên vai trò (Role-Based Access Control).
- Bảo vệ dữ liệu người dùng với các tiêu chuẩn an ninh hiện đại (2FA, HttpOnly Cookie, bảo vệ tài nguyên tĩnh).
- Hỗ trợ thời gian thực cho tin nhắn và thông báo.

---

## 2. Phân hệ chức năng chính

| Phân hệ | Chi tiết tính năng |
| :--- | :--- |
| **Xác thực & Bảo mật** | • Đăng ký tài khoản với mã xác thực OTP qua Email (SMTP).<br>• Đăng nhập an toàn lưu JWT trong `HttpOnly Cookie`.<br>• Đăng xuất vô hiệu hóa phiên, Quên & Đặt lại mật khẩu.<br>• Xác thực hai lớp (**2FA / TOTP**) qua Google Authenticator kèm mã khôi phục dự phòng.<br>• Phân quyền người dùng chặt chẽ theo vai trò (Role-Based Access Control - RBAC). |
| **Bảng tin & Nội dung** | • Bảng tin cá nhân hóa (Personal Feed) & Bảng tin khám phá.<br>• Cuộn vô tận (Infinite Scroll) mượt mà tối ưu trải nghiệm.<br>• Soạn thảo bài viết đa phương tiện (Văn bản, tải ảnh/video tích hợp Firebase Storage).<br>• Phân loại theo chủ đề / Hashtag (`#topic`).<br>• Tạo cuộc bình chọn thời gian thực (**Polls**).<br>• Trích dẫn bài viết (**Quote**), Đăng lại (**Repost**), Ghim bài viết (**Pin**), Lưu trữ (**Bookmark**), Ẩn bài viết / Bỏ theo dõi tác giả (**Mute**). |
| **Tương tác & Xã hội** | • Thả cảm xúc / Thích bài viết (Like).<br>• Bình luận & Phản hồi phân cấp (Nested Comments & Replies).<br>• Theo dõi / Bỏ theo dõi người dùng (Follow / Unfollow).<br>• Kết nối bạn bè, trang cá nhân tùy biến (Avatar, Ảnh bìa, Tiểu sử).<br>• Chặn người dùng vi phạm (Block). |
| **Quản lý Cộng đồng** | • Tạo và quản trị cộng đồng / nhóm học tập theo khoa, lớp, CLB.<br>• Chế độ công khai (Public) hoặc riêng tư (Private).<br>• Quản trị viên cộng đồng duyệt thành viên và kiểm duyệt bài viết.<br>• Phân vai trò thành viên: Admin cộng đồng, Kiểm duyệt viên (Moderator), Thành viên. |
| **Thời gian thực & Thông báo** | • Nhắn tin trực tiếp 1-1 (Direct Messaging) tức thì qua giao thức **STOMP over WebSocket**.<br>• Thông báo trong ứng dụng (In-app notifications) khi có tương tác mới.<br>• Tích hợp Firebase Cloud Messaging (**FCM**) cho thông báo đẩy trên thiết bị di động. |
| **Quản trị hệ thống (Admin)** | • Bảng điều khiển quản trị chuyên sâu (**Admin Dashboard**).<br>• Quản lý người dùng: Cảnh báo vi phạm, Tạm khóa / Đình chỉ tài khoản (Suspension), Cấp quyền vai trò.<br>• Kiểm duyệt nội dung: Tiếp nhận và xử lý báo cáo vi phạm (Reports / Moderation).<br>• Giám sát số liệu và thống kê hoạt động hệ thống. |

---

## 3. Công nghệ sử dụng (Technology Stack)

```
┌──────────────────────────────────────────────────────────────┐
│                        AloUTE Clients                        │
│        Web (Thymeleaf/Bootstrap/JS)   │   Mobile (Flutter)   │
└───────────────────────────────┬──────────────────────────────┘
                                │ HTTP/REST + WebSocket STOMP
┌───────────────────────────────▼──────────────────────────────┐
│                     Spring Boot 3 Backend                    │
│   Controller Layer   →   Service Layer   →  Repository Layer │
└──────────────┬──────────────────┬─────────────────┬──────────┘
               │                  │                 │
     ┌─────────▼────────┐  ┌──────▼──────┐  ┌───────▼───────┐
     │ MySQL 8 / H2 DB  │  │  Firebase   │  │   SMTP Mail   │
     │  (JPA/Hibernate) │  │   Storage   │  │ (OTP / Alert) │
     └──────────────────┘  └─────────────┘  └───────────────┘
```

- **Backend Framework**: Spring Boot 3.2.5 (Spring MVC, Spring Security 6, Spring Data JPA, Spring WebSocket)
- **Ngôn ngữ**: Java 17 (tương thích hoàn toàn trên JDK 21)
- **Cơ sở dữ liệu**:
  - **Môi trường Production**: MySQL 8.x
  - **Môi trường Local/Test**: H2 In-Memory Database (khởi chạy ngay không cần cài đặt DB ngoài)
- **Bảo mật & Mã hóa**:
  - Spring Security Filter Chain
  - JSON Web Token (JJWT 0.12.5) với cơ chế HttpOnly Cookie
  - TOTP 2FA (Brotatoolkit / Google Authenticator) & Mã hóa AES-GCM
- **Giao diện Web**: Thymeleaf Engine, Bootstrap 5, Bootstrap Icons, Vanilla JavaScript (ES6+), CSS Tokens & Glassmorphism
- **Real-time Engine**: Spring Messaging + STOMP over WebSocket
- **Dịch vụ tích hợp**:
  - Firebase Admin SDK (Cloud Storage lưu trữ media, Firebase Cloud Messaging - FCM)
  - JavaMailSender (Gửi OTP và email kích hoạt tài khoản)
- **Ứng dụng di động**: Flutter SDK (Dart)
- **Kiểm thử & Build**: Maven 3.9+, JUnit 5, MockMvc, Node.js Test Runner (Frontend static test)
- **Đóng gói & Triển khai**: Docker, Docker Compose, Nginx Reverse Proxy

---

## 4. Kiến trúc hệ thống & Quy chuẩn mã nguồn

Dự án tuân thủ nghiêm ngặt mô hình **Kiến trúc phân lớp chuẩn Enterprise** (Layered Architecture):

```
Controller (HTTP / REST / SSR)
    │
    ▼
Service Layer (Nghiệp vụ cốt lõi, Quản lý Transaction @Transactional)
    │
    ▼
Repository Layer (Spring Data JPA)
    │
    ▼
Database (MySQL 8 / H2)
```

### Các nguyên tắc bắt buộc:
1. **Phân tách trách nhiệm (Separation of Concerns)**:
   - `Controller` chỉ xử lý tiếp nhận request, validate DTO đầu vào và chuyển tiếp tới Service.
   - `Service` chứa toàn bộ business logic và ranh giới giao dịch (`@Transactional`); Controller tuyệt đối không truy cập trực tiếp `Repository`.
2. **Bảo mật theo ngữ cảnh người dùng (Principal-Driven Security)**:
   - `userId` và `authorId` **luôn luôn** được trích xuất từ đối tượng `UserPrincipal` đã được xác thực an toàn trong `SecurityContextHolder`.
   - **Tuyệt đối không** chấp nhận `userId` truyền tùy tiện từ phía client trong request body.
3. **Chuẩn hóa phản hồi API (API Response Envelope)**:
   - Mọi endpoint REST API đều đóng gói theo envelope thống nhất:
     ```json
     {
       "code": 200,
       "message": "Thành công",
       "data": { ... }
     }
     ```
   - Xử lý lỗi tập trung thông qua `GlobalExceptionHandler` kết hợp cùng danh mục `ErrorCode` và `ApiException`.
4. **Phân trang chuẩn hóa (Pagination)**:
   - Toàn bộ endpoint phân trang đều sử dụng định dạng 0-indexed (`?page=0&size=10`), kiểm soát giới hạn tối đa `max-page-size: 100` để chống tấn công cạn kiệt tài nguyên (DoS).

---

## 5. Cấu trúc thư mục dự án

```
aloute/
├── .github/workflows/          # Kịch bản CI/CD tự động (GitHub Actions)
├── aloute_mobile/              # Mã nguồn ứng dụng di động Flutter
├── deploy/                     # Cấu hình triển khai hệ thống (Nginx, Docker)
├── docs/                       # Tài liệu thiết kế, báo cáo kiểm thử & sơ đồ kỹ thuật
├── scripts/                    # Scripts tự động hóa (PowerShell run-local, test)
├── src/
│   ├── main/
│   │   ├── java/com/aloute/
│   │   │   ├── AlouteApplication.java # Điểm khởi chạy Spring Boot chính
│   │   │   ├── common/         # ApiResponse, ErrorCode, Exception handling, Utils
│   │   │   ├── config/         # Cấu hình Security, WebSocket, MVC, Test Data Seeder
│   │   │   ├── controller/     # Tầng tiếp nhận (REST API & Thymeleaf SSR Page Controllers)
│   │   │   ├── dto/            # Data Transfer Objects (Request / Response DTOs)
│   │   │   ├── entity/         # Các thực thể JPA (User, Post, Comment, Community,...)
│   │   │   ├── repository/     # Spring Data JPA Repositories
│   │   │   ├── security/       # JWT Provider, Custom UserDetailsService, Crypto 2FA
│   │   │   └── service/        # Interface & Implementation nghiệp vụ hệ thống
│   │   └── resources/
│   │       ├── db/migration/   # Scripts DDL khởi tạo và nâng cấp database
│   │       ├── static/         # Static assets (css, js, images)
│   │       ├── templates/      # Giao diện Thymeleaf SSR theo từng module
│   │       ├── application.yml # Cấu hình chung cho toàn hệ thống
│   │       ├── application-dev.yml  # Cấu hình môi trường dev (H2 Database)
│   │       └── application-prod.yml # Cấu hình môi trường production (MySQL 8)
│   └── test/java/com/aloute/   # Bộ kiểm thử Unit & Integration Tests (JUnit 5, MockMvc)
├── test/frontend/              # Bộ kiểm thử tĩnh cho giao diện Frontend
├── docker-compose.yml          # Cấu hình chạy toàn bộ hệ thống bằng Docker Compose
├── Dockerfile                  # Containerization image cho Spring Boot app
└── pom.xml                     # Quản lý thư viện và cấu hình build Maven
```

---

## 6. Hướng dẫn cài đặt & Chạy cục bộ (Local Development)

### 6.1. Yêu cầu môi trường
- **Java Development Kit (JDK)**: Phiên bản 17 hoặc 21 trở lên.
- **Apache Maven**: Phiên bản 3.9+ (hoặc sử dụng `mvnw`).
- **Node.js**: Phiên bản 20+ (tùy chọn, dùng khi chạy test tĩnh frontend).
- **Cơ sở dữ liệu**: Không bắt buộc cài đặt trước — cấu hình `dev` mặc định sử dụng **H2 In-Memory Database** tự động tạo bảng và nạp dữ liệu mẫu.

### 6.2. Thiết lập cấu hình môi trường (.env)
Tạo file `.env` tại thư mục gốc của repository (nếu chưa có) để cấu hình gửi mail và OTP:

```env
# Cấu hình gửi mail OTP xác thực (Ví dụ dùng Gmail App Password)
ALOUTE_MAIL_HOST=smtp.gmail.com
ALOUTE_MAIL_PORT=587
ALOUTE_MAIL_USERNAME=your-email@gmail.com
ALOUTE_MAIL_PASSWORD=your-app-password
ALOUTE_MAIL_FROM=your-email@gmail.com

# Cấu hình JWT cho môi trường Dev (đã có giá trị mặc định sẵn)
ALOUTE_JWT_SECRET=YWxvdXRlLWRldi1qd3Qtc2VjcmV0LWtleS0zMi1ieXRlcyEh
ALOUTE_JWT_EXPIRATION_SECONDS=3600
```

### 6.3. Khởi chạy ứng dụng

#### Cách 1: Chạy nhanh bằng PowerShell Script (Khuyến nghị trên Windows)
Script đi kèm đã được tối ưu: tự động nạp biến môi trường từ `.env`, tự động phát hiện JDK phù hợp và giải phóng cổng bị xung đột:

```powershell
./scripts/run-local.ps1
```

#### Cách 2: Chạy trực tiếp qua lệnh Maven CLI
```bash
# Nạp profile dev để kích hoạt H2 database và dữ liệu mẫu
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 6.4. Truy cập ứng dụng & Bảng điều khiển
- **Trang chủ mạng xã hội**: [`http://localhost:8080`](http://localhost:8080)
- **H2 Database Console**: [`http://localhost:8080/h2-console`](http://localhost:8080/h2-console)
  - *JDBC URL*: `jdbc:h2:mem:aloutedb`
  - *User Name*: `sa`
  - *Password*: *(để trống)*

### 6.5. Tài khoản thử nghiệm có sẵn (Seed Data)
Môi trường `dev` tự động nạp sẵn các tài khoản để kiểm thử ngay mà không cần qua bước xác thực email:

| Vai trò | Email đăng nhập | Mật khẩu | Chức năng truy cập |
| :--- | :--- | :--- | :--- |
| **System Admin** | `admin@aloute.local` | `Admin@123` | Bảng điều khiển Quản trị viên (`/admin`), Quản lý tài khoản, Kiểm duyệt bài viết |
| **Standard User** | `user@aloute.local` | `User@123` | Đăng bài, bình luận, tương tác, tham gia cộng đồng, chat trực tiếp |
| **Demo Student** | `demoan@aloute.test` | `Test@12345` | Tài khoản sinh viên thử nghiệm |

---

## 7. Kiểm thử & Đảm bảo chất lượng (Testing & QA)

Dự án sở hữu bộ kiểm thử tự động toàn diện từ tầng dữ liệu, nghiệp vụ cho đến giao diện:

```bash
# 1. Chạy toàn bộ Unit Test và Integration Test Backend (chạy trên H2 In-Memory)
mvn test

# 2. Chạy bộ kiểm thử tĩnh cho tài nguyên Frontend (Thymeleaf/JS/CSS)
node --test test/frontend/*.test.mjs

# 3. Đóng gói file JAR hoàn chỉnh (bỏ qua bước test)
mvn clean package -DskipTests

# 4. Kiểm tra định dạng code và ký tự xung đột Git
git diff --check
```

> **Quy trình CI/CD**: Mỗi Pull Request gửi lên repository được kiểm tra tự động qua GitHub Actions ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) bao gồm tuần tự: Kiểm tra cú pháp Frontend → Thực thi toàn bộ Backend Test Suite → Build gói JAR.

---

## 8. Đóng gói & Triển khai Docker

Hệ thống hỗ trợ đóng gói container hóa sẵn sàng cho việc đưa lên máy chủ hoặc VPS:

```bash
# Xây dựng và khởi chạy toàn bộ dịch vụ (Backend + Database + Nginx)
docker-compose up -d --build

# Xem logs hoạt động của hệ thống
docker-compose logs -f aloute-app

# Dừng hệ thống
docker-compose down
```

> **Lưu ý triển khai Production**:
> - Luôn kích hoạt profile `prod` (`-Dspring.profiles.active=prod`).
> - Cấu hình hệ thống phòng thủ `ProductionProfileGuard`: Hệ thống sẽ chủ động từ chối khởi động nếu phát hiện cấu hình lẫn lộn giữa tài khoản dev seed và môi trường production thật.
> - Tham khảo thêm quy trình phát hành tại [`docs/release/MANUAL_DEPLOYMENT_RUNBOOK.md`](docs/release/MANUAL_DEPLOYMENT_RUNBOOK.md).

---

## 9. Đội ngũ phát triển (Project Contributors)

Dự án được xây dựng và hoàn thiện bởi các thành viên:

| Thành viên | Tài khoản GitHub | Vai trò & Phạm vi đảm nhiệm chính |
| :--- | :--- | :--- |
| **Bằng Ngô** | [@bawgitne](https://github.com/bawgitne) | **Team Leader / System Architect / Security & DevOps**<br>• Thiết kế kiến trúc tổng thể, cơ sở dữ liệu & Spring Security.<br>• Xử lý xác thực 2FA, OTP Email, tích hợp Docker & Nginx.<br>• Quản lý quy trình Git, CI/CD và triển khai hệ thống. |
| **Phan Minh Quân** | [@pminhquan](https://github.com/pminhquan) | **Frontend Lead / UI-UX Designer / Fullstack E2E**<br>• Xây dựng hệ thống giao diện Thymeleaf SSR & Design System.<br>• Tối ưu trải nghiệm Responsive (Mobile / Tablet / Desktop) & Accessibility.<br>• Tích hợp tương tác JavaScript, infinite scroll & kiểm thử giao diện. |
| **Khởi Nguyên** | [@khoinguyenvlm123-bit](https://github.com/khoinguyenvlm123-bit) | **Fullstack Developer / Real-time & Mobile Integration**<br>• Triển khai các tính năng cốt lõi (Bảng tin, Bài viết, Bình luận, Cộng đồng).<br>• Phát triển hệ thống nhắn tin & thông báo thời gian thực (WebSocket STOMP).<br>• Tích hợp ứng dụng di động Flutter Client và bộ dữ liệu kiểm thử. |

---

## 10. Tài liệu tham khảo kỹ thuật

- [`ARCHITECTURE_TEMPLATE.md`](ARCHITECTURE_TEMPLATE.md): Mẫu cấu trúc và nguyên tắc thiết kế mã nguồn.
- [`UI_DESIGN_SYSTEM.md`](UI_DESIGN_SYSTEM.md): Hướng dẫn quy chuẩn màu sắc, typography và thành phần giao diện.
- [`SCREEN_FLOW.md`](SCREEN_FLOW.md): Sơ đồ luồng di chuyển và trạng thái giữa các màn hình ứng dụng.
- [`CONTRIBUTING.md`](CONTRIBUTING.md): Quy ước đặt tên nhánh (branch), commit message và tạo Pull Request.
- [`docs/README.md`](docs/README.md): Bản đồ tài liệu kỹ thuật, use case matrix và báo cáo kiểm định hệ thống.

---

## 11. Giấy phép & Bản quyền

Dự án phục vụ mục đích học tập và nghiên cứu trong khuôn khổ học phần Đồ án Công nghệ Phần mềm — **Trường Đại học Sư phạm Kỹ thuật TP.HCM (HCMUTE)**.

*Bản quyền © 2026 Nhóm phát triển AloUTE. Mọi quyền được bảo lưu.*
