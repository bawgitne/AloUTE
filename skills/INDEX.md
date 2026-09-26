# ALOUTE — RULE FOR READING & IMPLEMENTING THE SYSTEM

> **BẮT BUỘC ĐỌC FILE NÀY TRƯỚC KHI CODE**
>
> File này không tạo business rule hay implementation rule mới. Nó chỉ hướng dẫn Developer / AI / Reviewer biết phải đọc file nào, file nào là source of truth và cách xử lý khi nhiều tài liệu cùng liên quan.

---

# 1. NGUYÊN TẮC CAO NHẤT

ALOUTE dùng nguyên tắc:

> **ONE RULE → ONE OWNER → ONE SOURCE OF TRUTH**

Không copy một rule sang nhiều file rồi sửa độc lập.

Ví dụ:

```text
Post visibility
→ ALOUTE_DOMAIN_SYSTEM.md

HTTP status / pagination / ApiResponse
→ ALOUTE_SYSTEM_ARCHITECTURE.md

Exact endpoint / request / response
→ aloute-openapi.yaml

Controller / Service / Repository / JPA
→ ALOUTE_SPRING_BOOT_BACKEND_CONVENTION.md

JWT / refresh / authorization
→ SPRING_SECURITY_CONVENTION.md

Firebase upload / storage key
→ ALOUTE_MEDIA_STORAGE_CONVENTION.md

STOMP destination / realtime event
→ ALOUTE_REALTIME_WEBSOCKET_CONVENTION.md

Thymeleaf / Bootstrap / browser JavaScript
→ ALOUTE_FRONTEND_CONVENTION.md

Docker / Nginx / VPS
→ ALOUTE_DOCKER_REVERSE_PROXY_CONVENTION.md
```

Nếu rule đã có owner thì file khác chỉ được:

```text
REFERENCE
CONSUME
IMPLEMENT
```

Không được tự định nghĩa lại.

---

# 2. BỘ TÀI LIỆU CANONICAL

Luôn dùng bộ tài liệu final:

```text
RULE.md
README.md
ALOUTE_DOMAIN_SYSTEM.md
ALOUTE_SYSTEM_ARCHITECTURE.md
aloute-openapi.yaml
ALOUTE_SPRING_BOOT_BACKEND_CONVENTION.md
SPRING_SECURITY_CONVENTION.md
ALOUTE_MEDIA_STORAGE_CONVENTION.md
ALOUTE_REALTIME_WEBSOCKET_CONVENTION.md
ALOUTE_FRONTEND_CONVENTION.md
ALOUTE_DOCKER_REVERSE_PROXY_CONVENTION.md
```

Không dùng bản cũ nằm ngoài bộ FINAL nếu đã có bản final mới hơn.

---

# 3. THỨ TỰ ĐỌC KHI MỚI VÀO PROJECT

```text
1. RULE.md
2. README.md
3. ALOUTE_DOMAIN_SYSTEM.md
4. ALOUTE_SYSTEM_ARCHITECTURE.md
5. ALOUTE_SPRING_BOOT_BACKEND_CONVENTION.md
6. SPRING_SECURITY_CONVENTION.md
7. ALOUTE_MEDIA_STORAGE_CONVENTION.md
8. ALOUTE_REALTIME_WEBSOCKET_CONVENTION.md
9. ALOUTE_FRONTEND_CONVENTION.md
10. ALOUTE_DOCKER_REVERSE_PROXY_CONVENTION.md
11. aloute-openapi.yaml khi implement HTTP API
```

Không cần nhớ toàn bộ. Cần biết:

```text
VẤN ĐỀ NÀY THUỘC FILE NÀO?
```

---

# 4. README.md

README là bản đồ của bộ tài liệu.

Dùng để biết:

- file nào tồn tại;
- ownership của từng file;
- architecture direction hiện tại;
- thứ tự đọc.

README không phải owner của business rule cụ thể.

Nếu README tóm tắt khác owner file:

```text
OWNER FILE THẮNG.
```

---

# 5. ALOUTE_DOMAIN_SYSTEM.md

Đây là file phải đọc đầu tiên khi implement một feature.

Domain sở hữu:

```text
Feature
Actor
Entity
Relationship
Business Rule
Business Invariant
Business State
Business Flow
Ownership
Visibility
Privacy
WHO CAN DO WHAT
Physical relational data model
Table / Column / PK / FK
Unique Constraint
Important Index
ERD
```

Ví dụ:

```text
Private user follow thế nào?
Community Moderator làm được gì?
Post thuộc Community ra sao?
Poll single/multi choice hoạt động thế nào?
Conversation OWNER/ADMIN/MEMBER có quyền gì?
Block ảnh hưởng action nào?
Table nào giữ quan hệ?
Unique/index nào bảo vệ dữ liệu?
```

→ đọc Domain.

Domain không sở hữu:

```text
HTTP response
JWT
Spring layer
Firebase SDK
STOMP destination
Thymeleaf
Docker
```

---

# 6. ALOUTE_SYSTEM_ARCHITECTURE.md

System Architecture sở hữu các cross-cutting rule:

```text
system boundary
module boundary
shared API response
pagination
HTTP status
ErrorCode convention
request/header/date-time convention
persistence boundary
integration boundary
```

Ví dụ:

```text
ApiResponse shape?
page zero-based?
HTTP 404/409/422 dùng khi nào?
Entity có được trả thẳng API không?
MongoDB hiện có dùng không?
```

→ đọc System Architecture.

Không copy bảng HTTP status/pagination/error sang file feature.

---

# 7. aloute-openapi.yaml

OpenAPI là source of truth cho **EXACT HTTP CONTRACT**.

Khi code Controller hoặc frontend gọi API:

```text
BẮT BUỘC ĐỌC OPENAPI.
```

OpenAPI sở hữu:

```text
method
path
path variable
query parameter
request body
response schema
endpoint-specific security
endpoint-specific responses
```

Correct workflow:

```text
Domain use case
↓
OpenAPI contract
↓
Controller / frontend implementation
```

Nếu cần endpoint chưa có:

```text
không tự invent path;
update OpenAPI trước hoặc cùng change.
```

---

# 8. ALOUTE_SPRING_BOOT_BACKEND_CONVENTION.md

Backend Convention sở hữu **HOW TO CODE BACKEND**:

```text
Controller
Service
Repository
DTO
ViewModel
Mapper
JPA Entity
Transaction
Validation
Exception
Query
N+1
Projection
Event
Async
Cache
Migration implementation
Testing
Naming
Dependency direction
```

Ví dụ:

```text
Controller có gọi Repository không?
@Transactional đặt đâu?
Entity có public setter toàn bộ không?
Request DTO có nhận authorId không?
Mapper có query DB không?
Có cần Service + Impl không?
```

→ đọc Backend Convention.

Backend Convention không invent business permission.

---

# 9. SPRING_SECURITY_CONVENTION.md

Security sở hữu:

```text
Authentication
Authorization enforcement
Spring Security
JWT
HttpOnly cookie
Access token
Refresh session
Refresh rotation
Logout
Password reset
Email verification
SecurityContext
Principal
CurrentUserProvider
Global Role
Global Authority
CSRF
WebSocket authentication/authorization
```

Business permission vẫn lấy từ Domain.

Ví dụ:

```text
Community MODERATOR được làm gì?
→ Domain

Spring enforce rule đó thế nào?
→ Security
```

---

# 10. ALOUTE_MEDIA_STORAGE_CONVENTION.md

Đọc khi task có:

```text
image
video
GIF
avatar
cover
post media
message media
Firebase Storage
upload
replace
delete
orphan cleanup
```

Media file sở hữu:

```text
SQL metadata vs Firebase binary
MediaAsset lifecycle
storageKey convention
upload flow
MIME/size config
READY/FAILED/DELETED
attachment rule
delete strategy
orphan cleanup
Firebase adapter boundary
```

Canonical:

```text
SQL
→ metadata + ownership

Firebase Storage
→ binary
```

Frontend/client không tự chọn owner/storageKey.

---

# 11. ALOUTE_REALTIME_WEBSOCKET_CONVENTION.md

Đọc khi task có:

```text
chat realtime
notification realtime
read receipt
reaction realtime
STOMP
WebSocket
subscribe
publish
reconnect
```

Realtime file sở hữu:

```text
/ws
/app
/topic
/user/queue
STOMP destination
event envelope
event type
persist-before-publish
subscription authorization
reconnect
dedupe
ordering
heartbeat
offline reconciliation
```

Canonical:

```text
WebSocket
→ realtime transport

SQL
→ source of truth
```

Không dùng WebSocket thay history/search/upload/CRUD thông thường.

---

# 12. ALOUTE_FRONTEND_CONVENTION.md

Đọc khi implement:

```text
Thymeleaf
Bootstrap
browser JavaScript
template
fragment
AJAX
form
loading/error/empty state
pagination UI
media preview
WebSocket client
responsive
accessibility
DOM
```

Frontend không invent:

```text
business permission
API schema
JWT rule
pagination contract
ErrorCode
WebSocket destination
Firebase storage path
```

---

# 13. ALOUTE_DOCKER_REVERSE_PROXY_CONVENTION.md

Đọc khi làm:

```text
Dockerfile
Compose
Nginx
HTTPS
WSS
VPS
port
network
volume
secret
healthcheck
backup
rollback
deploy
```

Nginx không làm business authorization.

Internal app/database port không được public tùy tiện.

---

# 14. CÁCH ĐỌC FILE KHI NHẬN TASK

Không đọc tất cả file cho mọi task.

Chọn theo task.

## Ví dụ: Like Post

```text
DOMAIN
→ Like business rule + constraint + visibility

OPENAPI
→ endpoint

SECURITY
→ authenticated current user

BACKEND
→ transaction/service/repository/race handling

FRONTEND
→ button/loading/optimistic UI
```

## Ví dụ: Post có ảnh/video

```text
DOMAIN
→ Post + MediaAsset + PostMedia

MEDIA
→ upload/storage lifecycle

OPENAPI
→ /api/media + /api/posts

SECURITY
→ current user

BACKEND
→ service/DTO/transaction

FRONTEND
→ preview/upload/composer
```

## Ví dụ: Chat realtime

```text
DOMAIN
→ Conversation/Member/Message permission

REALTIME
→ STOMP SEND/SUBSCRIBE/event

SECURITY
→ Principal + subscribe/send authorization

BACKEND
→ MessageService + persistence

MEDIA
→ attachment

FRONTEND
→ websocket-client + chat UI

DOCKER
→ WSS/Nginx Upgrade
```

## Ví dụ: Moderator ban member

```text
DOMAIN
→ CommunityBan + role + ModerationLog

OPENAPI
→ endpoint

SECURITY
→ scoped authorization

BACKEND
→ transaction/repository

FRONTEND
→ confirmation/error UX
```

## Ví dụ: Refresh token

```text
SECURITY
→ AuthSession/rotation/revoke/cookie

OPENAPI
→ /api/auth/refresh

BACKEND
→ service/repository/exception

ARCHITECTURE
→ shared error contract

DOCKER
→ secret/config
```

---

# 15. KHI NHIỀU FILE CÙNG LIÊN QUAN

Không hỏi:

```text
"file nào quan trọng hơn?"
```

Hỏi:

```text
"rule này thuộc loại gì?"
```

Ownership:

```text
WHO CAN DO WHAT?
→ DOMAIN

Shared HTTP rule?
→ SYSTEM ARCHITECTURE

Exact endpoint?
→ OPENAPI

Spring implementation?
→ BACKEND

Auth/authz?
→ SECURITY

Firebase lifecycle?
→ MEDIA

Realtime protocol/event?
→ REALTIME

UI?
→ FRONTEND

Deployment?
→ DOCKER/NGINX
```

Không có file nào luôn thắng mọi file.

---

# 16. KHI HAI FILE THẬT SỰ MÂU THUẪN

Nếu hai file cùng tuyên bố sở hữu cùng loại rule:

```text
đó là lỗi documentation.
```

Không silently chọn một.

Phải:

```text
1. xác định owner đúng;
2. sửa owner file;
3. file còn lại chỉ reference.
```

---

# 17. KHÔNG TỰ INVENT BUSINESS RULE

Nếu Domain chưa nói ai được làm action X:

```text
không dựa Facebook/Threads/LinkedIn/Reddit để tự quyết.
```

Không dùng "best practice" làm business requirement.

Nếu thiếu business decision:

```text
update Domain trước.
```

---

