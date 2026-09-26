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
Application
├── Java
├── Spring Boot
├── Spring MVC
├── Spring Security
├── Spring Data JPA
├── Thymeleaf
├── Bootstrap
├── JavaScript
├── JWT
└── WebSocket

Persistence
├── Relational Database — chọn 1 database chính
│   ├── PostgreSQL
│   ├── MySQL
│   └── SQL Server
└── MongoDB — optional, chỉ khi có use case document rõ ràng

External Infrastructure
└── Firebase
    └── media/object storage integration của project
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

# 5. PROJECT STRUCTURE

```text
src/
├── main/
│   ├── java/com/aloute/
│   │   ├── AlouteApplication.java
│   │   │
│   │   ├── config/
│   │   │   ├── WebMvcConfig.java
│   │   │   ├── PersistenceConfig.java
│   │   │   ├── WebSocketConfig.java
│   │   │   └── FirebaseConfig.java
│   │   │
│   │   ├── common/
│   │   │   ├── api/
│   │   │   ├── exception/
│   │   │   ├── validation/
│   │   │   ├── pagination/
│   │   │   ├── mapper/
│   │   │   └── util/
│   │   │
│   │   ├── security/
│   │   │
│   │   ├── integration/
│   │   │   └── firebase/
│   │   │
│   │   ├── realtime/
│   │   │
│   │   └── feature/
│   │       ├── auth/
│   │       ├── user/
│   │       ├── social/
│   │       ├── post/
│   │       ├── topic/
│   │       ├── poll/
│   │       ├── community/
│   │       ├── feed/
│   │       ├── messaging/
│   │       ├── notification/
│   │       ├── moderation/
│   │       └── analytics/
│   │
│   └── resources/
│       ├── templates/
│       ├── static/
│       ├── application.yml
│       ├── application-dev.yml
│       ├── application-test.yml
│       └── application-prod.yml
│
└── test/
    └── java/com/aloute/
```

Chi tiết cấu trúc `security/` thuộc `SPRING_SECURITY_CONVENTION.md`.

Chi tiết `templates/` và `static/` thuộc `ALOUTE_FRONTEND_CONVENTION.md`.

Chi tiết entity/feature thuộc `ALOUTE_DOMAIN_SYSTEM.md`.

---

# 6. FEATURE PACKAGE

Một feature điển hình:

```text
feature/post/
├── controller/
│   ├── page/
│   └── api/
├── dto/
│   ├── request/
│   └── response/
├── entity/
├── repository/
├── service/
├── mapper/
├── policy/
└── exception/
```

Không bắt buộc tạo folder rỗng.

Dependency mặc định:

```text
Controller
   ↓
Service
   ↓
Repository / Integration Port
```

Cho phép service gọi public service của domain khác khi use case cần.

Không cho phép:

```text
Controller → Repository
Repository → Service
Repository → Controller
Entity → Controller
Template → Repository
Feature A → internal/private implementation của Feature B
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

Ví dụ:

```text
VALIDATION_FAILED

AUTH_INVALID_CREDENTIALS
AUTH_TOKEN_EXPIRED

USER_NOT_FOUND
USER_INACTIVE

POST_NOT_FOUND
POST_ALREADY_DELETED
POST_REPLY_NOT_ALLOWED

COMMUNITY_NOT_FOUND
COMMUNITY_MEMBERSHIP_REQUIRED
COMMUNITY_ALREADY_JOINED

CONVERSATION_NOT_FOUND
CONVERSATION_MEMBERSHIP_REQUIRED

POLL_ALREADY_VOTED
POLL_CLOSED

INTERNAL_ERROR
```

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

Trong architecture hiện tại, Firebase được coi là **external media/object-storage integration**.

```text
Business Service
   ↓
MediaStoragePort
   ↓
FirebaseMediaStorageAdapter
   ↓
Firebase
```

Không gọi Firebase SDK trực tiếp từ:

```text
Controller
Entity
Thymeleaf template
```

Relational DB lưu business metadata/reference cần thiết.

Firebase giữ binary/object theo scope đã định.

Nếu sau này project dùng thêm Firebase service khác, phải cập nhật architecture boundary trước; không tự tiện dùng Firestore/Firebase Auth/FCM trong feature.

---

# 21. WEBSOCKET BOUNDARY

WebSocket chỉ dùng cho realtime.

Phù hợp:

```text
chat delivery
live notification
presence nếu project triển khai
realtime event cần thiết
```

Không phù hợp để thay toàn bộ CRUD HTTP.

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

Frontend hiện tại:

```text
Server-rendered Thymeleaf
+
Bootstrap
+
JavaScript progressive enhancement
```

Không dùng Vue architecture cho phiên bản này.

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
