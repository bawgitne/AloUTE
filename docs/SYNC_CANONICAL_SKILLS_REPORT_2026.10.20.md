# ALOUTE Canonical Skills Sync Report

**Branch**: `docs/sync-canonical-skills-20261020` | **Commit**: `d92fce6` | **Date**: 2026-10-20

> **Ghi chú làm sạch (2026-10-28):** Bản báo cáo gốc bị lẫn cú pháp hội thoại/tool-call ở dòng 144 và nhiều câu bị trộn ngôn ngữ Việt–Anh–Trung. Bản này làm sạch cú pháp, câu chữ và các khẳng định không có bằng chứng, đồng thời giữ nguyên nội dung kỹ thuật đã ghi nhận tại thời điểm đồng bộ (commit `d92fce6`, 2026-10-20). Không bổ sung kết luận mới và không khôi phục nội dung không thể xác minh.

---

## Mục A — Khái quát chung

Nhóm đã thực hiện audit toàn diện tài liệu canonical trong thư mục `skills/` và đồng bộ chúng với implementation trên `origin/master` (SHA `6fd2496` tại thời điểm làm việc). Branch `docs/sync-canonical-skills-20261020` chứa **CHỈ tài liệu, KHÔNG thay đổi code**. Phạm vi audit gồm: domain contract, REST API, authentication/authorization, backend, frontend, Docker/Nginx.

Điều kiện kiểm tra:

- Admin privacy F-01…F-05, fencing, block owners: đối chiếu với `fix/community-private-discovery` (PR #30) và `fix/admin-mvp-hardening` (PR #3).
- Password reset + STOMP websocket: đối chiếu với `ALOUTE_FORGOT_PASSWORD_STOMP_E2E_2026-10-19.md`.
- Public API qua HTTP (POST login, gọi ngoài): kiểm chứng bằng browser smoke và integration thật.

Đánh giá hơn 80 tài liệu khả dĩ → chọn 8 file canonical trọng yếu và 1 tài liệu phụ (Flutter handoff). Các tài liệu khác trong `skills/` giữ nguyên hoặc được cập nhật theo tác vụ riêng. OpenAPI tại thời điểm đồng bộ (commit `d92fce6`): 79 paths, 203 component refs đều resolve (kiểm tra bằng script).

---

## Mục B — Danh sách file tham gia

### 6 file canonical đã cập nhật và kiểm chứng

1. **skills/INDEX.md** – Master routing index (quyền sở hữu, full names → shortnames, gỡ vòng tham chiếu vòng, đánh dấu các khoảng trống fe-vue/LIKE/MEDIA/REALTIME).
2. **skills/backend_rule.md** – Spring Boot JPA, transaction, trạng thái migration thực tế (§97.1).
3. **skills/system_architecture.md** – Biên hệ thống, REST envelope, pagination, biên STOMP (§21), bối cảnh CSDL (MySQL/H2 so với script PostgreSQL-only).
4. **skills/author_rule.md** – JWT transport (cookie/Bearer), CSRF Bearer exemption (§9), cascade thu hồi `authVersion` (`JwtSessionService.REQUIRES_NEW`).
5. **skills/frontend_rule.md** – Cấu trúc thư mục resource, trang admin, mục ban/lock P2.
6. **skills/docker_reverse_proxy_rule.md** – Biến môi trường cụ thể (§18): `ALOUTE_MAIL_HOST`, `DB_PORT`, `ALOUTE_APP_BASE_URL`.

### Tài liệu phụ (mục đích bàn giao)

7. **docs/FLUTTER_HANDOFF.md** – Quy ước client di động (`baseURL` `/api/*`, đăng nhập Set-Cookie + Bearer fallback, CSRF Bearer exemption, thu hồi `authVersion` trên mobile, các lỗi hiện tại đã ghi nhận).

### Tài liệu canonical không thay đổi

8. **skills/domain_system.md** – Giữ nguyên phần placeholder; domain contract (bảng, trigger/stored procedure) chờ hạng mục làm mới của Ngọc.

---

## Mục C — Phân loại bất đồng (A–F)

### Loại A: Quy ước lệch so với implementation (spec drift)

- **backend_rule.md §97.1**: trạng thái ban/lock P2 so với bảng `account_suspensions` tách rời (thuộc trách nhiệm của Bằng).
- **frontend_rule.md**: thiếu `settings.html` so với model; rủi ro thấp.
- **docker_reverse_proxy_rule.md §18**: tên biến môi trường placeholder lệch so với biến thật trong compose/yml.

### Loại B: Contract-only / khuyết thiếu (endpoint stub)

- **aloute-openapi.yaml**: các endpoint mới thiếu implementation được đánh dấu `x-implementation-status: contract-only`; một số URL ngoài trả 404 ở một số môi trường.

### Loại C: Domain invariant / bảo toàn quyền sở hữu

- **INDEX.md**: gỡ các tham chiếu trùng lặp full-name; đánh dấu các owner còn thiếu (REALTIME/MEDIA, fe-vue, LIKE).
- **domain_system.md**: cần hạng mục làm mới của Ngọc (biên + trigger JDBC), ảnh hưởng module liên quan.

### Loại D: Mô hình kiến trúc/kích hoạt

- **system_architecture.md**: sửa nhãn sai H2/PostgreSQL; thực tế là MySQL 8.0 production, trong khi script trigger/stored procedure chỉ là PostgreSQL-only; thay placeholder fe-vue bằng `aloute_mobile/`.

### Loại E: Chi tiết backend (DB/Hibernate so với Java service)

- **backend_rule.md §97.1**: cập nhật mẫu ownership (Service + JPA), trạng thái hiện tại vẫn khớp với nền tảng F-0* của Bằng.
- **author_rule.md §9**: ghi chú JWT cần owner xác nhận.

### Loại F: Quản trị/chiến lược tài liệu

- **INDEX.md**: dọn vòng tham chiếu không hợp lệ, chuẩn hóa heading.
- **rule placeholder**: bổ sung ghi chú (HTML injection) và đóng mục.

---

## Mục D — Cập nhật đã kiểm chứng

### 1. OpenAPI (skills/aloute-openapi.yaml)

- Tại thời điểm đồng bộ: 79 paths, 203 component refs đều resolve.
- Hợp đồng bidirectional notification `kind: read` → `type: int` (phân biệt Jackson enum `NotificationResponse.read` với DTO `isRead`).
- Nhãn hợp đồng `x-implementation-status` (FULL, CONTRACT_ONLY) cho các endpoint trước đây chưa được ghi nhận.
- Thêm path `/api/users/me/profile` (GET + PUT) khép kín phần social graph/private metadata qua API.
- Sắp xếp endpoint (OAuth, posts, users, community, messaging, notifications) cùng cluster và gắn nhãn `x-implementation-status`.

### 2. System Architecture (skills/system_architecture.md)

- Sửa `DATABASE STRATEGY` (production MySQL 8.0 + compose so với H2 in-memory cho dev).
- Thêm ghi chú về PostgreSQL trigger/stored procedure: chỉ tồn tại trong `db/schema.sql`, chưa được wire.
- Bổ sung đường dẫn backend `/api/*` với `appPath = /api/`.
- Đánh dấu `fe-vue/` là `KHÔNG TỒN TẠI trên master`; `aloute_mobile/` là Dart Flutter.
- Cập nhật bảng biên STOMP `§21` với routing đầy đủ (server→client: `/user/queue/notifications`, `/user/queue/messages`; client→`/app/...`).

### 3. Backend Rules (skills/backend_rule.md)

- Thêm `§97.1 TRẠNG THÁI THỰC TẾ TRÊN MASTER (2026-10-20)`:
  - Không có Flyway/Liquibase trong `pom.xml`.
  - Hibernate `ddl-auto=update` bật ở cả dev và prod.
  - `db/` chứa script SQL thủ công (`schema.sql`, `triggers.sql`, `procedures.sql`) nhưng chưa được wire.
  - `migration/README.md` ghi nhận script migration MySQL 8 chưa được kiểm chứng.
  - Mẫu triển khai: Service + JPA.

### 4. Frontend Rules (skills/frontend_rule.md)

- Gỡ placeholder `settings.html`.
- Gỡ thư mục `poll/` (nay render inline).
- Cập nhật danh sách trang admin thực có (`templates/admin/index`, `templates/admin/users`, `templates/admin/posts`).
- Đánh dấu nút ban/lock là **CHƯA IMPLEMENT (P2)**, tham chiếu bảng `account_suspensions` và authority `USER_SUSPEND`.

### 5. Auth Rules (skills/author_rule.md)

- Mở rộng **§9 CSRF behavior**:
  - `SecurityConfig.bearerRequest` miễn CSRF cho Bearer.
  - `CsrfCookieFilter` chạy sau `JwtAuthenticationFilter`, đảm bảo refresh cookie `XSRF-TOKEN`.
  - Luồng kiểm tra `authVersion` cho cascade logout (đọc lại User từ DB).
- Làm rõ `JwtSessionService.REQUIRES_NEW` đọc lại User để kích hoạt thu hồi.

### 6. Docker/Nginx Rules (skills/docker_reverse_proxy_rule.md)

- §18 Environment Variables cập nhật chuỗi biến thật:
  - `DB_PORT` → `5432` (placeholder), không phải HTTP route mapping.
  - `ALOUTE_MAIL_HOST`, `ALOUTE_APP_BASE_URL`.
- Gỡ các biến placeholder chung chung, thay bằng chuỗi thật từ compose/YAML.

### 7. Index (skills/INDEX.md)

- Thay full names bằng shortnames trong bảng routing (tránh suy diễn ownership mơ hồ).
- Bổ sung ghi chú:
  - `fe-vue` → `KHÔNG tồn tại trên master`.
  - `MEDIA` và `REALTIME` → "CHƯA CÓ OWNER, CẦN XỬ LÝ".
- Bổ sung ghi chú về tiền tố `#` của `<DOMAIN_SYSTEM.md>` và `<RULE_OWNER.md>`, kèm chức năng/giải thích.

### 8. Flutter Handoff (docs/FLUTTER_HANDOFF.md)

- Ghi nhận hiện trạng thực tế (kiểm chứng bằng curl Set-Cookie sau `POST /api/auth/login`).
- Luồng auth: body đăng nhập `{"email":"...","password":"..."}`, khác biệt so với tài liệu cũ.
- Yêu cầu cấu hình `http.Client` với `CookieJar`.
- Ví dụ thu hồi `authVersion` trên mobile.
- Các lỗi đang gặp: trường hợp `cookieJar.getForRequest` trả null.
- Endpoint stub dự phòng cho module tương lai.

---

## Mục E — Owner / phụ thuộc

Không có thay đổi nào phụ thuộc vào PR của Codex. Các trường hợp ownership mơ hồ được ghi nhận như việc dọn dẹp (housekeeping).

---

## Mục F — Độ lệch được ghi nhận (nợ kỹ thuật chờ owner xử lý)

Các độ lệch dưới đây không chặn báo cáo đồng bộ nhưng cần owner tương ứng xử lý (Bằng, Ngọc, Quân cho media; Khôi cho author/device; Bách cho device JWT; tất cả cho `account_suspensions`).

| Độ lệch | Loại | File | Owner | Ưu tiên | Ghi chú |
|-----------|------|------|-------|----------|-------|
| Trường `isRead` trong DTO so với enum `read` khi Jackson serialize | Lệch tên trường | `author_rule.md` | — | P3 | Template dùng một trong hai; cần mapping ad-hoc |
| Thiếu `settings.html` trong resource tree | Không nhất quán layout | `frontend_rule.md` §3 | Quân | P3 | Trang UI không nhạy cảm, có thể bổ sung theo quyết định |
| Nút ban/lock chưa nối tới controller/action | Khoảng trống chức năng | `frontend_rule.md` | Bằng | P2 | Cần tích hợp `account_suspensions` |
| URL dịch vụ ngoài trả 404 ở một số môi trường | Không nhất quán URL ngoài | `frontend_rule.md` | Bằng | P3 | Liên quan định tuyến Nginx/môi trường |
| `MediaPurpose` enum và endpoint mới ở mức contract-only | Stub hợp đồng | `system_architecture.md`, `backend_rule.md` | Bằng | P3 | Chờ pipeline media/mobile |
| STOMP chưa hỗ trợ multipart text/video (chỉ text) | Thiếu năng lực STOMP | `system_architecture.md` | Quân | P4 | Cần bổ sung hoặc ẩn tính năng |
| Bảng `account_suspensions` có nhưng chưa có UI/role hook để quản lý | Log chỉ đọc so với quản lý ghi được | `frontend_rule.md` | Bằng | P2 | Cần admin RBAC |
| Khóa Firebase admin FCM chưa được cấu hình môi trường cố định | Thiếu cấu hình | `docker_reverse_proxy_rule.md` | Quân | P4 | Cần cấu hình admin (file/env) |
| `/api/users/me/profile` (đọc) chưa có access log riêng | Thiếu trường/log | `backend_rule.md` | Ngọc | P2 | Ghi log nội dung phục vụ admin |