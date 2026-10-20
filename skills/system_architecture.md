# ALOUTE — SYSTEM ARCHITECTURE & CROSS-CUTTING CONTRACTS

> **Vai trò của file này**
>
> Đây là tài liệu **kiến trúc kỹ thuật tổng thể** và là **source of truth duy nhất** cho các contract kỹ thuật dùng chung giữa nhiều layer/module.
>
> File này **không mô tả chi tiết nghiệp vụ từng feature**, **không định nghĩa chi tiết Spring Security**, và **không quy định chi tiết cách viết UI**.

---

# 1. DOCUMENT OWNERSHIP — QUY TẮC KHÔNG TRÙNG LOGIC

ALOUTE có 4 tài liệu chính.

| File | Sở hữu duy nhất |
|---|---|
| `ALOUTE_SYSTEM_ARCHITECTURE.md` | Tech stack, system boundary, folder/dependency, API contract, response body, pagination, HTTP status, error code, request/header convention, persistence boundary, WebSocket/Firebase/Mongo boundary |
| `ALOUTE_DOMAIN_SYSTEM.md` | Feature, actor, entity, relationship, business flow, business invariant, visibility, ownership requirement, domain permission: **WHO CAN DO WHAT** |
| `SPRING_SECURITY_CONVENTION.md` | Authentication/authorization implementation: JWT, Spring Security, Principal, global Role/Authority, SecurityContext, policy/check pattern: **HOW SECURITY IS IMPLEMENTED** |
| `ALOUTE_FRONTEND_CONVENTION.md` | Thymeleaf, Bootstrap, browser JavaScript, template/static structure, UI state, DOM/AJAX/WebSocket client behavior |

## 1.1. Rule bắt buộc

Một rule chỉ có **một owner**.

Ví dụ:

```text
API response shape
→ chỉ nằm trong ALOUTE_SYSTEM_ARCHITECTURE.md

Pagination request/response
→ chỉ nằm trong ALOUTE_SYSTEM_ARCHITECTURE.md

HTTP status
→ chỉ nằm trong ALOUTE_SYSTEM_ARCHITECTURE.md

JWT
→ chỉ nằm trong SPRING_SECURITY_CONVENTION.md

Community Moderator được xóa nội dung nào
→ chỉ nằm trong ALOUTE_DOMAIN_SYSTEM.md

Cách render pagination bằng Bootstrap
→ chỉ nằm trong ALOUTE_FRONTEND_CONVENTION.md
```

Các file khác chỉ được viết:

```text
Follow contract defined in <canonical file>.
```

Không copy lại bảng, enum, response body hoặc rule.

---

# 2. TECH STACK CHÍNH

```text
Application & Backend (Monolith)
├── Java 21 / Spring Boot 3.2+
├── Spring MVC (API Controller & Page Controller)
├── Spring Security (JWT HttpOnly Cookie + Bearer Authorization fallback)
├── Spring Data JPA + Hibernate
└── Realtime: Spring WebSocket (STOMP Protocol over /ws)

Frontend Systems
├── Giao diện chính nghiệm thu đồ án (SSR): Thymeleaf + Bootstrap 5 + Vanilla JavaScript enhancement
│   └── Nằm tại src/main/resources/templates/ & static/ (Phạm Minh Quân phụ trách)
└── Client SPA mở rộng (Advanced Demo): Vue 3 SPA (Vite + Pinia + Vue Router)
    └── Nằm tại fe-vue/ tiêu thụ chung REST API (/api/*)

Persistence & Database Processing
├── Relational Database: MySQL 8.0 (Production: `application-prod.yml` + compose) / H2 in-memory (Local Dev & Test)
├── PostgreSQL: chỉ ở mức SQL script (`db/schema.sql`, `db/triggers.sql`, `db/procedures.sql`); chưa có profile runtime
├── JPA Entities & Repository Layer
└── Database Processing (Nguyễn Duy Ngọc phụ trách):
    ├── Database Triggers (Audit updated_at, Counter sync: likes, replies, followers, members)
    ├── Stored Procedures (Tìm kiếm nâng cao, Soft-delete cascade, Báo cáo kiểm duyệt)
    └── B-Tree Indexes & Constraints (Chống duplicate, tối ưu query phân trang)

External Infrastructure & Media Storage
├── Firebase Cloud Storage integration (Lưu trữ nhị phân media ảnh, video)
└── Free-Tier Quota Shield:
    ├── Giới hạn upload: Ảnh tối đa 5 MB (WebP tối ưu), Video tối đa 30 MB
    └── Hạn mức người dùng: Tối đa 150 MB/tài khoản; Trần an toàn hệ thống 8.5 GB

Deployment & DevOps (Khôi & Ngọc phụ trách)
├── Dockerfile (Multi-stage build cho Spring Boot app)
├── Docker Compose (Spring Boot + MySQL 8 + Nginx)
├── Nginx Reverse Proxy (SSL Let's Encrypt, HTTP/2, WebSocket Upgrade /ws)
└── Linux VPS (Ubuntu Server) + CI/CD GitHub Actions
```

---

# 3. KIẾN TRÚC TỔNG THỂ

ALOUTE triển khai theo **modular monolith**.

```text
Browser
   │
   ├── HTTP/HTTPS
   │     │
   │     ▼
   │  Spring MVC
   │     ├── Page Controller ──► Thymeleaf ──► HTML
   │     └── API Controller  ──► JSON
   │
   └── WebSocket
         │
         ▼
      Realtime Endpoint
         │
         ▼
      Application Service

Application Service
   │
   ├── Domain rule
   ├── Authorization policy/check
   ├── Transaction boundary
   ├── Repository
   └── Integration port
         │
         ├── JPA ──► Relational DB
         ├── Mongo adapter ──► MongoDB (optional)
         └── Firebase adapter ──► Firebase
```

Không tách microservice ở giai đoạn hiện tại.

---

# 4. ARCHITECTURAL PRINCIPLES

Thứ tự ưu tiên:

