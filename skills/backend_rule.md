# ALOUTE — SPRING BOOT BACKEND CONVENTION

> **Scope**
>
> File này là source of truth cho **cách tổ chức và implement backend bằng Spring Boot**.
>
> File này trả lời:
>
> ```text
> Backend code nằm ở đâu?
> Controller / Service / Repository chịu trách nhiệm gì?
> DTO / Mapper / Entity dùng thế nào?
> Transaction đặt ở đâu?
> JPA query viết thế nào?
> Exception đi qua layer nào?
> Validation đặt ở đâu?
> Event / async / cache dùng khi nào?
> Test backend ra sao?
> Naming và dependency rule thế nào?
> ```
>
> File này **không sở hữu**:
>
> ```text
> API response / pagination / HTTP status / error-code transport
> → ALOUTE_SYSTEM_ARCHITECTURE.md
>
> WHO CAN DO WHAT / business invariant / domain behavior
> → ALOUTE_DOMAIN_SYSTEM.md
>
> JWT / Spring Security / Principal / Role / Authorization implementation
> → SPRING_SECURITY_CONVENTION.md
>
> Thymeleaf / Bootstrap / browser JavaScript
> → ALOUTE_FRONTEND_CONVENTION.md
> ```

---

# 1. BACKEND ARCHITECTURE MODEL

ALOUTE backend dùng kiến trúc Monolith phân lớp chuẩn (Layered MVC Architecture):

```text
Spring Boot 3 (Java 21)
+
Spring MVC (Thymeleaf SSR + REST Controllers)
+
Spring Data JPA (Hibernate, PostgreSql/MySQL)
+
Spring Security (JWT, Cookie & Header)
+
Spring WebSocket (STOMP Broker)
+
Layered MVC Architecture (Controller → Service → Repository → Entity/DB)
```

Luồng mặc định:

```text
HTTP Request
   ↓
Controller (PageController hoặc ApiController)
   ↓
Service (Business Logic, Invariant, Transaction)
   ↓
Repository (Spring Data JPA Interface)
   ↓
Database (PostgreSQL/MySQL + Triggers + Stored Procedures)
```

Response:

```text
Database
   ↓
Repository
   ↓
Service (ModelMapperUtil / Entity to DTO)
   ↓
Response DTO / ViewModel
   ↓
Controller (Thymeleaf View hoặc ApiResponse<T>)
```

Không bỏ qua Service để Controller gọi Repository trực tiếp.

---

# 2. LAYERED MVC ARCHITECTURE & CODE OWNERSHIP

Theo quyết định thống nhất kiến trúc (commit `52f8622` và `ARCHITECTURE_TEMPLATE.md`), codebase được tổ chức theo Layered MVC để đảm bảo phân định ranh giới trách nhiệm rõ ràng giữa 4 thành viên:

```text
com.aloute
│
├── AlouteApplication.java
│
├── config/                  # App configs (WebMvc, WebSocket, ModelMapper)
├── security/                # Spring Security, JWT Filter, Principal (Khôi phụ trách)
├── common/                  # ApiResponse<T>, ErrorCode, Exceptions, ModelMapperUtil
│
├── controller/
│   ├── page/                # Thymeleaf SSR Controllers (Quân phụ trách)
│   └── api/                 # REST Controllers (Khôi, Bằng, Ngọc, Quân)
│
├── service/                 # Interface & Implementation business logic
│   ├── impl/
│   └── (AuthService, UserService, PostService, FeedService, ...)
│
├── repository/              # Spring Data JPA Repositories (Ngọc & Backend members)
├── entity/                  # JPA Entities (Đồng bộ với DB Schema của Ngọc)
└── dto/
    ├── request/             # Request payloads (@Valid)
    └── response/            # Response DTOs
```

### Phân công sở hữu code (Code Ownership Matrix):
- **Nguyễn Đoàn Minh Khôi**: `security/`, `service/AuthService`, `service/UserService`, `service/SocialService`, `controller/api/AuthController`, `controller/api/UserController`, `controller/api/SocialController`, Docker & VPS.
- **Nguyễn Duy Ngọc**: `entity/`, `repository/`, `service/PostService`, `service/CommunityService`, `service/MediaService`, Database Triggers, Stored Procedures, Indexes DDL.
- **Ngô Anh Bằng**: `config/WebSocketConfig`, `realtime/`, `service/FeedService`, `service/ConversationService`, `service/NotificationService`, `controller/api/ConversationController`, `controller/api/NotificationController`.
- **Phạm Minh Quân**: `controller/page/*`, Thymeleaf templates & static assets (`resources/templates/*`, `resources/static/*`), UI/UX Responsive, Form validation UI, E2E testing.

---

# 3. ROOT PACKAGE STRUCTURE & DEPENDENCY INVARIANTS

Quy tắc phụ thuộc giữa các package:
1. `controller` chỉ gọi `service`, KHÔNG ĐƯỢC gọi `repository`.
2. `service` gọi `repository`, `service` khác, hoặc `common/util`.
3. `repository` chỉ làm việc với `entity`.
4. `userId`/`authorId` trong mọi action bảo mật phải lấy từ `UserPrincipal` qua `CurrentUserProvider`, không nhận tùy tiện từ request body.

---

# 4. SPRING DATA JPA VỚI TRIGGERS VÀ STORED PROCEDURES

Vì hệ thống có Database Triggers tự động cập nhật counter (`like_count`, `reply_count`, `follower_count`, `member_count`) và Stored Procedures (Nguyễn Duy Ngọc phụ trách):

### 4.1. Đồng bộ Triggers với JPA Entity:
- Sau khi insert/update các quan hệ tương tác (ví dụ: `PostLike`, `Follow`), counter trên bảng chính được cập nhật ngầm dưới DB.
- Service cần gọi:
```java
entityManager.flush();
entityManager.refresh(post); // Làm mới state entity từ database để lấy count mới nhất từ Trigger
```

### 4.2. Gọi Stored Procedures qua Repository:
- Khai báo annotation `@Procedure` trong JPA Repository:
```java
@Procedure(procedureName = "sp_soft_delete_post_cascade")
void softDeletePostCascade(@Param("p_post_id") UUID postId, @Param("p_user_id") UUID userId);
```
```

Không bắt buộc tạo folder rỗng.

Feature nhỏ có thể đơn giản hơn:

```text
feature/topic/
├── controller/
├── dto/
├── entity/
├── repository/
└── service/
```

Architecture phải phục vụ readability, không phải tạo ceremony.

---

# 5. DEPENDENCY DIRECTION

Dependency mặc định:

```text
Controller
   ↓
Service
   ↓
Repository
```

Có thể:

```text
Service
→ Mapper

Service
→ Policy

Service
→ Integration Port