# 18. KHÔNG TỰ INVENT TECH CONTRACT

Nếu OpenAPI chưa có endpoint:

```text
không tự tạo /api/whatever.
```

Nếu Realtime chưa có event:

```text
không tự tạo event string mới.
```

Nếu Media chưa cho phép Firebase service khác:

```text
không tự thêm Firestore/FCM/Auth.
```

Nếu MongoDB đang unused:

```text
không tự thêm MongoRepository.
```

---

# 19. BUSINESS RULE KHÔNG NẰM TRONG CONTROLLER

Controller:

```text
transport boundary
```

Service/Policy:

```text
business workflow
resource authorization
state transition
```

Không duplicate role/ownership check rải ở nhiều Controller.

---

# 20. FRONTEND PERMISSION KHÔNG PHẢI SECURITY

Frontend có thể:

```text
hide button
disable action
show canEdit/canDelete
```

nhưng backend vẫn phải authorize lại.

```text
button hidden
≠ security
```

---

# 21. DATABASE CONSTRAINT KHÔNG THAY BUSINESS RULE

Ví dụ:

```text
UNIQUE(user_id, post_id)
```

ngăn duplicate Like.

Nhưng Service vẫn xử lý:

```text
visibility
block
resource state
permission
```

DB bảo vệ integrity.

Domain/Service bảo vệ meaning.

---

# 22. FIREBASE KHÔNG PHẢI BUSINESS DATABASE

Current Firebase scope:

```text
Firebase Storage
→ binary media
```

Không lưu User/Post/Community/Message/Permission vào Firebase chỉ vì project đã tích hợp Firebase.

---

# 23. WEBSOCKET KHÔNG PHẢI SOURCE OF TRUTH

Canonical:

```text
persist SQL
↓
commit
↓
publish realtime event
```

Không coi STOMP publish thành công là business state đã persist.

---

# 24. MONGODB

Current project:

```text
MongoDB = currently unused
```

Chỉ thêm khi:

```text
System Architecture
+
Domain
```

được cập nhật với use case/source-of-truth rõ.

---

# 25. KHI THÊM FEATURE MỚI

Thứ tự:

```text
1. Domain
   → business/entity/permission/invariant

2. Physical DB trong Domain
   → table/constraint/index nếu cần

3. OpenAPI
   → nếu có HTTP contract

4. Security
   → nếu có auth/security mechanism mới

5. Media
   → nếu thay media lifecycle

6. Realtime
   → nếu thêm event/destination/protocol

7. Backend Convention
   → chỉ khi project-wide coding rule thay đổi

8. Frontend Convention
   → chỉ khi project-wide UI rule thay đổi

9. System Architecture
   → nếu cross-cutting architecture thay đổi

10. Docker Convention
    → nếu runtime/deploy thay đổi
```

Không sửa mọi file chỉ vì thêm một feature bình thường.

---

# 26. KHI SỬA RULE CŨ

Ví dụ đổi Post reply permission:

```text
Domain
→ sửa meaning canonical
```

Sau đó update consumer cần thiết:

```text
OpenAPI schema
backend enum/code
frontend UI
tests
```

Không copy lại full business rule ở consumer.

---

# 27. SOURCE CODE PHẢI FOLLOW DOC

Nếu code khác canonical docs:

```text
không mặc định code hiện tại là đúng.
```

Xác định:

```text
code sai?
hay decision mới đã làm docs outdated?
```

Nếu business/architecture chưa đổi:

```text
fix code theo docs.
```

Nếu decision thật sự đổi:

```text
update source-of-truth cùng change.
```

---

# 28. CODE REVIEW CHECKLIST

Reviewer kiểm tra:

```text
Feature đúng Domain?
API đúng OpenAPI?
HTTP response đúng Architecture?
Controller/Service đúng Backend Convention?
Security check đúng Security Convention?
DB constraint đúng Domain physical model?
Media có bypass MediaStoragePort không?
Realtime có persist-before-publish không?
Frontend có invent permission/API không?
Docker/config có leak secret không?
```

Cross-cutting change phải update documentation trong cùng PR/change set.

---

# 29. TEST RULE

Test phải phản ánh canonical rule.

Ví dụ:

```text
Domain:
private account → FollowRequest

→ có test private follow.
```

