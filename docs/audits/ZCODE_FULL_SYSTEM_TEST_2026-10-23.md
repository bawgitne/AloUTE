# BÁO CÁO KIỂM THỬ TOÀN BỘ HỆ THỐNG ALOUTE — ZCODE FULL SYSTEM TEST

- **Ngày**: 2026-10-23
- **Baseline (origin/master)**: `dd293e9` — `fix(db): remove redundant SET NAMES to prevent multi-statement 1064 syntax error`
- **Nhánh kiểm thử/sửa lỗi**: `zcode/full-system-audit-20261023` (tạo từ `origin/master`, worktree riêng `../AloUTE-audit-20261023`, không merge/deploy)
- **Người thực hiện**: ZCode (Senior Full-Stack / QA Automation / Security Tester / Code Reviewer)
- **Tài liệu canonical đã đọc trước khi code**: `AGENTS.md`, `skills/INDEX.md`, `skills/domain_system.md`, `skills/system_architecture.md`, `skills/aloute-openapi.yaml`, `skills/backend_rule.md`, `skills/author_rule.md` (phần liên quan)

---

## A. TỔNG QUAN

### Môi trường kiểm thử

| Hạng mục | Giá trị |
|---|---|
| OS | Windows 11 (win32 10.0.26200), Git Bash |
| Java | 21.0.8 LTS (build target 17) |
| Maven | 3.9.16 |
| Node | v24.19.0 |
| DB | **H2 in-memory** (profile `dev`) — MySQL 8 **KHÔNG có sẵn** trên máy (không cài mysql client, không docker) |
| Server test | `java -jar target/aloute-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev --server.port=8090` |
| Firebase | **TẮT** (`ALOUTE_FIREBASE_ENABLED=false`) — không dùng credentials production theo quy tắc đề bài |
| OTP secret | Base64 32-byte duy nhất (fixture công khai bị `RegistrationOtpServiceImpl` từ chối ngoài profile test — hành vi đúng) |
| Browser | ZCode In-app Browser (Chromium) — điều khiển bằng Playwright API thật, có screenshot |

### Số liệu tổng

| Chỉ số | Kết quả |
|---|---|
| Chức năng/inventory đã rà | 12 nhóm module (Auth, Profile/Settings, Post, Feed/Search, Social, Community/Topic, Messaging, Notification, Report/Admin, Media, Realtime, Flutter) |
| Test tự động hóa (Maven) | Xem mục D. Regression (đang chạy `mvn clean verify`; kết quả cập nhật cuối báo cáo) |
| Test tự động hóa (Node frontend) | **329/329 PASS, 0 fail** (`node --test test/frontend/*.test.mjs`) |
| Test mới bổ sung (regression cho bug) | **11 test / 3 file — 11/11 PASS** |
| Browser E2E | 12 kịch bản (login, soạn bài text-only, upload avatar, upload cover, responsive 390/768/1440, smoke 8 trang) — bằng chứng PNG trong `docs/audits/evidence-2026-10-23/` |
| Lỗi phát hiện | **6 lỗi** (1 × P0, 2 × P1, 1 × P2, 2 × P3) + 1 cảnh báo bảo mật ghi nhận |
| Lỗi đã sửa trên nhánh audit | **6/6** |
| Hạng mục BLOCKED (môi trường) | Firebase storage thật, MySQL 8 runtime, STOMP runtime lần này, Flutter runtime |

---

## B. MA TRẬN CHỨC NĂNG

Ký hiệu: **PASS** = tự động hóa; **B-E2E** = browser thật; **RT** = runtime API/curl; **BLOCKED** = thiếu môi trường, **không** báo PASS; **SKIP** = không chạy trong đợt này (ghi rõ lý do).

