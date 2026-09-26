# ALOUTE ARCHITECTURE & SCAFFOLD TEMPLATE

> **Tài liệu hướng dẫn khung kiến trúc cho toàn team**
> Khi pull repo về, các thành viên tuân theo cấu trúc phân tầng (Package-by-Layer / MVC bên ngoài) bên dưới để code Backend (BE) và Frontend (FE).

---

## 1. TỔNG QUAN KIẾN TRÚC THƯ MỤC

```text
src/
├── main/
│   ├── java/com/aloute/          <--- BACKEND (BE - MVC BÊN NGOÀI)
│   │   ├── AlouteApplication.java # File khởi chạy Spring Boot
│   │   ├── config/               # Cấu hình Spring (Security, WebMvc, Database,...)
│   │   ├── controller/           # Tầng Controller tiếp nhận request
│   │   │   ├── api/              # RestController trả dữ liệu JSON (/api/...)
│   │   │   └── page/             # Controller trả Thymeleaf HTML view
│   │   ├── service/              # Tầng nghiệp vụ (Business Logic & Transactions)
│   │   │   └── impl/             # Service Implementations
│   │   ├── repository/           # Tầng truy xuất dữ liệu (Spring Data JPA)
│   │   ├── entity/               # Tầng JPA Entities / Data Model
│   │   ├── dto/                  # Data Transfer Objects
│   │   │   ├── request/          # DTO nhận từ client (@Valid)
│   │   │   └── response/         # DTO trả về cho client / ViewModel
│   │   ├── security/             # Role, UserPrincipal, Filters, JWT
│   │   └── common/               # Các thành phần dùng chung
│   │       ├── api/              # ApiResponse envelope, ErrorCode
│   │       ├── exception/        # Custom exceptions, GlobalExceptionHandler
│   │       └── util/             # ModelMapperUtil (map Entity <-> DTO trực tiếp trong Service)
│   │
│   └── resources/                <--- FRONTEND (FE) & CONFIG
│       ├── templates/            # Thymeleaf HTML Templates
│       │   ├── layouts/          # Layout master (default.html)
│       │   ├── fragments/        # Thành phần tái sử dụng (head, navbar, sidebar, pagination, flash)
│       │   ├── auth/             # Màn hình đăng nhập, đăng ký
│       │   ├── user/             # Trang cá nhân, profile
│       │   ├── post/             # Feed, bài viết chi tiết
│       │   ├── community/        # Màn hình nhóm/cộng đồng
│       │   ├── messaging/        # Chat & hộp thư
│       │   └── error/            # 404.html, 500.html
│       │
│       ├── static/               # Static assets
│       │   ├── css/              # Stylesheet (tokens.css, app.css)
│       │   ├── js/               # Javascript (core, app.js)
│       │   └── images/           # Ảnh tĩnh
│       │
│       ├── application.yml       # Cấu hình chung
│       └── application-dev.yml   # Cấu hình môi trường dev (H2 Database)
```

---

## 2. NGUYÊN TẮC LUỒNG DỮ LIỆU BACKEND

```text
HTTP Request
     ↓
Controller (api / page)
     ↓
Service (Interface & Impl)
     ↓
Repository (Spring Data JPA)
     ↓
Database
```

- **Controller**: Chỉ nhận request, validate DTO, lấy User qua Principal và gọi Service. Tuyệt đối không gọi Repository trực tiếp.
- **Service**: Nơi xử lý toàn bộ logic nghiệp vụ, tính toán quyền hạn, quản lý `@Transactional`, và **dùng `ModelMapperUtil.map(...)` để chuyển đổi Entity <-> DTO trực tiếp**.
- **Repository**: Chỉ thực hiện các thao tác CRUD và query database.
- **Entity**: Khai báo mapping JPA, không trả trực tiếp ra API Controller (luôn map qua DTO).

---

## 3. NGUYÊN TẮC FRONTEND (THYMELEAF)

- Layout chính nằm tại `templates/layouts/`.
- Các khối UI dùng chung (navbar, sidebar, pagination) nằm tại `templates/fragments/`.
- File tĩnh (CSS, JS, Images) đặt tại thư mục `static/`.