```text
Correctness
↓
Security
↓
Consistency
↓
Readability
↓
Maintainability
↓
Testability
↓
Performance
```

Rule:

1. Package theo feature/domain.
2. Business mutation đi qua service/application layer.
3. Controller không query database trực tiếp.
4. Repository không điều phối business workflow.
5. External SDK phải nằm sau adapter/port.
6. UI không phải security boundary.
7. Một dữ liệu có một source of truth rõ.
8. Không thêm abstraction/framework/database nếu chưa có use case.
9. Không tạo God Controller, God Service, God Utility.
10. Không duplicate contract giữa các tài liệu.

---

# 5. PROJECT STRUCTURE & LAYERED MVC ARCHITECTURE

Dự án áp dụng cấu trúc phân tầng **Layered MVC bên ngoài** đồng bộ theo `ARCHITECTURE_TEMPLATE.md` (commit `52f8622`) để đảm bảo cả 4 thành viên phát triển mượt mà, tránh xung đột:

```text
src/
├── main/
│   ├── java/com/aloute/          <--- BACKEND JAVA 21 / SPRING BOOT 3
│   │   ├── AlouteApplication.java # Khởi chạy Spring Boot
│   │   │
│   │   ├── config/               # Cấu hình Spring (Security, WebMvc, WebSocket, Firebase)
│   │   │
│   │   ├── controller/           # Tầng tiếp nhận request
│   │   │   ├── api/              # RestController trả JSON (@RestController cho AJAX/API /api/*)
│   │   │   └── page/             # Controller trả Thymeleaf HTML view (@Controller)
│   │   │
│   │   ├── service/              # Tầng nghiệp vụ (Interfaces & @Transactional boundaries)
│   │   │   └── impl/             # Service Implementations (Chuyển đổi Entity <-> DTO)
│   │   │
│   │   ├── repository/           # Tầng truy xuất dữ liệu (Spring Data JPA Repositories)
│   │   │
│   │   ├── entity/               # Tầng JPA Entities (Mapping bảng CSDL quan hệ)
│   │   │
│   │   ├── dto/                  # Data Transfer Objects
│   │   │   ├── request/          # DTO nhận từ client (@Valid)
│   │   │   └── response/         # DTO trả về cho client / ViewModel
│   │   │
│   │   ├── security/             # Spring Security, JWT (Filter, Provider, Principal)
│   │   │
│   │   └── common/               # Thành phần dùng chung
│   │       ├── api/              # ApiResponse<T>, PageMeta
│   │       ├── exception/        # ApiException, GlobalExceptionHandler, ErrorResponse
│   │       └── util/             # ModelMapperUtil, DateUtils
│   │
│   └── resources/                <--- FRONTEND THYMELEAF & DATABASE SCRIPTS
│       ├── templates/            # Giao diện chính Thymeleaf (Phạm Minh Quân)
│       │   ├── layouts/          # Layout master (default.html, auth.html)
│       │   ├── fragments/        # Thành phần tái sử dụng (head, navbar, sidebar, pagination, modal)
│       │   ├── auth/             # Màn hình đăng nhập, đăng ký
│       │   ├── user/             # Trang cá nhân, profile, friends
│       │   ├── feed/             # Trang chủ, bảng tin bài viết
│       │   ├── post/             # Chi tiết bài viết, lưu bài viết
│       │   ├── community/        # Trang nhóm/cộng đồng
│       │   ├── messaging/        # Chat 1-1, tin nhắn
│       │   ├── notification/     # Hộp thông báo
│       │   └── error/            # 404.html, 500.html, error.html
│       │
│       ├── static/               # Static assets
│       │   ├── css/              # tokens.css, app.css
│       │   ├── js/               # core (dom.js, http-client.js), features
│       │   └── images/           # Ảnh tĩnh, icon
│       │
│       ├── db/                   # Kịch bản CSDL & Stored Logic (Nguyễn Duy Ngọc)
│       │   ├── schema.sql        # DDL bảng, khóa chính/ngoại, index
│       │   ├── triggers.sql      # Database Triggers (audit updated_at, counter sync)
│       │   ├── procedures.sql    # Stored Procedures (search, cascade delete)
│       │   └── data-seed.sql     # Dữ liệu mẫu (30 users, 100 posts, groups)
│       │
│       ├── application.yml       # Cấu hình chung
│       ├── application-dev.yml   # Cấu hình dev: H2 in-memory
│       └── application-prod.yml  # Cấu hình prod: MySQL 8 + SMTP
│
├── fe-vue/                       <--- CHƯA TỒN TẠI trên master (kế hoạch: VUE 3 + VITE)
│   └── (Tiêu thụ chung các endpoint REST API /api/*)
│
├── aloute_mobile/                <--- FLUTTER CLIENT MỞ RỘNG (xem docs/FLUTTER_HANDOFF.md)
│
└── test/
    └── java/com/aloute/          # Unit test & Integration test (Bắt buộc theo kế hoạch)
```

---

# 6. MA TRẬN PHÂN CHIA MODULE & PHỤ TRÁCH MÃ NGUỒN

Để thực hiện đúng kế hoạch 3 tuần nộp GVHD, mã nguồn được phân định sở hữu rõ ràng:

| Phân hệ / Module | Tầng Controller & DTO | Tầng Service & Nghiệp vụ | Tầng Entity, DB & Stored Processing | Người phụ trách chính |
| :--- | :--- | :--- | :--- | :--- |
| **Auth & Security** | `AuthController`<br>`LoginRequest`, `RegisterRequest` | `AuthService`<br>`AuthServiceImpl` | `User` (passwordHash, isVerified, isActive)<br>Security Context, JWT Filters | **Khôi** |
| **User & Profile** | `UserController`<br>`UpdateProfileRequest`, `ProfileResponse` | `UserService`, `UserProfileService` | `Profile`, `User` (bio, avatarUrl, counters)<br>Trigger cập nhật `updated_at` | **Khôi** |
| **Social Graph** | `SocialController`<br>`SocialRelationResponse` | `SocialService`, `FollowService` | `Follow`, `Block`, `Mute`<br>Trigger đồng bộ `followers_count`, `following_count` | **Khôi** (chính) & **Ngọc** (DB) |
| **Post & Content** | `PostController`<br>`CreatePostRequest`, `PostResponse` | `PostService`<br>`PostServiceImpl` | `Post`, `PostMedia`, `PostTopic`, `PostVisibility`<br>Procedure tạo bài viết kèm media | **Ngọc** |
| **Reaction & Comment** | `ReactionController`, `CommentController`<br>`PostInteractionResponse` | `ReactionService`, `CommentService` | `PostLike`, `Bookmark`, `Repost`, `Comment`<br>Trigger đồng bộ `like_count`, `reply_count` | **Ngọc** |
| **Community / Group** | `CommunityController`<br>`CreateCommunityRequest` | `CommunityService`<br>`CommunityServiceImpl` | `Community`, `CommunityMember`, `CommunityRole`<br>Trigger bảo vệ không mất Owner cuối | **Ngọc** |
| **Feed & Search** | `FeedPageController`, `SearchController`<br>`FeedResponse`, `SearchResponse` | `FeedService`, `SearchService` | B-Tree Indexes phục vụ query Feed/Search<br>Nghiên cứu thuật toán tính điểm đề xuất | **Bằng** (Nghiệp vụ) & **Ngọc** (Query/Index) |
| **Messaging & Chat** | `ConversationController`, `MessageController`<br>`SendMessageRequest`, `MessageResponse` | `ConversationService`, `MessageService` | `Conversation`, `ConversationMember`, `Message`<br>Spring WebSocket STOMP endpoint (`/ws`) | **Bằng** |
| **Notification** | `NotificationController`<br>`NotificationResponse`, `UnreadCount` | `NotificationService`<br>`NotificationServiceImpl` | `Notification`, `NotificationType`<br>Gửi thông báo realtime qua STOMP | **Bằng** |
| **Report & Moderation** | `ReportController`<br>`ReportRequest`, `ReportResponse` | `ReportService`, `ModerationService` | `Report`, `ReportReason`, `ReportStatus`<br>Quyền quản trị duyệt/khóa nội dung | **Khôi** |
| **Giao diện SSR (Thymeleaf)** | `PageController` (Feed, User, Community, Msg...) | Chuyển tiếp ViewModel sang View | `templates/*`, `static/*`, HTML, CSS tokens, Form validation | **Quân** |
| **DevOps & Triển khai** | N/A | N/A | `Dockerfile`, `docker-compose.yml`, `nginx.conf`, VPS setup | **Khôi & Ngọc** |

Dependency chiều dọc chuẩn:
```text
Controller (api / page)
   ↓
Service (Interface & Impl)
   ↓
Repository (Spring Data JPA) / Database Triggers & Procedures
   ↓
Relational Database (MySQL 8 production / H2 dev & test)
```

Không cho phép:
```text
Controller → Repository
Repository → Controller
Entity → Controller trực tiếp (bắt buộc qua DTO / ViewModel)
```

---

# 7. CONTROLLER BOUNDARY

Có hai nhóm controller.

## 7.1. Page Controller

```java
@Controller
```

Trả:

```text
Thymeleaf view
+
Model/ViewModel
```

Dùng cho:

```text
/feed
/users/{id}
/communities/{id}
/settings
/messages
```

## 7.2. API Controller

```java
@RestController
```

Trả JSON cho:

- AJAX.
- Dynamic interaction.
- Infinite scroll.
- Modal form.
- Realtime-supporting HTTP calls.
- API nội bộ của frontend.

JSON endpoint dùng prefix:

```text
/api
```

Không để cùng một endpoint lúc trả HTML, lúc trả JSON bằng logic mơ hồ.

---

# 8. API URL CONVENTION

Dùng noun, plural, lowercase.

```text
/api/posts
/api/posts/{postId}
/api/users/{userId}
/api/communities/{communityId}
/api/conversations/{conversationId}/messages
```

Nested resource chỉ dùng khi quan hệ có ý nghĩa rõ.

Action đặc biệt chỉ dùng khi CRUD không diễn đạt được tự nhiên:

```text
POST /api/communities/{communityId}/join
POST /api/conversations/{conversationId}/read
```

Không tạo URL kiểu:

```text
/api/getAllPosts
/api/createNewPost
/api/deletePostById
```

---

# 9. API RESPONSE CONTRACT — SOURCE OF TRUTH

Phần này là canonical.

Frontend và domain docs không được định nghĩa lại response shape.

## 9.1. Success — single resource

```json
{
  "success": true,
  "data": {
    "id": 123
  },
  "meta": null
}
```

## 9.2. Success — action không cần data

Nếu cần body thống nhất:

```json
{
  "success": true,
  "data": null,
  "meta": null
}
```

Nếu endpoint chủ đích dùng `204 No Content`, không trả body.

Không dùng cả hai cách tùy hứng cho cùng loại operation.

## 9.3. Error

```json
{
  "success": false,
  "error": {
    "code": "POST_NOT_FOUND",
    "message": "Post not found",
    "fieldErrors": null
  },
  "traceId": "01H..."
}
```

