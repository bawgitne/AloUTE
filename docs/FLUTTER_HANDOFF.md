# ALOUTE — Flutter Client Handoff (aloute_mobile)

> Ngày tổng hợp: 2026-10-20 · Nguồn đối chiếu: master `6fd2496` (sau PR #37/#38)
> Phạm vi: mọi thứ nhà phát triển Flutter cần biết để tiếp tục `aloute_mobile/` mà không phải
> đọc lại backend. Contract gốc: `docs/api/openapi.yaml` (repo).

---

## 1. Kiến trúc backend cần biết

| Chủ đề | Thực tế đã verify |
| :--- | :--- |
| Base URL | REST mount tại `/api/*` (**không có** `/api/v1`) |
| Auth transport | Login/2FA verify trả JWT **qua `Set-Cookie: aloute_access_token` (HttpOnly)**; body `ApiResponse<LoginResponse>` **có kèm `token`** trong `data` (dùng được cho Bearer) |
| Bearer fallback | `JwtAuthenticationFilter` đọc cookie trước, nếu không có thì đọc `Authorization: Bearer <token>` → mobile bắt buộc dùng Bearer |
| CSRF | Request mang `Authorization: Bearer` được **miễn CSRF** (`SecurityConfig.bearerRequest`). Mobile không cần X-XSRF-TOKEN |
| Logout | `POST /api/auth/logout` (cần Bearer) → xóa cookie, **không** tăng `authVersion` |
| Đổi mật khẩu | `POST /api/auth/reset-password` tăng `authVersion` → mọi JWT cũ (Bearer gồm cả mobile) bị từ chối 401 ngay lập tức |
| Registration | 2 bước: `POST /api/auth/register` (201) → email OTP 6 số → `POST /api/auth/verify-registration-otp`. Login khi chưa verify trả 401 `AUTH_ACCOUNT_NOT_VERIFIED` |
| Response envelope | `{ success, data, meta? }`; lỗi: `{ success:false, error:{ code, message, fieldErrors? }, traceId }` + HTTP status đúng (xem `src/main/java/com/aloute/common/api/ApiResponse.java`, `GlobalExceptionHandler.java`) |
| Pagination | `page` **0-indexed**, `size` (mặc định 20, max 100), trả `meta: { page, size, totalElements, totalPages, ... }` |
| Dev server | `mvn spring-boot:run` → `http://localhost:8080`, H2 in-memory, seed `admin@aloute.local` / `user@aloute.local` mật khẩu `User@123` |
| WebSocket | STOMP `/ws` (SockJS + raw), CONNECT cần token; mobile hiện **chưa** dùng, feed đang poll REST |

## 2. Endpoint map cho mobile (đã implement trên master)

```text
POST /api/auth/register                  { username, email, password }        → 201
POST /api/auth/verify-registration-otp   { email, otp }                       → 200
POST /api/auth/resend-registration-otp   { email }                            → 200
POST /api/auth/login                     { email, password }                  → 200 + Set-Cookie
POST /api/auth/logout                    (Bearer)                             → 204
GET  /api/auth/me                        (Bearer)                             → UserEnvelope

GET  /api/users/{userId}                 (public)
GET  /api/users/by-username/{username}   (public)
GET  /api/users/me/profile               (Bearer; alias GET /api/users/profile)
PATCH/PUT /api/users/me/profile          (Bearer)
GET  /api/users/{userId}/posts|followers|following   (public)

GET  /api/feed/home|following|explore|recommended   (public; Bearer để cá nhân hóa)
GET  /api/posts?q=&page=&size=           (public)
GET  /api/posts/bookmarks                (Bearer)
POST /api/posts                          (Bearer)
GET/PATCH/DELETE /api/posts/{postId}     (owner mới được PATCH/DELETE)
GET/POST /api/posts/{postId}/replies     (POST = reply)
POST /api/posts/{postId}/quotes
PUT/DELETE /api/posts/{postId}/likes/me | bookmarks/me | reposts/me
GET  /api/topics, /api/topics/{id}, /api/topics/{id}/posts   (public)
GET  /api/polls/{pollId}; POST /api/polls/{pollId}/votes (+ alias /vote)
GET  /api/communities...                 (public reads; join/post cần Bearer)
GET  /api/notifications...               (Bearer; field read state tên là `read`)
```

Trạng thái contract-only (chưa có, đừng gọi): toàn bộ `/api/feeds/*` CRUD
(`x-implementation-status: contract-only` trong openapi), `/api/admin/users/{id}/moderation-actions`,
mọi API ban/suspend.

## 3. Lỗi hiện tại của aloute_mobile (audit 2026-10-20)

1. **Login không gửi được body đúng**: `ALOUTEApi.post` gửi `body: jsonEncode(queryParameters ?? {})`,
   và `AuthService.login` gọi với `queryParameters: {}` → body rỗng `{}` → backend 422
   (`LoginRequest.email @NotBlank`). Phải thêm param `body` riêng cho POST.
2. **Token không bao giờ được lưu**: login đọc `json['token']` nhưng backend không trả token trong
   body (chỉ Set-Cookie). Sửa: đọc `response.headers['set-cookie']`, tách `aloute_access_token=...`,
   gọi `tokenStorage.writeAccessToken(...)`. Dart `http` (native, không phải browser) đọc được
   Set-Cookie kể cả HttpOnly.
3. **CookieJar chưa wire**: `CookieSession.create()` được tạo nhưng không có `saveFromResponse`
   nào → cookie luôn rỗng. Sau khi fix (2) có thể bỏ hẳn cookie path, đi Bearer thuần.
4. **`_parseJson` trả `null as T`** khi `data == null` → crash cast với T non-nullable.
5. **404 cho `POST /api/posts/...` alias**: `AppPath.postsReposts = '/api/posts/{postId}/reposts/me'`
   nhưng `_buildUri` chỉ replace `{idParam}` — placeholder `{postId}` không khớp.
6. **Đăng ký 2 bước**: UI mobile chưa có màn verify-otp; account mới không verify được sẽ
   login 401 `AUTH_ACCOUNT_NOT_VERIFIED`.
7. **Logout nên gọi REST** trước khi xóa local token (hiện chỉ xóa local).
8. **`flutter analyze` 294 lỗi / test fail** (báo cáo `aloute_flutter_analyzer_fix_2026_10_20.md`) —
   barrel `export` đã sửa một phần; còn lại chủ yếu là null-safety + unused imports.

## 4. Cạm bẫy đã biết phía backend (không phải lỗi mobile)

- **H2 dev**: `GET /api/feed/home` (đã login) và `GET /api/users/me/profile` từng trả
  `500 INTERNAL_ERROR` trên H2 vì stored-procedure/trigger không tồn tại ở H2 — repro riêng
  trong báo cáo Flutter MVP 2026-10-20. Trên MySQL prod các path này hoạt động.
  Kỳ vọng test mobile: dùng endpoints public (feed explore, posts list) hoặc chạy DB MySQL.
- Notification read field: JSON là **`read`** (Jackson rename từ `isRead`), không phải `isRead`.
- `meta` chỉ có ở list endpoint; single-item envelope `meta: null`.

## 5. Checklist tiếp theo cho người nhận bàn giao

- [ ] Fix `ALOUTEApi.post` hỗ trợ body riêng; sửa các call site.
- [ ] Lưu token từ Set-Cookie như mục 3.2; xóa nhánh cookie chết.
- [ ] Thêm màn `verify-otp` (reuse contract `/api/auth/verify-registration-otp`).
- [ ] Chuẩn hóa `_parseJson` xử lý `data == null` theo generic an toàn.
- [ ] Đổi placeholder path sang `{idParam}` hoặc thêm map replace `{postId}`.
- [ ] Chạy `flutter analyze` về 0; khôi phục `flutter test`.
- [ ] E2E thủ công: register → verify → login → feed → like → profile (theo §2).