Service
→ Public Service của feature khác
```

Không được:

```text
Repository → Service
Repository → Controller
Entity → Controller
Entity → Repository
DTO → Repository
Mapper → Controller
Service → Thymeleaf
Repository → Firebase SDK
```

Low-level layer không import high-level web layer.

---

# 6. CONTROLLER RESPONSIBILITY

Controller chỉ chịu trách nhiệm HTTP/web boundary.

Controller được phép:

- Nhận path variable.
- Nhận query parameter.
- Bind request body/form.
- Validate request DTO.
- Lấy authenticated principal/current user abstraction.
- Gọi Service.
- Chuyển kết quả sang HTTP response hoặc Thymeleaf Model.
- Chọn view/redirect.

Controller không được:

- Viết business rule dài.
- Query Repository trực tiếp.
- Tự quản lý transaction.
- Tự kiểm tra ownership bằng query rải rác.
- Gọi Firebase SDK trực tiếp.
- Tự build SQL.
- Tự update nhiều entity trong một workflow.
- Tự định nghĩa API response contract khác canonical.

---

# 7. PAGE CONTROLLER VS API CONTROLLER

Tách responsibility.

## Page Controller

```java
@Controller
```

Ví dụ:

```text
PostPageController
CommunityPageController
ProfilePageController
```

Chịu trách nhiệm:

```text
route
model
view
redirect
flash message
```

## API Controller

```java
@RestController
```

Ví dụ:

```text
PostApiController
CommunityApiController
MessageApiController
```

Chịu trách nhiệm:

```text
JSON request
JSON response
HTTP status mapping thông qua canonical contract
```

Không gom page + API behavior vào một controller khổng lồ nếu feature đã lớn.

---

# 8. CONTROLLER METHOD SIZE

Controller method nên mỏng.

Ví dụ:

```java
@PostMapping("/api/posts")
public ApiResponse<PostResponse> create(
        @Valid @RequestBody CreatePostRequest request
) {
    Long currentUserId = currentUserProvider.requireUserId();

    PostResponse result =
        postService.create(currentUserId, request);

    return ApiResponse.success(result);
}
```

Không làm:

```java
@PostMapping(...)
public ... create(...) {

    User user = userRepository.findById(...);

    if (...) {
        ...
    }

    Post post = new Post(...);

    postRepository.save(post);

    notificationRepository.save(...);

    firebaseStorage.upload(...);

    ...
}
```

---

# 9. SERVICE RESPONSIBILITY

Service/Application layer là nơi điều phối use case.

Service chịu trách nhiệm:

- Business workflow.
- Domain validation.
- Authorization policy invocation.
- Transaction boundary.
- Entity state transition.
- Repository coordination.
- Integration coordination.
- Domain/application event creation.
- Mapping result khi phù hợp.

Service không chịu trách nhiệm:

- Render HTML.
- Bootstrap/CSS.
- Build arbitrary HTTP response shape.
- Parse raw JWT.
- Đọc trực tiếp Servlet request nếu không thật sự cần.
- Viết SQL string khi Repository abstraction đủ dùng.

---

# 10. SERVICE INTERFACE

Không bắt buộc mọi service đều phải có:

```text
Interface + Impl
```

Nếu chỉ có một implementation và không cần abstraction:

```java
@Service
public class PostService {
}
```

là hợp lệ.

Chỉ tạo:

```text
PostService
PostServiceImpl
```

khi interface có giá trị thật:

- Có nhiều implementation.
- Có external boundary.
- Có test seam rõ.
- Có module contract cần tách.
- Có khả năng swap implementation thật.

Không tạo interface chỉ vì "Spring project thường làm vậy".

---

# 11. USE-CASE ORIENTED SERVICE METHODS

Method đặt theo business action:

```text
createPost
editPost
deletePost
likePost
joinCommunity
leaveCommunity
sendMessage
votePoll
```

Tránh generic service kiểu:

```text
save
process
handle
execute
doAction
updateData
```

nếu không mô tả use case.

---

# 12. SERVICE SCOPE

Một service không nên ôm toàn domain.

Sai:

```text
SocialNetworkService
```

chứa:

```text
post
comment
community
chat
notification
user
```

Đúng hơn:

```text
PostService
CommunityService
ConversationService
MessageService
NotificationService
```

Service tương ứng business capability, không nhất thiết tương ứng một table.

---

# 13. TRANSACTION RULE

Transaction boundary mặc định đặt ở Service.

Mutation:

```java
@Transactional
public PostResponse createPost(...) {
}
```

Read-only query lớn:

```java
@Transactional(readOnly = true)
public PostResponse getPost(...) {
}
```

Không đặt `@Transactional` tùy tiện ở:

```text
Controller
Mapper
DTO
Utility
```

Repository method của Spring Data đã có transaction behavior riêng, nhưng business transaction vẫn được xác định từ Service.

---

# 14. TRANSACTION SIZE

Transaction nên bao trùm state thay đổi cần atomic trong relational database.

Ví dụ:

```text
Like Post
├── insert PostLike
└── update likeCount
```

có thể cùng transaction.

Không kéo transaction dài qua:

```text
HTTP remote call lâu
Firebase upload lớn
WebSocket wait
sleep
external retry loop
```

Nếu workflow có DB + external system, phải có failure/reconciliation strategy.

---

# 15. SELF-INVOKING TRANSACTION

Không dựa vào:

```java
this.otherTransactionalMethod()
```

để kỳ vọng Spring proxy mở transaction mới.

Nếu cần transaction boundary riêng:

- Tách service hợp lý.
- Dùng transaction template khi có lý do rõ.
- Thiết kế lại orchestration.

Không dùng workaround khó hiểu chỉ để ép proxy.

---

# 16. ENTITY RESPONSIBILITY

JPA Entity biểu diễn persistent domain state.

Entity được phép:

- Giữ state.
- Giữ quan hệ.
- Có method nhỏ bảo vệ invariant nội tại.
- Có helper thay đổi state có meaning.

Ví dụ:

```java
post.markDeleted(now);
community.incrementMemberCount();
```

Entity không được:

- Inject Repository.
- Inject Service.
- Gọi Firebase.
- Đọc SecurityContext.
- Trả HTTP response.
- Biết Controller.
- Parse JSON.

---

# 17. ENTITY SETTER RULE

Không mặc định tạo public setter cho mọi field.

Ưu tiên:

```text
constructor
factory
meaningful method
controlled setter
```

Ví dụ:

```java
post.updateContent(newContent);
post.markDeleted(now);
```

tốt hơn:

```java
post.setContent(...);
post.setDeletedAt(...);
post.setUser(...);
post.setLikeCount(...);
```

ở mọi nơi.

Mục tiêu:

```text
state transition dễ trace
```

---

# 18. ENTITY ID

ID type phải thống nhất trong project.

Ví dụ:

```java
Long
```

hoặc UUID nếu architecture đã chọn.

Không để:

```text
User = Long
Post = Integer
Community = String random
Message = UUID
```

mà không có lý do.

Nếu hiện project đã chọn `Long`, giữ nhất quán cho relational entity trừ use case đặc biệt.

---

# 19. BASE ENTITY

Có thể tạo base class cho common audit field:

```text
createdAt
updatedAt
```

Ví dụ:

```java
@MappedSuperclass
public abstract class BaseEntity {
}
```

Không đưa mọi field vào base class.

Soft-delete:

```text
deletedAt
```

chỉ thuộc entity thực sự có semantics soft-delete.

Không đặt `deletedAt` vào toàn bộ entity chỉ để "sau này biết đâu cần".

---

# 20. TIMESTAMP

Backend dùng type machine-friendly:

```java
Instant
```

hoặc type thời gian được team chốt thống nhất.

Không dùng:

```java
String createdAt;
```

trong Entity.

Conversion sang API format theo architecture contract.

Không format localized date trong entity/service.

---

# 21. ENUM

Business enum dùng Java enum.

Ví dụ:

```java
public enum PostVisibility {
    PUBLIC,
    FOLLOWERS,
    MENTIONED
}
```

JPA enum phải lưu theo string:

```java
@Enumerated(EnumType.STRING)
```

Không dùng ordinal:

```text
0
1
2
```

vì đổi thứ tự enum có thể phá dữ liệu.

Canonical enum business thuộc Domain System.

Backend chỉ implement enum tương ứng.

---

# 22. ENTITY RELATIONSHIP

Mapping JPA phải có ownership rõ.

Cẩn thận với:

```text
@OneToMany
@ManyToMany
CascadeType.ALL
FetchType.EAGER
```

Không dùng tự động vì tiện.

Ưu tiên:

```text
LAZY
```

cho collection relationship trừ khi có use case chứng minh khác.

Không bật `EAGER` để chữa `LazyInitializationException`.

---

# 23. CASCADE RULE

Cascade phải phản ánh lifecycle thật.

Dùng cascade khi child thực sự phụ thuộc lifecycle parent.

Ví dụ có thể hợp lý:

```text
Post → PostMedia
```

nếu media metadata không tồn tại độc lập.

Không dùng:

```java
cascade = CascadeType.ALL
```

trên relationship lớn chỉ để save dễ.

Đặc biệt thận trọng với:

```text
User
Community
Post
Conversation
```

vì cascade sai có thể xóa hàng loạt dữ liệu.

---

# 24. MANY-TO-MANY

Không mặc định dùng direct:

```java
@ManyToMany
```

khi relationship có business field.

Ví dụ:

```text
CommunityMember
```

có:

```text
role
joinedAt
status
```

thì phải là entity relationship riêng.

Tương tự:

```text
ConversationMember
PollVote
```

nếu cần metadata/business behavior.

---

# 25. BIDIRECTIONAL RELATIONSHIP

Không tạo bidirectional relation chỉ vì JPA hỗ trợ.

Chỉ dùng khi cả hai hướng navigation đều có value.

Ví dụ nếu thường chỉ cần:

```text
Post → User
```

không bắt buộc User phải giữ:

```text
List<Post>
```

Khối collection lớn ở parent dễ gây:

- memory issue.
- lazy load khó kiểm soát.
- serialization issue.
- accidental cascade.

---

# 26. DTO TYPES

Phân biệt:

```text
Request DTO
Response DTO
ViewModel
Entity
```

Ví dụ:

```text
CreatePostRequest
UpdatePostRequest

