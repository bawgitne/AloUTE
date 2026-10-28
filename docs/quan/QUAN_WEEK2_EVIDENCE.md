# Tuần 2 — Bằng chứng hiện thực hóa giao diện Thymeleaf + Bootstrap

> **Kế hoạch tuần:** 27/09/2026 – 04/10/2026
> **Nhiệm vụ theo phân công:** Code Thymeleaf + Bootstrap cho trang chính; layout/fragment; hình giao diện vào báo cáo.
> **Nguồn phân công:** [`skills/INDEX.md` §1.1](../../skills/INDEX.md); kế hoạch tuần tại [`PROJECT_EXECUTION_PLAN.md`](../../PROJECT_EXECUTION_PLAN.md) (local, chưa track).

## 1. Commit thực tế trong và quanh Tuần 2

| Commit | Ngày tác giả | Tác giả | Nội dung | Đối chiếu lịch |
| :--- | :--- | :--- | :--- | :--- |
| [`83e5729`](https://github.com/bawgitne/AloUTE/commit/83e5729) | 2026-09-28 | `Quan <qm33165@gmail.com>` | 10 wireframe SVG + `UI_DESIGN_SYSTEM.md` + `SCREEN_FLOW.md` | Đầu Tuần 2 (là bằng chứng Tuần 1, xem [`QUAN_WEEK1_EVIDENCE.md`](QUAN_WEEK1_EVIDENCE.md)) |
| [`63aad4e`](https://github.com/bawgitne/AloUTE/commit/63aad4e) | 2026-09-30 | `Quan <qm33165@gmail.com>` | Layout, fragment, CSS/tokens và 6 màn hình chính | **Trong Tuần 2** |
| [`8aed6fb`](https://github.com/bawgitne/AloUTE/commit/8aed6fb) | 2026-10-06 | `Quan <qm33165@gmail.com>` | Accessibility + design system alignment | Sau khi Tuần 2 kết thúc 2 ngày |
| [`894bf4f`](https://github.com/bawgitne/AloUTE/commit/894bf4f) | 2026-10-07 | `Quan <qm33165@gmail.com>` | Tích hợp auth/profile/follow flows | Sau khi Tuần 2 kết thúc 3 ngày |

Chỉ **một** commit (`63aad4e`) nằm trong khung 27/09–04/10. Hai commit còn lại nằm trong khung Tuần 3 (05/10–11/10). Hồ sơ không tuyên bố Tuần 2 hoàn thành đúng hạn.

## 2. Sản phẩm của `63aad4e`

Commit thay đổi 18 tệp, thêm 2.370 dòng, gồm:

| Nhóm | Tệp |
| :--- | :--- |
| CSS & token | [`static/css/app.css`](../../src/main/resources/static/css/app.css), [`static/css/tokens.css`](../../src/main/resources/static/css/tokens.css) |
| Layout | [`layouts/default.html`](../../src/main/resources/templates/layouts/default.html), [`layouts/auth.html`](../../src/main/resources/templates/layouts/auth.html) |
| Fragment | [`navbar.html`](../../src/main/resources/templates/fragments/navbar.html), [`sidebar.html`](../../src/main/resources/templates/fragments/sidebar.html), [`head.html`](../../src/main/resources/templates/fragments/head.html), [`flash.html`](../../src/main/resources/templates/fragments/flash.html), [`info-card.html`](../../src/main/resources/templates/fragments/info-card.html), [`pagination.html`](../../src/main/resources/templates/fragments/pagination.html) |
| Màn hình | [`auth/login.html`](../../src/main/resources/templates/auth/login.html), [`auth/register.html`](../../src/main/resources/templates/auth/register.html), [`feed/index.html`](../../src/main/resources/templates/feed/index.html), [`user/profile.html`](../../src/main/resources/templates/user/profile.html), [`post/create.html`](../../src/main/resources/templates/post/create.html), [`post/detail.html`](../../src/main/resources/templates/post/detail.html) |
| Post component | [`post/_post-card.html`](../../src/main/resources/templates/post/_post-card.html), [`post/_interaction-bar.html`](../../src/main/resources/templates/post/_interaction-bar.html) |

Sáu màn hình chính có template sau commit này: Login, Register, Feed, Profile, Create Post, Post Detail. Đây là **source evidence**; giao diện chạy thật được chụp muộn hơn (xem [`QUAN_WEEK3_EVIDENCE.md`](QUAN_WEEK3_EVIDENCE.md)).

## 3. Sản phẩm của `8aed6fb` (06/10)

- Cập nhật [`UI_DESIGN_SYSTEM.md`](../../UI_DESIGN_SYSTEM.md) và [`docs/ui/wireframes/README.md`](../ui/wireframes/README.md) để phân định rõ design evidence lịch sử và baseline triển khai.
- Thêm token/accessibility rules trong [`app.css`](../../src/main/resources/static/css/app.css) và [`tokens.css`](../../src/main/resources/static/css/tokens.css).
- Chỉnh các template `feed/index.html`, `post/create.html`, `post/detail.html`, `layouts/auth.html`.
- Thống kê commit: 9 tệp, +267/−109.

## 4. Sản phẩm của `894bf4f` (07/10)

- Tích hợp luồng auth/profile/follow ở phía UI: [`static/js/auth.js`](../../src/main/resources/static/js/auth.js), [`static/js/page-actions.js`](../../src/main/resources/static/js/page-actions.js), `auth/login.html`, `auth/register.html`, `user/profile.html`, `layouts/default.html`.
- Thống kê commit: 9 tệp, +1.043/−218.
- Đây là phần “tích hợp giao diện Thymeleaf với backend” ở mức UI; không thay đổi backend contract.

## 5. Phạm vi chưa có trong Tuần 2

Các màn hình sau **không** xuất hiện trong commit Tuần 2 của Quân và được thêm sau đó:

| Màn hình | Commit thêm template đầu tiên | Ghi chú |
| :--- | :--- | :--- |
| Messaging, Notification | [`932376a`](https://github.com/bawgitne/AloUTE/commit/932376a) — 2026-10-08, `pminhquan <qm33165@gmail.com>` | Cùng email tác giả; nằm trong khung Tuần 3 |
| Community list/detail | [`82e3a23`](https://github.com/bawgitne/AloUTE/commit/82e3a23) — 2026-10-08, `bawgitne` | Không phải commit của Quân; Quân chỉ polish UI sau đó (PR #50, #73) |
| Search, Settings | [`06255ab`](https://github.com/bawgitne/AloUTE/commit/06255ab) — 2026-10-12, `bawgitne` | Không phải commit của Quân; Quân sửa accessibility search trong working tree hiện tại |

Vì vậy không gán quyền tác giả toàn bộ 10 màn hình cho Quân. Ma trận truy xuất ghi rõ nguồn từng màn hình tại [`UI_TRACEABILITY_MATRIX.md`](UI_TRACEABILITY_MATRIX.md).

## 6. Kiểm thử liên quan

- Test render Thymeleaf hiện hành: [`ThymeleafViewRenderIntegrationTest.java`](../../src/test/java/com/aloute/controller/page/ThymeleafViewRenderIntegrationTest.java) — được thêm lần đầu bởi commit [`749ead0`](https://github.com/bawgitne/AloUTE/commit/749ead0) (2026-10-11, `pminhquan <qm33165@gmail.com>`), tức cuối khung Tuần 3, không phải Tuần 2.
- Test tĩnh frontend liên quan: [`batch1-ux-upgrade.test.mjs`](../../test/frontend/batch1-ux-upgrade.test.mjs) (thêm bởi `5bf3f68`, 2026-10-14) và [`navbar-responsive-overflow.test.mjs`](../../test/frontend/navbar-responsive-overflow.test.mjs) (thêm bởi `c1d674b`, 2026-10-22).
- Kết quả chạy hiện tại: xem [`FRONTEND_TEST_CASES.md`](FRONTEND_TEST_CASES.md). Không có kết quả test lịch sử của riêng Tuần 2 được lưu lại; không tuyên bố “Tuần 2 pass test” khi thiếu log.

## 7. Kết luận Tuần 2

**Đã có:** layout/fragment dùng chung, design token, 6 màn hình chính, CSS responsive nền tảng, và một phần tích hợp auth/profile.
**Chưa có trong khung Tuần 2:** Messaging, Notification, Community, Search, Settings, kiểm thử browser/E2E, ảnh runtime cho báo cáo.
**Sai lệch thời gian:** hai commit quan trọng nhất về accessibility và tích hợp nằm sau hạn Tuần 2; cần phản ánh trung thực trong báo cáo nhóm.