```text
Domain:
one PollVote per user/poll

→ có test + constraint.
```

```text
Realtime:
persist before MESSAGE_CREATED

→ có test failure/commit ordering.
```

---

# 30. AI CODING AGENT RULE

Nếu AI code ALOUTE, AI phải:

```text
1. đọc RULE.md;
2. xác định loại task;
3. đọc owner files liên quan;
4. không invent business/architecture;
5. không duplicate canonical contract;
6. follow package/layer convention;
7. update source-of-truth nếu task thay canonical design.
```

AI không được tự thêm:

```text
microservice
Kafka
RabbitMQ
Redis
Elasticsearch
MongoDB
Firestore
FCM
Kubernetes
CQRS framework
```

chỉ vì đó là "best practice".

---

# 31. AI KHI GẶP GAP

Nếu gap chỉ là implementation nội bộ và không thay contract/business:

```text
chọn cách đơn giản nhất phù hợp convention.
```

Nếu gap ảnh hưởng:

```text
business behavior
API contract
security behavior
data model
media lifecycle
realtime protocol
deployment architecture
```

thì:

```text
không silently invent.
update source-of-truth trước/cùng code.
```

---

# 32. KHÔNG OVERENGINEER

ALOUTE là:

```text
Modular Monolith
```

Ưu tiên:

```text
simple
consistent
testable
maintainable
```

Không nâng thành distributed architecture nếu project requirement chưa yêu cầu.

---

# 33. CURRENT TECHNICAL BASELINE

```text
Backend
→ Spring Boot

Web
→ Spring MVC + Thymeleaf

Frontend
→ Bootstrap + browser JavaScript

Persistence
→ Spring Data JPA + one relational DB

Security
→ Spring Security + JWT + refresh session

Media
→ Firebase Storage

Realtime
→ Spring WebSocket + STOMP

Deployment
→ Docker + Nginx
```

Version cụ thể của Java/Spring/dependency sau khi project được tạo:

```text
pom.xml / build configuration
```

là source of truth cho version số.

---

# 34. QUICK LOOKUP

| Câu hỏi | Đọc file |
|---|---|
| Feature có gì? | `ALOUTE_DOMAIN_SYSTEM.md` |
| Ai được làm gì? | `ALOUTE_DOMAIN_SYSTEM.md` |
| Table/FK/index? | `ALOUTE_DOMAIN_SYSTEM.md` |
| ApiResponse/pagination/status? | `ALOUTE_SYSTEM_ARCHITECTURE.md` |
| Endpoint/request/response cụ thể? | `aloute-openapi.yaml` |
| Controller/Service/Repository? | `ALOUTE_SPRING_BOOT_BACKEND_CONVENTION.md` |
| Transaction/JPA/query/test? | `ALOUTE_SPRING_BOOT_BACKEND_CONVENTION.md` |
| JWT/Role/Principal? | `SPRING_SECURITY_CONVENTION.md` |
| Refresh/reset/verify? | `SPRING_SECURITY_CONVENTION.md` |
| Firebase upload/storage? | `ALOUTE_MEDIA_STORAGE_CONVENTION.md` |
| STOMP/chat realtime? | `ALOUTE_REALTIME_WEBSOCKET_CONVENTION.md` |
| Thymeleaf/Bootstrap/JS? | `ALOUTE_FRONTEND_CONVENTION.md` |
| Docker/Nginx/HTTPS/WSS? | `ALOUTE_DOCKER_REVERSE_PROXY_CONVENTION.md` |

---

# 35. FINAL RULE

Trước khi code, luôn hỏi:

```text
Rule này thuộc loại gì?
Owner file là file nào?
Tôi đã đọc owner đó chưa?
```

Nếu chưa:

```text
ĐỌC OWNER FILE TRƯỚC.
```

Nếu rule đã tồn tại:

```text
IMPLEMENT
KHÔNG REDEFINE.
```

Nếu rule chưa tồn tại và ảnh hưởng business/contract/architecture:

```text
UPDATE SOURCE OF TRUTH
RỒI MỚI CODE.
```

> **ALOUTE không cho phép mỗi developer tự có một architecture riêng trong đầu.**
>
> **Mọi quyết định dùng chung phải nằm ở đúng source-of-truth file.**