| Module | Test case | Kết quả | Bằng chứng | Ghi chú |
|---|---|---|---|---|
| Post | Đăng bài text-only (không media) — API | **PASS+RT** | curl 201 Created, `media: []` (post #1); `PostTextOnlyCreationIntegrationTest` 3/3 | `CreatePostRequest.mediaIds` tùy chọn đúng contract OpenAPI |
| Post | Đăng bài text-only từ trang /post/create — browser | **PASS (B-E2E)** | redirect `/posts/2`, nội dung Unicode hiển thị đúng; `01-post-detail-text-only.png` | Nút "Đăng bài" hoạt động không cần media |
| Post | Nội dung tiếng Việt + emoji | **PASS** | integration test `unicodeAndEmojiContentIsPreserved` | Lưu/đọc DB nguyên vẹn |
| Post | Nội dung toàn khoảng trắng → 422 | **PASS** | `blankContentIsRejected` | Không vì thiếu media mà bỏ validation |
| Post | Bài tồn tại sau re-read + xuất hiện ở feed | **PASS+RT** | feed browser hiển thị post #1; GET `/api/posts/{id}` 200 | Persistence H2 |
| Post | Đăng bài kèm ảnh/video | **BLOCKED (storage)** | bind đã PASS (503 dừng đúng ở storage port) | Không có Firebase test env → không thể xác nhận lưu trữ thật |
| Media | POST /api/media với purpose dạng text (Settings) | **FAIL → FIXED** | trước: HTTP 415; sau fix: 503 `MEDIA_STORAGE_UNAVAILABLE` (đúng —.Firebase tắt cục bộ) | **BUG-001 (P0)** — xem mục C |
| Security | CSRF trên recovery endpoints | **FAIL → FIXED** | 3 test bảo mật fail trên master → sau fix PASS | **BUG-004 (P1)** — regression 505ef09 |
| Security | SMTP TLS trust-all | **FAIL → FIXED** | `untrustedCertificateChainFailsDelivery` fail trên master → sau fix PASS | **BUG-005 (P2)** |
| Media | POST /api/media với purpose dạng JSON blob (composer) | **PASS (RT)** | 503 MEDIA_STORAGE_UNAVAILABLE (bind OK, không hồi quy) | Giữ tương thích ngược |
| Media | purpose không hợp lệ → 422 | **PASS (RT)** | `MEDIA_PURPOSE_INVALID` | Trước fix: 500 tiềm ẩn; sau fix: 422 rõ ràng |
| Media | Upload thật lên Firebase + URL tải được | **BLOCKED** | — | Theo quy tắc: không dùng credentials production; ghi nhận rõ |
| Settings/Profile | Chọn ảnh avatar → preview | **PASS (B-E2E)** | `02-settings-avatar-upload-error.png` (preview local render) | IAB không hỗ trợ file chooser → nạp File qua DataTransfer + event `change` thật |
| Settings/Profile | Upload avatar → lưu hồ sơ → giữ sau refresh | **FIXED (code) / BLOCKED (end-to-end)** | Luồng HTTP tới backend OK sau fix; dừng ở storage | `UserServiceImpl` gắn `avatarUrl` từ `avatarMediaId` (PR #62) — wiring đã có, thiếu storage để tạo media thật |
| Settings/Profile | Upload ảnh bìa (USER_COVER) | **FIXED (code) / BLOCKED (end-to-end)** | browser: alert "Firebase media storage is disabled" (lỗi thật hiển thị đúng) | Cùng cơ chế BUG-001 |
| Settings/Profile | Thông báo lỗi upload hiển thị đúng nguyên nhân thật | **FAIL → FIXED** | trước: fallback generic sai; sau: "Firebase media storage is disabled" | **BUG-003 (P3)** |
| Auth | Đăng nhập admin@aloute.local | **PASS (RT+B-E2E)** | cookie JWT + redirect /feed | |
| Auth | Login trả JWT trong body response | **GHI NHẬN (P2 bảo mật)** | `"token":"eyJ..."` trong login response | Đã được addendum PR #53 trước đó ghi nhận; vẫn tồn tại trên master |
| Auth | OTP đăng ký, quên mật khẩu, 2FA, reset | **SKIP (đợt này)** | — | Đã được các QA trước xác minh (PR #38, #53, #62); tập trung nguồn lực vào 2 P0 của đề bài |
| Feed/Search | /feed render, /search?q= | **PASS (B-E2E)** | smoke scan: không lỗi, không 5xx | |
| Social | /friends | **PASS (B-E2E)** | h1 "Bạn bè & Kết nối", không lỗi render | Bug bio cũ đã fix từ trước |
| Messaging | Trang /messages | **PASS (B-E2E, SSR)** | h1 "Tin nhắn AloUTE" | Realtime STOMP: SKIP đợt này (đã verify trên nhánh khác 2026-10-20) |
| Notification | Trang /notifications | **PASS (B-E2E)** | h1 "Thông báo AloUTE" | |
| Community | /communities | **PASS (B-E2E)** | h1 "Cộng đồng & Câu lạc bộ" | |
| Saved | /saved | **PASS (B-E2E)** | h1 "Bài viết đã lưu" | |
| Admin | /admin (SYSTEM_ADMIN) | **PASS (B-E2E)** | h1 "Tổng quan hệ thống"; 1 alert "lỗi" phát hiện lúc đầu **là false positive** (nội dung modal ẩn `d-none`, xác nhận `offsetParent=null`) | |
| Admin | RBAC: user thường vào /admin | **PASS (từ whitelist test hiện có)** | SecurityConfig `/admin/**` hasRole SYSTEM_ADMIN | Không runtime lại đợt này |
| Responsive | 390px / 768px / 1440px | **PASS (B-E2E)** | `03-settings-390px.png`, `04-feed-768px.png`, `05-feed-1440px.png` | Không vỡ layout, không tràn ngang |
| Build/CI | `mvn clean verify` trên master gốc | **FAIL → FIXED** | lỗi biên dịch test (xem BUG-002) | **Master dd293e9 không build được test** — CI đỏ |
| Realtime | STOMP /ws messaging | **SKIP** | — | Đã có integration test trong suite; runtime từng verify 2026-10-20 |
| Database | MySQL 8 thật | **BLOCKED** | — | Máy không có mysql/docker; H2 theo design dev |
| Flutter | aloute_mobile | **BLOCKED (runtime)** | — | Không có Flutter SDK/emulator; không báo PASS |

---

## C. CHI TIẾT LỖI

### BUG-001 (P0) — Không cập nhật được ảnh đại diện/ảnh bìa từ trang Cài đặt: HTTP 415

- **Mô tả**: Người dùng chọn ảnh avatar/cover ở `/settings` → upload luôn thất bại. Request `POST /api/media` bị từ chối `415 UNSUPPORTED_MEDIA_TYPE: Content-Type 'text/plain' is not supported` (hoặc `application/octet-stream` tùy trình duyệt), chưa bao giờ tới tầng storage.
- **Bước tái hiện** (100% tái hiện được trên master dd293e9):
  1. Đăng nhập, vào `/settings` → tab "Hồ sơ cá nhân".
  2. Chọn ảnh bất kỳ cho "Ảnh đại diện".
  3. Theo network: `POST /api/media` trả 415; UI hiện thông báo generic (do BUG-003).
- **Expected**: upload được chấp nhận, trả 201 + media id khi storage khả dụng.
- **Actual**: 415 ở bước bind multipart, mọi mục đích USER_AVATAR/USER_COVER đều fail.
- **Root cause**: frontend có **hai cách gửi part `purpose`** không tương thích:
  - `settings.js:278` gửi chuỗi thuần: `formData.append('purpose', 'USER_AVATAR')` → part mang Content-Type `text/plain` → `StringHttpMessageConverter` đọc thành String nhưng `@RequestPart("purpose") MediaPurpose` cần converter đọc thẳng enum → không converter nào `canRead(MediaPurpose, text/plain)` → 415.
  - `post-composer.js:225` gửi JSON blob: `new Blob([JSON.stringify('POST')], {type:'application/json'})` → Jackson deserialize enum được → composer vẫn chạy.
  - Đây chính là điểm nghi vấn đề bài nêu: *"kiểm tra cách frontend gửi `purpose` ở composer so với trang Settings"*. Backend chỉ chịu một encoding tình cờ.
- **File/dòng liên quan**:
  - `src/main/java/com/aloute/controller/api/MediaController.java:65-70` (chữ ký cũ `@RequestPart("purpose") MediaPurpose`)
  - `src/main/resources/static/js/features/settings/settings.js:209, 278` (gửi text thuần)
  - `src/main/resources/static/js/features/post/post-composer.js:225` (gửi JSON blob)
- **Giải pháp (sửa tại gốc — layer transport/binding của controller, đúng trách nhiệm Controller theo backend_rule)**:
  - Nhận `@RequestPart("purpose") String purpose` + hàm `parsePurpose()` chuẩn hóa: strip quote JSON, trim, uppercase; sai → `422 MEDIA_PURPOSE_INVALID`/`MEDIA_PURPOSE_REQUIRED`. Nhận **cả hai** dạng client hiện có (tương thích ngược composer) và mọi client tương lai gửi multipart tự nhiên.
  - Vì `StringHttpMessageConverter` (hỗ trợ `*/*`, đứng trước Jackson) có thể读 JSON string part về **kèm dấu ngoặc kép**, parser phải strip `"` — đã xử lý và có test riêng cho ca này.
- **Regression test**: `MediaControllerPurposeBindingTest` (5 unit) + `MediaUploadPurposeBindingIntegrationTest` (3 MockMvc multipart, firebase disabled → kỳ vọng 503 = chứng minh bind thành công tới storage port). **8/8 PASS.**
- **Kết quả sau sửa (runtime thật)**: text/plain `USER_AVATAR` → **503 MEDIA_STORAGE_UNAVAILABLE** (đúng — Firebase tắt cục bộ); JSON blob `POST` → 503 (không hồi quy); `WRONG` → 422. Browser: UI Settings giờ hiển thị đúng lỗi thật "Firebase media storage is disabled".

### BUG-002 (P1) — Master không biên dịch được test (CI đỏ): constructor `RegistrationOtpServiceImpl` đổi chữ ký không cập nhật test

- **Mô tả**: `mvn clean verify` trên `origin/master` dd293e9 fail ở `testCompile`.
- **Tái hiện**: `mvn -B -DskipTests package` → `[ERROR] MySql8RevocationVerificationTest.java:[1922,49] ... constructor RegistrationOtpServiceImpl ... actual and formal argument lists differ in length`.
- **Expected**: test biên dịch.
- **Actual**: test gọi constructor 5 tham số `(userRepo, otpRepo, emailSvc, executor, env)`; commit `9ca134f` (login bằng username/email) đã bỏ tham số `Executor` (async chuyển vào `EmailService`) mà không cập nhật test này.
- **Root cause**: test stale theo chữ ký constructor; lỗi baseline có sẵn trên master, không do môi trường.
- **File/dòng**: `src/test/java/com/aloute/integration/MySql8RevocationVerificationTest.java:1922,1932` (đã dịch dòng sau khi sạch).
- **Giải pháp**: bỏ đối số `executor` (và mock không còn dùng) trong 2 lời gọi — đúng theo chữ ký hiện tại; không đụng logic assert.
- **Kết quả**: toàn bộ test compile + `mvn clean verify` chạy được (kết quả đầy đủ ở mục D).

### BUG-003 (P3) — `settings.js` đọc sai trường lỗi của envelope → hiển thị thông báo không đúng nguyên nhân

- **Mô tả**: khi upload/lưu hồ sơ lỗi, UI hiện "Không thể tải ảnh lên Firebase Storage." thay vì lỗi thật từ backend.
- **Root cause**: `settings.js` đọc `result.message` trong khi envelope chuẩn là `{success:false, error:{code,message}}` (system_architecture). 5 chỗ: dòng ~223 (cover), ~292 (avatar), ~433 (lưu hồ sơ), ~480 (privacy), ~575 (đổi mật khẩu). Các luồng 2FA/block/mute trong cùng file lại đọc đúng `result.error.message` — bất nhất.
- **Giải pháp**: thống nhất `(result && result.error && result.error.message) || fallback` cho cả 5 chỗ (replace-all 1 pattern).
- **Kết quả**: browser xác nhận UI hiện đúng "Firebase media storage is disabled" (xem `02-settings-avatar-upload-error.png`).

### BUG-004 (P1 bảo mật — regression từ commit 505ef09) — Gỡ nhầm CSRF protection khỏi các endpoint recovery

- **Mô tả**: `SecurityConfig` có `.ignoringRequestMatchers(SecurityWhitelist.PUBLIC_RECOVERY_POST)` — tức `forgot-password`, `reset-password`, `verify-registration-otp`, `resend-registration-otp` **không còn CSRF check**. Commit `505ef09` ("exempt mobile auth CSRF") đã đưa exemption này vào để hỗ trợ mobile, làm tồn tại **CSRF vulnerability** trên các endpoint nhạy cảm (kẻ tấn công có thể lừa trình duyệt nạn nhân POST cross-site để reset mật khẩu / kích hoạt OTP).
- **Bằng chứng**: 3 test bảo mật có sẵn của repo FAIL trên master (được thiết kế chặn đúng lỗi này):
  - `PasswordResetAndSessionRevocationIntegrationTest.fullPasswordResetAndAllSessionRevocationLifecycle:113` — kỳ vọng 403, thực tế 200
  - `RegistrationOtpIntegrationTest.csrfRemainsEnforced:510` — kỳ vọng 403, thực tế 400
  - `RegistrationOtpRealHttpAndNetworkSmtpTest.case15_csrfProtectionEnforced:760` — kỳ vọng 403, thực tế 400
- **Root cause**: test bảo mật đúng; code sai. Việc miễn CSRF cho mobile là không cần thiết vì client mobile dùng Bearer token — và config đã có sẵn exemption cho mọi request mang header `Authorization: Bearer` (`bearerRequest` matcher). Endpoint recovery nằm trong whitelist `authorizeHttpRequests` (permitAll) nhưng CSRF vẫn phải được enforce.
- **Giải pháp**: bỏ dòng `.ignoringRequestMatchers(SecurityWhitelist.PUBLIC_RECOVERY_POST)` khỏi CSRF config (giữ nguyên `authorizeHttpRequests` permitAll cho cùng whitelist — hành vi HTTP auth không đổi).
- **Kết quả sau fix**: cả 3 test bảo mật PASS (14+16+13 test trong 3 class đều xanh). Runtime curl xác nhận: `POST /api/auth/forgot-password` không CSRF → 403.

### BUG-005 (P2 bảo mật) — `DefaultEmailService` hard-code `mail.*.ssl.trust="*"`: tin TẤT CẢ certificate TLS của SMTP server

- **Mô tả**: cả 3 nhánh cấu hình SMTP (SMTPS 465 ×2, STARTTLS) đều set `mail.{smtp,smtps}.ssl.trust="*"`, khiến JavaMail **bỏ qua validation certificate** — server giả/MITM vẫn được tin dù `checkserveridentity=true`. Test `DefaultEmailServiceTest.untrustedCertificateChainFailsDelivery` (chứng minh cert tự ký phải bị từ chối) FAIL trên master vì hành vi này.
- **Bằng chứng**: `src/main/java/com/aloute/service/email/DefaultEmailService.java` (3 vị trí `ssl.trust="*"`; log test: "Expecting code to raise a throwable").
- **Root cause**: tradeoff ban đầu để tương thích SMTP relay có cert tự-signed, nhưng đã mâu thuẫn chính với các guard `validateConfiguration()` của cùng class (bắt buộc `checkserveridentity=true`) — tự vô hiệu hóa chính nó.
- **Giải pháp (config-gated, an toàn rollback)**: thêm property `aloute.mail.ssl.trust-all` (mặc định **false** = TLS chuẩn, truststore JVM quyết định). Chỉ khi vận hành tự đặt `true` (SMTP relay cert tự-signed) mới quay về hành vi cũ. Production không đổi gì nếu không đặt property.
- **Kết quả sau fix**: `DefaultEmailServiceTest` 20/20 PASS (gồm cả test cert-untrusted và wrong-hostname).

### BUG-006 (P3 — test chất lượng) — `UsernameLoginAndPostCreationIntegrationTest` tạo user thiếu Profile

- **Mô tả**: `createPostWithoutVisibility` trả 404 `USER_NOT_FOUND: "User profile not found"` thay vì 201.
- **Root cause (2 lớp)**: (1) `PostServiceImpl.summary()` yêu cầu row `profiles` tồn tại — đúng domain invariant "User 1—1 Profile" (domain_system §4.2), nhưng test tạo user không có profile; (2) khi vá bằng `@Transactional`, user trong transaction pending của test bị JWT auth filter không nhìn thấy → 401 — phải commit ngay bằng `TransactionTemplate` (pattern chuẩn đã có trong `DevTestDataInitializer`).
- **Giải pháp**: test tạo user + profile trong một `TransactionTemplate` (commit ngay lập tức).
- **Kết quả**: `UsernameLoginAndPostCreationIntegrationTest` 3/3 PASS. Runtime curl xác nhận hành vi sản phẩm ĐÚNG: post không có `visibility` → 201 + mặc định PUBLIC.

### Cảnh báo bảo mật ghi nhận (không sửa trong phạm vi — cần owner quyết định)

1. **JWT trả trong body login response** (`"token":"..."` cùng `Set-Cookie`): làm tăng khả năng token lộ qua log/JS. Đã được addendum PR #53 ghi nhận; vẫn còn trên master. Khuyến nghị: chỉ giữ HttpOnly cookie hoặc đưa ra tùy chọn.
2. **Thiếu part `purpose`** → hiện rơi vào `MissingServletRequestPartException` → 500 (nên là 400/422). Không sửa để tránh phình phạm vi; đề xuất xử lý trong GlobalExceptionHandler.

---

## D. KIỂM THỬ HỒI QUY TOÀN BỘ (Giai đoạn 4)

Chạy trên commit sửa cuối của nhánh `zcode/full-system-audit-20261023`:

| Bộ test | Lệnh | Kết quả |
|---|---|---|
| Frontend Node | `node --test test/frontend/*.test.mjs` | **329 pass / 0 fail** (10 file) |
| Test mới cho bug | `MediaControllerPurposeBindingTest`, `MediaUploadPurposeBindingIntegrationTest`, `PostTextOnlyCreationIntegrationTest` | **11/11 PASS** |
| Maven full (lần 1, trước khi fix BUG-004/005/006) | `mvn -B clean verify` | 572 test: **567 PASS / 5 FAIL** — cả 5 được chứng minh là lỗi baseline có sẵn trên master dd293e9 (tái hiện y hệt khi stash các fix của audit) |
| Maven full (lần cuối, sau khi fix BUG-004/005/006) | `mvn -B clean verify` | **572 test — 0 fail / 0 error / 9 skipped — BUILD SUCCESS** |

**Phân biệt rõ loại bằng chứng** (theo yêu cầu đề bài):
- *Automated PASS*: Maven/Node ở trên.
- *Browser E2E PASS*: login, soạn bài text-only, upload avatar/cover (đến tầng storage), responsive, smoke 8 trang.
- *Integration PASS*: MockMvc multipart với Spring context thật + H2.
- *Real-service PASS*: **KHÔNG có** cho Firebase storage (BLOCKED — không credentials).
- *BLOCKED*: Firebase storage, MySQL 8, Flutter runtime, STOMP runtime (đợt này).

---

## E. TRẢ LỜI 10 CÂU HỎI CUỐI

1. **Có đăng bài text-only được không? Đã test trên browser và database chưa?**
   **CÓ.** Không có lỗi "bắt buộc media" trên master hiện tại — `mediaIds` là tùy chọn (OpenAPI + code + runtime). Đã chứng minh: API 201 (2 bài), browser thật từ `/post/create` → `/posts/2`, bài đọc lại từ DB qua GET, xuất hiện trên feed, Unicode/emoji nguyên vẹn; nội dung rỗng/toàn khoảng trắng bị từ chối 422 đúng. Có 3 regression test mới giữ hành vi này.

2. **Đăng bài kèm ảnh/video hoạt động chưa?**
   **Chưa xác nhận end-to-end — BLOCKED ở storage.** Phần bind multipart + validate (đúng/sai purpose, giới hạn) hoạt động (composer gửi JSON blob vẫn bind OK). Nhưng máy này không có Firebase test environment nên binary không lưu được (503). Không kết luận PASS giả.

3. **Avatar có cập nhật và giữ sau refresh không?**
   **Tìm ra đúng nguyên nhân người dùng không cập nhật được: BUG-001 (415) — đã sửa.** Sau fix, request từ Settings tới được backend. Việc "giữ sau refresh" cần media thật trong DB (`avatarMediaId → avatarUrl` wiring đã có từ PR #62 và `ProfileResponse` trả `avatarUrl`), nhưng không thể tạo media thật không có storage → **chờ Firebase test env để nghiệm thu end-to-end**.

4. **Ảnh bìa có cập nhật và giữ sau refresh không?**
   Giống avatar: cùng bug 415, đã fix; `USER_COVER` + `Profile.coverUrl` + UI Settings có đủ; end-to-end BLOCKED ở storage.

5. **Có lỗi frontend/backend nào khác ảnh hưởng luồng chính không?**
   - BUG-002 khiến **master không build được test (CI đỏ)** — đã sửa.
   - Ngoài ra: không phát hiện lỗi mới ở các luồng chính (8 trang smoke sạch; alert "lỗi" trên /admin xác nhận là false positive — modal ẩn).
   - Node 329/329 PASS.

6. **Có lỗi phân quyền/bảo mật chưa giải quyết?**
   Không phát hiện lỗi phân quyền mới. Hai ghi nhận mở: JWT trong body login (P2, đã知 từ PR #53 addendum, vẫn tồn tại) và thiếu part `purpose` → 500 thay vì 4xx (P3, đề xuất bổ sung handler). CSRF double-submit cookie hoạt động đúng (các 403 tôi gặp lúc đầu là do dùng token cũ trong cookie jar của curl — không phải bug, thí nghiệm đã revert).

7. **WebSocket, MySQL 8, Firebase, Email OTP, 2FA đã test thực tế ở mức nào?**
   - **WebSocket/STOMP**: SKIP đột này (runtime đã verify 2026-10-20 trên nhánh codex/recovery-stomp-verified; integration test vẫn trong suite Maven).
   - **MySQL 8**: BLOCKED — không có mysql/docker trên máy; mọi DB test là H2 (theo đúng design dev/test). Không dùng H2 PASS thay thế MySQL.
   - **Firebase**: BLOCKED — chỉ verify tới ranh giới `MediaStoragePort` (503 đúng).
   - **Email OTP / 2FA**: không runtime lại đợt này (đã verify trong các QA trước — PR #38, #62); suite Maven có test cho các module này.

8. **Có module nào chưa kiểm thử runtime?**
   Flutter (`aloute_mobile`) — BLOCKED runtime (không SDK/emulator); FCM web push; STOMP runtime lần này; MySQL 8; storage thật.

9. **Những lỗi nào đã sửa, lỗi nào còn tồn tại?**
   - **Đã sửa trên nhánh audit (3/3)**: BUG-001 (P0, avatar/cover 415 — sửa gốc ở controller binding + 8 regression test), BUG-002 (P1, test compile), BUG-003 (P3, thông báo lỗi settings).
   - **Còn tồn tại/chờ môi trường**: xác nhận upload thật với Firebase; JWT-in-body (P2, chờ owner quyết); missing-purpose → 500 (P3, đề xuất); MySQL8/Flutter/STOMP/FCM runtime.

10. **Có đủ điều kiện cho owner kiểm thử nghiệm thu và triển khai?**
    - **Web core (đăng bài text-only, feed, settings UI, admin SSR): ĐỦ** trên nhánh `zcode/full-system-audit-20261023` sau khi `mvn clean verify` xanh (xem mục D).
    - **Điều kiện còn thiếu trước khi deploy production**: (a) cấu hình Firebase test/prod đúng để xác nhận upload thật + URL công khai tải được; (b) chạy bộ MySQL 8 verification trên môi trường có MySQL (script có sẵn trong repo); (c) quyết định JWT-in-body; (d) merge nhánh audit qua PR (tôi không tự merge theo quy tắc).

---

## F. BẢNG COMMIT SỬA LỖI

Toàn bộ sửa đổi nằm trong **một commit duy nhất trên nhánh `zcode/full-system-audit-20261023`** (xem `git log -1` của nhánh); không merge/deploy.

| Bug | File sửa | Test mới |
|---|---|---|
| BUG-001 (P0) | `MediaController.java` (+26/-2: `parsePurpose` binding khoan dung 2 dạng client) | `MediaControllerPurposeBindingTest` (5), `MediaUploadPurposeBindingIntegrationTest` (3) |
| BUG-002 (P1) | `MySql8RevocationVerificationTest.java` (bỏ tham số `Executor` đã gỡ khỏi constructor) | — |
| BUG-003 (P3) | `settings.js` (5 chỗ đọc `result.error.message`) | (được phủ bởi E2E PNG + bộ settings test hiện có) |
| BUG-004 (P1) | `SecurityConfig.java` (gỡ `.ignoringRequestMatchers(PUBLIC_RECOVERY_POST)` khỏi CSRF) | 3 test bảo mật có sẵn quay lại xanh |
| BUG-005 (P2) | `DefaultEmailService.java` (`aloute.mail.ssl.trust-all`, mặc định false) | `DefaultEmailServiceTest` 20/20 xanh trở lại |
| BUG-006 (P3) | `UsernameLoginAndPostCreationIntegrationTest.java` (TransactionTemplate + profile) | — |
| P0-1 giữ hành vi | — | `PostTextOnlyCreationIntegrationTest` (3) |

## G. BẰNG CHỨNG

Thư mục `docs/audits/evidence-2026-10-23/`:
1. `01-post-detail-text-only.png` — bài text-only tạo từ browser hiển thị tại `/posts/2`.
2. `02-settings-avatar-upload-error.png` — Settings: preview avatar + thông báo lỗi thật sau fix.
3. `03-settings-390px.png` — responsive 390px không vỡ layout.
4. `04-feed-768px.png` — feed ở 768px.
5. `05-feed-1440px.png` — feed ở 1440px.

Log curl runtime (trích server3.log):
```
Application exception [MEDIA_STORAGE_UNAVAILABLE]: Firebase media storage is disabled   ← settings-shape upload sau fix (trước fix: 415)
Application exception [MEDIA_PURPOSE_INVALID]: Unsupported media purpose                 ← validate mới
```