PostResponse
PostSummaryResponse

PostDetailViewModel
PostCardViewModel
```

Không tạo:

```text
PostDto
```

rồi dùng cùng một class cho:

```text
create
update
response
view
```

nếu các use case khác contract.

---

# 27. REQUEST DTO

Request DTO chỉ chứa field client được phép gửi.

Ví dụ:

```java
public record CreatePostRequest(
    String content,
    PostVisibility visibility,
    Boolean allowReply,
    Boolean allowQuote
) {}
```

Không cho client gửi nếu server tự xác định:

```text
authorId
ownerId
createdAt
likeCount
role
isAdmin
```

Identity/current user đến từ security abstraction.

---

# 28. RESPONSE DTO

Response DTO chỉ trả field consumer cần.

Không expose entity graph.

Không trả:

```text
User.passwordHash
RefreshToken.token
internal moderation note
Firebase credential
```

Response DTO có thể chứa derived field phục vụ UI:

```text
canEdit
canDelete
isLiked
isFollowing
```

nếu backend cần cung cấp semantic capability/view state.

Business meaning của các capability lấy từ Domain System.

---

# 29. VIEW MODEL

Thymeleaf page dùng ViewModel khi page cần shape khác API response.

Ví dụ:

```text
ProfilePageViewModel
CommunityPageViewModel
FeedPageViewModel
```

Không bắt API DTO phải phục vụ mọi nhu cầu template.

Không để Thymeleaf tự navigate entity graph sâu.

---

# 30. JAVA RECORD

DTO immutable có thể dùng:

```java
record
```

nếu Java version của project hỗ trợ và team thống nhất.

Entity không dùng record.

Ví dụ:

```java
public record PostSummaryResponse(
    Long id,
    String content,
    String authorName
) {}
```

---

# 31. MAPPER

Mapper chịu trách nhiệm conversion:

```text
Entity → Response DTO
Entity → ViewModel
Request DTO → initial value
```

Mapper không được:

- Query Repository.
- Gọi Service.
- Check permission bằng DB.
- Gọi Firebase.
- Mở transaction.
- Chứa workflow.

Mapper nên gần pure function nhất có thể.

---

# 32. MAPPER STRATEGY

Có thể dùng manual mapper.

Ví dụ:

```java
@Component
public class PostMapper {
}
```

Không bắt buộc thêm MapStruct nếu project chưa có nhu cầu.

Nếu dùng MapStruct, toàn project phải thống nhất convention.

Không mix:

```text
feature A manual
feature B ModelMapper
feature C MapStruct
feature D BeanUtils
```

một cách ngẫu nhiên.

---

# 33. REPOSITORY RESPONSIBILITY

Repository chịu trách nhiệm:

- CRUD persistence.
- Query data.
- Existence check.
- Aggregate lookup.
- Projection query khi cần.
- Locking/query optimization khi thật sự cần.

Repository không chịu trách nhiệm:

- Business workflow.
- Authorization decision.
- Send notification.
- Upload media.
- WebSocket publish.
- Build API response.

---

# 34. REPOSITORY NAMING

Spring Data method phải dễ đọc.

Ví dụ:

```java
findByUsername(...)
existsByEmail(...)
findByCommunityIdAndUserId(...)
```

Nếu method name quá dài:

```text
findByAAndBAndCAndDAndEAndF...
```

thì dùng:

- `@Query`.
- Specification nếu thật sự phù hợp.
- Custom Repository.
- QueryDSL nếu project đã chọn.

Không cố nhét query phức tạp vào method name.

---

# 35. FIND VS REQUIRE

Repository:

```java
Optional<Post> findById(...)
```

Service quyết định:

```text
không tồn tại → business exception nào
```

Không để Repository tự throw HTTP-specific exception.

Có thể tạo helper/service method:

```java
Post requirePost(Long postId)
```

trong domain service phù hợp nếu được dùng nhiều và semantics rõ.

---

# 36. EXISTS QUERY

Khi chỉ cần biết có tồn tại:

```java
existsBy...
```

không load toàn entity.

Ví dụ:

```text
đã like chưa?
đã join community chưa?
username tồn tại chưa?
```

Nhưng nếu sau đó vẫn cần entity ngay lập tức thì tránh query duplicate.

---

# 37. N+1 QUERY

Không để view/feed load:

```text
20 posts
→ 20 author query
→ 20 media query
→ 20 like-state query
→ 20 permission query
```

Phải review query shape.

Có thể dùng:

```text
fetch join
@EntityGraph
projection
batch fetching
purpose-built query
```

theo use case.

Không sửa N+1 bằng cách đổi tất cả relationship sang `EAGER`.

---

# 38. PROJECTION

List/feed có thể dùng projection khi không cần full entity.

Ví dụ:

```text
PostSummaryProjection
NotificationListProjection
ConversationSummaryProjection
```

Projection là optimization/read model.

Không dùng projection làm entity mutation source.

---

# 39. PAGINATION IN REPOSITORY/SERVICE

Backend implementation dùng Spring Data:

```text
Pageable
Page<T>
Slice<T>
```

theo use case.

Nhưng:

```text
request parameter name
default size
maximum size
response meta shape
```

thuộc duy nhất:

```text
ALOUTE_SYSTEM_ARCHITECTURE.md
```

Backend convention chỉ quy định implementation không tự invent pagination contract.

---

# 40. PAGE VS SLICE

Dùng:

```text
Page
```

khi cần:

```text
totalElements
totalPages
```

Dùng:

```text
Slice
```

khi chỉ cần biết:

```text
hasNext
```

và muốn tránh count query.

Feed/infinite stream có thể cân nhắc Slice/cursor theo architecture.

Không chọn Page mặc định cho mọi list nếu total count không có giá trị.

---

# 41. SORT

Không nhận arbitrary property rồi đưa thẳng vào query nếu có security/performance risk.

Nếu endpoint chỉ cho sort:

```text
createdAt
popular
relevant
```

thì map whitelist.

Không cho client sort theo internal/sensitive column tùy ý.

Contract sort public thuộc architecture/API design.

---

# 42. CUSTOM QUERY

Custom query phải:

- Có tên thể hiện use case.
- Chỉ select field cần thiết khi phù hợp.
- Có index strategy hỗ trợ.
- Không chứa logic business khó hiểu.
- Có test nếu query quan trọng.

Không viết native SQL nếu JPQL/projection giải quyết sạch.

Native query được dùng khi có lợi ích rõ và được cô lập.

---

# 43. DATABASE CONSTRAINT

Invariant quan trọng nên được bảo vệ ở nhiều layer khi phù hợp:

```text
application validation
+
database constraint
```

Ví dụ:

```text
unique username
unique email
unique like(user_id, post_id)
unique membership(user_id, community_id)
```

Database constraint không thay business error handling.

Service phải translate violation quan trọng sang business exception phù hợp.

---

# 44. OPTIMISTIC LOCKING

Entity có concurrent update quan trọng có thể dùng:

```java
@Version
```

khi race condition thực tế cần bảo vệ.

Không thêm `@Version` cho mọi entity chỉ vì "best practice".

Các hot counter như like/view có thể cần strategy riêng; không blindly dùng load → increment → save dưới traffic lớn.

---

# 45. COUNTER UPDATE

Cached counter được Domain System định nghĩa là derived/denormalized.

Backend phải tránh:

```text
read current count
count++
save
```

nếu concurrent request có thể làm lost update.

Có thể dùng atomic update query khi cần:

```text
UPDATE ... SET like_count = like_count + 1
```

hoặc strategy khác phù hợp.

Không coi counter là source of truth duy nhất.

---

# 46. SOFT DELETE IMPLEMENTATION

Chỉ entity có domain semantics soft-delete mới dùng.

Query đọc phải thống nhất việc exclude deleted resource.

Không để mỗi service tự nhớ:

```java
if (deletedAt != null)
```

khắp nơi.

Có thể dùng repository query convention hoặc reusable persistence pattern.

Business visibility của deleted resource lấy từ Domain System.

---

# 47. VALIDATION LAYERS

## Request validation

Dùng Jakarta Bean Validation:

```text
@NotNull
@NotBlank
@Size
@Email
@Min
@Max
```

trên Request DTO.

## Business validation

Service/domain:

```text
không follow chính mình
poll đã đóng
post không cho reply
không phải conversation member
```

## Persistence validation

DB:

```text
NOT NULL
UNIQUE
FOREIGN KEY
CHECK khi phù hợp
```

Không cố nhét business rule database-dependent vào custom annotation phức tạp nếu Service rõ hơn.

---

# 48. CUSTOM VALIDATOR

Custom Bean Validation chỉ dùng cho validation structural/request-level có tính reusable.

Không inject nhiều Repository vào validator để biến validator thành hidden service.

Nếu validation cần:

```text
DB
current user
ownership
resource state
```

thường thuộc Service/domain.

---

# 49. EXCEPTION TYPES

Cây kế thừa Exception chính thức tại `common/exception/`:

```text
RuntimeException
└── AppException (Base unchecked exception, bọc ErrorCode hoặc HttpStatus/code/message)
    ├── ApiException (Tương thích REST API controllers & services)
    ├── ResourceNotFoundException (HTTP 404 - RESOURCE_NOT_FOUND hoặc domain _NOT_FOUND)
    ├── BadRequestException (HTTP 400 - BAD_REQUEST)
    ├── ForbiddenException (HTTP 403 - FORBIDDEN hoặc domain permissions)
    ├── UnauthorizedException (HTTP 401 - AUTH_UNAUTHENTICATED)
    └── ConflictException (HTTP 409 - CONFLICT hoặc domain conflicts)

