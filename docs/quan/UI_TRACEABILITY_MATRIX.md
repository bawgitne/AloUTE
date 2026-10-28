# Ma trận truy xuất UI — Wireframe → Source → Runtime → Test

> **Phạm vi:** 10 màn hình trong [`docs/ui/wireframes/`](../ui/wireframes/README.md).
> **Nguyên tắc:** mỗi cột là một loại bằng chứng khác nhau; không thay thế cột này bằng cột khác.
> **Baseline source:** `origin/master` @ `99ab72e` + working tree hardening (đo 2026-10-27; nay đã merge qua PR #85, merge commit `cd0d85c`).
> **Runtime evidence:** local, profile `dev`, H2 in-memory, 2026-10-20/21 (PR #42, #49) — không phải production.
> **Production evidence:** guest-only audit ngày 2026-10-27 trên `https://justneed.food`; commit production không xác minh được.

## 1. Quy ước cột

| Cột | Ý nghĩa | Không có nghĩa là |
| :--- | :--- | :--- |
| **Design** | SVG wireframe Tuần 1, đã đóng băng | UI đã chạy |
| **Source** | Template/CSS/JS hiện có trong repo | UI đã được deploy |
| **Runtime** | Ảnh chụp local từ ứng dụng chạy thật | Ảnh production |
| **Test** | Test tĩnh Node, render Maven, browser Playwright | E2E đầy đủ mọi luồng |
| **Production** | Quan sát guest trên live site ngày 2026-10-27 | Xác nhận commit production |

## 2. Ma trận 10 màn hình

| Màn hình | Design | Source (template chính) | Runtime (local) | Test liên quan | Production (guest, 2026-10-27) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **WF-01 Đăng nhập** | [`01-login.svg`](../ui/wireframes/01-login.svg) | [`auth/login.html`](../../src/main/resources/templates/auth/login.html) | [`01-login.png`](../report-assets/runtime-ui/01-login.png) | [`auth-otp-behavior.test.mjs`](../../test/frontend/auth-otp-behavior.test.mjs); browser check “local dev login succeeds”; Maven auth integration tests | `/login` 200; form render đúng — PASS (audit) |
| **WF-02 Đăng ký** | [`02-register.svg`](../ui/wireframes/02-register.svg) | [`auth/register.html`](../../src/main/resources/templates/auth/register.html) | [`02-register.png`](../report-assets/runtime-ui/02-register.png), [`02-register-b.png`](../report-assets/runtime-ui/02-register-b.png) | [`auth-otp-behavior.test.mjs`](../../test/frontend/auth-otp-behavior.test.mjs); Maven registration/OTP tests | `/register` 200; 4 trường, minlength đúng — PASS (audit) |
| **WF-03 Bảng tin** | [`03-home-feed.svg`](../ui/wireframes/03-home-feed.svg) | [`feed/index.html`](../../src/main/resources/templates/feed/index.html), [`post/_post-card.html`](../../src/main/resources/templates/post/_post-card.html) | [`03-home-feed-a.png`](../report-assets/runtime-ui/03-home-feed-a.png), [`03-home-feed-b.png`](../report-assets/runtime-ui/03-home-feed-b.png) | [`guest-ux-accessibility.test.mjs`](../../test/frontend/guest-ux-accessibility.test.mjs), [`navbar-responsive-overflow.test.mjs`](../../test/frontend/navbar-responsive-overflow.test.mjs), [`batch1-ux-upgrade.test.mjs`](../../test/frontend/batch1-ux-upgrade.test.mjs); đo 5 viewport | `/feed` 200; guest thấy composer/action bar — FAIL UX (audit); nhánh hiện tại đã gate composer/action cho guest |
| **WF-04 Hồ sơ** | [`04-profile.svg`](../ui/wireframes/04-profile.svg) | [`user/profile.html`](../../src/main/resources/templates/user/profile.html) | [`04-profile-a.png`](../report-assets/runtime-ui/04-profile-a.png), [`04-profile-b.png`](../report-assets/runtime-ui/04-profile-b.png), [`04-profile-peer.png`](../report-assets/runtime-ui/04-profile-peer.png) | Maven `ThymeleafViewRenderIntegrationTest` (profile tabs/retry), [`batch2-features.test.mjs`](../../test/frontend/batch2-features.test.mjs) | `/users/demoan` 200; **counter sai** (0 bài dù có 3) — FAIL (audit), chưa sửa trong nhánh |
| **WF-05 Tạo bài viết** | [`05-create-post.svg`](../ui/wireframes/05-create-post.svg) | [`post/create.html`](../../src/main/resources/templates/post/create.html), [`post/_composer-form.html`](../../src/main/resources/templates/post/_composer-form.html) | [`05-create-post-a.png`](../report-assets/runtime-ui/05-create-post-a.png), [`05-create-post-b.png`](../report-assets/runtime-ui/05-create-post-b.png) | [`poll-composer.test.mjs`](../../test/frontend/poll-composer.test.mjs), [`poll-media.test.mjs`](../../test/frontend/poll-media.test.mjs), Maven composer tests | Guest `/post/create` local: 500 HTML thay vì redirect login — BL-22, xem [`FRONTEND_BUGFIX_LOG.md`](FRONTEND_BUGFIX_LOG.md) |
| **WF-06 Chi tiết bài viết** | [`06-post-detail.svg`](../ui/wireframes/06-post-detail.svg) | [`post/detail.html`](../../src/main/resources/templates/post/detail.html), [`post/_interaction-bar.html`](../../src/main/resources/templates/post/_interaction-bar.html) | [`06-post-detail-a.png`](../report-assets/runtime-ui/06-post-detail-a.png), [`06-post-detail-b.png`](../report-assets/runtime-ui/06-post-detail-b.png) | [`post-interactions.test.mjs`](../../test/frontend/post-interactions.test.mjs), [`quote-visibility-dropdown.test.mjs`](../../test/frontend/quote-visibility-dropdown.test.mjs), [`guest-ux-accessibility.test.mjs`](../../test/frontend/guest-ux-accessibility.test.mjs), Maven render quote-fail-closed | `/posts/4` 200; guest thấy form comment — FAIL UX (audit); nhánh hiện tại thay bằng prompt đăng nhập |
| **WF-07 Tìm kiếm** | [`07-search.svg`](../ui/wireframes/07-search.svg) | [`search/index.html`](../../src/main/resources/templates/search/index.html) | [`07-search-a.png`](../report-assets/runtime-ui/07-search-a.png) | [`guest-ux-accessibility.test.mjs`](../../test/frontend/guest-ux-accessibility.test.mjs) (một `<h1>`); đo 5 viewport | `/search?q=hoc-tap` 200; **không có `<h1>`** — FAIL a11y (audit); nhánh hiện tại đã thêm `<h1 class="visually-hidden">` |
| **WF-08 Cộng đồng** | [`08-community.svg`](../ui/wireframes/08-community.svg) | [`community/index.html`](../../src/main/resources/templates/community/index.html), [`community/detail.html`](../../src/main/resources/templates/community/detail.html) | [`08-community-a.png`](../report-assets/runtime-ui/08-community-a.png) … [`08-community-d.png`](../report-assets/runtime-ui/08-community-d.png) | [`community-manage.test.mjs`](../../test/frontend/community-manage.test.mjs), [`batch2-features.test.mjs`](../../test/frontend/batch2-features.test.mjs), Maven community render tests | `/communities` 200 nhưng rỗng trên live — PASS render / GAP dữ liệu; moderation/manage mới có trên nhánh PR #73, **chưa deploy** |
| **WF-09 Nhắn tin** | [`09-messaging.svg`](../ui/wireframes/09-messaging.svg) | [`messaging/index.html`](../../src/main/resources/templates/messaging/index.html) | [`09-messaging-a.png`](../report-assets/runtime-ui/09-messaging-a.png) | [`messaging-behavior.test.mjs`](../../test/frontend/messaging-behavior.test.mjs) | **BLOCKED** — cần đăng nhập; audit không có tài khoản |
| **WF-10 Thông báo** | [`10-notification.svg`](../ui/wireframes/10-notification.svg) | [`notification/index.html`](../../src/main/resources/templates/notification/index.html) | [`10-notifications-a.png`](../report-assets/runtime-ui/10-notifications-a.png), [`10-notifications-b.png`](../report-assets/runtime-ui/10-notifications-b.png) | [`notification-module.test.mjs`](../../test/frontend/notification-module.test.mjs) | **BLOCKED** — cần đăng nhập; audit không có tài khoản |

## 3. Nguồn tác giả theo từng màn hình

| Màn hình | Commit/template đầu tiên | Tác giả | Ghi chú |
| :--- | :--- | :--- | :--- |
| WF-01, WF-02, WF-03, WF-04, WF-05, WF-06 | [`63aad4e`](https://github.com/bawgitne/AloUTE/commit/63aad4e) (2026-09-30) | `Quan <qm33165@gmail.com>` | 6 màn hình chính + layout/fragment |
| WF-07 Search | [`06255ab`](https://github.com/bawgitne/AloUTE/commit/06255ab) (2026-10-12) | `bawgitne` | Quân chỉ thêm `<h1>` và đo responsive trong nhánh hardening |
| WF-08 Community | [`82e3a23`](https://github.com/bawgitne/AloUTE/commit/82e3a23) (2026-10-08) | `bawgitne` | Quân polish UI ở PR [#50](https://github.com/bawgitne/AloUTE/pull/50), [#73](https://github.com/bawgitne/AloUTE/pull/73) |
| WF-09 Messaging, WF-10 Notification | [`932376a`](https://github.com/bawgitne/AloUTE/commit/932376a) (2026-10-08) | `pminhquan <qm33165@gmail.com>` | Cùng email với `Quan`; sửa tiếp ở PR #60, #68 |

Bảng này thay thế cho mọi tuyên bố “Quân làm toàn bộ 10 màn hình”. Quyền tác giả được xác định bằng commit, không bằng tên màn hình.

## 4. Sai lệch giữa thiết kế và hiện thực

| Chủ đề | Wireframe / design | Hiện thực hiện tại | Đánh giá |
| :--- | :--- | :--- | :--- |
| Bảng màu | SVG Tuần 1 dùng dark palette lịch sử | Light Campus tokens trong `tokens.css`/`UI_DESIGN_SYSTEM.md` | Chấp nhận: SVG là design evidence lịch sử, Light Campus là baseline triển khai |
| Guest composer/action | WF-03/WF-06 thể hiện composer và action bar | Nhánh hardening ẩn composer/form/action với guest, hiện CTA đăng nhập | Cải thiện UX; cần cập nhật ảnh runtime sau khi merge |
| Search heading | WF-07 có tiêu đề màn hình | Source cũ thiếu `<h1>`; nhánh hardening thêm `<h1 class="visually-hidden">` | Đã sửa phần heading; ảnh runtime cũ chưa phản ánh |
| Messaging auto-select | WF-09 mô tả chọn hội thoại | `/messages` từng tự chọn hội thoại đầu; PR #68 sửa | Ảnh runtime 20/10 chụp trước khi sửa |
| Responsive mobile | WF README quy định 1 cột dưới mobile | Ảnh runtime chỉ 1440×900; đo responsive thực hiện 2026-10-27 | Ảnh mobile mới nằm trong `test-results/quan-frontend-hardening/after/` |

## 5. Kết luận truy xuất

- **10/10 màn hình** có design artifact, template source và ít nhất một ảnh runtime local.
- **6/10 màn hình** có commit đầu tiên của Quân; 3 màn hình do thành viên khác tạo template, Quân tham gia polish/QA; không gán lại quyền tác giả.
- **Không màn hình nào** có bằng chứng E2E đầy đủ trên production với tài khoản thật.
- **Không có** ảnh production mới cho nhánh hardening; ảnh trong hồ sơ này là local.
