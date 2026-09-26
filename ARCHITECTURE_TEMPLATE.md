# ALOUTE ARCHITECTURE & SCAFFOLD TEMPLATE

> **Tài liệu hướng dẫn khung kiến trúc cho toàn team**
> Khi pull repo về, các thành viên tuân theo cấu trúc đã phân chia bên dưới để code Backend (BE) và Frontend (FE).

---

## 1. TỔNG QUAN PHÂN CHIA BE & FE

Dự án sử dụng **Modular Monolith với Spring Boot + Thymeleaf + Bootstrap**:

```text
src/
├── main/
│   ├── java/com/aloute/          <--- BACKEND (BE)
│   │   ├── config/               # Cấu hình WebMvc, Security, v.v.
│   │   ├── common/               # Lớp dùng chung: ApiResponse, ErrorCode, BaseEntity, Exception
│   │   ├── security/             # Role, UserPrincipal, CurrentUser
│   │   └── feature/              # Các module nghiệp vụ (Package-by-Feature)
│   │       ├── auth/
│   │       ├── user/
│   │       ├── post/
│   │       ├── community/
│   │       ├── social/
│   │       ├── messaging/
│   │       └── notification/
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
│       │   ├── css/              # tokens.css, app.css
│       │   ├── js/               # http-client.js, app.js
│       │   └── images/
│       │
│       ├── application.yml
│       └── application-dev.yml
```

---

## 2. DÀNH CHO THÀNH VIÊN CODE BACKEND (BE)

Khi code một module trong `feature/<module>/` (ví dụ `feature/post/`), hãy tuân thủ cấu trúc package chuẩn:

```text
feature/<module>/
├── controller/
│   ├── api/            # @RestController trả ApiResponse<T> (/api/...)
│   └── page/           # @Controller trả Thymeleaf view (Model/ViewModel)
├── dto/
│   ├── request/        # DTO nhận từ client (@Valid)
│   └── response/       # DTO trả ra API
├── entity/             # JPA Entity kế thừa BaseEntity
├── repository/         # Spring Data JPA Repository
├── service/            # Service interface & ServiceImpl (@Transactional)
└── mapper/             # MapStruct hoặc Mapper class
```

**Nguyên tắc bắt buộc:**
1. **Flow**: `Controller -> Service -> Repository`. Controller tuyệt đối không gọi Repository trực tiếp.
2. **API Response**: Luôn bọc qua `ApiResponse.success(...)` hoặc `ApiResponse.paged(...)` từ package `com.aloute.common.api`.
3. **Quyền hạn**: Lấy ID người dùng qua `@CurrentUser UserPrincipal user`, tuyệt đối không nhận `authorId` từ body client.
4. Đọc [skills/backend_rule.md](skills/backend_rule.md) và [skills/domain_system.md](skills/domain_system.md) trước khi code.

---

## 3. DÀNH CHO THÀNH VIÊN CODE FRONTEND (FE)

Toàn bộ template và static asset nằm trong `src/main/resources/`:

1. **Tạo trang mới**: Kế thừa layout chung `layouts/default.html`:
   ```html
   <!DOCTYPE html>
   <html xmlns:th="http://www.thymeleaf.org"
         th:replace="~{layouts/default :: layout(~{::content}, 'Tiêu đề trang')}">
   <div th:fragment="content">
       <!-- Code giao diện màn hình tại đây -->
   </div>
   </html>
   ```
2. **Gọi API bằng JavaScript**: Sử dụng thư viện có sẵn `HttpClient` (`/js/core/http-client.js`):
   ```javascript
   HttpClient.get('/api/posts')
       .then(res => console.log(res.data))
       .catch(err => alert(err.message));
   ```
3. **Styling**: Sử dụng class của Bootstrap 5 kết hợp design tokens trong `tokens.css` và `app.css`.
4. Đọc [skills/frontend_rule.md](skills/frontend_rule.md) trước khi code.