RequestValidationException (Bọc Map<String, List<String>> fieldErrors -> HTTP 422 VALIDATION_FAILED)
```

Không tạo class exception cho mọi một dòng validation nhỏ nếu ErrorCode đã đủ biểu đạt.

---

# 50. EXCEPTION OWNERSHIP

Service throw semantic exception.

Global handler chuyển exception thành transport response theo:

```text
ALOUTE_SYSTEM_ARCHITECTURE.md
```

Service không trả:

```java
ResponseEntity
ApiError
HttpStatus
```

trừ trường hợp service đó thực sự là web adapter, không phải business service.

---

# 51. GLOBAL EXCEPTION HANDLER

Cross-cutting handler đặt trong:

```text
common/exception/GlobalExceptionHandler.java
```

Chịu trách nhiệm bao phủ toàn bộ:

- `AppException` (bao gồm `ApiException`, `ResourceNotFoundException`, `ForbiddenException`, `ConflictException`, v.v.)
- `RequestValidationException` (HTTP 422 `VALIDATION_FAILED` kèm fieldErrors)
- `MethodArgumentNotValidException` (Bean validation DTO `@Valid`)
- `ConstraintViolationException` (Param/Entity validation)
- `AccessDeniedException` (Spring Security authorization failure -> HTTP 403 `AUTH_FORBIDDEN`)
- `AuthenticationException` (Spring Security authentication failure -> HTTP 401 `AUTH_UNAUTHENTICATED`)
- `DataIntegrityViolationException` (Database unique/FK conflict -> HTTP 409 `CONFLICT`)
- `HttpRequestMethodNotSupportedException` (HTTP 405 `METHOD_NOT_ALLOWED`)
- `HttpMediaTypeNotSupportedException` (HTTP 415 `UNSUPPORTED_MEDIA_TYPE`)
- `HttpMessageNotReadableException` / `MethodArgumentTypeMismatchException` (HTTP 400 `BAD_REQUEST`)
- `NoResourceFoundException` (HTTP 404 `RESOURCE_NOT_FOUND`)
- `MaxUploadSizeExceededException` (HTTP 422 `MEDIA_TOO_LARGE`)
- `Exception` (Unexpected server failure -> HTTP 500 `INTERNAL_ERROR`)
- Gán `traceId` chuẩn (từ header / attribute hoặc `UUID.randomUUID()`).
- Logging theo level phù hợp (`log.warn` cho client/business error, `log.error` cho unexpected failure).

Không chứa business decision.
Không swallow exception rồi trả success.

---

# 52. ERROR CODE USAGE

Backend dùng canonical `ErrorCode` catalog (`com.aloute.common.exception.ErrorCode`) thuộc architecture contract (`ALOUTE_SYSTEM_ARCHITECTURE.md`).

Enum `ErrorCode` đóng gói:
- `code`: chuỗi định danh dạng `<DOMAIN>_<REASON>` (ổn định cho client).
- `status`: mã `HttpStatus` chuẩn RESTful.
- `defaultMessage`: thông điệp mô tả lỗi thân thiện với người dùng.

Không mỗi feature tạo format error code khác.
Không hardcode chuỗi mã lỗi tùy tiện không theo quy ước.

---

# 53. COMMON API CLASSES

`common/api/` có thể chứa:

```text
ApiResponse<T>
ApiError
PageMeta
PageResponse<T> nếu architecture chọn wrapper này
```

Exact field contract thuộc:

```text
ALOUTE_SYSTEM_ARCHITECTURE.md
```

Backend file này chỉ quy định:

```text
dùng chung một implementation
không tự tạo wrapper ở từng feature
```

---

# 54. SECURITY INTERACTION

Backend Service không parse JWT.

Không dùng:

```text
HttpServletRequest
Authorization header
JWT claims
```

rải trong domain service để lấy current user.

Dùng abstraction canonical trong:

```text
SPRING_SECURITY_CONVENTION.md
```

Ví dụ:

```text
CurrentUserProvider
```

Permission implementation cũng theo Security Convention.

Business permission source theo Domain System.

---

# 55. POLICY / AUTHORIZATION CALL

Flow mutation nhạy cảm:

```text
load target
↓
policy.require...
↓
validate business state
↓
mutate
↓
save
```

Hoặc nếu query có thể combine permission hiệu quả, được tối ưu nhưng semantics phải giữ rõ.

Không chỉ ẩn button frontend rồi bỏ backend check.

---

# 56. FIREBASE INTEGRATION

Firebase code nằm ở:

```text
integration/firebase/
```

Business service phụ thuộc interface/port:

```text
MediaStorageService
```

Implementation:

```text
FirebaseMediaStorageAdapter
```

Không để:

```text
PostController
PostService
```

phụ thuộc trực tiếp Firebase SDK nếu có thể tránh.

Mục tiêu:

```text
business code không phụ thuộc vendor SDK
```

---

# 57. MEDIA STORAGE PORT

Ví dụ:

```java
public interface MediaStorageService {

