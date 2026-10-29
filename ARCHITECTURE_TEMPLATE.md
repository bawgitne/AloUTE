# ALOUTE — Kiến trúc & Cấu trúc mã nguồn

> Tài liệu mô tả kiến trúc hiện hành của AloUTE: Spring Boot 3 (MVC + REST), Thymeleaf SSR, JPA và Flutter client.
> Hợp đồng API canonical: [`docs/api/openapi.yaml`](docs/api/openapi.yaml). Quy ước đóng góp: [`CONTRIBUTING.md`](CONTRIBUTING.md).

---

## 1. Tổng quan kiến trúc

AloUTE dùng kiến trúc phân lớp:

```text
HTTP / WebSocket Request
        ↓
Controller (REST API, SSR Page, STOMP)
        ↓
Service (nghiệp vụ, @Transactional, phân quyền)
        ↓
Repository (Spring Data JPA)
        ↓
MySQL 8 (production) / H2 (local & test)
```

- **Controller** chỉ xử lý HTTP, validate DTO và gọi Service. Controller không truy cập Repository.
- **Service** chứa business logic, kiểm tra quyền và ranh giới giao dịch; là nơi mapping Entity ↔ DTO.
- **Repository** chỉ thực hiện truy vấn dữ liệu.
- **Entity** ánh xạ JPA, không trả trực tiếp ra API; response luôn đi qua DTO.
- `userId`/`authorId` lấy từ `UserPrincipal` trong `SecurityContextHolder`, không nhận từ request body.

---

## 2. Cấu trúc thư mục

```text
src/
├── main/
│   ├── java/com/aloute/
│   │   ├── AlouteApplication.java   # Entry point Spring Boot
│   │   ├── common/                  # ApiResponse, ErrorCode, exception, util, web
│   │   ├── config/                  # Security, WebSocket, MVC, seed & profile guards
│   │   ├── controller/
│   │   │   ├── api/                 # REST API (/api/**)
│   │   │   ├── page/                # Thymeleaf SSR pages
│   │   │   ├── ws/                  # STOMP/WebSocket endpoints
│   │   │   └── advice/              # Global exception handling
│   │   ├── dto/request|response/    # Request/response DTO
│   │   ├── entity/                  # JPA entities
│   │   ├── event/ | listener/       # Domain events & listeners
│   │   ├── repository/              # Spring Data JPA repositories
│   │   ├── security/                # JWT, authorization, current-user, crypto, handlers
│   │   └── service/                 # Service interfaces + impl, admin, email
│   └── resources/
│       ├── db/migration/            # DDL H2/MySQL 8 (thực thi thủ công)
│       ├── static/                  # css, js, images, websocket-demo
│       ├── templates/               # Thymeleaf layouts/fragments/pages
│       └── application*.yml         # Cấu hình chung, dev, test, prod
└── test/java/com/aloute/            # Unit & integration tests (JUnit 5, MockMvc)
```

Các thành phần ngoài `src/`:

| Thư mục | Vai trò |
| :--- | :--- |
| `test/frontend/` | Test tĩnh Node cho template/CSS/JS |
| `test/e2e/quan-acceptance/` | Browser E2E (Playwright) |
| `aloute_mobile/` | Flutter client |
| `deploy/` | Nginx, Docker và cấu hình triển khai |
| `scripts/` | Script chạy local/test |
| `docs/` | Kiến trúc, thiết kế, kiểm thử và bằng chứng |

---

## 3. Luồng xử lý chính

### 3.1. REST API

1. Client gửi request kèm cookie JWT `HttpOnly`.
2. Security filter xác thực token và dựng `UserPrincipal`.
3. Controller validate DTO, lấy principal và gọi Service.
4. Service xử lý nghiệp vụ, ghi dữ liệu trong transaction và trả DTO.
5. `ApiResponse<T>` bọc kết quả; lỗi đi qua `GlobalExceptionHandler` với `ErrorCode` tương ứng.

### 3.2. SSR Thymeleaf

1. Page Controller lấy dữ liệu qua Service và đưa vào model.
2. Template dùng `layouts/` và `fragments/` dùng chung.
3. Tương tác ghi dữ liệu gọi REST API qua `static/js/core/http-client.js` (cookie + CSRF header).

### 3.3. Real-time

- STOMP over WebSocket cho tin nhắn và thông báo.
- Kênh `/app/**` xử lý message; client subscribe `/user/queue/**`.
- Quyền truy cập được kiểm tra ở interceptor và service, không chỉ ở UI.

---

## 4. Quy ước triển khai

- Envelope REST: `{"success": true, "data": ..., "meta": ...}`; lỗi: `{"success": false, "error": {"code", "message", "fieldErrors"}}`.
- Phân trang 0-indexed, giới hạn `max-page-size`.
- DDL trong `db/migration/` chạy thủ công; test tự động dùng H2.
- Bí mật (JWT, khóa 2FA, SMTP, Firebase) chỉ lấy từ biến môi trường; không commit.
- Production dùng profile `prod`; `ProductionProfileGuard` chặn cấu hình dev-seed trên môi trường production.
