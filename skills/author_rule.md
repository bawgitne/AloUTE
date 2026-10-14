# ALOUTE — SPRING SECURITY CONVENTION

> **Scope**
>
> File này là source of truth cho **cách implement authentication và authorization bằng Spring Security + JWT**.
>
> File này không sở hữu:
>
> ```text
> HTTP status table / API error body / error-code transport
> → ALOUTE_SYSTEM_ARCHITECTURE.md
>
> Business permission: WHO CAN DO WHAT
> → ALOUTE_DOMAIN_SYSTEM.md
>
> Frontend auth UX
> → ALOUTE_FRONTEND_CONVENTION.md
> ```

---

# 1. SECURITY RESPONSIBILITY

Spring Security chịu trách nhiệm:

```text
Authentication
+
Global Authorization
+
SecurityContext
+
Authenticated Principal
```

Service/domain chịu trách nhiệm resource-dependent permission:

```text
ownership
community membership
community role
conversation membership
privacy
visibility
block relationship
resource state
```

Flow:

```text
Request
   ↓
Security Filter Chain
   ↓
JWT authentication
   ↓
UserPrincipal
   ↓
SecurityContext
   ↓
Controller
   ↓
Service / Policy
   ↓
Domain permission
```

---

# 2. GLOBAL ROLE

Global Role chỉ biểu diễn quyền toàn hệ thống.

Canonical global roles ban đầu:

```java
public enum Role {
    USER,
    SYSTEM_ADMIN
}
```

Không lưu prefix `ROLE_` trong enum.

Spring Security mapping:

```text
USER
→ ROLE_USER

SYSTEM_ADMIN
→ ROLE_SYSTEM_ADMIN
```

Không thêm role chỉ để tiện viết condition.

---

# 3. DOMAIN ROLE KHÔNG PHẢI GLOBAL ROLE

Community role thuộc resource Community.

Security Convention **không liệt kê lại các giá trị CommunityRole**. Canonical enum/value và quyền của từng role chỉ nằm trong:

```text
ALOUTE_DOMAIN_SYSTEM.md
```

Không merge scoped CommunityRole vào global `Role`.

Một user có thể có role khác nhau ở từng community.

Canonical domain role và quyền cụ thể của từng role lấy từ:

```text
ALOUTE_DOMAIN_SYSTEM.md
```

Security layer chỉ quy định cách kiểm tra nó.

---

# 4. GLOBAL AUTHORITY

Authority chỉ dùng cho global technical permission có use case thật.

Ví dụ có thể có:

```text
REPORT_REVIEW
USER_SUSPEND
SYSTEM_CONFIG_UPDATE
```

Không tạo Authority cho mọi business action:

```text
POST_CREATE
POST_DELETE
MESSAGE_SEND
COMMUNITY_JOIN
POST_LIKE
```

vì các action đó phụ thuộc resource/context.

---

# 5. ROLE VS AUTHORITY

```text
Role
= nhóm quyền global cấp cao

Authority
= global technical permission cụ thể

Domain Permission
= quyền phụ thuộc resource/context
```

Có thể:

```text
ROLE_SYSTEM_ADMIN
   ├── REPORT_REVIEW
   └── USER_SUSPEND
```

Không bắt buộc dùng Authority nếu project chưa có use case.

---

# 6. USER PRINCIPAL

Toàn hệ thống dùng một authenticated principal thống nhất.

Ví dụ:

```java
public class UserPrincipal implements UserDetails {

    private Long id;
    private String username;
    private String password;

    private Collection<? extends GrantedAuthority> authorities;
}
```

Principal chỉ chứa dữ liệu cần cho authentication/global authorization.

Không nhét toàn bộ profile/community memberships vào Principal.

---

# 7. SPRING AUTHORITY MAPPING

Global role:

```java
new SimpleGrantedAuthority("ROLE_" + role.name())
```

Global authority:

```java
new SimpleGrantedAuthority(authority.name())
```

Kết quả có thể cùng nằm trong:

```text
Collection<GrantedAuthority>
```

---

# 8. JWT RESPONSIBILITY