    StoredMedia upload(MediaUploadCommand command);

    void delete(String storageKey);
}
```

Domain/API naming cụ thể tùy media model.

Không đưa Firebase-specific object vào contract business:

```text
Blob
Bucket
StorageReference
FirebaseMetadata
```

nếu business không cần.

---

# 58. MONGODB BOUNDARY

Nếu MongoDB được dùng:

- Code nằm trong module/infrastructure rõ.
- Dùng Spring Data MongoDB.
- Không annotate cùng entity bằng cả JPA và Mongo mapping.
- Không copy aggregate SQL sang Mongo tùy tiện.
- Phải xác định source of truth.

Nếu chưa có use case được Architecture chấp nhận:

```text
không tạo MongoRepository.
```

---

# 59. WEB SOCKET BACKEND STRUCTURE

Có thể tổ chức:

```text
realtime/
├── config/
├── messaging/
├── notification/
└── presence/
```

hoặc feature-local handler nếu scope nhỏ.

WebSocket adapter chỉ:

- Nhận message.
- Validate transport input.
- Xác định principal.
- Gọi application service.
- Publish response/event.

Không chứa business workflow chính.

---

# 60. WEBSOCKET SERVICE BOUNDARY

Ví dụ:

```text
WebSocket Message Handler
   ↓
MessageService
   ↓
ConversationPolicy
   ↓
MessageRepository
   ↓
RealtimePublisher
```

Không bypass Service:

```text
WebSocket Handler
→ MessageRepository.save(...)
```

---

# 61. EVENT

Event dùng khi side effect không nên làm coupling trực tiếp.

Ví dụ:

```text
PostLiked
PostReplied
UserFollowed
MessageSent
CommunityJoined
```

Producer không cần biết mọi consumer.

Ví dụ:

```text
PostService
   ↓
PostLikedEvent
   ├── Notification listener
   └── Analytics listener
```

Không tạo event bus cho CRUD đơn giản nếu direct call rõ hơn.

---

# 62. DOMAIN EVENT VS INTEGRATION EVENT

Trong modular monolith hiện tại có thể dùng simple application/domain event.

Không cần thiết kế Kafka-style integration event nếu chưa có distributed architecture.

Tên event phải ở past tense:

```text
PostLiked
UserFollowed
MessageSent
```

thay vì command:

```text
LikePost
FollowUser
```

---

# 63. EVENT + TRANSACTION

Cần phân biệt side effect:

```text
phải thành công cùng transaction
```

với:

```text
có thể xử lý sau commit
```

Ví dụ notification có thể cân nhắc sau commit nếu business cho phép.

Không publish external effect trước khi DB commit rồi giả định mọi thứ nhất quán.

---

# 64. @ASYNC

Không dùng:

```java
@Async
```

chỉ để method "nhanh hơn".

Chỉ dùng khi:

- Side effect không cần trả kết quả ngay.
- Failure semantics rõ.
- Thread/executor được config.
- Logging/retry phù hợp.

Không dùng `@Async` cho operation cần transaction atomic với request.

---

# 65. SCHEDULER

Scheduled job chỉ dùng cho background maintenance/workflow thật.

Ví dụ:

```text
cleanup expired token
recalculate stale metric
scheduled notification job
```

Không nhét critical request workflow vào scheduler chỉ để tránh xử lý sync.

Mỗi job cần:

- owner.
- idempotency.
- logging.
- failure handling.

---

# 66. CACHE

Cache chỉ thêm khi có bottleneck/use case.

Candidate có thể:

```text
public profile
community info
popular topic
read-heavy reference data
```

Không cache permission/resource state mù.

Không cache trước khi xác định:

```text
key
TTL
invalidated when?
source of truth?
```

Cache không phải source of truth.

---

# 67. CACHE ANNOTATION

Nếu dùng Spring Cache:

```text
@Cacheable
@CacheEvict
@CachePut
```

phải có invalidation strategy rõ.

Không rải annotation khắp service mà không biết mutation nào invalidates.

Dynamic permission/private data cần đặc biệt cẩn thận.

---

# 68. CONFIGURATION

Config class chỉ wiring/configure infrastructure.

Ví dụ:

```text
WebMvcConfig
JpaConfig
FirebaseConfig
WebSocketConfig
JacksonConfig
```

Security config theo security convention.

Không đặt business data initialization lớn trong config class.

---

# 69. CONFIGURATION PROPERTIES

External config nên bind bằng:

```java
@ConfigurationProperties
```

khi có nhóm property.

Ví dụ:

```text
firebase.*
storage.*
app.*
jwt.*  → security scope
```

Không gọi:

```java
System.getenv(...)
```

rải khắp feature code.

---

# 70. PROFILE

Environment:

```text
dev
test
prod
```

có thể dùng Spring Profile.

Không viết business logic:

```java
if (profile.equals("prod")) {
   ...
}
```

rải trong service.

Environment-specific behavior phải nằm ở configuration/adapter boundary.

---

# 71. SECRET

Không commit:

```text
DB password
JWT key
Firebase service-account private key
production credential
```

Không hardcode secret trong Java.

Không log secret.

---

# 72. LOGGING

Dùng logging framework của Spring ecosystem.

Không dùng:

```java
System.out.println(...)
```

trong production code.

Log theo event/use case:

```text
post created
login failed
community member removed
external storage failure
unexpected exception
```

Không log dữ liệu nhạy cảm.

---

# 73. LOG LEVEL

```text
ERROR
→ unexpected/system failure cần attention

WARN
→ suspicious/recoverable condition

INFO
→ meaningful lifecycle/business operation có ích

DEBUG
→ developer diagnostic

