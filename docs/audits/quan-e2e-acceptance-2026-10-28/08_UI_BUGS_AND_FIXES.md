# 08 — UI Bugs & Fixes

Chỉ các thay đổi frontend an toàn (Thymeleaf, CSS, JS) được thực hiện. Không sửa backend, security, DTO, migration hay cấu hình production. Mọi fix đều được xác minh lại bằng browser E2E và axe-core.

## 1. Lỗi phát hiện trong đợt QA này

| ID | Mức | Loại | Mô tả | Trạng thái | Case xác minh |
|---|---|---|---|---|---|
| QA-UI-01 | Serious | Accessibility | Tên truy cập (`aria-label`) của nút like/comment/repost/quote không chứa nội dung nhìn thấy (số đếm, nhãn ẩn) — vi phạm WCAG 2.5.3 | Fixed | A11Y-04..20, SOC-P11, cross-run-2 |
| QA-UI-02 | Serious | Accessibility | Nhiều cặp màu chữ/nền dưới 4.5:1: chữ muted trên nền card, badge subtle, nav pill active, outline button, badge/nút admin, `btn-outline-warning` | Fixed | A11Y-01..20 |
| QA-UI-03 | Serious | Accessibility | Icon `<i aria-label>` thiếu `role="img"` (verified badge, poll check) — axe `aria-prohibited-attr` | Fixed | A11Y-04..20 |
| QA-UI-04 | Moderate | Accessibility | Lạm dụng ARIA: `role="feed"` cho danh sách thông báo, `role="tablist"` cho nhóm link lọc và nhóm chứa nút đăng xuất | Fixed | A11Y-07, A11Y-09, A11Y-18 |
| QA-UI-05 | Moderate | Accessibility | Nút menu tài khoản có `aria-label="Menu tài khoản"` không chứa tên người dùng hiển thị | Fixed | A11Y-04..14 |
| QA-UI-06 | Moderate | Accessibility | Nút phân trang `aria-label="Trang tiếp"` nhưng chữ hiển thị là “Sau” — label/content mismatch | Fixed | A11Y-04..14 |
| QA-UI-07 | Moderate | Accessibility | Tab settings chứa nút Đăng xuất trong `nav`/tablist; chữ tab active trắng mờ chỉ 3.94:1 | Fixed | A11Y-09 |
| QA-UI-08 | Moderate | Accessibility | Trang auth không có skip link và `<main>` không có `id`/`tabindex` để nhận focus | Fixed | A11Y-02,03, A11Y-22 |
| QA-UI-09 | High | Functional | Messaging hiển thị lỗi tiếng Anh/`TypeError` thô khi backend trả `FORBIDDEN` hoặc mất mạng | Fixed | MSG-09, RES-06..09 |
| QA-UI-10 | Medium | Functional | Trùng slug/tên cộng đồng với mã lỗi `COMMUNITY_*_EXISTS` không được map sang thông báo tiếng Việt | Fixed | COM-03 |
| QA-UI-11 | Medium | Hardening | Form auth không khai báo `method="post"`; nếu JS lỗi, submit có thể rơi về GET | Fixed | AUTH-22, AUTH-23 |
| QA-UI-12 | Medium | Functional | `post-like.js` tự ghi đè `aria-label` sau mỗi lần like/repost, gây lệch với nội dung số đếm | Fixed | SOC-P11, SOC-P12, SOC-P16 |
| QA-UI-13 | Low | Infra/UX | CSS không có ETag, cache 12h; 4 request CDN bên ngoài | Not fixed (ngoài phạm vi frontend an toàn) | PERF-03 |
| QA-UI-14 | High | Backend | Trang bảo vệ trả JSON 401/403/404 thay vì HTML/redirect (BL-22) | Ticket cho ZCode | AUTH-15/16, ADM-05, RES-02 |
| QA-UI-15 | Moderate | Accessibility | Hai link icon navbar (Tin nhắn/Thông báo) chỉ rộng 30px ở desktop, dưới baseline 44×44 của BL-17 | Fixed | RESP-06 |

## 2. Chi tiết fix