JWT dùng để mang authentication identity và global claims ổn định đủ lâu trong vòng đời token.

Ví dụ:

```json
{
  "sub": "123",
  "roles": ["USER"]
}
```

Nếu thật sự dùng global Authority:

```json
{
  "sub": "123",
  "roles": ["SYSTEM_ADMIN"],
  "authorities": ["REPORT_REVIEW"]
}
```

Không đưa dynamic permission vào JWT:

```text
community_1 = ADMIN
community_2 = MEMBER
post_123 = OWNER
conversation_456 = MEMBER
```

Những dữ liệu này phải đọc từ domain/database khi cần.

---

# 9. JWT TRANSPORT FOR THYMELEAF WEB APP & REST CLIENTS

> **Người phụ trách**: Nguyễn Đoàn Minh Khôi.

ALOUTE là hệ thống đa giao diện (Thymeleaf SSR chính thức + REST API cho SPA/Postman).

Quy ước truyền nhận Token:

```text
1. Web Thymeleaf (Browser):
   JWT access credential → HttpOnly Cookie (tên: `aloute_access_token`)
2. REST API Clients / Postman / fe-vue:
   JWT access credential → HTTP Header `Authorization: Bearer <token>`
```

Filter `JwtAuthenticationFilter` phải trích xuất token theo thứ tự linh hoạt:
1. Đọc từ Cookie `aloute_access_token`.
2. Nếu không có cookie, đọc từ Header `Authorization: Bearer <token>`.

Cookie production cần security attributes phù hợp:

```text
HttpOnly
Secure
SameSite=Lax
Path=/
```

Browser JavaScript trên Thymeleaf không đọc raw JWT để ngăn ngừa XSS.
CSRF Token (`XSRF-TOKEN` cookie + `X-XSRF-TOKEN` header) bắt buộc kích hoạt cho các request ghi dữ liệu (POST, PUT, PATCH, DELETE) khi gửi từ Cookie.

Nếu sau này project mở external API dùng Bearer token, phải định nghĩa profile đó riêng; không trộn hai chiến lược tùy endpoint.

CSRF strategy phải tương thích với credential transport đã chọn.

---

# 10. JWT CONTENT RULE

Token chỉ chứa claim thật sự cần.

Không nhét:

- Full profile.
- Dynamic community membership.
- Conversation list.
- Resource ownership.
- Mutable counters.
- Sensitive data.

Role/authority thay đổi có thể làm token cũ stale trong thời gian còn hiệu lực; token lifetime/revocation strategy phải được Auth module quyết định rõ.

---

# 11. SECURITY FILTER CHAIN

Security flow:

```text
SecurityFilterChain
   ↓
JWT authentication filter
   ↓
JWT token provider/service
   ↓
UserPrincipal
   ↓
SecurityContext
```

Filter không chứa business permission.

Filter chỉ xác thực credential và dựng authenticated context.

---

# 12. SECURITY UTILS / CURRENT USER

Không module nào tự parse JWT.

Current authenticated identity lấy từ SecurityContext thông qua abstraction thống nhất.

Có thể dùng:

```text
SecurityUtils
```

hoặc:

```text
CurrentUserProvider
```

Toàn project chọn một cách chính.

Khuyến nghị để test tốt hơn:

```java
public interface CurrentUserProvider {
    Long requireUserId();
    UserPrincipal requirePrincipal();
}
```

Không lấy `userId` từ request rồi coi là current user.

---

# 13. @PREAUTHORIZE

Phù hợp cho rule global/static:

```java
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
```

hoặc:

```java
@PreAuthorize("hasAuthority('REPORT_REVIEW')")
```

Không cố nhét toàn bộ domain permission phức tạp vào SpEL.

Resource permission nên rõ trong application/service/policy.

---

# 14. OWNERSHIP CHECK

Ví dụ edit post:

```text
authenticated USER
```

chưa đủ.

Service/policy phải check ownership theo resource.

Pattern:

```text
load resource
↓
require permission
↓
mutate
↓
persist
```

Không tin:

```text
authorId
ownerId
senderId
```