TRACE
→ rất chi tiết, hiếm dùng
```

Không log mọi request payload ở INFO nếu chứa user content/sensitive data.

---

# 74. CORRELATION / TRACE ID

Request có thể có trace ID để liên kết log.

Transport contract thuộc Architecture.

Backend implementation nên propagate trace ID qua logging context nếu project dùng.

Không tự phát minh nhiều ID:

```text
requestId
traceId
correlationId
trackingId
```

cho cùng một purpose.

---

# 75. UTILITY

Utility phải:

- Stateless nếu có thể.
- Pure nếu có thể.
- Có trách nhiệm rõ.
- Không phụ thuộc web/domain nặng.

Ví dụ:

```text
SlugUtils
StringNormalizer
DateTimeUtils
```

Tránh:

```text
CommonUtils.java
Helper.java
AppUtils.java
```

hàng nghìn dòng.

Business logic không được giấu trong util.

---

# 76. CONSTANT

Constant chỉ tạo cho value có meaning/stability.

Ví dụ:

```text
MAX_POST_LENGTH
DEFAULT_AVATAR_PATH
```

Nhưng API pagination default canonical thuộc Architecture; backend constant chỉ mirror implementation và phải lấy từ một config/common location.

Không constant hóa mọi string dùng một lần.

---

# 77. MAGIC NUMBER

Không viết:

```java
if (content.length() > 500) ...
```

ở 5 service.

Nếu `500` là domain limit, phải có một source semantic rõ.

Nếu business requirement nằm Domain System, implementation constant backend phải map đúng requirement đó.

---

# 78. NAMING — CLASS

Use meaningful suffix:

```text
Controller
Service
Repository
Mapper
Policy
Validator
Listener
Publisher
Adapter
Client
Properties
Config
Request
Response
ViewModel
Entity
```

Không dùng:

```text
Manager
Processor
Handler
Helper
Util
```

nếu responsibility thực tế có tên tốt hơn.

`Handler` chỉ nên dùng khi đúng nghĩa framework/event/error handling.

---

# 79. NAMING — METHOD

Method là động từ:

```text
createPost
updateProfile
findPost
requirePost
joinCommunity
leaveCommunity
sendMessage
publishNotification
```

Boolean:

```text
existsBy...
isMember
canEdit
hasPermission
```

Không:

```text
processData
handleEverything
doLogic
executeStuff
```

---

# 80. NAMING — VARIABLES

Dùng business name:

```text
currentUserId
postId
communityId
membership
conversation
```

Không:

```text
id1
id2
obj
data
temp
value2
```

nếu scope không đủ nhỏ và rõ.

---

# 81. NULL

Không dùng `null` tùy tiện làm nhiều nghĩa:

```text
not found
not loaded
not allowed
empty
```

Repository lookup có thể dùng:

```text
Optional<T>
```

Collection trả:

```text
empty list
```

thay vì `null`.

DTO optional field có thể nullable khi contract có nghĩa rõ.

---

# 82. OPTIONAL

`Optional` chủ yếu dùng cho return value lookup.

Không dùng `Optional` làm:

- Entity field.
- JPA relationship field.
- Request DTO field mặc định.
- Method parameter tùy tiện.

Không gọi:

```java
optional.get()
```

mà chưa bảo đảm presence.

---

# 83. COLLECTION

Method trả collection nên trả empty collection nếu không có dữ liệu.

Không trả null.

Entity collection nên được initialize hợp lý:

```java
new ArrayList<>()
new HashSet<>()
```

nhưng không expose mutable internal collection bừa bãi nếu invariant cần bảo vệ.

---

# 84. IMMUTABILITY

DTO ưu tiên immutable.

Configuration properties ưu tiên immutable nếu phù hợp.

Entity mutable theo JPA requirement nhưng state transition phải controlled.

Không truyền mutable collection shared giữa layer mà không rõ ownership.

---

# 85. LOMBOK

Nếu project dùng Lombok, thống nhất convention.

Được cân nhắc:

```text
@Getter
@RequiredArgsConstructor
@Builder cho DTO khi thật sự hữu ích
```

Cẩn thận:

```text
@Data
@EqualsAndHashCode
@ToString
```

trên JPA Entity.

Không để generated `toString()` đi xuyên relationship và gây recursion/lazy load/sensitive leak.

---

# 86. CONSTRUCTOR INJECTION

Dependency injection mặc định dùng constructor.

Ví dụ:

```java
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
}
```

Không dùng field injection:

```java
@Autowired
private PostRepository postRepository;
```

trừ legacy code có lý do đặc biệt.

---

# 87. BEAN SCOPE

Service/Repository mặc định singleton stateless.

Không giữ request-specific mutable state trong field của Service.

Sai:

```java
@Service
class PostService {
    private Long currentUserId;
}
```

Current request state lấy từ parameter/current-user provider.

---

# 88. THREAD SAFETY

Spring singleton bean phải được xem là shared giữa request.

Không lưu:

```text
temporary request list
current entity
current user
current pagination
```

trong instance field.

Local variable là mặc định.

---

# 89. API IDEMPOTENCY THINKING

Operation phải xác định duplicate request behavior.

Ví dụ relation:

```text
like
bookmark
follow
join
```

nên có behavior rõ khi request lặp.

Business semantics lấy từ Domain System.

Backend phải dựa DB constraint/service rule để chống duplicate/race.

Không chỉ check:

```text
if (!exists) insert
```

mà bỏ qua race condition nếu constraint quan trọng.

---

# 90. CONCURRENCY

Các operation có race risk:

```text
like/unlike
follow/unfollow
vote
join community
counter increment
message read marker
```

phải được review:

- unique constraint.
- transaction.
- atomic update.
- optimistic/pessimistic lock nếu cần.
- idempotency.

Không giả định local `if` đủ chống concurrent requests.

---

# 91. FILE UPLOAD

Controller nhận multipart/form input.

Flow:

```text
Controller
↓
validate basic file request
↓
Media Service
↓
storage port
↓
Firebase adapter
```

Backend phải validate thật:

```text
content type
size
ownership
allowed count
association
```

Không chỉ tin browser validation.

---

# 92. EXTERNAL SERVICE FAILURE

External integration phải translate vendor exception sang application-level exception.

Không leak:

```text
FirebaseException
Mongo driver exception
vendor SDK type
```

lên Controller nếu không cần.

Log technical cause.

Client nhận canonical application error contract.

---

# 93. RETRY

Không auto retry mutation mù.

Retry chỉ khi:

- Operation idempotent hoặc có deduplication.
- Failure được xác định transient.
- Backoff hợp lý.
- Có max attempts.

Không retry database/business validation error.

---

# 94. TIMEOUT

External call phải có timeout phù hợp.

Không để request treo vô hạn vì Firebase/remote dependency.

Timeout value thuộc infrastructure config.

Không hardcode trong business service.

---

# 95. SERIALIZATION

Jackson config nên centralize.

Không annotate JPA Entity lung tung chỉ để sửa API JSON nếu API không nên serialize Entity ngay từ đầu.

DTO là serialization boundary.

---

# 96. LAZY INITIALIZATION

Không chữa lazy-loading problem bằng:

```text
Open Session in View + truy cập Entity graph tùy ý
```

như một architecture mặc định.

Service/query phải load đúng data cần cho response/view.

Nếu project giữ OSIV mặc định, dev vẫn không được dựa vào nó để template query ngầm hàng loạt.

---

# 97. DATABASE MIGRATION

Schema phải version hóa.

Khuyến nghị chọn một:

```text
Flyway
Liquibase
```

Không dùng cả hai.

Không dựa vào:

```text
hibernate ddl-auto=update
```

cho production schema management.

Development có thể khác theo config, nhưng canonical schema change phải có migration.

## 97.1. Trạng thái thực tế trên master (2026-10-20)

Hiện tại **chưa có Flyway/Liquibase** trong `pom.xml`. Schema được quản lý theo cách:

```text
Hibernate ddl-auto=update  → sinh/đổi bảng theo Entity ở mọi profile (kể cả prod)
src/main/resources/db/     → script SQL thủ công, không chạy tự động
├── schema.sql             # DDL PostgreSQL (tài liệu thiết kế, không wire vào runtime)
├── triggers.sql           # plpgsql trigger (chưa chạy trên MySQL)
├── procedures.sql         # stored procedure (chưa chạy trên MySQL)
└── migration/
    ├── h2_auth_and_registration_otp.sql     # đã verify bằng H2MigrationScriptVerificationTest
    ├── mysql8_auth_and_registration_otp.sql # UNVERIFIED trên MySQL thật (theo README.md)
    └── README.md                            # hướng dẫn chạy thủ công
