# ALOUTE — ADMIN DASHBOARD / BACKOFFICE SPECIFICATION

> **Phiên bản:** Draft 1.0 — 14/10/2026  
> **Phạm vi:** **Chỉ Admin Dashboard/Backoffice** (trang quản trị tập trung). **Không** bao gồm Inline Admin Controls trên Feed/Profile/Community của giao diện người dùng.  
> **Định hướng:** Bản đề xuất đủ chi tiết để thảo luận, cập nhật Domain & OpenAPI, triển khai API, database, backend và Thymeleaf frontend. Đây **không phải** bản mô tả các chức năng đã được code.  
> **Trạng thái ký hiệu:** `[EXISTING]` có trong tài liệu ALOUTE; `[PROPOSED]` cần phê duyệt và bổ sung tài liệu; `[GAP]` thiếu quy tắc/thiếu bằng chứng trong ZIP; `[DEFERRED]` giai đoạn sau.

---

## Mục lục

1. [Nguồn tham chiếu, giới hạn và nguyên tắc](#1-nguồn-tham-chiếu-giới-hạn-và-nguyên-tắc)
2. [Tầm nhìn sản phẩm và phạm vi](#2-tầm-nhìn-sản-phẩm-và-phạm-vi)
3. [Sitemap, màn hình và trải nghiệm](#3-sitemap-màn-hình-và-trải-nghiệm)
4. [Ma trận nghiệp vụ và quyền](#4-ma-trận-nghiệp-vụ-và-quyền)
5. [Domain model, dữ liệu và migration](#5-domain-model-dữ-liệu-và-migration)
6. [Quy ước API áp dụng cho toàn bộ Backoffice](#6-quy-ước-api-áp-dụng-cho-toàn-bộ-backoffice)
7. [API nhóm Overview & Analytics](#7-api-nhóm-overview--analytics)
8. [API nhóm User Management](#8-api-nhóm-user-management)
9. [API nhóm Post Moderation](#9-api-nhóm-post-moderation)
10. [API nhóm Reports & Case Review](#10-api-nhóm-reports--case-review)
11. [API nhóm Moderation History / Audit](#11-api-nhóm-moderation-history--audit)
12. [API nhóm Account Suspension & Restrictions](#12-api-nhóm-account-suspension--restrictions)
13. [API nhóm Community Management](#13-api-nhóm-community-management)
14. [API nhóm Topic Management](#14-api-nhóm-topic-management)
15. [API nhóm Poll Management](#15-api-nhóm-poll-management)
16. [API nhóm Media Management & Quota](#16-api-nhóm-media-management--quota)
17. [API nhóm System Broadcast](#17-api-nhóm-system-broadcast)
18. [Phản hồi mẫu và error contract](#18-phản-hồi-mẫu-và-error-contract)
19. [Workflow end-to-end, transaction và concurrency](#19-workflow-end-to-end-transaction-và-concurrency)
20. [Kiến trúc Spring Boot và cấu trúc frontend](#20-kiến-trúc-spring-boot-và-cấu-trúc-frontend)
21. [Bảo mật, quyền riêng tư và vận hành](#21-bảo-mật-quyền-riêng-tư-và-vận-hành)
22. [Bộ kiểm thử / tiêu chí nghiệm thu](#22-bộ-kiểm-thử--tiêu-chí-nghiệm-thu)
23. [Roadmap và các PR](#23-roadmap-và-các-pr)
24. [Quyết định phải chốt trước khi code](#24-quyết-định-phải-chốt-trước-khi-code)
25. [Danh mục API tổng hợp](#25-danh-mục-api-tổng-hợp)

---

## 1. Nguồn tham chiếu, giới hạn và nguyên tắc

### 1.1. Nguồn tham chiếu đã đọc

Tài liệu được đối chiếu trực tiếp từ `skills.zip` đã cung cấp:

- `skills/INDEX.md` — source of truth, giới hạn ownership và thứ tự cập nhật tài liệu.
- `skills/domain_system.md` — domain, permission, soft-delete, reports, notifications, Community, media quota, trigger/procedure.
- `skills/system_architecture.md` — API envelope, URL, pagination, HTTP status, error code, transaction.
- `skills/author_rule.md` — role/authority, JWT, Security, CSRF, deny-by-default.
- `skills/backend_rule.md` — Layered MVC, DTO, JPA, Service, Repository, testing.
- `skills/frontend_rule.md` — Thymeleaf + Bootstrap 5 + Vanilla JS, fragment, HTTP client, UI state.
- `skills/aloute-openapi.yaml` — API contract **hiện có**, version `0.1.0`.
- `skills/docker_reverse_proxy_rule.md`, `skills/github_workflow.md` — deployment và quy trình làm việc khi triển khai.

> **Chưa có trong ZIP:** những file được `INDEX.md` nhắc đến như `RULE.md`, `README.md`, tài liệu chuyên biệt Media Storage và Realtime WebSocket. Không giả định nội dung các file này. ZIP cũng không chứa toàn bộ Java source và DB migration thật; mọi quyết định phụ thuộc repository phải kiểm tra lại trước khi tạo code.

### 1.2. Phân biệt rõ hiện có và đề xuất

**[EXISTING] Theo domain / OpenAPI:**

- Global role `USER`, `SYSTEM_ADMIN` (mapped thành `ROLE_USER`, `ROLE_SYSTEM_ADMIN` trong Spring Security).
- User/Profile, Post/Reply/Quote, Community, Topics, Polls, Media, Notification, Report.
- `posts.deleted_at` là trạng thái soft-delete; Reply là Post có `parent_post_id`.
- Report target hiện chỉ **User hoặc Post**, reason `SPAM`, `HARASSMENT`, `HATE`, `MISINFORMATION`, `VIOLENCE`, `OTHER`.
- Report status: `PENDING`, `REVIEWED`, `REJECTED`, `RESOLVED`.
- `Block`, `Mute`, `RestrictedUser` là khái niệm **quan hệ giữa các user**, không phải lệnh đình chỉ toàn hệ thống.
- API công khai `/api/reports` hiện **chỉ có POST tạo report**; chưa có admin API trong OpenAPI ZIP.
- API User/Post/... sử dụng `id` dạng `integer/int64` theo OpenAPI. **[GAP]** Ví dụ stored procedure `sp_soft_delete_post_cascade(p_post_id UUID, p_user_id UUID)` lại dùng UUID; xác nhận schema/code thực tế trước khi chốt type.

**[PROPOSED] Trong tài liệu này:** nhóm API `/api/admin/**`, workflow review đầy đủ, bảng audit, nguồn gốc post removal, tài khoản đình chỉ/feature restriction, quyền quản trị đặc biệt, Community/Topic/Poll/Media/Broadcast backoffice.

### 1.3. Source of truth khi phê duyệt triển khai

1. **Business rule / permission** → cập nhật `domain_system.md` trước.
2. **Global HTTP + pagination + error** → giữ theo `system_architecture.md`.
3. **Auth/JWT/CSRF** → giữ theo `author_rule.md`.
4. **Public/REST request-response** → bổ sung `aloute-openapi.yaml` sau khi domain chốt.
5. **Thực thi** → theo `backend_rule.md` và `frontend_rule.md`.

Tài liệu này là **spec đề xuất cho Admin Dashboard**, **không âm thầm ghi đè** quy tắc canonical hiện tại.

---

## 2. Tầm nhìn sản phẩm và phạm vi

### 2.1. Mục tiêu

Xây **trang quản trị riêng** cho `SYSTEM_ADMIN` nhằm:

1. Quan sát sức khỏe hệ thống và lượng nội dung, người dùng, report.
2. Tra cứu User/Post/Report an toàn, theo quyền và có phân trang.
3. Xử lý report theo quy trình minh bạch, tránh hai Admin tranh chấp.
4. Gỡ/khôi phục Post bằng moderation operation có lưu lý do và audit.
5. Theo dõi được **ai** đã xử lý **đối tượng nào**, **khi nào**, **vì sao**.
6. Mở rộng dần sang đình chỉ tài khoản, quản lý Community, Topic, Poll, Media và Broadcast mà **không** cho CRUD dữ liệu tùy ý.

### 2.2. Các giai đoạn

| Giai đoạn | Nhóm chức năng | Tình trạng |
|---|---|---|
| **MVP / P1** | Overview, Users (read-only), Posts (read + remove/restore), Reports (review), Audit Log | Ưu tiên triển khai |
| **P2** | Cảnh cáo, đình chỉ có hạn, giới hạn tạo Post/Reply, lifecycle credential, lịch sử lệnh xử lý | Sau khi MVP ổn định |
| **P3** | Community administration, Topic, Poll, Media/quota, system broadcast, phân quyền Admin nhạy cảm, analytics nâng cao | Cần bổ sung domain/các contract mới |

### 2.3. Ngoài phạm vi

- **Không** làm Inline Admin Toolbar/Menu trên Feed, Profile, Community trong file này.
- **Không** làm generic CRUD kiểu `GET/PATCH/DELETE /api/admin/entities/{table}`.
- **Không** cho phép sửa trực tiếp bảng `poll_votes`, `community_members`, `messages`, `notifications` mà không thông qua domain service.
- **Không** truy cập/tải toàn bộ nội dung direct/group message riêng tư vì có quyền `SYSTEM_ADMIN`.
- **Không** dựng chat surveillance, report Message hoặc đọc private conversation nếu chưa được bổ sung domain, quyền truy cập bằng chứng và kiểm soát audit.
- **Không** hard-delete dữ liệu tham chiếu/đã phát sinh giao dịch chỉ bằng một nút trên UI.

### 2.4. Nguyên lý quan trọng

> Dashboard chỉ là giao diện. Mọi thao tác ghi sử dụng **service/policy nghiệp vụ** tương ứng; security không dựa vào việc ẩn nút. Mọi destructive/privileged action phải được kiểm tra quyền và lưu vết trong backend.

---

## 3. Sitemap, màn hình và trải nghiệm

### 3.1. Route Thymeleaf (SSR)

| Route UI | Màn hình | Giai đoạn |
|---|---|---|
| `GET /admin` | Tổng quan / Overview | P1 |
| `GET /admin/users` | Tra cứu người dùng | P1 |
| `GET /admin/users/{userId}` | User Detail + tabs | P1 |
| `GET /admin/posts` | Tra cứu Post / Reply | P1 |
| `GET /admin/posts/{postId}` | Post moderation detail | P1 |
| `GET /admin/reports` | Hàng đợi báo cáo | P1 |
| `GET /admin/reports/{reportId}` | Report review detail | P1 |
| `GET /admin/moderation-actions` | Audit / moderation history | P1 |
| `GET /admin/enforcement` | Suspension + restrictions còn hiệu lực | P2 |
| `GET /admin/communities` | Quản trị cộng đồng | P3 |
| `GET /admin/topics` | Quản trị topic | P3 |
| `GET /admin/polls` | Giám sát poll | P3 |
| `GET /admin/media` | Media và quota | P3 |
| `GET /admin/broadcasts` | Thông báo toàn hệ thống | P3 |

Page Controller SSR chỉ render HTML/ViewModel, **không** thay thế API JSON.

### 3.2. Sidebar

- **Tổng quan:** KPI nhanh, thông tin hệ thống.
- **Kiểm duyệt:** Reports, Posts, Audit.
- **Người dùng:** User list/detail, sau đó Enforcement.
- **Nội dung:** Communities, Topics, Polls (P3).
- **Vận hành:** Media, Quota, Broadcast (P3).
- **Đi về trang ALOUTE:** link về ứng dụng người dùng (không phải Inline Admin).

Badge `SYSTEM_ADMIN` chỉ là chỉ báo UI, **không** phải nguồn phân quyền.

### 3.3. Overview `/admin`

**Các thẻ KPI:** totalUsers, activeUsers, totalPosts, postsCreatedToday, pendingReports, reviewedReports, activeSuspensions (P2), totalCommunities, storageUsedBytes và media cap (P3).

**Các khối dữ liệu:**

1. Report mới chưa xử lý (ưu tiên theo thời gian tạo, không tự tính mức độ nguy hiểm nếu chưa có policy).
2. Biểu đồ số Post và User mới theo ngày (7/30 ngày).
3. Những moderation action gần nhất.
4. Tình trạng media quota toàn hệ thống (P3).

**UX:** mọi KPI hiển thị thời điểm `asOf`; thống kê có thể eventual consistency; không ép trang load full tables; màn hình có skeleton/loading/empty/error.

### 3.4. Users `/admin/users`

**Table:** ID, username, displayName, email (chỉ nếu nghiệp vụ thật sự cần), isVerified, isPrivate, isActive, computed moderation status, createdAt, last moderation date.

**Bộ lọc:** `q` (id/username/email theo policy), `isActive`, `isVerified`, `moderationStatus` (P2), `createdFrom`, `createdTo`, `sort`, `page`, `size`.

**Chi tiết User:**

- Thông tin identity/profile cần thiết (không gồm password hash/token).
- Các Post thuộc account trong phạm vi moderation.
- Các Report nhắm vào account và số lượng theo trạng thái.
- History của moderation action.
- P2: lệnh đình chỉ/cấm đăng đang có hiệu lực, form cảnh cáo/đình chỉ/gỡ đình chỉ.
- P3: reset nội dung profile vi phạm; verification; phân quyền Global Role nếu đã chốt thủ tục an toàn.

### 3.5. Posts `/admin/posts`

**Table:** ID, author, content excerpt (sanitize), `parentPostId`, community context, visibility, `deletedAt`, `reportedCount` (derived), createdAt.

**Bộ lọc:** `q`, `authorId`, `communityId`, `visibility`, `contentState=ACTIVE|AUTHOR_DELETED|ADMIN_REMOVED`, `reportedOnly`, khoảng thời gian, pagination.

**Post Detail:** thông tin nội dung được phép xem, tác giả, metadata, relation Reply/Quote, tổng hợp reports, moderation removal gần nhất, audit history; nút **Gỡ** hoặc **Khôi phục** theo capability từ backend.

**Riêng tư:** không mặc định bỏ qua visibility để browse mọi `FOLLOWERS`, `MENTIONED`, private Community. Nếu cần xem bằng chứng private, phải có workflow/cơ sở quyền và audit được Domain phê duyệt trước.

### 3.6. Reports `/admin/reports`

**Tab/Filter:** `PENDING`, `REVIEWED`, `RESOLVED`, `REJECTED`, tất cả.

**Table:** report ID, target (USER/POST), reason, reporter, createdAt, reviewer, status, thời gian chờ, action mở chi tiết.

**Detail:**

- Chi tiết report: reason, description; reporter/target theo permission.
- Link đến đối tượng được report, **không** tự động vượt privacy.
- Bằng chứng hiện có chỉ là trường của report và nội dung target được phép truy cập; **chưa có evidence attachment trong OpenAPI**.
- Nút `Nhận xử lý` khi `PENDING`.
- Khi `REVIEWED`: `Từ chối báo cáo` hoặc `Giải quyết báo cáo` (có thể kèm Post removal trong cùng transaction).
- Có ghi chú bắt buộc và hiển thị conflict khi report bị xử lý ở tab khác.

### 3.7. Audit `/admin/moderation-actions`

**Table:** actionId, actor, target, actionType, relatedReportId, createdAt, reason summary, outcome. Có filter actor/target/action/date; chỉ read-only; **không có nút xóa**.

### 3.8. Quy tắc UX chung

- Destructive form có **confirmation modal**, tên/ID đối tượng rõ ràng, lý do bắt buộc.
- **Không optimistic update** cho delete/restore/ban/role change. Chờ `2xx`, sau đó refetch hoặc cập nhật UI.
- Disable submit khi đang xử lý; hỗ trợ `Idempotency-Key`/chống double-submit.
- Hiển thị lỗi HTTP 401/403/404/409/422 theo error `code` ổn định; hiển thị traceId khi hỗ trợ.
- URL query giữ search/filter/page để back/forward không mất trạng thái.
- Menu keyboard-accessible, focus trap trong modal, responsive table.
- Không log content riêng tư hoặc raw request nhạy cảm vào browser console.

---

## 4. Ma trận nghiệp vụ và quyền

### 4.1. Các actor

| Actor | Ý nghĩa |
|---|---|
| `GUEST` | Không vào `/admin` hoặc `/api/admin/**` |
| `USER` | Không vào `/admin` hoặc `/api/admin/**` |
| `SYSTEM_ADMIN` | Được vào backoffice; từng operation vẫn kiểm tra resource/policy |
| Community `MODERATOR` / `ADMIN` / `OWNER` | **Không** mặc nhiên là `SYSTEM_ADMIN` |

Ở P1 có thể dùng `hasRole('SYSTEM_ADMIN')` và resource-level policy. P2/P3 **có thể** thêm global authority như `REPORT_REVIEW`, `USER_SUSPEND` nếu ứng dụng thật sự cần phân quyền con; không sinh authority cho từng thao tác tùy tiện.

### 4.2. Ma trận hành động

| Tác vụ | Giai đoạn | Quy tắc |
|---|---|---|
| Xem Overview | P1 | SYSTEM_ADMIN |
| Xem danh sách User/Post/Report/Audit | P1 | SYSTEM_ADMIN + data minimization/privacy policy |
| `POST_REMOVE` | P1 | SYSTEM_ADMIN + post hợp lệ + không xóa hai lần |
| `POST_RESTORE` | P1 | SYSTEM_ADMIN + active removal hợp lệ + đủ điều kiện restore |
| Start report review | P1 | SYSTEM_ADMIN + status PENDING |
| Reject/resolve report | P1 | Reviewer được giao + status REVIEWED + version khớp |
| `USER_WARN` | P2 | SYSTEM_ADMIN + lý do + hạn chế self/peer admin nếu cần |
| `USER_SUSPEND` / revoke | P2 | SYSTEM_ADMIN + policy an toàn + credential enforcement |
| Feature restriction | P2 | SYSTEM_ADMIN + feature whitelist + hạn sử dụng |
| Community suspend/owner transfer | P3 | SYSTEM_ADMIN + domain Community transition/owner invariants |
| Topic create/edit/archive | P3 | SYSTEM_ADMIN + reference constraints |
| Poll close | P3 | SYSTEM_ADMIN + thời điểm đóng hợp lệ + bảo toàn vote |
| Media quarantine/purge | P3 | SYSTEM_ADMIN + asset policy + storage lifecycle |
| Broadcast | P3 | SYSTEM_ADMIN + preview/confirmation + recipients + rate/idempotency |
| Global role change | P3 | Quy trình quyền đặc biệt, không tự thay role, không mất admin cuối |

### 4.3. Các bất biến bắt buộc phải chốt

1. Không được tự đình chỉ/tước role của mình.
2. Không được khóa/hạ cấp **SYSTEM_ADMIN cuối cùng**.
3. Không cho phép Admin sửa trực tiếp nội dung do user tạo để đánh tráo lời nói; gỡ nội dung và ghi lý do.
4. Không cho phép `POST_RESTORE` khôi phục bài do tác giả tự xóa hoặc từ chối khôi phục theo các lý do moderation đã chốt.
5. Không cho chỉnh sửa/xóa moderation audit.
6. Không nhập `actorId`, `adminId`, `reporterId` từ browser cho thao tác mà principal quyết định.
7. `Block/Mute/RestrictedUser` không được tái sử dụng làm system-wide suspension.
8. Bất kỳ access vào private content phục vụ điều tra phải có điều kiện, phạm vi và nhật ký riêng; không đặt ngoại lệ global vô điều kiện.
9. `is_verified` là trạng thái profile/account, **không** là bằng chứng có quyền admin.
10. Số liệu từ cached counters không được dùng như nguồn sự thật để sửa raw relation.

---

## 5. Domain model, dữ liệu và migration

> Cấu trúc sau là **logical schema đề xuất**. Không tạo DDL cuối cùng trước khi kiểm tra entity/schema/migration thực tế. Ưu tiên foreign key với ID type canonical của repository.

### 5.1. Existing tables / data cần sử dụng

| Nguồn | Phục vụ | Ghi chú |
|---|---|---|
| `users`, `profiles` | Users, Overview | `is_active`, `is_private`, `is_verified`; không expose password hash |
| `posts` | Post search/moderation | Reply/Quote đều ở `posts`; `deleted_at` soft delete |
| `reports` | Review queue | User/Post target; enum status/reason canonical |
| `communities`, `community_members` | P3 community | `owner_id` là source của Owner; scoped roles MEMBER/MODERATOR/ADMIN |
| `topics`, `post_topics`, `user_topics` | P3 topic | Không arbitrary cascade xóa relation |
| `polls`, `poll_options`, `poll_votes` | P3 poll | Single-choice/multiple-choice invariant |
| Media metadata / post media | P3 media | Storage thực tế trong Firebase; quota user 150 MB, global cap 8.5 GB theo Domain |
| `notifications`, `user_fcm_tokens` | P3 broadcast/notification | `SYSTEM`/`MODERATION` type chưa có: cần mở rộng |
| `blocks`, `mutes`, restricted relation | Social graph | **Không** phải enforcement system-wide |
| `conversations`, `messages` | Chưa ở scope | Không duyệt tự do tin nhắn riêng tư |

### 5.2. `[PROPOSED] moderation_actions` (P1)

Bảng ghi **mọi quyết định/hành động kiểm duyệt**. Append-only.

| Column | Logical type | Bắt buộc | Vai trò |
|---|---|---|---|
| `id` | ID canonical | Có | Primary key |
| `actor_user_id` | FK User | Có | Người thực hiện |
| `target_type` | enum | Có | `USER`, `POST`, `REPORT`, `COMMUNITY`, ... |
| `target_id` | ID canonical | Có | Đối tượng |
| `action_type` | enum | Có | Ví dụ `POST_REMOVE`, `POST_RESTORE`, `REPORT_RESOLVE` |
| `reason_code` | enum/text | Tùy action | Danh mục lý do moderation (phải chốt) |
| `reason_detail` | text | Có đối với action nhạy cảm | Lý do hiển thị/audit |
| `related_report_id` | FK Report nullable | Không | Report liên quan |
| `metadata` | JSON/structured nullable | Không | Snapshot tối thiểu trạng thái cũ/mới, **không** chứa bí mật |
| `created_at` | timestamptz | Có | UTC instant |
| `request_id` | text nullable | Không | Truy vết/idempotency nếu áp dụng |

**Indexes đề xuất:** `(target_type, target_id, created_at DESC)`, `(actor_user_id, created_at DESC)`, `(action_type, created_at DESC)`, `(related_report_id)`; chỉ thêm sau khi review query plan.

**Lưu ý:** audit log **không thay thế** bảng thể hiện trạng thái hiện tại của Post/Suspension. Không sửa lại audit khi action được undo; ghi action hoàn nguyên mới.

### 5.3. `[PROPOSED] post_moderation_removals` (P1)

Dùng để biết Post có bị gỡ **bởi Admin** hay là owner tự xóa:

| Column | Ý nghĩa |
|---|---|
| `id` | ID của lần gỡ moderation |
| `post_id` | Post bị gỡ |
| `remove_action_id` | Audit action gỡ |
| `restore_action_id` nullable | Audit action khôi phục |
| `removed_at` | Instant gỡ |
| `restored_at` nullable | Instant khôi phục |

**Invariant:** một Post tại một thời điểm chỉ có tối đa một active admin removal; DB unique/partial unique hoặc locking phù hợp vendor. Nếu người dùng tự xóa Post, không tạo active admin removal giả.

**Quan hệ với `deleted_at`:** `deleted_at` hiện vẫn đánh dấu soft-delete. Cần thiết kế rõ thuật toán cascade và restore của Reply/Quote/counter. **Không** chỉ `SET deleted_at=NULL` cho Post cha rồi coi như xong.

**Kiểm tra bắt buộc:** `sp_soft_delete_post_cascade` hiện ghi ví dụ tham số `UUID`, trong khi OpenAPI ID `int64`; đối chiếu implementation và trigger `trg_post_reply_count` để không trừ counter hai lần.

### 5.4. `[PROPOSED] mở rộng reports` (P1)

Giữ nguyên tất cả existing fields, bổ sung:

- `reviewed_by` nullable → Admin nhận xử lý.
- `reviewed_at` nullable → thời điểm từ PENDING sang REVIEWED.
- `resolved_at` nullable → lúc REJECTED/RESOLVED (tên cột có thể thống nhất `closed_at` vì bao gồm REJECTED).
- `resolution_note` nullable → quyết định xử lý.
- `version` integer/bigint → optimistic concurrency; hoặc một cơ chế conditional update tương đương.

**Transition đề xuất:** `PENDING → REVIEWED → RESOLVED | REJECTED`. Terminal report không bị sửa bởi endpoint decision thường; re-open là use case mới có audit riêng (P3 nếu cần).

### 5.5. `[PROPOSED] account_suspensions` (P2)

| Column | Ý nghĩa |
|---|---|
| `id` | Suspension record ID |
| `user_id` | User bị đình chỉ |
| `created_by` | Admin thực hiện |
| `reason_code`, `reason_detail` | Lý do |
| `starts_at`, `expires_at` | Hiệu lực; `expires_at=null` nếu indefinite được phép |
| `revoked_at`, `revoked_by`, `revoke_reason` | Gỡ trước hạn |
| `related_report_id` nullable | Truy nguyên report |
| `created_at` | Audit timeline |

**Active** nếu `starts_at <= now` và `(expires_at IS NULL OR expires_at > now)` và `revoked_at IS NULL`.

**Không** dùng `blocks`, `mutes`, `restrictions` (social) cho lệnh đình chỉ; không chỉ dựa vào một boolean `is_active` để suy ra lý do/thời hạn.

### 5.6. `[PROPOSED] account_feature_restrictions` (P2)

Fields: `id`, `user_id`, `feature`, `starts_at`, `expires_at`, `created_by`, `reason_code`, `reason_detail`, `revoked_at`, `revoked_by`, `related_report_id`, `created_at`.

Feature whitelist ban đầu: `POST_CREATE`, `POST_REPLY`. **Không** mở `MESSAGE_READ`, `MESSAGE_SEND`, `POLL_VOTE`... nếu chưa được Domain phê duyệt.

**Active logic** theo thời gian giống suspension; API tạo Post/Reply phải kiểm tra chính sách tại service trước transaction ghi dữ liệu.

### 5.7. `[DEFERRED]` Data model bổ sung cho P3

- Community state: muốn `SUSPENDED`/`ARCHIVED` phải bổ sung state và behavior (join/post/search/owner), không cố xóa community trực tiếp.
- Topic lifecycle: thêm `is_archived`/visibility sau khi chốt liệu slug đổi được hay không và các post liên kết xử lý thế nào.
- Poll close: nếu chỉ có `closesAt`, cần chốt close sớm là cập nhật `closesAt=now` hay phải có `closed_by_admin`/`closed_reason`/audit riêng; không sửa vote history.
- Media quarantine/retention: cần data model/quy trình Firebase riêng, chưa đủ dữ liệu trong ZIP.
- Broadcast: entity `system_broadcasts` + delivery job/outbox nếu cần gửi tới nhiều người và retry; type `SYSTEM`/`MODERATION` cần cập nhật enum Notification.

---

## 6. Quy ước API áp dụng cho toàn bộ Backoffice

### 6.1. Prefix, HTTP và transport

- **SSR:** `/admin/**` trả HTML.
- **JSON:** `/api/admin/**` trả `ApiResponse<T>` envelope canonical.
- **Auth:** browser Thymeleaf dùng JWT HttpOnly Cookie `aloute_access_token`; REST client dùng `Authorization: Bearer <token>` (chi tiết theo Auth rule).
- **CSRF:** request ghi từ cookie phải gửi `X-XSRF-TOKEN` tương ứng token CSRF; không cho JavaScript đọc JWT cookie.
- **HTTP content:** `Accept: application/json`, `Content-Type: application/json` khi có body.
- **Request trace:** `X-Request-Id` tùy chọn; gợi ý `Idempotency-Key` cho POST destructive/creation (phải được formalize trong architecture nếu áp dụng).
- **Role:** `ROLE_SYSTEM_ADMIN`, **không** nhận `X-Is-Admin`, `X-Role`, `adminId` từ client.

### 6.2. Query & pagination

- `page=0`, `size=20`, `size <= 100`.
- Sort định dạng `sort=createdAt,desc`; whitelist sort keys tại từng endpoint.
- Date-time ISO-8601 UTC trong API, chẳng hạn `2026-10-14T04:30:00Z`.
- Không trộn page + cursor ở cùng endpoint.
- Filter tham số tùy chọn chỉ tác động phần server query, **không** tự filtering tất cả record ở browser.

### 6.3. Response envelope chuẩn `[EXISTING]`

Single-resource:

```json
{
  "success": true,
  "data": {"id": 1001},
  "meta": null
}
```

Page:

```json
{
  "success": true,
  "data": [{"id": 1001}],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

Error:

```json
{
  "success": false,
  "error": {
    "code": "POST_ALREADY_DELETED",
    "message": "Post has already been deleted",
    "fieldErrors": null
  },
  "traceId": "01H..."
}
```

> Các object `data` trong phần API dưới đây là **DTO mới được đề xuất**, không phải schema đã tồn tại trong OpenAPI hiện hành. `data` không bao giờ là raw JPA Entity.

### 6.4. Quy ước request của Admin

- DTO nhận các trường **cần để mô tả quyết định**, không nhận `actorUserId`, `performedBy`, `createdAt`, `status` do client tự quyết định.
- Lệnh xóa/đình chỉ/khôi phục cần `reasonDetail` không rỗng; giới hạn độ dài chính thức phải được chốt (đề xuất 10–1000 ký tự).
- Report review/decision sử dụng `expectedVersion` để chống ghi đè; tốt hơn có thêm `If-Match`/ETag nếu architecture thống nhất.
- `reasonCode` moderation ở đây đề xuất tái sử dụng `SPAM`, `HARASSMENT`, `HATE`, `MISINFORMATION`, `VIOLENCE`, `OTHER` để giảm số enum, nhưng **không tự coi nó đã là enum chính thức của ModerationAction**.
- `reasonDetail` là mô tả quyết định; nếu cần thông điệp gửi cho user khác với ghi chú nội bộ, tách `publicNotice` và `internalNote` (không gửi internalNote ra bên ngoài).
- `GET` **không có JSON request body**; dữ liệu lọc nằm trong query.
- Các endpoint `POST` thực hiện action và trả resource/action response `200`; create resource có thể trả `201`, phải chọn ổn định khi đưa vào OpenAPI.

---

## 7. API nhóm Overview & Analytics

### 7.1. `GET /api/admin/overview` `[P1][PROPOSED]`

**Mục đích:** dữ liệu trang tổng quan, không lấy toàn bộ record.

**Query:** `?period=7d` (`7d|30d` theo chính sách), GET **không có body**.

**200 Data mẫu:**

```json
{
  "success": true,
  "data": {
    "asOf": "2026-10-14T05:00:00Z",
    "period": "7d",
    "metrics": {
      "totalUsers": 1200,
      "activeUsers": 1150,
      "totalPosts": 8500,
      "postsCreatedToday": 132,
      "pendingReports": 21,
      "reviewedReports": 8,
      "totalCommunities": 35
    },
    "dailyActivity": [
      {"date": "2026-10-13", "newUsers": 15, "newPosts": 110},
      {"date": "2026-10-14", "newUsers": 7, "newPosts": 132}
    ],
    "recentReportIds": [601, 602, 603],
    "recentModerationActionIds": [901, 902]
  },
  "meta": null
}
```

**Quy tắc:** tách `totalPosts` (all-time/scope rõ) với `postsCreatedToday`; timezone tính “hôm nay” phải xác định (đề xuất timezone hiển thị mặc định là Asia/Ho_Chi_Minh, UTC lưu trữ). `recent...Ids` là tham chiếu để load detail khi cần, có thể thay bằng DTO summary sau khi chốt UI.

### 7.2. `GET /api/admin/analytics/activity` `[P3][PROPOSED]`

Query: `?from=2026-10-01&to=2026-10-14&granularity=DAY&metric=POST_CREATED`; allowlist metric `POST_CREATED`, `USER_REGISTERED`, `REPORT_CREATED`, `REPORT_RESOLVED` (đề xuất). Return time series `{bucket, value}`; giới hạn số bucket và phạm vi ngày; không có body.

---

## 8. API nhóm User Management

### 8.1. `GET /api/admin/users` `[P1][PROPOSED]`

**Query mẫu:**

```http
GET /api/admin/users?q=anh&isActive=true&isVerified=false&sort=createdAt,desc&page=0&size=20
```

**Không có body.**

**Data item đề xuất:**

```json
{
  "id": 123,
  "username": "student_123",
  "displayName": "Nguyen A",
  "isActive": true,
  "isVerified": false,
  "isPrivate": false,
  "moderationStatus": "NORMAL",
  "createdAt": "2026-09-20T08:30:00Z"
}
```

`moderationStatus=NORMAL|SUSPENDED|RESTRICTED` là **computed field P2**, không nên ép lưu trùng trong User. Email nên cân nhắc chỉ hiển thị tại detail hoặc khi đã có policy cho phép.

### 8.2. `GET /api/admin/users/{userId}` `[P1][PROPOSED]`

**200 Data mẫu:**

```json
{
  "success": true,
  "data": {
    "id": 123,
    "username": "student_123",
    "email": "example@university.edu",
    "displayName": "Nguyen A",
    "bio": "Hello ALOUTE",
    "avatarUrl": null,
    "isActive": true,
    "isVerified": false,
    "isPrivate": false,
    "createdAt": "2026-09-20T08:30:00Z",
    "counts": {"posts": 12, "reportsReceived": 2},
    "capabilities": {
      "canWarn": false,
      "canSuspend": false,
      "canResetProfile": false,
      "canChangeGlobalRole": false
    }
  },
  "meta": null
}
```

`capabilities` mô tả quyền *server-side* theo giai đoạn. User DTO không bao gồm password, token, raw security credentials.

### 8.3. `GET /api/admin/users/{userId}/posts` `[P1][PROPOSED]`

Query: `?contentState=ACTIVE&page=0&size=20`; return AdminPostSummary Page (cùng model với section 9); không body. Quyền truy cập nội dung riêng tư phụ thuộc policy — không mặc định bypass.

### 8.4. `GET /api/admin/users/{userId}/moderation-actions` `[P1][PROPOSED]`

Query: `?page=0&size=20&sort=createdAt,desc`; return ModerationAction Page; cùng nguồn log section 11, không nhân đôi bảng.

### 8.5. `POST /api/admin/users/{userId}/warnings` `[P2][PROPOSED]`

**Request body:**

```json
{
  "reasonCode": "HARASSMENT",
  "reasonDetail": "Đã xác nhận hành vi công kích cá nhân nhiều lần.",
  "relatedReportId": 601,
  "publicNotice": "Tài khoản đã nhận cảnh cáo về quy tắc ứng xử."
}
```

**Kết quả:** tạo Warning/ModerationAction theo model được chốt; notification gửi sau commit; không tự động đình chỉ nếu chưa có policy tích lũy cảnh cáo.

### 8.6. `POST /api/admin/users/{userId}/profile-resets` `[P3][PROPOSED]`

**Request body:**

```json
{
  "fields": ["AVATAR", "BIO"],
  "reasonCode": "OTHER",
  "reasonDetail": "Ảnh đại diện và bio chứa nội dung vi phạm.",
  "relatedReportId": 601
}
```

**Quy tắc:** chỉ whitelist `AVATAR`, `BIO`, có thể `DISPLAY_NAME` nếu Domain cho phép. Reset về default, không cho arbitrary edit, không thay đổi username/email. Avatar storage xử lý theo retention policy để không làm mất bằng chứng.

### 8.7. `POST /api/admin/users/{userId}/verification-changes` `[P3][PROPOSED]`

**Request body:**

```json
{
  "verified": true,
  "reasonDetail": "Đã đối chiếu tiêu chí xác thực được nhà trường phê duyệt."
}
```

**Chặn triển khai cho tới khi có policy:** ai được cấp verified, điều kiện chứng minh, cách thu hồi, audit và notification. Không xem verified như role.

### 8.8. `POST /api/admin/users/{userId}/global-role-changes` `[P3][PROPOSED, HIGH RISK]`

**Request body:**

```json
{
  "targetRole": "SYSTEM_ADMIN",
  "reasonDetail": "Bổ nhiệm quản trị viên theo quyết định nội bộ."
}
```

**Bảo vệ bắt buộc:** prevent self-role-change, protect last system admin, atomic recheck under lock, nhật ký bất biến, session/JWT permission refresh hoặc revoke. Nếu hệ thống có đủ điều kiện, nên tách quyền phê duyệt khỏi người yêu cầu (4-eyes principle). Chỉ mở endpoint khi đã quyết định đầy đủ business/policy.

---

## 9. API nhóm Post Moderation

### 9.1. `GET /api/admin/posts` `[P1][PROPOSED]`

**Query mẫu:**

```http
GET /api/admin/posts?authorId=123&contentState=ACTIVE&reportedOnly=true&sort=createdAt,desc&page=0&size=20
```

Các filter có thể hỗ trợ: `q`, `postId`, `authorId`, `communityId`, `visibility`, `contentState`, `reportedOnly`, `createdFrom`, `createdTo`, `page`, `size`, `sort`. Không body.

**AdminPostSummary item mẫu:**

```json
{
  "id": 3001,
  "author": {"id": 123, "username": "student_123"},
  "contentExcerpt": "Đây là nội dung hiển thị theo quyền...",
  "parentPostId": null,
  "quotedPostId": null,
  "communityId": null,
  "visibility": "PUBLIC",
  "contentState": "ACTIVE",
  "reportedCount": 2,
  "createdAt": "2026-10-13T06:20:00Z",
  "capabilities": {"canRemove": true, "canRestore": false}
}
```

`contentState` là projection theo owner deletion/admin removal, không phải Post enum canonical hiện có. Trường `visibility` lấy từ enum hiện có; cần đối chiếu repository để không cố cho Admin xem nội dung không được phép.

### 9.2. `GET /api/admin/posts/{postId}` `[P1][PROPOSED]`

Chi tiết có metadata, related reports, latest removal, author, capabilities; dùng redaction nếu bị giới hạn quyền riêng tư. Nếu không có quyền truy cập, trả `403` hoặc `404` theo conceal policy — không leak excerpt.

**Ví dụ Data:**

```json
{
  "id": 3001,
  "author": {"id": 123, "username": "student_123"},
  "content": "Nội dung Post được quyền xem",
  "visibility": "PUBLIC",
  "contentState": "ACTIVE",
  "parentPostId": null,
  "quotedPostId": null,
  "createdAt": "2026-10-13T06:20:00Z",
  "deletedAt": null,
  "relatedReportIds": [601, 602],
  "activeRemovalId": null,
  "capabilities": {"canRemove": true, "canRestore": false}
}
```

### 9.3. `POST /api/admin/posts/{postId}/remove` `[P1][PROPOSED]`

**Action:** Admin gỡ Post vi phạm (khác với `DELETE /api/posts/{postId}` do owner tự xóa).

**Request body:**

```json
{
  "reasonCode": "HARASSMENT",
  "reasonDetail": "Bài viết chứa hành vi quấy rối, nhắm đến cá nhân cụ thể.",
  "relatedReportId": 601,
  "publicNotice": "Bài viết của bạn đã bị gỡ do vi phạm quy tắc ứng xử."
}
```

**Validation:**

- `reasonCode` trong danh mục hợp lệ; `reasonDetail` bắt buộc 10–1000 ký tự (đề xuất).
- `relatedReportId` nếu có phải trỏ đến report **về đúng Post** (nếu report về User thì chỉ cho phép khi policy liên kết rõ).
- Post tồn tại và có quyền xử lý; Post chưa self-deleted hoặc already-admin-removed.
- Nếu Post trong Community có rule moderation riêng, vẫn xử lý qua domain post removal/cascade chung.
- Không cho chọn `createdBy`, `removedAt`, `deletedAt` trong body.

**200 Data mẫu:**

```json
{
  "success": true,
  "data": {
    "actionId": 901,
    "postId": 3001,
    "actionType": "POST_REMOVE",
    "contentState": "ADMIN_REMOVED",
    "removalId": 701,
    "occurredAt": "2026-10-14T05:20:00Z"
  },
  "meta": null
}
```

**Important:** Không được chỉ gọi `postRepository.delete()` hoặc cập nhật `deleted_at` theo đường tắt; phải dùng `PostModerationService` điều phối cascade/trigger/counter, audit và transaction. Không cập nhật DOM trước khi server trả success.

### 9.4. `POST /api/admin/posts/{postId}/restore` `[P1][PROPOSED]`

**Request body:**

```json
{
  "expectedRemovalId": 701,
  "reasonDetail": "Đã xác minh gỡ bài nhầm sau khi xem xét bằng chứng mới."
}
```

**Quy tắc:**

- `expectedRemovalId` phải là active removal của **đúng** Post, để chống restore nhầm phiên.
- Chỉ restore Post **bị gỡ bởi Admin**, không phải Post owner tự xóa; không đảo ngược hành động tác giả.
- Khi cascade từ Post gốc sang Reply/Quote, phải thiết kế restore chính xác theo provenance; **không** khôi phục các Reply đã được user tự xóa hoặc bị gỡ độc lập.
- Ghi `POST_RESTORE` audit, đánh dấu removal đã restore, thay đổi Post aggregate trong cùng transaction.

**200 Data mẫu:**

```json
{
  "success": true,
  "data": {
    "actionId": 902,
    "postId": 3001,
    "actionType": "POST_RESTORE",
    "contentState": "ACTIVE",
    "restoredRemovalId": 701,
    "occurredAt": "2026-10-14T06:10:00Z"
  },
  "meta": null
}
```

### 9.5. `GET /api/admin/posts/{postId}/moderation-actions` `[P1][PROPOSED]`

Query `?page=0&size=20&sort=createdAt,desc`; pagination; không body; trả history từ bảng `moderation_actions`.

### 9.6. Chức năng **không làm** trong P1

- Admin sửa content của Post người khác.
- Tự đổi visibility sang PUBLIC để “xử lý”.
- Arbitrary sửa `allowReply`, `allowQuote` như tác giả mà chưa có domain policy.
- Ghim bài lên feed toàn hệ thống. Nếu sau này làm, phải định nghĩa slot, priority, expiry, audience, chống abuse và ranking integration riêng — không tái sử dụng trường Post không có.

---

## 10. API nhóm Reports & Case Review

### 10.1. Quy tắc hiện có và đề xuất

**[EXISTING] Report reasons:** `SPAM`, `HARASSMENT`, `HATE`, `MISINFORMATION`, `VIOLENCE`, `OTHER`.  
**[EXISTING] Report statuses:** `PENDING`, `REVIEWED`, `REJECTED`, `RESOLVED`.  
**[PROPOSED] Review transition chặt:** `PENDING → REVIEWED → (REJECTED | RESOLVED)`.

Report chỉ target **User hoặc Post**. Không tạo status `DISMISSED` và không phát minh `MESSAGE` target trong phiên bản này.

### 10.2. `GET /api/admin/reports` `[P1][PROPOSED]`

**Query:**

```http
GET /api/admin/reports?status=PENDING&targetType=POST&reason=HARASSMENT&sort=createdAt,asc&page=0&size=20
```

Các filter thêm: `reviewedBy`, `reportedUserId`, `reportedPostId`, `createdFrom`, `createdTo`. Không body.

**Data item mẫu:**

```json
{
  "id": 601,
  "targetType": "POST",
  "targetId": 3001,
  "reporter": {"id": 901, "username": "reporter01"},
  "reason": "HARASSMENT",
  "descriptionExcerpt": "Nội dung có dấu hiệu quấy rối...",
  "status": "PENDING",
  "reviewedBy": null,
  "createdAt": "2026-10-14T04:00:00Z",
  "version": 0
}
```

### 10.3. `GET /api/admin/reports/{reportId}` `[P1][PROPOSED]`

**200 Data mẫu:**

```json
{
  "success": true,
  "data": {
    "id": 601,
    "target": {"type": "POST", "id": 3001},
    "reporter": {"id": 901, "username": "reporter01"},
    "reason": "HARASSMENT",
    "description": "Bài viết có nội dung công kích cá nhân.",
    "status": "PENDING",
    "reviewedBy": null,
    "reviewedAt": null,
    "closedAt": null,
    "resolutionNote": null,
    "createdAt": "2026-10-14T04:00:00Z",
    "version": 0,
    "capabilities": {"canStartReview": true, "canDecide": false}
  },
  "meta": null
}
```

### 10.4. `POST /api/admin/reports/{reportId}/start-review` `[P1][PROPOSED]`

**Request body:**

```json
{
  "expectedVersion": 0
}
```

**Nghiệp vụ:** Atomic conditional update `PENDING -> REVIEWED`, set `reviewed_by` bằng current principal, `reviewed_at=now`, version +1, ghi `REPORT_REVIEW` audit nếu Domain yêu cầu audit review action.

**200 Data mẫu:**

```json
{
  "success": true,
  "data": {
    "id": 601,
    "status": "REVIEWED",
    "reviewedBy": 55,
    "reviewedAt": "2026-10-14T04:30:00Z",
    "version": 1
  },
  "meta": null
}
```

**Conflict:** nếu report đã REVIEWED hoặc version không khớp → `409`. Không được ghi đè reviewer ngầm.

### 10.5. `POST /api/admin/reports/{reportId}/decisions` `[P1][PROPOSED]`

**Case 1 — REJECT (không phát sinh xử phạt)**

```json
{
  "expectedVersion": 1,
  "decision": "REJECT",
  "resolutionNote": "Không tìm thấy vi phạm đủ căn cứ.",
  "resolutionAction": null
}
```

**Case 2 — RESOLVE kèm gỡ Post được báo cáo**

```json
{
  "expectedVersion": 1,
  "decision": "RESOLVE",
  "resolutionNote": "Báo cáo hợp lệ, đã gỡ nội dung vi phạm.",
  "resolutionAction": {
    "type": "POST_REMOVE",
    "reasonCode": "HARASSMENT",
    "reasonDetail": "Post chứa nội dung công kích cá nhân.",
    "publicNotice": "Bài viết đã bị gỡ vì quấy rối."
  }
}
```

**Case 3 — RESOLVE, không cần action mới** (ví dụ đã có admin khác xử lý Post trước đó, chỉ đóng report sau khi kiểm tra):

```json
{
  "expectedVersion": 1,
  "decision": "RESOLVE",
  "resolutionNote": "Nội dung liên quan đã được xử lý ở action 901.",
  "resolutionAction": null
}
```

**Validation & state:**

- Chỉ reviewer được gán hoặc người có quyền reassign đặc biệt mới quyết định; policy P1 nên ưu tiên reviewer đã nhận case.
- Report phải `REVIEWED`; client `expectedVersion` khớp.
- `REJECT` không được có action xử phạt trong cùng request.
- `RESOLVE` có action thì action phải áp dụng **đúng target report**. Với report target User, `POST_REMOVE` không áp dụng nếu chưa có policy link target rõ ràng.
- `resolutionNote` bắt buộc; `resolutionAction.type` allowlist (P1 chỉ `POST_REMOVE`).
- Gỡ Post, log action và chuyển report thành `RESOLVED` phải **cùng transaction**. Nếu bất kỳ bước nào fail → rollback.
- Nếu không có action mới, bắt buộc giải thích/tài liệu tham chiếu việc xử lý theo policy được duyệt.

**200 Data mẫu:**

```json
{
  "success": true,
  "data": {
    "reportId": 601,
    "status": "RESOLVED",
    "resolutionActionId": 901,
    "closedAt": "2026-10-14T05:20:00Z",
    "version": 2
  },
  "meta": null
}
```

> **Phân biệt:** `POST /api/admin/posts/{id}/remove` là xử lý nội dung **độc lập** với Report. `POST /api/admin/reports/{id}/decisions` có thể **bao gồm** Post removal trong cùng transaction. UI không được gọi cả hai cho cùng một quyết định để tránh double-remove.

### 10.6. Các luồng deferred

- Reassign reviewer, reopen report, multi-report grouping, appeal/khiếu nại, evidence upload: cần Domain/Policy/Schema mới.
- Report Message/Conversation: **không nằm trong scope**; không lén dùng `targetType=MESSAGE`.

---

## 11. API nhóm Moderation History / Audit

### 11.1. `GET /api/admin/moderation-actions` `[P1][PROPOSED]`

**Query:**

```http
GET /api/admin/moderation-actions?targetType=POST&targetId=3001&actionType=POST_REMOVE&from=2026-10-01T00:00:00Z&to=2026-10-14T23:59:59Z&page=0&size=20
```

Hỗ trợ `actorId`, `relatedReportId`, `sort=createdAt,desc`. Không body.

**Item mẫu:**

```json
{
  "id": 901,
  "actor": {"id": 55, "username": "system_admin"},
  "targetType": "POST",
  "targetId": 3001,
  "actionType": "POST_REMOVE",
  "reasonCode": "HARASSMENT",
  "reasonDetail": "Bài viết có hành vi quấy rối.",
  "relatedReportId": 601,
  "createdAt": "2026-10-14T05:20:00Z"
}
```

### 11.2. `GET /api/admin/moderation-actions/{actionId}` `[P1][PROPOSED]`

Trả audit detail + sanitized `metadata` + related entity refs. Không body. Chỉ hiển thị phiên bản phù hợp quyền; không trả token, password hash, private content snapshot không hợp lệ.

### 11.3. Tuyệt đối không hỗ trợ

- `DELETE /api/admin/moderation-actions/{id}`.
- `PATCH` sửa reason/actor/history đã tạo.
- UI “xóa nhật ký” hoặc “xóa vết”.

Nếu log sai, tạo **correction event** với tham chiếu log cũ, không overwrite sự thật lịch sử.

---

## 12. API nhóm Account Suspension & Restrictions

> **[P2][PROPOSED]** Không có model suspension system-wide hiện tại. Cần chốt domain, lifecycle token/session và toàn bộ entry point ghi dữ liệu trước khi bật API.

### 12.1. `GET /api/admin/enforcement` `[P2]`

Query `?kind=SUSPENSION&active=true&page=0&size=20`; hỗ trợ `userId`, `sort`; không body. Trả danh sách enforcement records đang hoạt động và lịch sử.

### 12.2. `GET /api/admin/users/{userId}/suspensions` `[P2]`

Query `?active=true&page=0&size=20`; không body; trả suspension records.

### 12.3. `POST /api/admin/users/{userId}/suspensions` `[P2]`

**Request khóa có thời hạn:**

```json
{
  "reasonCode": "HARASSMENT",
  "reasonDetail": "Quấy rối nhiều tài khoản khác sau khi đã được cảnh cáo.",
  "expiresAt": "2026-10-21T05:00:00Z",
  "relatedReportId": 601,
  "publicNotice": "Tài khoản bị đình chỉ đến ngày 21/10/2026."
}
```

**Khóa vô thời hạn (chỉ khi Domain cho phép):** `"expiresAt": null`, kèm lý do; không mặc định cho phép mọi Admin.

**Validation:** ngày hết hạn > server now, không quá maximum suspension policy; không tự khóa mình, không khóa admin cuối, policy về xử lý peer Admin; prevent overlapping duplicative suspension; `relatedReportId` nếu có phải liên quan đúng User.

**201 Data mẫu:**

```json
{
  "success": true,
  "data": {
    "id": 801,
    "userId": 123,
    "startsAt": "2026-10-14T05:00:00Z",
    "expiresAt": "2026-10-21T05:00:00Z",
    "active": true,
    "moderationActionId": 910
  },
  "meta": null
}
```

**Bảo mật:** khi suspension có hiệu lực, mọi protected action phải recheck account policy; JWT/session cũ không được cấp hành động trái policy. Không chỉ cấm login mới.

### 12.4. `POST /api/admin/users/{userId}/suspensions/{suspensionId}/revoke` `[P2]`

**Request body:**

```json
{
  "reasonDetail": "Đã chấp nhận khiếu nại và quyết định mở khóa sớm."
}
```

**200:** record có `revokedAt`, `active=false`, new audit action. Không dùng `POST /unban` không biết revoking bản ghi nào.

### 12.5. `GET /api/admin/users/{userId}/restrictions` `[P2]`

Query `?active=true&feature=POST_CREATE&page=0&size=20`; không body.

### 12.6. `POST /api/admin/users/{userId}/restrictions` `[P2]`

**Request body:**

```json
{
  "feature": "POST_CREATE",
  "reasonCode": "SPAM",
  "reasonDetail": "Đăng nội dung spam liên tiếp.",
  "expiresAt": "2026-10-17T05:00:00Z",
  "relatedReportId": 602,
  "publicNotice": "Tạm thời không thể đăng bài mới trong 3 ngày."
}
```

Feature whitelist khởi đầu: `POST_CREATE`, `POST_REPLY`. Nếu account có nhiều restrictions, effective permission phải được tính theo **các restriction còn active**, không cache boolean vĩnh viễn trên `users`.

### 12.7. `POST /api/admin/users/{userId}/restrictions/{restrictionId}/revoke` `[P2]`

**Request body:**

```json
{
  "reasonDetail": "Đã hoàn tất thời gian thử thách, gỡ hạn chế sớm."
}
```

Ghi revoke action + cập nhật restriction trong transaction. Vẫn kiểm tra nếu target record đúng User và còn active.

### 12.8. Quy tắc thời hạn

- `expiresAt` là UTC instant, backend quyết định hiện hành theo server time.
- Không cần scheduler để lệnh **hết hiệu lực**; scheduled job chỉ dùng reconciliation/notification/cleanup.
- Hết hạn suspension không tự sửa `users.is_active=true` nếu account còn bị inactive vì một nguyên nhân khác.
- Trường `is_active` không bị biến thành trạng thái phản ánh một nguyên nhân đình chỉ duy nhất.
- Cần policy kiểm tra tại authentication, API tạo Post/Reply và nếu có WebSocket protected action.

---

## 13. API nhóm Community Management

> **[P3][DEFERRED]** ALOUTE có `communities`, `community_members`, `owner_id`, nhưng transfer-owner/suspension/community moderation nâng cao là known GAP. Không code endpoint ghi trước khi cập nhật Domain.

### 13.1. `GET /api/admin/communities`

Query `?q=dev&isPrivate=false&ownerId=123&page=0&size=20&sort=createdAt,desc`; không body. DTO: id, name, slug, ownerId, isPrivate, memberCount, postCount, computedStatus (nếu bổ sung lifecycle).

### 13.2. `GET /api/admin/communities/{communityId}`

Trả summary, owner, member count, recent/reported posts, moderator/admin distribution **trong phạm vi privacy policy**. Không mặc định expose private community content.

### 13.3. `POST /api/admin/communities/{communityId}/suspensions`

**Request body đề xuất:**

```json
{
  "reasonCode": "OTHER",
  "reasonDetail": "Cộng đồng vi phạm quy chế và đang được điều tra.",
  "expiresAt": "2026-10-21T05:00:00Z"
}
```

**GAP:** phải định nghĩa Community `status` và behavior cụ thể khi suspended: có cho xem bài cũ? owner/moderator có đăng được không? người dùng có join/leave không? App vẫn cần duy trì membership/history ra sao? Không dùng `is_private=true` để giả làm khóa.

### 13.4. `POST /api/admin/communities/{communityId}/suspensions/{suspensionId}/revoke`

**Request body:**

```json
{
  "reasonDetail": "Cộng đồng đã hoàn thành khắc phục vi phạm."
}
```

### 13.5. `POST /api/admin/communities/{communityId}/owner-transfers`

**Request body:**

```json
{
  "newOwnerUserId": 234,
  "reasonDetail": "Owner cũ không còn quản lý; chuyển cho thành viên đủ điều kiện."
}
```

**Bảo toàn invariants:** người nhận đủ điều kiện, row membership nếu required, giữ đúng một `owner_id`, không làm mất owner cuối, không nhầm `CommunityRole.ADMIN` với owner. Transaction lock community + cập nhật owner + audit; trigger `trg_community_prevent_owner_leave` phải được kiểm chứng.

### 13.6. Quản lý scoped role

Có API hiện có `PATCH /api/communities/{communityId}/members/{userId}/role`. Nếu cần admin override từ Backoffice, **không** trực tiếp sửa `community_members.role`; có thể tạo endpoint `PATCH /api/admin/communities/{communityId}/members/{userId}/role` nhưng phải dùng chung Community membership policy/service và chỉ áp dụng cho `MEMBER|MODERATOR|ADMIN`, **không** gán `OWNER` trong enum role.

**Body minh họa:**

```json
{
  "role": "MODERATOR",
  "reasonDetail": "Phân công hỗ trợ kiểm duyệt theo quyết định quản trị."
}
```

**Không làm:** xóa vĩnh viễn Community trong MVP, thay đổi owner bằng raw update, xem mọi bài private không cần thủ tục.

---

## 14. API nhóm Topic Management

> **[P3][DEFERRED]** Topic/slug tồn tại; archive/pinning chưa có lifecycle chính thức.

### 14.1. `GET /api/admin/topics`

Query `?q=java&page=0&size=20&sort=name,asc`; không body. DTO id, name, slug, useCount (derived), archived (nếu đã có schema mới).

### 14.2. `POST /api/admin/topics`

**Request body:**

```json
{
  "name": "Khoa học dữ liệu",
  "slug": "khoa-hoc-du-lieu"
}
```

Validation: unique slug, normalization, length, chống trùng name theo rule chốt; actor từ principal. Response `201` với Topic DTO.

### 14.3. `PATCH /api/admin/topics/{topicId}`

**Request body:**

```json
{
  "name": "Data Science",
  "slug": "data-science",
  "reasonDetail": "Chuẩn hóa tên chủ đề."
}
```

Slug change có thể làm hỏng link/backlinks; cần policy redirect/alias trước khi cho đổi.

### 14.4. `POST /api/admin/topics/{topicId}/archive`

**Request body:**

```json
{
  "reasonDetail": "Chủ đề trùng lặp và không còn được dùng cho bài mới."
}
```

Đặt topic không được chọn cho content mới; **không xóa tùy ý** các quan hệ `post_topics` và `user_topics` lịch sử. Nếu muốn pin topic trên Explore, đó là separate recommendation/placement domain chưa có, **không** ngầm thêm `is_pinned` vào Topic.

---

## 15. API nhóm Poll Management

> **[P3][DEFERRED]** Poll thuộc Post, có `closesAt` và `allowMultiple`; các vote có business invariants, không cho sửa trực tiếp vote count.

### 15.1. `GET /api/admin/polls`

Query `?q=survey&state=OPEN&page=0&size=20`; không body. Summary: id, postId, question, closesAt, computedState, totalVotes; tôn trọng content privacy.

### 15.2. `GET /api/admin/polls/{pollId}`

Trả metadata, option aggregate counts, đóng/mở, author và related post. Không trả raw danh sách ai vote nếu Domain chưa cho phép vì privacy của lựa chọn bình chọn.

### 15.3. `POST /api/admin/polls/{pollId}/close`

**Request body:**

```json
{
  "reasonDetail": "Poll có nội dung không phù hợp và cần kết thúc sớm."
}
```

**Business:** nếu đang mở thì đóng sớm theo time/poll policy, ghi audit; không sửa votes cũ, không xóa poll mà giữ Post dẫn tới dangling reference. Phải quyết định `closesAt=now` có đủ hay cần `closedByAdminAt`, `closedReason`.

Không hỗ trợ Admin tự tạo/sửa phiếu bầu giả hoặc xóa từng vote để thay kết quả.

---

## 16. API nhóm Media Management & Quota

> **[P3][DEFERRED]** Media storage thực tế trong Firebase, riêng user quota 150 MB, global safety cap 8.5 GB; phải đối chiếu tài liệu Media Storage còn thiếu trước khi code destructive operations.

### 16.1. `GET /api/admin/media`

Query `?ownerId=123&type=IMAGE&state=ATTACHED&page=0&size=20`; không body. DTO metadata: mediaId, ownerId, type, sizeBytes, attachedTo, createdAt, storageState. URL có thể phải được cấp ngắn hạn tùy policy; không public hóa private media qua dashboard.

### 16.2. `GET /api/admin/media/quota`

GET không body. Data đề xuất:

```json
{
  "globalUsedBytes": 2147483648,
  "globalSafetyCapBytes": 9126805504,
  "userQuotaBytes": 157286400,
  "uploadEnabled": true,
  "asOf": "2026-10-14T05:00:00Z"
}
```

`globalSafetyCapBytes` ví dụ thể hiện xấp xỉ 8.5 GiB; hệ thống phải dùng **đúng cách định nghĩa đơn vị GB/GiB** đã được chốt trong cấu hình/storage rule, không hardcode từ ví dụ này. Quota source-of-truth nằm tại Media subsystem.

### 16.3. `GET /api/admin/media/orphan-candidates`

Query `?olderThan=2026-10-01T00:00:00Z&page=0&size=20`; trả **ứng viên** mồ côi, không tự xác nhận là có thể xóa. Chỉ list để review.

### 16.4. `POST /api/admin/media/{mediaId}/quarantine`

**Request body:**

```json
{
  "reasonCode": "OTHER",
  "reasonDetail": "Tệp có nội dung vi phạm quy chế sử dụng."
}
```

**GAP:** định nghĩa quarantine state/khả năng khôi phục, kiểm tra media được dùng ở Post/Message/Avatar nào. Không xóa Firebase object trước khi chốt retention và consistency với DB.

### 16.5. `POST /api/admin/media/cleanup-jobs` (tùy chọn sau)

**Request body:**

```json
{
  "dryRun": true,
  "onlyOrphans": true,
  "olderThan": "2026-09-14T00:00:00Z",
  "reasonDetail": "Kiểm tra danh sách media mồ côi trước khi dọn dẹp."
}
```

**Nguyên tắc:** dry-run đầu tiên, preview số lượng/dung lượng, chỉ xóa khi chốt policy, async job có idempotency/rollback khả thi và audit; không cho request tự chọn raw Firebase key để xóa.

---

## 17. API nhóm System Broadcast

> **[P3][DEFERRED]** NotificationType hiện chỉ có `LIKE`, `REPLY`, `FOLLOW`, `REPOST`, `QUOTE`, `MENTION`, `MESSAGE`, `COMMUNITY`. **Chưa có** `SYSTEM` hoặc `MODERATION`. Broadcast cần bổ sung domain và type/schema trước khi thực thi.

### 17.1. Use case

Thông báo toàn trường/nhóm đối tượng được phép: bảo trì, sự kiện chung, lịch nghỉ, quy định hệ thống. Không dùng tùy ý như công cụ nhắn tin hàng loạt; policy chống spam, review nội dung, audience selection, rate limit và scheduling.

### 17.2. `GET /api/admin/broadcasts`

Query `?status=DRAFT&page=0&size=20`; không body. DTO: id, title, audience, status, recipientCount, createdAt, sentAt.

### 17.3. `POST /api/admin/broadcasts` — tạo bản nháp

**Request body:**

```json
{
  "title": "Thông báo bảo trì hệ thống",
  "body": "ALOUTE sẽ bảo trì hệ thống theo kế hoạch. Cảm ơn bạn đã thông cảm.",
  "audience": {"type": "ALL_ACTIVE_USERS"},
  "channels": ["IN_APP"],
  "scheduledAt": null
}
```

Đề xuất channel MVP chỉ `IN_APP`; `WEB_PUSH` phụ thuộc token, permission, preference và error handling của FCM. Chưa hỗ trợ targeting bằng SQL tùy ý hoặc upload danh sách user IDs không kiểm tra.

### 17.4. `POST /api/admin/broadcasts/{broadcastId}/send`

**Request body:**

```json
{
  "confirmationText": "SEND",
  "reasonDetail": "Thông báo bảo trì chính thức đã được duyệt."
}
```

**Business:** server kiểm tra draft/approval, chốt audience và nội dung, tạo delivery job/outbox sau transaction; xử lý batch, retry, chống duplicate, rate limit; response nên trả `broadcastId`, `status=QUEUED`, `recipientCountEstimated` (không khẳng định đã giao hết).

### 17.5. `GET /api/admin/broadcasts/{broadcastId}`

Không body; trả trạng thái: `DRAFT|QUEUED|SENDING|SENT|PARTIAL_FAILED|FAILED` (đề xuất), counts `queued/delivered/failed` có ý nghĩa rõ; không được gọi thành công chỉ vì message đã được ghi vào DB nhưng push chưa được gửi.

### 17.6. Notification riêng cho moderation

Post remove hoặc suspension có thể gửi notification đến user sau commit. Cần quy định type `MODERATION` hoặc một inbox riêng; không “mượn” `COMMUNITY` hoặc `MESSAGE` vì sai semantics. Không gửi `internalNote` của Admin ra notification.

---

## 18. Phản hồi mẫu và error contract

### 18.1. Các error code hiện có dùng lại `[EXISTING]`

| ErrorCode | HTTP | Dùng khi |
|---|---|---|
| `AUTH_UNAUTHENTICATED` | 401 | Chưa login/token không hợp lệ |
| `AUTH_FORBIDDEN` hoặc `FORBIDDEN` | 403 | Không có quyền Admin/resource |
| `AUTH_ACCOUNT_LOCKED` | 403 | Tài khoản bị đình chỉ (khi P2) |
| `USER_NOT_FOUND` | 404 | User không tồn tại |
| `USER_SELF_ACTION_NOT_ALLOWED` | 409 | Tự thực hiện hành động trái policy |
| `POST_NOT_FOUND` | 404 | Post không tìm thấy theo contract |
| `POST_ALREADY_DELETED` | 409 | Post đã bị xóa |
| `POST_ACCESS_DENIED` | 403 | Không được xem Post theo policy |
| `REPORT_NOT_FOUND` | 404 | Report không tìm thấy |
| `VALIDATION_FAILED` | 422 | Body không đạt validation |
| `CONFLICT` | 409 | Conflict chưa có code cụ thể |
| `RATE_LIMIT_EXCEEDED` | 429 | Quá giới hạn |
| `INTERNAL_ERROR` | 500 | Lỗi bất ngờ |

### 18.2. Error code mới cần duyệt `[PROPOSED]`

Không tự thêm enum dưới đây vào code trước khi mở rộng catalog ở `system_architecture.md` / OpenAPI.

| Đề xuất | HTTP | Ý nghĩa |
|---|---|---|
| `REPORT_ALREADY_IN_REVIEW` | 409 | Report đã được Admin khác nhận |
| `REPORT_INVALID_TRANSITION` | 409 | Chuyển trạng thái Report không hợp lệ |
| `REPORT_VERSION_CONFLICT` | 409 | Client version cũ |
| `REPORT_REVIEWER_REQUIRED` | 403 | Admin chưa được giao report |
| `POST_RESTORE_NOT_ALLOWED` | 409 | Không đủ điều kiện restore |
| `POST_REMOVAL_NOT_FOUND` | 404 | Active removal không tồn tại |
| `USER_SUSPENSION_ALREADY_ACTIVE` | 409 | Suspension overlap theo policy |
| `USER_LAST_ADMIN_PROTECTED` | 409 | Không được mất Admin cuối cùng |
| `COMMUNITY_OWNER_TRANSFER_INVALID` | 409 | Transfer owner vi phạm invariant |
| `BROADCAST_INVALID_STATE` | 409 | Broadcast không ở trạng thái cho phép send |

### 18.3. Ví dụ lỗi validation

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Request validation failed",
    "fieldErrors": {
      "reasonDetail": ["Must contain at least 10 characters"]
    }
  },
  "traceId": "01H..."
}
```

### 18.4. Ví dụ conflict report

```json
{
  "success": false,
  "error": {
    "code": "REPORT_VERSION_CONFLICT",
    "message": "Report has changed; refresh to continue",
    "fieldErrors": null
  },
  "traceId": "01H..."
}
```

**UI:** reload detail/show actual status, **không** tự retry POST destructive với `expectedVersion` mới mà người dùng không xác nhận.

---

## 19. Workflow end-to-end, transaction và concurrency

### 19.1. Luồng report → gỡ Post (P1)

```text
User gửi POST /api/reports (API existing)
  ↓
Report PENDING
  ↓
Admin vào /admin/reports (SSR) → GET /api/admin/reports
  ↓
Mở detail → GET /api/admin/reports/{id}
  ↓
POST /api/admin/reports/{id}/start-review {expectedVersion:0}
  ↓
Atomic PENDING → REVIEWED; reviewedBy=currentAdmin; version=1
  ↓
Admin nhập resolutionNote và chọn POST_REMOVE
  ↓
POST /api/admin/reports/{id}/decisions {decision:RESOLVE, resolutionAction:...}
  ↓
@Transactional:
  check reviewer + version + target + privacy/policy
  lock/fetch target Post and active removal
  invoke Post aggregate removal/cascade (triggers/procedure respected)
  insert moderation_actions
  insert post_moderation_removals
  update report RESOLVED and version=2
  commit
  ↓
after commit: schedule moderation notification, metrics refresh
  ↓
UI nhận 200 → trạng thái RESOLVED, liên kết action log
```

Nếu bất kỳ DB write nào fail, **rollback toàn bộ** phần DB. Notification delivery sau commit cần có retry (outbox nếu đòi hỏi độ tin cậy).

### 19.2. Luồng admin gỡ Post không có report

`/admin/posts/{id}` → `POST /api/admin/posts/{id}/remove` với `relatedReportId=null` → validate → transaction Post + removal + audit → after-commit notification → UI cập nhật. **Không** tạo report giả.

### 19.3. Luồng khôi phục Post

`/admin/posts/{id}` → read `activeRemovalId` → confirm + reason → `POST /restore {expectedRemovalId}` → check owner deletion/provenance/cascade → transaction restore + audit + mark removal restored → UI cập nhật. Nếu đang bị gỡ lại/bị owner xóa → 409.

### 19.4. Race conditions và idempotency

- **Report:** optimistic version hoặc conditional update (`WHERE status='PENDING' AND version=...`) và kiểm tra số row affected; tuyệt đối không last-write-wins.
- **Post:** sử dụng row locking hoặc uniqueness bảo đảm chỉ một active removal, kết hợp transaction. `POST_ALREADY_DELETED`/conflict rõ ràng.
- **Double-click:** UI disable submit; API nên hỗ trợ `Idempotency-Key` ổn định trong một request decision, được ghi nhận để replay cùng response nếu request lặp **cùng key+payload**; key trùng payload khác phải conflict. Nếu architecture chưa chốt idempotency, ít nhất phải enforce bằng trạng thái DB.
- **Counters:** khi stored procedure/trigger cùng cập nhật `reply_count` phải review tránh double decrement, prevent negative counts; transaction and integration test.
- **Outbox notification:** persist intent trong transaction, delivery sau commit có thể retry; event consumer phải idempotent tránh gửi trùng.
- **Pagination:** sort có tie-breaker stable ID khi dữ liệu thay đổi liên tục.

### 19.5. Restore cascade — quyết định kỹ thuật khó nhất

Một Post parent có thể chứa replies, quotes, reactions, media, poll. Khi remove parent:

1. Domain quyết định item nào thật sự soft-delete, item nào vẫn tồn tại nhưng ẩn.
2. Ghi rõ provenance về trạng thái item do cascade ảnh hưởng nếu muốn restore chuẩn.
3. Không sửa owner-deleted/independently-moderated child.
4. Trigger count phải phù hợp khi remove/restore.
5. Restore cần test với Post root, Reply, nested Reply, Quote, cùng các thành phần liên quan.

**Nếu chưa chốt được cascade restore:** chỉ triển khai **remove** trước; ẩn nút restore thay vì làm một API nguy hiểm có thể phá dữ liệu.

---

## 20. Kiến trúc Spring Boot và cấu trúc frontend

### 20.1. Luồng tổng quát

```text
Thymeleaf Admin Pages (/admin/**)
  └── Admin JS modules
        └── shared core/http-client.js
              └── /api/admin/**  [Security + CSRF]
                    └── API Controller
                          └── Admin / Moderation Services
                                ├── ModerationPolicy / authorization
                                ├── Existing Post/User/Report/Community domain services
                                ├── JPA Repository + DB procedure/trigger
                                └── Moderation Audit / Outbox
```

**Một nghiệp vụ, một service:** Backend API dành cho Backoffice không thực thi logic trực tiếp trong Controller; tránh viết một phiên bản `removePost` cho Dashboard và một phiên bản khác cho sau này.

### 20.2. Cấu trúc backend tham khảo (không ép thay package hiện tại)

```text
controller/page/AdminPageController.java
controller/api/admin/AdminOverviewController.java
controller/api/admin/AdminUserController.java
controller/api/admin/AdminPostController.java
controller/api/admin/AdminReportController.java
controller/api/admin/AdminModerationActionController.java

service/AdminOverviewService.java
service/ModerationService.java
service/ReportReviewService.java
service/ModerationPolicy.java

entity/ModerationAction.java
entity/PostModerationRemoval.java
repository/ModerationActionRepository.java
repository/PostModerationRemovalRepository.java

dto/request/AdminRemovePostRequest.java
dto/request/AdminRestorePostRequest.java
dto/request/AdminStartReportReviewRequest.java
dto/request/AdminReportDecisionRequest.java

dto/response/AdminOverviewResponse.java
dto/response/AdminUserSummaryResponse.java
dto/response/AdminPostSummaryResponse.java
dto/response/AdminReportDetailResponse.java
dto/response/ModerationActionResponse.java
```

- Thực tế class name phải đối chiếu repository để không tạo class trùng.
- API Controller dùng `@RestController`; Page Controller dùng `@Controller`.
- `@PreAuthorize("hasRole('SYSTEM_ADMIN')")` ở controller/service khi method security thực sự bật; sensitive method có policy kiểm tra resource.
- Request DTO có Jakarta Bean Validation; Service check semantic validation.
- `@Transactional` trên use case service; không dồn transaction vào controller.
- DTO mapping tránh N+1 query; list endpoints trả projection, chỉ detail trả phần dữ liệu cần thiết.

### 20.3. Cấu trúc Thymeleaf/JS đề xuất

```text
src/main/resources/templates/
  layouts/admin.html
  admin/overview.html
  admin/users/list.html
  admin/users/detail.html
  admin/posts/list.html
  admin/posts/detail.html
  admin/reports/list.html
  admin/reports/detail.html
  admin/moderation-actions/list.html
  fragments/admin/sidebar.html
  fragments/admin/confirm-action-modal.html
  fragments/admin/status-badge.html

src/main/resources/static/
  css/admin/dashboard.css
  js/core/http-client.js                  (dùng lại nếu đã có)
  js/features/admin/report-review.js
  js/features/admin/post-moderation.js
  js/features/admin/user-search.js
  js/pages/admin-overview-page.js
  js/pages/admin-reports-page.js
  js/pages/admin-posts-page.js
```

SSR render layout, nav, dữ liệu initial phù hợp; JS chủ yếu xử lý table filter, pagination, modal và submit. Không tạo `page-actions.js` dài hàng nghìn dòng. Áp dụng Bootstrap components + token CSS hiện có.

### 20.4. UI components nên có

- `AdminSidebar` / breadcrumbs / topbar.
- KPI card với `asOf`.
- Search/filter toolbar (giữ query trong URL).
- Reusable paginated table.
- `ReportStatusBadge` chỉ hiển thị trạng thái (không là nguồn permission).
- `ConfirmDestructiveActionModal`: resource id, lý do, expectedVersion/removalId, loading state.
- `AuditTimeline`: read-only.
- Empty/error/loading skeleton và toast.

---

## 21. Bảo mật, quyền riêng tư và vận hành

### 21.1. Endpoint security

- Match `/admin/**` và `/api/admin/**` → authenticated `SYSTEM_ADMIN`, deny-by-default, không rò thông tin từ redirect/HTML error.
- Admin API phải kiểm tra account active và role **ở hiện tại**, không chỉ đọc role claim stale trong JWT lâu hạn; chốt strategy revoke/refresh sau P2 role/suspension.
- Request ghi từ Thymeleaf browser sử dụng JWT HttpOnly Cookie → CSRF `XSRF-TOKEN` / `X-XSRF-TOKEN` theo Auth convention.
- Bearer auth fallback phù hợp API client; không tự mở public admin endpoints.
- Mọi audit lấy actor từ `CurrentUserProvider`, không nhận Admin ID của client.

### 21.2. Data minimization / privacy

- List user không lộ email nếu không cần; detail chỉ mở cho vai trò có nghiệp vụ, không log nội dung bí mật.
- Post private không được đưa vô điều kiện vào Admin list theo logic “System Admin xem hết”. Chốt investigation override riêng nếu cần, phân biệt metadata và content evidence.
- Không cho Admin truy vấn nội dung các DM chỉ qua `GET /admin/messages`.
- Audit `metadata` chỉ giữ state fields tối thiểu, không snapshot private message.
- Broadcast không gửi thông tin internal moderation notes cho người dùng.

### 21.3. An toàn ứng dụng và hạ tầng

- Rate limit các thao tác admin quan trọng.
- Cookie `Secure`, `HttpOnly`, `SameSite=Lax`/policy phù hợp theo Auth convention; HTTPS khi deploy.
- XSS: escape content do user tạo khi render Thymeleaf/JS; tuyệt đối không `innerHTML` unsanitized.
- Kể cả khi frontend ẩn nút, API phải trả 403 nếu quyền không đủ.
- Log có request/trace ID; không log Authorization/Cookie/JWT.
- Backup/migration/rollback strategy trước release; index cho report status/createdAt và audit target/createdAt.
- Analytics có thể cache có TTL nhưng moderation decision phải đọc trạng thái transactionally fresh.
- Monitoring: số reports chờ, conflict rate, moderation failure rate, notification delivery failure, DB errors.

### 21.4. Quota Media hiện có (không đổi tùy ý)

- Ảnh tối đa 5 MB; video 30 MB.
- User quota 150 MB.
- Global safety cap 8.5 GB.

Những giới hạn này từ Domain; Admin Dashboard có thể giám sát chúng, nhưng không tự thêm endpoint “sửa quota bất kỳ” khi chưa có media rule và vận hành được phê duyệt.

---

## 22. Bộ kiểm thử / tiêu chí nghiệm thu

### 22.1. Security tests

- Guest → `/admin`, `/api/admin` bị chặn.
- Authenticated USER → 403.
- SYSTEM_ADMIN đúng quyền → 200 cho read, action theo policy.
- CSRF thiếu trên write request cookie-auth → bị chặn.
- Spoof `actorUserId` trong body hoặc `X-Role` header → không được phép đổi actor/quyền.
- JWT cũ sau role demotion/suspension (P2/P3) → không thực hiện được privileged action.
- Private post/report evidence: không được lộ content trái policy.

### 22.2. Workflow tests

- PENDING → REVIEWED → RESOLVED hợp lệ.
- PENDING → RESOLVED trực tiếp phải conflict theo proposed strict workflow.
- REVIEWED → REJECTED hợp lệ, không tạo post removal.
- Report đã terminal không được resolve/reject lần hai.
- Hai Admin tranh review một report → một thành công, một 409.
- Decision gỡ Post thành công nhưng update Report fail → rollback tất cả.
- Post đã admin-removed: remove lần hai → 409 hoặc idempotent replay nếu cùng key.
- Restore sai removal ID → 409/404 theo contract.
- Không restore được owner-deleted Post.
- Xóa/khôi phục Post có Replies/counters không sai số.
- Audit row được tạo cùng transaction; fail audit phải rollback.
- Notification fail sau commit không rollback dữ liệu core; cơ chế retry/alert xử lý riêng.

### 22.3. P2/P3 tests

- Không tự suspend/self-demote; không ảnh hưởng Admin cuối cùng.
- Hết hạn restriction tự được tính inactive dù job scheduler chưa chạy.
- Account inactive vì lý do khác không bị tự mở sau suspension expiry.
- Community owner transfer không làm community thiếu owner/không phá role invariants.
- Poll close không xóa vote hoặc sai aggregate count.
- Media cleanup dry-run không xóa file; attached media không được purge nhầm.
- Broadcast dùng idempotency và không tạo notification duplicate hàng loạt.

### 22.4. Frontend/E2E

- Search/filter/sort/page hoạt động, query URL giữ lại.
- Loading/empty/403/409/422 hiển thị đúng.
- Destructive modal bắt buộc nhập lý do; Enter/double click không gửi hai lần.
- Sau 2xx refetch table/detail; 409 cho phép reload resource.
- Responsive mobile/tablet; keyboard focus & screen reader label.
- Không hiển thị menu Admin cho USER, nhưng vẫn có backend denial tests.

### 22.5. Định nghĩa Done cho MVP

- Dashboard overview có KPI lấy từ backend thật.
- User/Post/Report paginated list và detail hoạt động.
- Report nhận/đóng với strict transition, conflict handling.
- Post admin remove hoạt động transactionally, đủ audit.
- Restore chỉ mở khi cascade provenance được chứng minh đúng; nếu chưa chốt, không claim tính năng đã hoàn thành.
- API request/response khớp OpenAPI mới; integration/security tests pass.
- Không có arbitrary database CRUD, không lộ DM/private Post sai quyền.

---

## 23. Roadmap và các PR

| PR | Nội dung | Kết quả |
|---|---|---|
| **PR 1 — Business Approval** | Chốt report transition, remove/restore provenance, privacy, audit; cập nhật Domain và owner review | Có invariant rõ ràng |
| **PR 2 — Contract** | Thêm Admin OpenAPI schemas/endpoints, proposed errors vào canonical catalog sau phê duyệt | Contract review được |
| **PR 3 — DB** | Migration moderation_actions, post_moderation_removals, report reviewer/version + index; đối chiếu procedure/trigger | Schema test được |
| **PR 4 — Backend** | AdminOverview/User/Post/Report APIs, ModerationPolicy, transactional audit, test | MVP APIs chạy |
| **PR 5 — Backoffice SSR** | Layout, sidebar, tables, filter, detail, action modal | MVP Admin Dashboard |
| **PR 6 — P2 Enforcement** | Warning, suspension, restriction, token/session strategy, notification | Quản trị tài khoản |
| **PR 7 — P3 Community/Topic/Poll** | Domain gap approvals + API + UI | Quản trị nội dung mở rộng |
| **PR 8 — P3 Media/Broadcast** | Storage safety, quota, broadcast lifecycle, outbox, retry | Vận hành nâng cao |

**Gợi ý phân công theo INDEX:** moderation/auth/User thuộc Security/Identity owner; Post/DB migration theo Content/Database owner; Thymeleaf/UX theo Frontend owner; Notification/Broadcast delivery theo Communication owner. Chủ nhiệm PR tích hợp phải làm việc với owner module, không tự chỉnh chéo code.

---

## 24. Quyết định phải chốt trước khi code

**Nhóm P1 — bắt buộc:**

1. **Type ID:** OpenAPI `int64` vs stored procedure example `UUID` — đâu là kiểu đang chạy?
2. **Visibility investigation:** Admin chỉ xem public + metadata riêng tư hay được mở nội dung từ report theo thủ tục nào? Có audit read-access không?
3. **Restore cascade:** trạng thái Reply/Quote nào được ẩn, khôi phục, counter thay đổi thế nào? Nếu chưa chốt thì hoãn restore.
4. **Report reviewer:** một report chỉ một reviewer? Có timeout/hand-off không? P1 đề xuất single reviewer và chưa support reassignment.
5. **Decision semantics:** `RESOLVED` có thể không có action nếu target đã xử lý trước? Có cần reference actionId bắt buộc?
6. **Moderation reason taxonomy:** tái sử dụng ReportReason cho moderation hay enum riêng?
7. **Audit payload:** giữ state-before/after ở mức nào, retention bao lâu?
8. **Notifying user:** thêm NotificationType `MODERATION` hay dùng kênh inbox khác? Có retry/outbox không?
9. **Error catalog:** error mới nào thực sự cần để UI phân biệt?

**Nhóm P2/P3 — không chặn MVP nhưng chặn tính năng tương ứng:**

10. Tài khoản bị suspend có còn đọc public content không? JWT cũ revoke hay check real-time?
11. Suspension vô thời hạn và thời hạn max được phép ra sao?
12. Ai được xử lý peer SYSTEM_ADMIN và quy trình bảo vệ last admin thế nào?
13. Community suspended ảnh hưởng join/post/read thế nào? Owner transfer yêu cầu điều kiện gì?
14. Topic archive/slug redirect policy là gì?
15. Poll close sớm, dữ liệu vote và privacy khi thống kê thế nào?
16. Media quarantine/retention/Firebase deletion, GB vs GiB, orphan detection chính xác ra sao?
17. Broadcast target audience, approval, FCM preference, opt-out, rate limit và idempotency ra sao?
18. Có cần phê duyệt hai người với role change và broadcast toàn trường không?

**Nguyên tắc xử lý GAP:** nếu câu trả lời chưa có trong Domain, ghi thành **open decision**, không tự coi một ví dụ request body là quy tắc đã duyệt.

---

## 25. Danh mục API tổng hợp

### 25.1. P1 — triển khai trước

| Method | Endpoint | Request body | Kết quả |
|---|---|---|---|
| GET | `/api/admin/overview` | Không | Summary/KPI |
| GET | `/api/admin/users` | Không | Page UserSummary |
| GET | `/api/admin/users/{userId}` | Không | UserDetail |
| GET | `/api/admin/users/{userId}/posts` | Không | Page AdminPost |
| GET | `/api/admin/users/{userId}/moderation-actions` | Không | Page Audit |
| GET | `/api/admin/posts` | Không | Page AdminPost |
| GET | `/api/admin/posts/{postId}` | Không | AdminPostDetail |
| POST | `/api/admin/posts/{postId}/remove` | `reasonCode,reasonDetail,relatedReportId?,publicNotice?` | RemovalAction |
| POST | `/api/admin/posts/{postId}/restore` | `expectedRemovalId,reasonDetail` | RestoreAction |
| GET | `/api/admin/posts/{postId}/moderation-actions` | Không | Page Audit |
| GET | `/api/admin/reports` | Không | Page AdminReport |
| GET | `/api/admin/reports/{reportId}` | Không | AdminReportDetail |
| POST | `/api/admin/reports/{reportId}/start-review` | `expectedVersion` | Updated Report |
| POST | `/api/admin/reports/{reportId}/decisions` | `expectedVersion,decision,resolutionNote,resolutionAction?` | DecisionResult |
| GET | `/api/admin/moderation-actions` | Không | Page Audit |
| GET | `/api/admin/moderation-actions/{actionId}` | Không | AuditDetail |

### 25.2. P2 — xử phạt hệ thống

| Method | Endpoint | Request body |
|---|---|---|
| GET | `/api/admin/enforcement` | Không |
| POST | `/api/admin/users/{userId}/warnings` | `reasonCode,reasonDetail,relatedReportId?,publicNotice?` |
| GET | `/api/admin/users/{userId}/suspensions` | Không |
| POST | `/api/admin/users/{userId}/suspensions` | `reasonCode,reasonDetail,expiresAt?,relatedReportId?,publicNotice?` |
| POST | `/api/admin/users/{userId}/suspensions/{suspensionId}/revoke` | `reasonDetail` |
| GET | `/api/admin/users/{userId}/restrictions` | Không |
| POST | `/api/admin/users/{userId}/restrictions` | `feature,reasonCode,reasonDetail,expiresAt,relatedReportId?` |
| POST | `/api/admin/users/{userId}/restrictions/{restrictionId}/revoke` | `reasonDetail` |

### 25.3. P3 — mở rộng Backoffice

| Method | Endpoint | Request body |
|---|---|---|
| GET | `/api/admin/analytics/activity` | Không |
| POST | `/api/admin/users/{userId}/profile-resets` | `fields[],reasonCode,reasonDetail,relatedReportId?` |
| POST | `/api/admin/users/{userId}/verification-changes` | `verified,reasonDetail` |
| POST | `/api/admin/users/{userId}/global-role-changes` | `targetRole,reasonDetail` |
| GET | `/api/admin/communities` | Không |
| GET | `/api/admin/communities/{communityId}` | Không |
| POST | `/api/admin/communities/{communityId}/suspensions` | `reasonCode,reasonDetail,expiresAt?` |
| POST | `/api/admin/communities/{communityId}/suspensions/{suspensionId}/revoke` | `reasonDetail` |
| POST | `/api/admin/communities/{communityId}/owner-transfers` | `newOwnerUserId,reasonDetail` |
| PATCH | `/api/admin/communities/{communityId}/members/{userId}/role` | `role,reasonDetail` |
| GET | `/api/admin/topics` | Không |
| POST | `/api/admin/topics` | `name,slug` |
| PATCH | `/api/admin/topics/{topicId}` | `name?,slug?,reasonDetail` |
| POST | `/api/admin/topics/{topicId}/archive` | `reasonDetail` |
| GET | `/api/admin/polls` | Không |
| GET | `/api/admin/polls/{pollId}` | Không |
| POST | `/api/admin/polls/{pollId}/close` | `reasonDetail` |
| GET | `/api/admin/media` | Không |
| GET | `/api/admin/media/quota` | Không |
| GET | `/api/admin/media/orphan-candidates` | Không |
| POST | `/api/admin/media/{mediaId}/quarantine` | `reasonCode,reasonDetail` |
| POST | `/api/admin/media/cleanup-jobs` | `dryRun,onlyOrphans,olderThan,reasonDetail` |
| GET | `/api/admin/broadcasts` | Không |
| POST | `/api/admin/broadcasts` | `title,body,audience,channels,scheduledAt?` |
| POST | `/api/admin/broadcasts/{broadcastId}/send` | `confirmationText,reasonDetail` |
| GET | `/api/admin/broadcasts/{broadcastId}` | Không |

### 25.4. Ghi chú cuối

- **Tất cả API trên `/api/admin/**` là PROPOSED**, chưa có trong `aloute-openapi.yaml` ZIP v0.1.0.
- Các body JSON được thiết kế để có thể chuyển sang Request DTO và OpenAPI schema sau khi được phê duyệt.
- Đặc biệt ưu tiên hoàn thành **Reports → Review → Post Remove → Audit** rồi mới mở rộng tính năng nhiều bảng.
- Admin Dashboard **không** là công cụ tùy ý đọc/sửa tất cả bảng DB: nó là Backoffice thực thi các **use case được kiểm soát**.

---

**END OF SPEC — ALOUTE ADMIN DASHBOARD / BACKOFFICE**