### QA-UI-01 + QA-UI-12 — Tên truy cập thanh tương tác

**Trước:** nút like có `aria-label="Thích bài viết"` + nội dung “12 lượt thích”; khi like, JS đổi label thành “Bỏ thích bài viết”. Tên truy cập không chứa nội dung nhìn thấy → axe `label-content-name-mismatch` (serious).

**Sau:**
- `templates/post/_interaction-bar.html`: bỏ `aria-label` ở like/comment/repost/quote; thêm `<span class="visually-hidden">Thích bài viết,</span>` trước số đếm và giữ hậu tố “lượt thích”. Tên truy cập trở thành “Thích bài viết, 12 lượt thích”, khớp nội dung.
- `static/js/features/post/post-like.js`: bỏ hai dòng `setAttribute('aria-label', …)` cho like/repost; bookmark vẫn giữ label vì không có số đếm.

**Xác minh:** SOC-P11/P12/P16 PASS; axe 0 vi phạm trên `/feed`, `/posts/1`, `/saved`.

### QA-UI-02 — Tương phản màu

| Cặp màu | Trước | Sau | Ngưỡng |
|---|---|---|---|
| Muted trên nền card `#f1f5f9` | `#64748b` — 4.34:1 | `#5b6779` — 5.24:1 | 4.5:1 |
| Primary trên primary-subtle `#cfe2ff` | `#0d6efd` — 3.42:1 | `#084298` — 7.12:1 | 4.5:1 |
| Success trên success-subtle `#f0fdf4` | `#16a34a` — 3.15:1 | `#166534` — 6.81:1 | 4.5:1 |
| Danger trên danger-subtle `#fef2f2` | `#dc2626` — 4.41:1 | `#b91c1c` — 5.91:1 | 4.5:1 |
| Warning trên trắng | `#ffc107` — 1.63:1 | `#664d03` — 7.99:1 | 4.5:1 |
| Outline secondary trên `#f8fafc` | `#6c757d` — 4.48:1 | `#475569` — 7.24:1 | 4.5:1 |
| Tab settings active | trắng 90% trên xanh — 3.94:1 | `#fff` — 4.50:1 | 4.5:1 |

Thay đổi:
- `static/css/tokens.css`: `--aloute-text-muted: #5b6779`.
- `static/css/app.css`: utility `.text-muted`/`.text-secondary` dùng token với `!important`; màu emphasis cho `bg-*-subtle`; nav pill active chữ trắng; `btn-outline-secondary` dùng token.
- `static/css/admin/admin.css`: badge success/danger/secondary đậm hơn; bổ sung biến `--bs-btn-*` cho `.btn-outline-warning`.

**Xác minh:** axe 20 route không còn `color-contrast` serious/critical; ảnh responsive không đổi nhận diện thị giác.

### QA-UI-03 — Icon mang nhãn

Thêm `role="img"` cho:
- `templates/post/_post-card.html`: verified badge, poll check “Lựa chọn của bạn”.
- `templates/user/profile.html`, `templates/user/friends.html`, `templates/community/manage/members.html`: verified badge.
- `static/js/page-actions.js`: icon check được tạo động sau khi vote.

**Xác minh:** axe `aria-prohibited-attr` không còn trong danh sách vi phạm (vẫn nằm ở `incomplete` do axe không kết luận được với icon font, đã kiểm tra thủ công).

### QA-UI-04 — ARIA role

- `notification/index.html`: `role="feed"` → `role="region"` (danh sách không có `article`/`aria-posinset`).
- `admin/reports/index.html`: bỏ `role="tablist"` khỏi `<ul class="nav nav-tabs">` (chỉ là link lọc, không có `role="tab"`/`tabpanel`).
- `settings/index.html`: đóng `</nav>` trước `<hr>`, đưa nút Đăng xuất ra ngoài vùng điều hướng; thêm `w-100` để giữ bố cục.

### QA-UI-05/06 — Nhãn lệch nội dung