```

Quy tắc bắt buộc khi thêm cột/bảng mới:

1. Đổi Entity Java là nguồn tạo schema lúc runtime (ddl-auto=update).
2. Nếu đổi ảnh hưởng dữ liệu có sẵn (backfill, rename, constraint), **phải** thêm script tương ứng
   vào `db/migration/` cho cả H2 và MySQL 8 và note trạng thái verify trong `migration/README.md`.
3. Migration đã merge không sửa nội dung — tạo script mới (đồng bộ §98 và `github_workflow.md` §43).

---

# 98. MIGRATION RULE

Migration đã merge/deploy không sửa lịch sử tùy tiện.

Tạo migration mới.

Tên migration phải mô tả thay đổi.

Ví dụ:

```text
V12__create_community_join_requests.sql
V13__add_post_visibility_index.sql
```

Không đặt:

```text
V14__fix.sql
V15__fix2.sql
```

nếu có thể mô tả rõ.

---

# 99. INDEX

Index phải dựa query pattern.

Các index cụ thể của domain/schema thuộc architecture/database design tương ứng.

Backend dev khi thêm query mới phải hỏi:

```text
query này chạy trên bảng lớn không?
filter/order theo column nào?
đã có index phù hợp chưa?
```

Không tạo index cho mọi column.

---

# 100. FETCH SIZE / BATCH

List lớn cần pagination.

Không:

```java
findAll()
```

cho table có thể tăng lớn như:

```text
Post
Message
Notification
User
PostView
```

Batch insert/update chỉ thêm khi đo được bottleneck.

---

# 101. BULK OPERATION

Bulk query:

```text
UPDATE
DELETE
```

bypass persistence context state.

Nếu dùng, phải hiểu entity đang loaded có thể stale.

Không mix bulk update với entity mutation trong cùng flow mà không clear/refresh strategy.

---

# 102. TEST STRUCTURE

Mirror package production:

```text
src/test/java/com/aloute/
├── feature/
├── security/
├── integration/
└── common/
```

Test đặt gần responsibility.

---

# 103. UNIT TEST

Unit test ưu tiên:

- Service business branch.
- Mapper.
- Policy.
- Pure validator.
- Utility.
- State transition.

Mock boundary:

```text
Repository
External port
Publisher
```

Không mock từng private/internal method.

---

# 104. REPOSITORY TEST

Repository test dùng:

```text
@DataJpaTest
```

khi phù hợp.

Test:

- Custom query.
- Unique constraint expectation.
- Relationship persistence.
- Projection.
- Soft-delete query behavior.
- Locking/query semantics quan trọng.

Không cần test Spring Data CRUD built-in đơn giản.

---

# 105. CONTROLLER TEST

Dùng:

```text
MockMvc
```

hoặc Spring MVC test phù hợp.

Test:

- Binding.
- Bean validation.
- Route.
- Authentication boundary.
- Response canonical contract.
- HTTP mapping.

Exact security expected behavior tham chiếu Security Convention.

Exact API/status contract tham chiếu System Architecture.

---

# 106. INTEGRATION TEST

Critical use case có thể test xuyên:

```text
Controller
→ Service
→ Repository
→ test database
```

Ví dụ:

- Create post.
- Like idempotency.
- Community join.
- Poll vote.
- Message permission.
- Follow duplicate.

Không cần integration test mọi getter đơn giản.

---

# 107. TEST DATA

Không tạo fixture khổng lồ dùng cho mọi test.

Ưu tiên builder/factory nhỏ:

```text
UserTestFactory
PostTestFactory
CommunityTestFactory
```

chỉ tạo field test cần.

Test phải đọc được intent.

---

# 108. TEST NAMING

Tên test nên mô tả:

```text
givenCondition_whenAction_thenResult
```

hoặc sentence style thống nhất.

Ví dụ:

```text
shouldRejectVoteWhenSingleChoicePollAlreadyVoted
shouldAllowOwnerToEditOwnPost
```

Không:

```text
test1
testCreate
works
```

---

# 109. CODE STYLE

Một class nên có một responsibility chính.

Không đặt:

```text
entity
DTO
repository
service
controller
```

trong cùng file.

Method dài/if nesting sâu là signal cần refactor.

Không refactor thành abstraction vô nghĩa chỉ để giảm line count.

---

# 110. EARLY RETURN

Ưu tiên guard clause khi giúp code rõ:

```java
if (!condition) {
    throw ...;
}
```

tránh nesting:

```java
if (...) {
    if (...) {
        if (...) {
            ...
        }
    }
}
```

Business flow nên đọc từ trên xuống.

---

# 111. COMMENT

Comment giải thích:

```text
WHY
```

không lặp lại:

```text
WHAT code already says
```

Sai:

```java
// increment count
count++;
```

Đúng hơn:

```java
// Counter is cached and updated atomically to avoid lost updates.
```

khi context không tự rõ.

---

# 112. TODO

TODO phải có meaning.

Không để:

```text
TODO fix later
TODO bug
```

hàng tháng.

Nếu TODO liên quan known domain gap, reference issue/design section tương ứng.

---

# 113. DEAD CODE

Không giữ:

- Commented-out implementation.
- Unused method.
- Duplicate DTO.
- Legacy endpoint không còn dùng.

Git đã giữ history.

---

# 114. FEATURE CROSS-CALL

Nếu Post cần Notification:

Không làm:

```text
PostRepository
→ NotificationRepository
```

Tùy coupling có thể:

```text
PostService
→ NotificationService
```

hoặc:

```text
PostService
→ PostCreatedEvent
→ NotificationListener
```

Chọn simplest model đáp ứng requirement.

---

# 115. CIRCULAR DEPENDENCY

Không chấp nhận:

```text
PostService → CommunityService
CommunityService → PostService
```

như architecture mặc định.

Nếu cycle xuất hiện:

- Xem lại responsibility.
- Tách policy/query service.
- Move orchestration lên service cao hơn.
- Dùng event nếu coupling là side effect.

Không bật:

```text
spring.main.allow-circular-references=true
```

để che architecture sai.

---

# 116. QUERY SERVICE

Read case phức tạp có thể tách:

```text
PostQueryService
FeedQueryService
ConversationQueryService
```

khi read model khác mutation workflow rõ rệt.

Không cần CQRS framework.

Chỉ tách khi service hiện tại thực sự khó đọc.

---

# 117. COMMAND OBJECT

Use case nhiều parameter có thể dùng command:

```text
CreatePostCommand
SendMessageCommand
```

nếu giúp service boundary rõ.

Không bắt buộc biến mọi request DTO thành command layer thứ hai.

Nếu Request DTO và command giống hoàn toàn, project nhỏ có thể dùng trực tiếp Request DTO ở application boundary nếu coupling được chấp nhận.

---

# 118. APPLICATION VS DOMAIN SERVICE

Không cần DDD ceremony đầy đủ.

Có thể hiểu thực dụng:

```text
Application Service
→ điều phối use case, transaction, repository