## 9.4. Validation error

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Request validation failed",
    "fieldErrors": {
      "content": [
        "Content must not be blank"
      ]
    }
  },
  "traceId": "01H..."
}
```

## 9.5. Không trả raw JPA Entity

API trả DTO.

Không để response vô tình expose:

```text
passwordHash
refresh token
security metadata
internal audit field
lazy relation
persistence-only field
```

---

# 10. PAGINATION CONTRACT — SOURCE OF TRUTH

## 10.1. Request

Canonical query:

```text
?page=0&size=20
```

Quy ước:

```text
page = zero-based
size = số item mỗi page
```

Default:

```text
page = 0
size = 20
```

Maximum:

```text
size <= 100
```

Nếu cần sort:

```text
?sort=createdAt,desc
```

Không để mỗi endpoint tự invent:

```text
pageNumber
pageIndex
limit
perPage
take
offset
```

trừ endpoint cursor-based được thiết kế riêng.

## 10.2. Response

```json
{
  "success": true,
  "data": [],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 125,
    "totalPages": 7,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

## 10.3. Cursor pagination

Chỉ dùng cho use case có lý do rõ như feed/infinite stream lớn.

Nếu dùng cursor, contract của endpoint phải dùng một shape thống nhất riêng:

```json
{
  "success": true,
  "data": [],
  "meta": {
    "nextCursor": "opaque-value",
    "hasNext": true
  }
}
```

Không trộn page pagination và cursor pagination trong cùng endpoint.

---

# 11. HTTP STATUS CONTRACT — SOURCE OF TRUTH

| Status | Ý nghĩa chuẩn |
|---|---|
| `200 OK` | Read/update/action thành công và có response body |
| `201 Created` | Tạo resource thành công |
| `204 No Content` | Thành công và chủ đích không trả body |
| `400 Bad Request` | Request malformed hoặc request-level input không hợp lệ |
| `401 Unauthorized` | Authentication thất bại / chưa authenticated |
| `403 Forbidden` | Đã authenticated nhưng không được phép thực hiện |
| `404 Not Found` | Resource không tồn tại hoặc contract chủ đích conceal resource |
| `409 Conflict` | Conflict với current resource state |
| `422 Unprocessable Entity` | Business/semantic validation nếu endpoint dùng semantic validation |
| `429 Too Many Requests` | Rate limit |
| `500 Internal Server Error` | Unexpected server failure |
| `503 Service Unavailable` | Dependency/service tạm unavailable |

Rule:

- Không trả `200` rồi nhét business error vào body.
- Không dùng `500` cho lỗi user input.
- Một lỗi phải có cả HTTP category và stable application error code khi trả JSON.

Security implementation tạo ra authentication/authorization failure theo `SPRING_SECURITY_CONVENTION.md`; mapping sang HTTP vẫn theo bảng này.

---

# 12. ERROR CODE CONTRACT — SOURCE OF TRUTH

Format:

```text
<DOMAIN>_<REASON>
```

### 12.1. Canonical ErrorCode Catalog

| Domain | ErrorCode | HTTP Status | Ý nghĩa chuẩn |
| :--- | :--- | :--- | :--- |
| **COMMON** | `VALIDATION_FAILED` | 422 Unprocessable Entity | Dữ liệu request không thỏa mãn validation nghiệp vụ hoặc form |
| | `BAD_REQUEST` | 400 Bad Request | Payload malformed hoặc tham số request không hợp lệ |
| | `RESOURCE_NOT_FOUND` | 404 Not Found | Resource yêu cầu không tồn tại |
| | `METHOD_NOT_ALLOWED` | 405 Method Not Allowed | Phương thức HTTP không được hỗ trợ trên endpoint |
| | `UNSUPPORTED_MEDIA_TYPE` | 415 Unsupported Media Type | Định dạng Content-Type không được hỗ trợ |
| | `FORBIDDEN` | 403 Forbidden | Quyền truy cập bị từ chối |
| | `CONFLICT` | 409 Conflict | Trùng lặp hoặc xung đột với trạng thái hiện tại |
| | `RATE_LIMIT_EXCEEDED` | 429 Too Many Requests | Vượt quá giới hạn số lượt request cho phép |
| | `INTERNAL_ERROR` | 500 Internal Server Error | Lỗi hệ thống bất ngờ chưa được phân loại |
| | `SERVICE_UNAVAILABLE` | 503 Service Unavailable | Dịch vụ bên ngoài hoặc phụ thuộc tạm thời gián đoạn |
| **AUTH** | `AUTH_INVALID_CREDENTIALS` | 401 Unauthorized | Email hoặc mật khẩu không chính xác |
| | `AUTH_UNAUTHENTICATED` | 401 Unauthorized | Yêu cầu xác thực nhưng token thiếu hoặc không hợp lệ |
| | `AUTH_TOKEN_EXPIRED` | 401 Unauthorized | Token xác thực đã hết hạn |
| | `AUTH_FORBIDDEN` | 403 Forbidden | Đã xác thực nhưng tài khoản không có quyền thực hiện hành động |
| | `AUTH_ACCOUNT_LOCKED` | 403 Forbidden | Tài khoản bị tạm khóa hoặc đình chỉ hoạt động |
| | `AUTH_ACCOUNT_NOT_VERIFIED` | 401 Unauthorized | Tài khoản chưa hoàn thành xác thực email OTP |
| | `INVALID_OTP` | 400 Bad Request | Mã xác thực không hợp lệ hoặc đã hết hạn |
| | `OTP_EXPIRED` | 400 Bad Request | Mã xác thực đã hết hạn |
| | `OTP_MAX_ATTEMPTS_EXCEEDED` | 400 Bad Request | Đã vượt quá số lần thử xác thực tối đa cho phép |
| **USER** | `USER_NOT_FOUND` | 404 Not Found | Người dùng không tồn tại |
| | `USER_INACTIVE` | 403 Forbidden | Tài khoản người dùng đang bị vô hiệu hóa |
| | `USER_USERNAME_ALREADY_EXISTS` | 409 Conflict | Username đã được sử dụng |
| | `USER_EMAIL_ALREADY_EXISTS` | 409 Conflict | Email đã được sử dụng |
| | `USER_SELF_ACTION_NOT_ALLOWED` | 409 Conflict | Không được phép tự thực hiện hành động này lên chính mình |
| **SOCIAL** | `FOLLOW_SELF_NOT_ALLOWED` | 409 Conflict | Không được phép tự follow chính mình |
| | `FOLLOW_ALREADY_EXISTS` | 409 Conflict | Quan hệ follow đã tồn tại |
| | `FOLLOW_NOT_FOUND` | 404 Not Found | Không tìm thấy quan hệ follow để hủy |
| | `FOLLOW_PRIVATE_ACCOUNT` | 409 Conflict | Tài khoản đích đặt chế độ riêng tư |
| | `BLOCK_SELF_NOT_ALLOWED` | 409 Conflict | Không thể tự block chính mình |
| | `BLOCK_ALREADY_EXISTS` | 409 Conflict | Quan hệ block đã tồn tại |
| | `BLOCK_NOT_FOUND` | 404 Not Found | Không tìm thấy quan hệ block |
| | `MUTE_SELF_NOT_ALLOWED` | 409 Conflict | Không thể tự mute chính mình |
| | `MUTE_ALREADY_EXISTS` | 409 Conflict | Quan hệ mute đã tồn tại |
| | `MUTE_NOT_FOUND` | 404 Not Found | Không tìm thấy quan hệ mute |
| | `RESTRICT_SELF_NOT_ALLOWED` | 409 Conflict | Không thể tự hạn chế chính mình |
| | `RESTRICTION_ALREADY_EXISTS` | 409 Conflict | Quan hệ hạn chế đã tồn tại |
| | `RESTRICTION_NOT_FOUND` | 404 Not Found | Không tìm thấy quan hệ hạn chế |
| **POST** | `POST_NOT_FOUND` | 404 Not Found | Bài viết không tồn tại hoặc đã bị xóa |
| | `POST_ALREADY_DELETED` | 409 Conflict | Bài viết đã bị xóa trước đó |
| | `POST_REPLY_NOT_ALLOWED` | 403 Forbidden | Bài viết tắt tính năng trả lời/bình luận |
| | `POST_QUOTE_NOT_ALLOWED` | 403 Forbidden | Bài viết tắt tính năng quote/trích dẫn |
| | `POST_OWNER_REQUIRED` | 403 Forbidden | Chỉ chủ bài viết mới có quyền chỉnh sửa hoặc xóa |
| | `POST_ACCESS_DENIED` | 403 Forbidden | Không có quyền xem bài viết (do giới hạn đối tượng) |
| **POLL** | `POLL_NOT_FOUND` | 404 Not Found | Cuộc bình chọn không tồn tại |
| | `POLL_CLOSED` | 400 Bad Request | Cuộc bình chọn đã kết thúc |
| | `POLL_ALREADY_VOTED` | 409 Conflict | Người dùng đã bình chọn trước đó |
| | `POLL_NOT_MULTIPLE_CHOICE` | 400 Bad Request | Bình chọn chỉ cho phép chọn 1 phương án duy nhất |
| | `POLL_OPTION_NOT_FOUND` | 404 Not Found | Phương án bình chọn không tồn tại hoặc không thuộc poll |
| **COMMUNITY** | `COMMUNITY_NOT_FOUND` | 404 Not Found | Cộng đồng không tồn tại |
| | `COMMUNITY_SLUG_EXISTS` | 409 Conflict | Đường dẫn slug của cộng đồng đã bị trùng |
| | `COMMUNITY_MEMBERSHIP_REQUIRED` | 403 Forbidden | Cần là thành viên cộng đồng để thực hiện |
| | `COMMUNITY_MEMBER_NOT_FOUND` | 404 Not Found | Không tìm thấy thành viên trong cộng đồng |
| | `COMMUNITY_ALREADY_JOINED` | 409 Conflict | Đã tham gia cộng đồng này |
| | `COMMUNITY_ACCESS_DENIED` | 403 Forbidden | Không có quyền truy cập cộng đồng riêng tư |
| | `COMMUNITY_ADMIN_REQUIRED` | 403 Forbidden | Yêu cầu quyền Quản trị viên cộng đồng |
| | `COMMUNITY_OWNER_REQUIRED` | 403 Forbidden | Yêu cầu quyền Trưởng nhóm / Owner |
| | `COMMUNITY_OWNER_CANNOT_LEAVE` | 409 Conflict | Owner không thể rời nhóm khi chưa chuyển giao quyền |
| **CONVERSATION** | `CONVERSATION_NOT_FOUND` | 404 Not Found | Hội thoại không tồn tại |
| | `CONVERSATION_MEMBERSHIP_REQUIRED` | 403 Forbidden | Bạn không phải thành viên của hội thoại |
| | `CONVERSATION_MEMBER_NOT_FOUND` | 404 Not Found | Không tìm thấy thành viên hội thoại |
| | `CONVERSATION_SELF_DIRECT_NOT_ALLOWED` | 400 Bad Request | Không thể tạo hội thoại 1-1 với chính mình |
| | `CONVERSATION_PARTICIPANTS_REQUIRED` | 400 Bad Request | Cần ít nhất một thành viên tham gia |
| | `MESSAGE_NOT_FOUND` | 404 Not Found | Tin nhắn không tồn tại |
| | `INVALID_REPLY_CONVERSATION` | 400 Bad Request | Tin nhắn được trả lời thuộc hội thoại khác |
| **MEDIA** | `MEDIA_NOT_FOUND` | 404 Not Found | Tệp đa phương tiện không tồn tại |
| | `MEDIA_FILE_REQUIRED` | 422 Unprocessable Entity | Yêu cầu đính kèm tệp tin |
| | `MEDIA_TOO_LARGE` | 422 Unprocessable Entity | Kích thước tệp vượt quá giới hạn cấu hình |
| | `MEDIA_QUOTA_EXCEEDED` | 409 Conflict | Vượt quá hạn mức dung lượng media của người dùng |
| | `MEDIA_ALREADY_ATTACHED` | 409 Conflict | Media đã được đính kèm, không thể xóa trực tiếp |
| | `MEDIA_TYPE_UNSUPPORTED` | 422 Unprocessable Entity | Định dạng tệp tin không được hỗ trợ |
| | `MEDIA_STORAGE_UNAVAILABLE` | 503 Service Unavailable | Dịch vụ lưu trữ media tạm thời không khả dụng |
| | `MEDIA_UPLOAD_FAILED` | 503 Service Unavailable | Không thể tải tệp tin lên dịch vụ lưu trữ |
| **TOPIC** | `TOPIC_NOT_FOUND` | 404 Not Found | Chủ đề không tồn tại |
| **REPORT** | `REPORT_NOT_FOUND` | 404 Not Found | Báo cáo vi phạm không tồn tại |
| | `REPORT_TARGET_REQUIRED` | 422 Unprocessable Entity | Yêu cầu chỉ định đúng một đối tượng vi phạm (User hoặc Post) |
| | `REPORT_SELF_NOT_ALLOWED` | 409 Conflict | Không thể tự báo cáo chính mình |
| | `REPORT_ALREADY_IN_REVIEW` | 409 Conflict | Báo cáo đã được Quản trị viên khác tiếp nhận xử lý |
| | `REPORT_INVALID_TRANSITION` | 409 Conflict | Chuyển đổi trạng thái báo cáo không hợp lệ |
| | `REPORT_VERSION_CONFLICT` | 409 Conflict | Dữ liệu báo cáo đã bị thay đổi bởi phiên khác (optimistic lock conflict) |
| | `REPORT_REVIEWER_REQUIRED` | 403 Forbidden | Chỉ Quản trị viên đang tiếp nhận mới có quyền ra quyết định |
| **MODERATION** | `POST_RESTORE_NOT_ALLOWED` | 409 Conflict | Không đủ điều kiện khôi phục (bài viết do tác giả tự xóa hoặc đã bị gỡ độc lập) |
| | `POST_REMOVAL_NOT_FOUND` | 404 Not Found | Không tìm thấy bản ghi gỡ bài đang có hiệu lực |
| | `USER_SUSPENSION_ALREADY_ACTIVE` | 409 Conflict | Tài khoản đã có lệnh đình chỉ đang còn hiệu lực |
| | `USER_LAST_ADMIN_PROTECTED` | 409 Conflict | Không thể khóa hoặc hạ cấp Quản trị viên hệ thống (SYSTEM_ADMIN) cuối cùng |
| | `COMMUNITY_OWNER_TRANSFER_INVALID` | 409 Conflict | Chuyển giao quyền Trưởng nhóm không hợp lệ |
| | `BROADCAST_INVALID_STATE` | 409 Conflict | Thông báo toàn hệ thống không ở trạng thái cho phép gửi |
| **FEED** | `FEED_NOT_FOUND` | 404 Not Found | Danh sách bảng tin tùy chỉnh không tồn tại |
| | `FEED_ACCESS_DENIED` | 403 Forbidden | Không có quyền truy cập bảng tin riêng tư |
| **NOTIFICATION** | `NOTIFICATION_NOT_FOUND` | 404 Not Found | Thông báo không tồn tại |

Rule:

1. `code` ổn định cho client logic.
2. `message` human-readable.
3. Frontend không viết logic dựa trên exact `message`.
4. Không dùng một code chung `ERROR` cho mọi trường hợp.
5. Không tạo hàng trăm code nếu UI/business không phân biệt.

---

# 13. REQUEST BODY CONTRACT

Request JSON luôn qua Request DTO.

Ví dụ:

```java
CreatePostRequest
UpdateProfileRequest
CreateCommunityRequest
SendMessageRequest
```

Không nhận JPA Entity trực tiếp từ client.

Không nhận `authorId`, `ownerId`, `senderId` từ client nếu server có thể suy ra từ authenticated principal.

Field không được phép update phải không xuất hiện trong request DTO.

---

# 14. HTTP HEADER CONTRACT

Header chung có thể dùng:

```text
Content-Type: application/json
Accept: application/json
X-Request-Id: <optional-client-request-id>
```

Authentication credential transport thuộc **duy nhất** `SPRING_SECURITY_CONVENTION.md`.

Không invent business authority header:

```text
X-User-Id
X-Role
X-Is-Admin
X-Community-Role
```

và không tin các giá trị đó từ browser.

---

# 15. DATE/TIME CONTRACT

Backend trả machine-readable timestamp theo ISO-8601.

Ví dụ:

```text
2026-09-26T10:30:00Z
```

Persist instant/timezone theo chiến lược database đã chốt.

Frontend chịu trách nhiệm format cho user.

Không trả custom string kiểu:

```text
26/09/2026 5 giờ chiều
```

trong API contract.

---

# 16. VALIDATION BOUNDARY

Validation chia thành:

```text
Request structural validation
→ DTO validation

Business validation
→ Service/domain

Database integrity
→ DB constraint
```

Ví dụ:

```text
@NotBlank
@Size
@Email
```

thuộc request validation.

Ví dụ:

```text
không được follow chính mình
poll đã đóng
không phải member community
```

thuộc business/domain validation.

---

# 17. EXCEPTION FLOW

```text
Controller
   ↓
Service
   ↓ throws typed exception
GlobalExceptionHandler
   ↓
HTTP status + ErrorCode + ApiError
```

Business/service không tự build JSON response.

Common exception translation nằm tại:

```text
common/exception/
```

Page Controller có thể map exception phù hợp sang error view.

JSON API theo canonical API error contract.

---

# 18. TRANSACTION BOUNDARY

Transaction đặt ở application/service boundary.

Ví dụ:

```text
create post
follow user
join community
vote poll
send persisted message
```

Các mutation liên quan nhiều relational write phải xác định atomicity.

Không mở transaction qua template rendering.

Không giả định database transaction rollback được Firebase hoặc external service.

Khi operation gồm DB + external service, phải xác định failure/reconciliation strategy.

---

# 19. PERSISTENCE STRATEGY

## 19.1. Relational database là primary business store

Chọn **một**:

```text
PostgreSQL
MySQL
SQL Server
```

cho deployment chính.

JPA dùng cho relational persistence.

Các DB trên là lựa chọn thay thế, không phải ba source of truth đồng thời.

**Trạng thái đã triển khai trên master (2026-10-20):** MySQL 8.0 là engine production
(`application-prod.yml`, `docker-compose.yml`, `deploy/compose/compose.prod.yml` đều dùng
`jdbc:mysql://...`); dev/test dùng H2 in-memory. Script PostgreSQL trong `db/` là tài liệu thiết kế
schema, không chạy tự động. Triggers/stored procedures chưa được wire vào runtime — counter
(like_count, reply_count, follower_count, member_count) hiện được cập nhật trong Java service layer.

## 19.2. MongoDB

MongoDB không nằm dưới JPA.

Nếu dùng:

```text
Spring Data MongoDB
```

MongoDB chỉ được đưa vào khi có documented use case:

- document shape phù hợp;
- query pattern rõ;
- source of truth rõ;
- không duplicate aggregate tùy tiện với SQL.

Nếu chưa có use case hợp lý:

```text
Không dùng MongoDB.
```

## 19.3. Không viết business logic phụ thuộc vendor

Ưu tiên JPA/standard SQL behavior cho domain thông thường.

Vendor-specific query chỉ dùng khi có lý do và phải được cô lập ở persistence layer.

---

# 20. FIREBASE BOUNDARY

Trong architecture ALOUTE, Firebase được coi là **external integration boundary** cho 2 vai trò cụ thể:

1. **Media/Object Storage (Firebase Cloud Storage)**:
   - Lưu trữ nhị phân ảnh, video bài viết, avatar, cover.
   - Nằm sau `MediaStoragePort` -> `FirebaseMediaStorageAdapter`.

2. **Web Push Notification (Firebase Cloud Messaging - FCM)**:
   - Đẩy thông báo trình duyệt ngầm khi người dùng không active trên tab web (background web push).
   - Nằm sau `FcmNotificationService` điều phối qua Firebase Admin SDK (`FirebaseMessaging.getInstance().send(...)`).
   - Client đăng ký device token qua endpoint `POST /api/notifications/fcm-token` và nhận tin qua `firebase-messaging-sw.js`.

```text
Business Service (NotificationServiceImpl)
   ├── SimpMessagingTemplate ──► Spring WebSocket STOMP (/ws) [Foreground / In-app]
   └── FcmNotificationService ──► Firebase Admin SDK (FCM) ──► Service Worker [Background / Web Push]
```

Không gọi Firebase SDK trực tiếp từ:
- Controller
- Entity
- Thymeleaf template

Relational DB lưu business metadata, token và quan hệ cần thiết.
Firebase chỉ giữ binary storage và gateway push notification.
Các dịch vụ khác của Firebase (Firestore, Firebase Auth) không được tự tiện thêm vào nếu không có ADR.

---

# 21. WEBSOCKET BOUNDARY

WebSocket chỉ dùng cho realtime in-app.

Phù hợp:
- chat delivery
- live in-app notification
- presence nếu project triển khai
- realtime event cần thiết

Không phù hợp để thay toàn bộ CRUD HTTP.

## 21.1. Dual-Dispatch Notification Architecture
Hệ thống áp dụng cơ chế điều phối thông báo song song:
1. **In-app Foreground (Đang mở tab web ALOUTE)**:
   - Sử dụng **Spring WebSocket (STOMP Protocol qua `/ws`)**.
   - Server gọi `messagingTemplate.convertAndSendToUser(recipientId, "/queue/notifications", payload)`;
     client SUBSCRIBE vào user destination `/user/queue/notifications`.
   - Độ trễ tức thời, cập nhật badge chuông và toast UI mượt mà.
2. **Background / Offline (Thu nhỏ trình duyệt hoặc tắt tab)**:
   - Sử dụng **FCM Web Push Notification**.
   - Bắt qua Service Worker `firebase-messaging-sw.js` hiển thị system popup trên OS/trình duyệt.

Bảng destination canonical (đúng theo `StompAuthChannelInterceptor.ALLOWED_SUBSCRIBE_DESTINATIONS`
và các `@MessageMapping`):

| Hướng | Destination | Owner |
| :--- | :--- | :--- |
| Server → client | `/user/queue/messages` | chat delivery |
| Server → client | `/user/queue/typing` | typing indicator |
| Server → client | `/user/queue/read-receipts` | read receipt |
| Server → client | `/user/queue/notifications` | in-app notification |
| Client → server | `/app/chat.send/{conversationId}` | gửi tin nhắn realtime |
| Client → server | `/app/chat.typing/{conversationId}` | typing |
| Client → server | `/app/chat.read/{conversationId}` | đánh dấu đã đọc |

Client chỉ được SUBSCRIBE đúng 4 user destination ở trên; destination khác bị interceptor từ chối.
Broker dùng prefix `/topic`, `/queue`; `applicationDestinationPrefixes` là `/app`;
`userDestinationPrefix` là `/user`.

Rule:

```text
Load history → HTTP
Search → HTTP
Create/update normal resource → HTTP
Realtime delivery → WebSocket
```

WebSocket endpoint vẫn đi qua authentication, authorization, validation và application service.

Không persist business data chỉ trong WebSocket session.

---

# 22. SECURITY BOUNDARY

File này không định nghĩa:

```text
JWT claim
JWT storage
JWT filter
Role mapping
Authority mapping
SecurityContext
@PreAuthorize rule
current user helper
```

Tất cả thuộc:

```text
SPRING_SECURITY_CONVENTION.md
```

File domain quyết định **quyền nghiệp vụ nào tồn tại**.

Security convention quyết định **cách backend enforce**.

Architecture file chỉ giữ HTTP/security boundary ở mức system integration.

---

# 23. FRONTEND BOUNDARY

Frontend được hỗ trợ:

```text
1. Server-rendered Thymeleaf (SSR — Giao diện chính thức nghiệm thu học phần):
   Spring MVC + Thymeleaf + Bootstrap 5 + Vanilla JavaScript progressive enhancement (Phạm Minh Quân phụ trách)
2. Vue 3 SPA Architecture (fe-vue — Client Single Page Application mở rộng):
   Vue 3 (Composition API, <script setup>) + Vite + Pinia + Vue Router + Custom Token Design System
```

Giao diện Thymeleaf tại `src/main/resources/templates/` là sản phẩm chính thức nộp đồ án cho GVHD Th.S Nguyễn Trần Thi Văn theo cam kết trong `Nhom13_KeHoachCodeTest.docx`. Vue 3 SPA tại `fe-vue/` đóng vai trò client mở rộng độc lập, tiêu thụ chung các API RESTful từ Backend Spring Boot.

File này không định nghĩa chi tiết:

```text
fragment naming
CSS structure
browser module structure
DOM behavior
Bootstrap component rule
AJAX UI state
```

Các rule đó thuộc:

```text
ALOUTE_FRONTEND_CONVENTION.md
```

Frontend phải consume API contract từ file hiện tại, không định nghĩa một API contract mới.

---

# 24. DOMAIN BOUNDARY

Feature/domain canonical nằm trong:

```text
ALOUTE_DOMAIN_SYSTEM.md
```

Ví dụ:

```text
Post là gì?
Reply/Quote hoạt động ra sao?
Ai xem được FOLLOWERS post?
Moderator có quyền gì?
Conversation member được làm gì?
Poll single-choice invariant là gì?
```

Architecture không copy các rule này.

---

# 25. CONFIGURATION

```text
application.yml
application-dev.yml
application-test.yml
application-prod.yml
```

Không hardcode:

```text
database password
JWT signing secret/key
Firebase credential
production host
private API credential
```

Secret đến từ environment/secret mechanism của deployment.

---

# 26. LOGGING

Application log cần hỗ trợ trace request.

Có thể log:

```text
timestamp
level
traceId
operation
resource id
actor id khi phù hợp
exception
```

Không log:

```text
password
JWT raw token
refresh token
Firebase private credential
database credential
sensitive private message content nếu không có lý do audit rõ
```

---

# 27. TESTING BOUNDARY

Tối thiểu:

```text
Unit test
├── business service
├── domain/policy logic
├── mapper
└── utility

Repository test
└── important persistence query/constraint

Controller test
├── request binding
├── validation
├── API contract
└── HTTP status mapping

Integration test
└── critical workflows
```

Security-specific tests theo `SPRING_SECURITY_CONVENTION.md`.

Frontend rendering/browser behavior theo `ALOUTE_FRONTEND_CONVENTION.md`.

---

# 28. FEATURE IMPLEMENTATION CHECKLIST

Trước khi code feature:

```text
1. Business use case đã có trong DOMAIN SYSTEM chưa?
2. Ai được làm? Domain permission đã rõ chưa?
3. Page hay JSON API?
4. Request DTO là gì?
5. Response DTO/ViewModel là gì?
6. Có pagination không? Dùng canonical contract nào?
7. Error code nào cần có?
8. Service boundary ở đâu?
9. Transaction boundary ở đâu?
10. Data source of truth là gì?
11. Có external integration không?
12. Có cần WebSocket không?
13. Security implementation theo convention nào?
14. Frontend render/interaction theo convention nào?
15. Test case quan trọng là gì?
```

---

# 29. REVIEW CHECKLIST

Không merge nếu có một trong các lỗi:

- API response tự invent shape mới.
- Pagination dùng field khác canonical mà không có ADR.
- HTTP error trả status sai semantic.
- Frontend định nghĩa lại error/status contract.
- Security file định nghĩa lại HTTP body/status table.
- Domain file chứa JWT/Spring/JPA implementation.
- Controller gọi Repository trực tiếp.
- Feature gọi Firebase SDK trực tiếp.
- MongoDB được dùng nhưng không có source-of-truth/use-case document.
- WebSocket được dùng cho CRUD thông thường không có lý do.
- JPA Entity bị trả trực tiếp ra API.
- Client gửi identity/role và backend tin giá trị đó.
- Secret bị commit hoặc log.
- Cùng một rule được copy ở nhiều file.

---

# 30. ARCHITECTURE SUMMARY

```text
ALOUTE
=
Modular Monolith
+
Spring Boot
+
Spring MVC
+
Thymeleaf SSR
+
Bootstrap
+
JavaScript progressive enhancement
+
Spring Data JPA
+
ONE primary relational DB
+
Optional MongoDB with explicit use case
+
Firebase behind integration port
+
JWT/Spring Security defined in security convention
+
WebSocket only for realtime
+
Canonical API contract defined only here
```

Nguyên tắc quan trọng nhất:

> **Business rule có một owner. Technical contract có một owner. Các tài liệu khác reference, không copy.**

---

# 26. ADMIN DASHBOARD & BACKOFFICE TRANSPORT ARCHITECTURE

1. **Phân tách Route:**
   - **Giao diện quản trị SSR:** `/admin/**` (Thymeleaf layout `layouts/admin.html`).
   - **API REST Backend:** `/api/admin/**` (Trả về `ApiResponse<T>` envelope canonical).
2. **Quyền & Xác thực:**
   - Bắt buộc kiểm tra `ROLE_SYSTEM_ADMIN` (`@PreAuthorize("hasRole('SYSTEM_ADMIN')")`).
   - Phủ nhận mặc định (Deny-by-default).
   - Tuyệt đối không chấp nhận các header client tự phong như `X-Is-Admin`, `X-Role`, hay `adminId` trong request body; thông tin Admin thực hiện (`actorUserId`) luôn lấy từ authenticated `UserPrincipal`.
3. **Bảo mật Request:**
   - Browser Thymeleaf sử dụng JWT HttpOnly Cookie `aloute_access_token`.
   - Mọi request ghi (POST/PATCH/DELETE) từ browser bắt buộc phải gửi kèm CSRF Token (`X-XSRF-TOKEN`).
   - REST Client độc lập sử dụng `Authorization: Bearer <token>`.
4. **Tính nhất quán Transaction & Audit:**
   - Mọi thao tác can thiệp quản trị (gỡ bài viết, duyệt report, khóa tài khoản) phải được thực thi trong một transaction (`@Transactional`) cùng với việc ghi bản ghi Audit Log (`moderation_actions`).
   - Không được cập nhật DOM trước khi server phản hồi HTTP `2xx`.
   - Lệnh hủy/xóa/khóa phải có xác nhận (Confirmation modal) và lý do bắt buộc (`reasonDetail`).