từ request nếu server suy ra được.

Quyền owner cụ thể thuộc `ALOUTE_DOMAIN_SYSTEM.md`.

---

# 15. COMMUNITY PERMISSION CHECK

Community permission phải đọc membership theo:

```text
communityId + userId
```

Không đọc một global `ROLE_MODERATOR`.

Pattern:

```text
CommunityPolicy.requireModerator(...)
CommunityPolicy.requireAdmin(...)
CommunityPolicy.requireMember(...)
```

Tên method cụ thể có thể thay đổi, nhưng check phải scoped theo Community.

Tên role và quyền chi tiết của từng CommunityRole thuộc domain document.

---

# 16. CONVERSATION PERMISSION CHECK

Mọi operation nhạy cảm của conversation phải xác định membership/role theo conversation cụ thể.

Không vì user authenticated mà cho phép:

```text
read arbitrary messages
send to arbitrary conversation
react arbitrary message
```

Domain document quyết định chính xác participant được làm gì.

---

# 17. VISIBILITY / PRIVACY / BLOCK

Các rule:

```text
Post visibility
private profile
block
mute
restrict
```

là domain permission/business behavior.

Security implementation phải gọi domain/policy phù hợp.

Không duplicate logic đó trong SecurityConfig.

---

# 18. POLICY LOCATION

Có hai dạng hợp lý:

```text
feature/post/policy/PostPolicy.java
feature/community/policy/CommunityPolicy.java
feature/messaging/policy/ConversationPolicy.java
```

hoặc một authorization package có boundary rõ.

Ưu tiên policy gần domain nếu rule phụ thuộc business data.

Không gom mọi permission toàn hệ thống thành một class khổng lồ.

---

# 19. SECURITY PACKAGE STRUCTURE

Chi tiết nội bộ security package do file này sở hữu:

```text
security/
├── config/
│   └── SecurityConfig.java
│
├── jwt/
│   ├── JwtAuthenticationFilter.java
│   ├── JwtTokenProvider.java
│   └── JwtProperties.java
│
├── principal/
│   ├── UserPrincipal.java
│   └── CustomUserDetailsService.java
│
├── currentuser/
│   └── CurrentUserProvider.java
│
└── handler/
    ├── AuthenticationFailureHandler.java
    └── AccessDeniedHandler.java
```

Tên exact có thể điều chỉnh, nhưng responsibility không được trộn.

---

# 20. ERROR TRANSLATION

Security layer có thể phát sinh hai category:

```text
authentication failure
authorization failure
```

Security file **không định nghĩa lại**:

```text
HTTP status number
ApiError shape
ErrorCode response body
```

Mapping transport phải follow duy nhất:

```text
ALOUTE_SYSTEM_ARCHITECTURE.md
```

Nhờ vậy HTTP semantics không bị copy giữa Security và Frontend.

---

# 21. ACCOUNT STATE

Authenticated token không tự động có nghĩa account vẫn được phép hoạt động.

Nếu domain có:

```text
inactive
suspended
deleted/deactivated
```

security/auth flow phải xác định state nào làm credential/session không còn hợp lệ.

Business meaning của account state thuộc domain file.

Cách enforce nằm ở security/auth implementation.

---

# 22. METHOD SECURITY

Nếu dùng:

```text
@PreAuthorize
```

phải enable method security thống nhất trong Security configuration.

Không để một module tưởng annotation đang enforce trong khi project chưa enable.

---

# 23. DENY BY DEFAULT

Endpoint/resource nhạy cảm không được mở chỉ vì chưa viết rule.

Nguyên tắc:

```text
no explicit allow
→ deny
```

Public endpoint phải được khai báo có chủ đích.

---

# 24. SECURITY CONFIG SCOPE

`SecurityConfig` chịu trách nhiệm:

- Filter chain.
- Public/protected route configuration.
- Security handlers.
- Authentication provider wiring.
- CSRF/session policy phù hợp architecture.
- Password encoder.
- Method security integration khi cần.

Không chứa:

- Post ownership query.
- Community business rule.
- Conversation membership business workflow.

---

