# Hồ sơ Phạm Minh Quân — Frontend, UI/UX & E2E Testing

> **Thành viên:** Phạm Minh Quân (MSSV 24110311)
> **Vai trò theo kế hoạch nhóm:** Frontend, UI/UX & E2E Testing
> **Phạm vi sở hữu:** `src/main/resources/templates/**`, `src/main/resources/static/{css,js,images}/**`, `docs/ui/wireframes/**`
> **Nhánh làm việc hiện tại:** `fix/quan-frontend-hardening-20261027`
> **Baseline đã xác minh:** `origin/master` @ [`99ab72e`](https://github.com/bawgitne/AloUTE/commit/99ab72ee450fd7cfb7bfae91af4f8e40d23d5ab6) (PR #73 đã merge)
> **Ngày lập hồ sơ:** 2026-10-27
> **Trạng thái tại thời điểm đo (2026-10-27):** thay đổi frontend hardening nằm trong working tree trên nhánh `fix/quan-frontend-hardening-20261027`; các số liệu test dưới đây đo tại baseline này.
> **Trạng thái hiện tại (2026-10-28):** các thay đổi frontend hardening **đã merge vào `master` qua PR #85** (merge commit [`cd0d85c`](https://github.com/bawgitne/AloUTE/commit/cd0d85c)); **chưa deploy production**.

## 1. Mục đích và giới hạn

Thư mục này tập hợp bằng chứng cá nhân cho ba nhóm trách nhiệm: thiết kế giao diện, hiện thực hóa giao diện Thymeleaf + Bootstrap 5, và kiểm thử frontend/E2E. Mọi tuyên bố hoàn thành đều trỏ tới một trong các nguồn sau:

| Loại bằng chứng | Ý nghĩa | Ví dụ trong hồ sơ |
| :--- | :--- | :--- |
| **Design evidence** | Artifact thiết kế, không phải mã chạy | 10 SVG trong [`docs/ui/wireframes/`](../ui/wireframes/README.md) |
| **Source evidence** | Mã nguồn/template/CSS/JS trong repository | [`navbar.html`](../../src/main/resources/templates/fragments/navbar.html), [`app.css`](../../src/main/resources/static/css/app.css) |
| **Local runtime evidence** | Ứng dụng chạy local, profile `dev`, H2 in-memory | [`runtime-ui`](../report-assets/runtime-ui/RUNTIME_UI_CAPTURE_REPORT_2026-10-20.md), [`test-results/quan-frontend-hardening`](../../test-results/quan-frontend-hardening/) |
| **Production evidence** | Quan sát `https://justneed.food` ở trạng thái guest | [`03_LIVE_WEBSITE_AUDIT.md`](../audits/independent-full-audit-2026-10-27/03_LIVE_WEBSITE_AUDIT.md) |
| **Historical gap** | Nguồn lịch sử không có trong git, không thể xác minh | `WEEK2_UI_STYLE_DECISION.md`, `Nhom13_KeHoachCodeTest.docx` |

Không có artifact thiết kế nào được sao chép vào thư mục này; các tài liệu canonical và wireframe được tham chiếu bằng liên kết.

## 2. Danh mục tài liệu

| Tài liệu | Nội dung |
| :--- | :--- |
| [`QUAN_WEEK1_EVIDENCE.md`](QUAN_WEEK1_EVIDENCE.md) | Đối chiếu kế hoạch Tuần 1 với commit thiết kế thực tế, kiểm định 10 SVG |
| [`QUAN_WEEK2_EVIDENCE.md`](QUAN_WEEK2_EVIDENCE.md) | Đối chiếu kế hoạch Tuần 2 với các commit template/layout/design token |
| [`QUAN_WEEK3_EVIDENCE.md`](QUAN_WEEK3_EVIDENCE.md) | Tích hợp UI, runtime captures, các PR frontend và E2E |
| [`UI_TRACEABILITY_MATRIX.md`](UI_TRACEABILITY_MATRIX.md) | Truy xuất 10 wireframe → template → ảnh runtime → test |
| [`FRONTEND_TEST_CASES.md`](FRONTEND_TEST_CASES.md) | Danh mục test case, lệnh chạy, PASS/FAIL/SKIPPED, giới hạn |
| [`RESPONSIVE_ACCESSIBILITY_REPORT.md`](RESPONSIVE_ACCESSIBILITY_REPORT.md) | Đo responsive 360/375/390/768/1440 và kiểm tra accessibility |
| [`FRONTEND_BUGFIX_LOG.md`](FRONTEND_BUGFIX_LOG.md) | BL-17, BL-20, BL-21, BL-22, BL-23, BL-25: nguyên nhân, sửa, bằng chứng |

## 3. Ảnh chụp kiểm chứng hiện tại

- **Node frontend suite:** 396/396 PASS, 94 suite, 0 fail, 0 skip — lệnh `node --test test/frontend/*.test.mjs`.
- **Maven suite:** 641 test, 73 suite, 0 failure, 0 error, 9 skipped — lệnh `mvn clean test`, exit 0.
- **Browser verification:** 27/27 check PASS trên Chrome thật qua Playwright — [`verification.json`](../../test-results/quan-frontend-hardening/after/verification.json).
- **Đo responsive:** 3 route × 5 viewport (360/375/390/768/1440), trước và sau — [`baseline/measurements.json`](../../test-results/quan-frontend-hardening/baseline/measurements.json), [`after/measurements.json`](../../test-results/quan-frontend-hardening/after/measurements.json).
- **Không** coi test tĩnh Node là bằng chứng browser E2E; hai loại bằng chứng được tách riêng trong [`FRONTEND_TEST_CASES.md`](FRONTEND_TEST_CASES.md).

## 4. Phạm vi thay đổi frontend hardening (đã merge qua PR #85)

Các file thay đổi so với `99ab72e`; nay đã nằm trên `master` qua PR #85:

| Nhóm | File | Mục đích |
| :--- | :--- | :--- |
| Guest navigation | [`navbar.html`](../../src/main/resources/templates/fragments/navbar.html), [`sidebar.html`](../../src/main/resources/templates/fragments/sidebar.html) | Thay `href="#"` bằng `/login`, `/register`; giữ hook JS |
| Guest action gating | [`navbar.html`](../../src/main/resources/templates/fragments/navbar.html), [`modal.html`](../../src/main/resources/templates/fragments/modal.html), [`feed/index.html`](../../src/main/resources/templates/feed/index.html), [`post/detail.html`](../../src/main/resources/templates/post/detail.html), [`post/_post-card.html`](../../src/main/resources/templates/post/_post-card.html), [`post/_interaction-bar.html`](../../src/main/resources/templates/post/_interaction-bar.html) | Ẩn composer/form/action ghi với guest (gồm nút “Tạo bài viết” và modal composer); thêm CTA đăng nhập |
| Accessibility | [`layouts/default.html`](../../src/main/resources/templates/layouts/default.html), [`layouts/single.html`](../../src/main/resources/templates/layouts/single.html), [`search/index.html`](../../src/main/resources/templates/search/index.html), [`app.css`](../../src/main/resources/static/css/app.css) | Skip link ở cả hai layout, `<h1>` cho search, touch target 44px (navbar, interaction bar, CTA guest, sidebar) |
| Design tokens | `right-sidebar.html` (đã gỡ 2026-10-29) | Sửa token `--al-text-primary` không tồn tại; fragment demo không còn template nào include nên được dọn trong đợt frontend cleanup, lịch sử giữ trong git |
| Asset delivery | [`head.html`](../../src/main/resources/templates/fragments/head.html), [`admin.html`](../../src/main/resources/templates/layouts/admin.html) | SRI cho Bootstrap Icons ở cả layout public và admin; cache asset giữ nguyên mặc định 12h (thay đổi thử 365d đã hoàn nguyên vì nhiều URL `/images/...` hardcode trong JS không đi qua content-hash) |
| Regression tests | [`guest-ux-accessibility.test.mjs`](../../test/frontend/guest-ux-accessibility.test.mjs), [`ThymeleafViewRenderIntegrationTest.java`](../../src/test/java/com/aloute/controller/page/ThymeleafViewRenderIntegrationTest.java) | 26 test tĩnh mới; cập nhật test render cho quote gate và create modal |

Không thay đổi backend security, schema, migration, deployment hoặc production. Chi tiết và giới hạn ở [`FRONTEND_BUGFIX_LOG.md`](FRONTEND_BUGFIX_LOG.md).

**Giới hạn đã biết:** [`FeedPageController`](../../src/main/java/com/aloute/controller/page/FeedPageController.java#L31) và [`CommunityManagePageController`](../../src/main/java/com/aloute/controller/page/CommunityManagePageController.java#L45) lấy `currentUser` theo kiểu best-effort; nếu lookup profile lỗi, user đã đăng nhập có thể tạm thời thấy UI guest. Đây là hành vi controller hiện có, không sửa trong nhánh frontend.

## 5. Nguồn lịch sử bị thiếu hoặc không ổn định

Các mục dưới đây **không được dùng làm bằng chứng duy nhất** cho một quyết định đã hoàn tất:

1. `WEEK2_UI_STYLE_DECISION.md` — có trên máy (20.645 byte, sửa lần cuối 2026-09-30) nhưng **không được track trong git**. Vì vậy không thể xác minh lịch sử phê duyệt, tác giả hoặc nội dung trên GitHub. Các tài liệu tracked [`UI_DESIGN_SYSTEM.md`](../../UI_DESIGN_SYSTEM.md) và [`docs/ui/wireframes/README.md`](../ui/wireframes/README.md) có liên kết tới file này; liên kết chỉ resolve trong workspace local, không resolve trên GitHub. Không suy đoán nội dung quyết định.
2. `Nhom13_KeHoachCodeTest.docx` — file Word gốc không được track; nội dung phân công 3 tuần chỉ được tóm tắt lại trong [`skills/INDEX.md`](../../skills/INDEX.md) (tracked) và các file kế hoạch local (`MODULE_ASSIGNMENT.md`, `PROJECT_EXECUTION_PLAN.md`, `FE_WEEK2_IMPLEMENTATION_PLAN.md`).
3. `FE_WEEK2_IMPLEMENTATION_PLAN.md`, `FE_INTEGRATION_PLAN.md`, `MODULE_ASSIGNMENT.md`, `ANTI-Q-FE-TASK.md` — không track; chỉ dùng làm bối cảnh local.
4. `docs/audits/independent-full-audit-2026-10-27/**` — bộ audit ngày 2026-10-27 **local-only, không có trong repository** (chưa được commit); các liên kết trong hồ sơ này chỉ resolve trong workspace local.
5. **Không xác minh được commit production.** Audit live ghi nhận `origin/master` tại thời điểm audit là `6f257eb`, trong khi master hiện tại đã là `99ab72e` sau PR #73. Không có GitHub Deployment/environment record hay endpoint version công khai.

## 6. Cách đọc trạng thái

- **PASS**: đã chạy và có output thực tế trong workspace.
- **PARTIAL**: phần frontend đã làm, phần phụ thuộc backend bị chặn.
- **BLOCKED / NOT VERIFIED**: chưa có tài khoản, môi trường hoặc bằng chứng để kết luận.
- Commit lịch sử dùng SHA GitHub. Các số liệu trong hồ sơ được đo tại baseline lịch sử (`99ab72e`, 2026-10-27) và giữ nguyên; trạng thái mã nguồn hiện tại là đã merge qua PR #85 (merge commit `cd0d85c`).