Policy/Domain helper
→ business decision tập trung
```

Không tạo hàng chục domain service trừ khi logic thực sự cần.

---

# 119. MODULAR MONOLITH BOUNDARY

Feature khác không được deep-import arbitrary internal class.

Ưu tiên public contract:

```text
UserQueryService
NotificationService
CommunityPolicy
```

theo use case.

Không:

```text
feature.post
→ feature.user.repository.UserRepository
```

nếu User feature phải sở hữu cách truy cập đó.

Exception: relational aggregate/reference đơn giản có thể được thiết kế khác nếu team chốt rõ, nhưng không biến thành free-for-all repository access.

---

# 120. PUBLIC MODULE CONTRACT

Feature lớn nên biết class nào là public cho feature khác.

Ví dụ:

```text
community/
├── CommunityQueryService
├── CommunityPolicy
└── internal...
```

Java package visibility có thể được dùng khi hữu ích.

Không bắt buộc xây Java Module System.

---

# 121. ARCHITECTURE ENFORCEMENT

Nếu project đủ lớn, có thể dùng:

```text
ArchUnit
```

để test rule như:

```text
Controller không phụ thuộc Repository
Repository không phụ thuộc Service
feature không import controller feature khác
```

Không bắt buộc ở phase đầu, nhưng hữu ích khi nhiều dev cùng code.

---

# 122. DEVELOPMENT WORKFLOW

Khi nhận feature:

```text
1. Đọc ALOUTE_DOMAIN_SYSTEM.md
2. Xác định business rule/use case.
3. Đọc ALOUTE_SYSTEM_ARCHITECTURE.md nếu có HTTP/API/persistence concern.
4. Đọc SPRING_SECURITY_CONVENTION.md nếu có permission/auth.
5. Xác định package feature.
6. Viết Request/Response/ViewModel cần thiết.
7. Viết Service workflow.
8. Viết Repository query tối thiểu.
9. Viết Mapper.
10. Viết Controller.
11. Viết test critical branch.
12. Review query/security/transaction.
```

Không bắt đầu bằng việc generate CRUD controller/entity hàng loạt rồi mới ghép business rule.

---

# 123. BACKEND DEFINITION OF DONE

Feature backend chỉ được coi là xong khi:

- Code nằm đúng module/layer.
- Không duplicate canonical contract.
- Request DTO có validation.
- Business rule nằm đúng service/policy.
- Security check server-side đầy đủ.
- Transaction boundary rõ.
- Query không có obvious N+1.
- DB constraint quan trọng đã có.
- ErrorCode đúng canonical.
- API DTO không leak Entity.
- External integration đi qua abstraction.
- Logging không leak sensitive data.
- Critical test pass.
- Không có circular dependency.
- Không có TODO critical chưa giải quyết.

---

# 124. BACKEND REVIEW CHECKLIST

## Controller

- Có gọi Repository trực tiếp không?
- Có business logic không?
- Có nhận Entity trực tiếp không?
- Có tin `userId/ownerId/role` từ client không?
- Có tự invent response/status không?

## Service

- Responsibility có rõ không?
- Transaction đúng chưa?
- Permission check đúng layer chưa?
- Có gọi external service trong transaction dài không?
- Có God Service không?

## Repository

- Query có đúng use case không?
- Có N+1 không?
- Có `findAll()` trên bảng lớn không?
- Có query method name quá dài không?
- Constraint/index có phù hợp không?

## Entity

- Có cascade nguy hiểm không?
- Có EAGER collection không?
- Enum có dùng STRING không?
- Setter có phá invariant không?
- `equals/hashCode/toString` có kéo relation không?

## DTO / Mapper

- Request có field client không được phép set không?
- Response có leak internal data không?
- Mapper có query DB không?
- Có duplicate DTO vô nghĩa không?

## Security

- Có parse JWT ngoài security layer không?
- Resource permission có được backend check không?
- Dynamic permission có bị hardcode role không?

## Infrastructure

- Firebase có bị gọi trực tiếp từ feature/controller không?
- MongoDB có use case/source of truth rõ không?
- WebSocket có bypass service không?

## Test

- Business branch quan trọng có test không?
- Repository query custom có test không?
- Security path quan trọng có test không?
- API contract có integration/controller test không?

---

# 125. CORE BACKEND RULES

1. Spring Boot backend dùng modular monolith.
2. Package-by-feature là mặc định.
3. Controller mỏng.
4. Controller không gọi Repository trực tiếp.
5. Service điều phối use case.
6. Transaction đặt ở Service.
7. Repository chỉ data access.
8. Entity không biết Controller/Service/SecurityContext.
9. Request/Response/ViewModel tách khỏi Entity.
10. Không expose Entity trực tiếp.
11. Mapper không query database.
12. Collection relationship mặc định cẩn thận với LAZY.
13. Không lạm dụng CascadeType.ALL.
14. Enum persistence dùng STRING.
15. Không tạo interface service nếu không có giá trị.
16. Constructor injection là mặc định.
17. Singleton bean không giữ request mutable state.
18. Query list lớn phải paginate.
19. Không fix N+1 bằng EAGER toàn cục.
20. Database constraint bảo vệ invariant quan trọng khi phù hợp.
21. Counter concurrent phải tránh lost update.
22. Soft delete chỉ dùng khi domain có semantics.
23. Validation chia request/business/database.
24. Exception semantic từ Service.
25. Global handler map sang canonical API contract.
26. Không duplicate ErrorCode/HTTP contract.
27. Không parse JWT ngoài security layer.
28. Không tin identity/role từ client.
29. Firebase nằm sau integration port.
30. MongoDB chỉ dùng khi Architecture cho phép.
31. WebSocket adapter vẫn gọi Service.
32. Event chỉ dùng khi giảm coupling thật.
33. `@Async` không phải nút tăng performance.
34. Cache chỉ thêm khi có strategy invalidation.
35. Config/secret centralize.
36. Không log credential/sensitive data.
37. Không bật circular dependency để chữa design.
38. Không để common/utils trở thành sọt rác.
39. Test business/security/query critical path.
40. Một rule chỉ có một source of truth.

---

# 64. ADMIN & MODERATION BACKEND CONVENTIONS

## 64.1. Phân chia Layer & Package
1. **Controller Layer:**
   - `controller.page.AdminPageController`: Điều hướng và render SSR cho `/admin/**`.
   - `controller.api.admin.AdminOverviewController`: Thống kê KPI hệ thống.
   - `controller.api.admin.AdminUserController`: Tra cứu và quản lý tài khoản người dùng.
   - `controller.api.admin.AdminPostController`: Tra cứu, gỡ và khôi phục bài viết.
   - `controller.api.admin.AdminReportController`: Quản lý và xử lý hàng đợi báo cáo vi phạm.
   - `controller.api.admin.AdminModerationActionController`: Truy vấn lịch sử kiểm duyệt (Audit Log).
2. **Service Layer:**
   - `service.ModerationService`: Xử lý nghiệp vụ gỡ bài, khôi phục bài viết, ghi log `moderation_actions`, quản lý `post_moderation_removals`.
   - `service.ReportReviewService`: Quản lý quy trình chuyển trạng thái báo cáo (`PENDING -> REVIEWED -> RESOLVED/REJECTED`) và kiểm tra optimistic lock `version`.
   - `service.AdminOverviewService`: Tổng hợp số liệu KPI và thống kê tăng trưởng hệ thống.
   - *Quy tắc:* Tuyệt đối không viết logic nghiệp vụ trong Controller. Một use case chỉ có duy nhất một Service method quản lý.

## 64.2. Transaction & Audit Invariant
1. **Tính nguyên tử (@Transactional):**
   - Khi Admin ra quyết định giải quyết báo cáo (`REPORT_RESOLVE`) kèm theo gỡ bài viết (`POST_REMOVE`), toàn bộ các thao tác:
     1. Khóa bản ghi Report và Post để kiểm tra tính hợp lệ.
     2. Gỡ bài viết (`deleted_at = now()`) và cập nhật counter liên quan.
     3. Tạo bản ghi `post_moderation_removals`.
     4. Ghi bản ghi `moderation_actions` (Audit Log).
     5. Cập nhật Report sang `RESOLVED` và tăng `version`.
     **BẮT BUỘC** phải nằm trong một transaction duy nhất. Nếu bất kỳ bước nào thất bại, hệ thống tự động rollback toàn bộ.
2. **Gửi Notification sau Commit:**
   - Việc phát thông báo cảnh báo đến tác giả bài viết bị gỡ phải được thực hiện **sau khi commit** (`afterCommit`) để tránh việc lỗi gửi thông báo làm rollback nghiệp vụ cốt lõi của cơ sở dữ liệu.
3. **Bảo vệ Nhật ký Kiểm duyệt:**
   - Repository của `ModerationAction` chỉ cung cấp các phương thức `save()` và `find*()`. Không bao giờ cung cấp hoặc gọi `delete()`, `deleteAll()`.