# 25. PASSWORD

Password phải dùng `PasswordEncoder`.

Không lưu plaintext.

Không tự viết hash algorithm.

Password validation/business requirement của account thuộc auth/domain requirement; storage/security implementation nằm tại auth/security layer.

---

# 26. WEBSOCKET SECURITY

WebSocket không bypass security.

Khi connect/send/subscribe cần xác định:

```text
authenticated principal
destination/resource
domain permission
```

Chat/conversation membership phải được check server-side.

Cách WebSocket được dùng cho feature nào thuộc architecture/domain docs.

---

# 27. SECURITY TESTING

Tối thiểu test:

- Public endpoint thực sự public.
- Protected endpoint reject unauthenticated request.
- Global admin-only rule.
- Owner vs non-owner.
- Community member/moderator/admin scoped correctly.
- Conversation non-member bị deny.
- Dynamic permission không dựa vào JWT stale claim.
- Invalid/expired credential.
- Account state enforcement.
- WebSocket authorization quan trọng.

Expected HTTP/API shape lấy từ architecture contract, không copy vào test convention này.

---

# 28. SOURCE-OF-TRUTH RULE

```text
ALOUTE_DOMAIN_SYSTEM.md
→ WHO CAN DO WHAT

SPRING_SECURITY_CONVENTION.md
→ HOW TO ENFORCE

ALOUTE_SYSTEM_ARCHITECTURE.md
→ HOW FAILURE/SUCCESS IS EXPOSED OVER HTTP

ALOUTE_FRONTEND_CONVENTION.md
→ HOW UI REACTS
```

---

# 29. MANDATORY RULES

1. Không parse JWT ngoài security/auth layer.
2. Không tin client-provided identity khi server suy ra được.
3. Tách global Role và scoped domain Role.
4. Không biến CommunityRole thành Spring global Role.
5. Authority chỉ dùng cho global permission có use case.
6. Ownership check server-side.
7. Resource permission check server-side.
8. Community permission query theo community cụ thể.
9. Conversation permission query theo conversation cụ thể.
10. Dynamic permission không cố định trong JWT.
11. Browser JS không đọc raw JWT theo strategy hiện tại.
12. `@PreAuthorize` không thay toàn bộ domain policy.
13. SecurityConfig không chứa business rule.
14. Error HTTP/body contract không được định nghĩa trong file này.
15. Domain-specific permission không được invent trong file này.

---

# 30. ADMIN & MODERATION SECURITY CONVENTION

1. **Mapping Global Role & Authority:**
   - Role `SYSTEM_ADMIN` được map thành `ROLE_SYSTEM_ADMIN` trong Spring Security Context.
   - Khi Principal có role `SYSTEM_ADMIN`, nạp thêm các quyền đặc thù:
     - `GlobalAuthority.REPORT_REVIEW` (Quyền tiếp nhận và ra quyết định xử lý báo cáo vi phạm).
     - `GlobalAuthority.USER_SUSPEND` (Quyền đình chỉ tài khoản và hạn chế tính năng).
2. **Cấu hình Security Filter Chain:**
   - Cụm route giao diện SSR: `/admin/**` → Yêu cầu authenticated `hasRole('SYSTEM_ADMIN')`.
   - Cụm route API JSON: `/api/admin/**` → Yêu cầu authenticated `hasRole('SYSTEM_ADMIN')`.
   - Áp dụng nguyên tắc **Deny-by-default** cho toàn bộ admin routes; không được phép whitelist công khai.
3. **Bảo vệ CSRF & Cookie:**
   - Khi Admin tương tác qua giao diện web (dùng JWT HttpOnly Cookie `aloute_access_token`), các request ghi (`POST`, `PATCH`, `DELETE`) bắt buộc phải gửi kèm header `X-XSRF-TOKEN`.
4. **Không tin cậy Client Identity:**
   - Thông tin Admin thực hiện (`actorUserId`) **bắt buộc** lấy trực tiếp từ `CurrentUserProvider.requireUserId()`, tuyệt đối không đọc từ Request Body hay Request Header do client gửi lên.