- Navbar: `aria-label` động “Menu tài khoản của {tên}” thay cho nhãn tĩnh.
- Pagination (2 fragment): `"Trang tiếp"` → `"Trang sau"` khớp chữ hiển thị “Sau”.
- Nút “Đã đọc tất cả”: bỏ `aria-label` trùng, giữ nội dung hiển thị làm tên truy cập.

### QA-UI-07/08 — Settings và auth layout

- Settings: tách logout khỏi tablist; tab active dùng chữ trắng đặc.
- `layouts/auth.html`: thêm skip link, `id="main-content"` và `tabindex="-1"` cho `<main>`; áp dụng cho login/register/forgot/reset/verify. A11Y-22 xác minh Enter thực sự đưa focus vào main trên `/login`.

### QA-UI-09 — Thông báo lỗi messaging

- `FORBIDDEN` được map sang “Bạn không phải thành viên của cuộc trò chuyện này.”
- `TypeError` (mất mạng) hiển thị “Không thể kết nối đến máy chủ.” thay vì `Failed to fetch`.
- Áp dụng cho tải danh sách hội thoại, tải lịch sử, gửi tin.

**Xác minh:** MSG-09, RES-06, RES-07, RES-08, RES-09 PASS.

### QA-UI-10 — Mã lỗi cộng đồng

`page-actions.js` bổ sung `COMMUNITY_NAME_EXISTS`, `COMMUNITY_SLUG_EXISTS` vào map thông báo tiếng Việt.

**Xác minh:** COM-03 PASS (API 409 + UI báo lỗi, giữ trang).

### QA-UI-11 — Form auth

Thêm `method="post"` cho 5 form auth. Không thay đổi action/endpoint; JS vẫn chặn submit và gọi API. Khi JS tắt, form gửi POST body và **không** đưa mật khẩu vào URL.

**Giới hạn:** backend không có form-login fallback (`POST /login` trả 403 JSON), nên đăng nhập **vẫn cần JS**. Không tuyên bố JS-off login dùng được.

**Xác minh:** AUTH-22 (render/navigation) và AUTH-23 (quan sát POST body, không có `password=` trong URL) PASS.

### QA-UI-15 — Navbar icon links đạt 44px trên desktop

**Trước:** rule 44px cho `.aloute-navbar .btn-link.p-1` và `.navbar-notification-link` chỉ nằm trong media query `<576px`; đo tại 1440×900 cho kết quả 30×44px cho “Tin nhắn” và “Thông báo”.

**Fix:** thêm rule toàn cục `min-height/min-width: 44px` + `inline-flex` cho hai selector; mobile vẫn giữ `padding: 0` để không phình navbar.

**Xác minh:** RESP-06 đo mobile 360 và desktop 1440; không còn target <44px ở navbar, interaction bar, guest CTA hoặc sidebar. Dropdown “Thao tác bài viết”/nút compact không bị ép 44px.

## 3. Không sửa (có lý do)

| Hạng mục | Lý do |
|---|---|
| BL-22 (JSON error cho page route) | Cần sửa security/exception handling backend — ngoài phạm vi cho phép; đã lập ticket QA-BE-01/02/03 |
| Upload Firebase | Thiếu service account môi trường, không phải lỗi UI |
| `role="feed"` chuyên sâu | Đã hạ về `region`; muốn `feed` đúng chuẩn cần thêm `article` + `aria-posinset`/`aria-setsize` và JS cập nhật — không cần cho release hiện tại |
| CDN self-host / SRI / ETag | Thay đổi hạ tầng/tài nguyên; cần quyết định deploy |
| Poll hết hạn, custom feed | Thiếu công cụ seed/contract; xem `09_BACKEND_DEPENDENCIES.md` |

## 4. Tác động và regression

- 23 tệp thay đổi: 3 CSS, 4 JS, 16 template.
- Không đổi route, API call, DTO, quyền, cấu hình.
- Sau fix: 155 PASS / 4 FAIL (đều BL-22) / 3 BLOCKED / 3 NOT RUN.
- Axe: 0 vi phạm serious/critical trên 20 route.
- Không phát hiện regression chức năng ở social, community, messaging, notification, admin (ngoài ADM-05 đã biết).
