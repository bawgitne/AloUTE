# 05 — Báo cáo Accessibility (WCAG 2.0/2.1/2.2 AA)

- Ngày chạy: 2026-10-28
- Môi trường: `http://127.0.0.1:8082` (Spring Boot 3.2.5, H2 in-memory, seed local)
- Công cụ: axe-core 4.10.2 nhúng qua Playwright 1.64.1, Chrome 153.0.8010.53, headless
- Bộ tag: `wcag2a, wcag2aa, wcag21a, wcag21aa, wcag22aa`
- Bằng chứng: `evidence/cross-run-2-results.json`, `evidence/axe-results.json`, `evidence/final-verification-results.json`, `evidence/screenshots/`

## 1. Phương pháp

Mỗi route được mở bằng phiên thật (guest / user `demoan` / admin), chờ DOM ổn định, sau đó chạy `axe.run(document, { runOnly: { type: 'tag', values: AXE_TAGS } })`. Chỉ các vi phạm `impact = serious | critical` mới làm test FAIL; `moderate | minor` được ghi nhận nhưng không chặn. Các rule trả về `incomplete` được liệt kê để kiểm tra thủ công, không tự động coi là PASS.

Bổ sung kiểm tra thủ công có kịch bản:

| Kiểm tra | Cách làm | Kết quả |
|---|---|---|
| Cấu trúc tài liệu | `lang`, `title`, đúng 1 `<main>`, ≥1 `<h1>`, skip link, `main#main-content` | PASS (A11Y-21) |
| Bàn phím — skip link | `Tab` lần đầu, `Enter`, kiểm tra `document.activeElement` trên `/feed` và `/login` | PASS (A11Y-22) |
| Touch target | đo bounding box navbar/action bar/guest CTA/sidebar tại 360×800 và 1440×900 | PASS, ≥44×44 px (RESP-06) |

## 2. Kết quả tổng hợp

20 route được quét bằng axe, **0 vi phạm serious/critical** ở lần chạy cuối.

| # | Vai trò | Route | Số rule PASS | Vi phạm | Incomplete (thủ công) |
|---|---|---|---|---|---|
| A11Y-01 | guest | `/` | 26 | 0 | — |
| A11Y-02 | guest | `/login` | 26 | 0 | — |
| A11Y-03 | guest | `/register` | 26 | 0 | — |
| A11Y-04 | user | `/feed` | 29 | 0 | aria-prohibited-attr, color-contrast |
| A11Y-05 | user | `/posts/1` | 28 | 0 | aria-prohibited-attr, color-contrast |
| A11Y-06 | user | `/users/demoan` | 31 | 0 | aria-prohibited-attr, color-contrast |
| A11Y-07 | user | `/notifications` | 29 | 0 | aria-prohibited-attr, color-contrast |
| A11Y-08 | user | `/messages` | 32 | 0 | aria-prohibited-attr, color-contrast |
| A11Y-09 | user | `/settings` | 28 | 0 | — |
| A11Y-10 | user | `/communities` | 28 | 0 | aria-prohibited-attr, color-contrast |
| A11Y-11 | user | `/topics/hoc-tap` | 28 | 0 | aria-prohibited-attr, color-contrast |
| A11Y-12 | user | `/friends` | 30 | 0 | aria-prohibited-attr, color-contrast |
| A11Y-13 | user | `/saved` | 27 | 0 | aria-prohibited-attr, color-contrast |
| A11Y-14 | user | `/search?q=aloute` | 30 | 0 | aria-prohibited-attr, color-contrast |
| A11Y-15 | admin | `/admin` | 25 | 0 | color-contrast |
| A11Y-16 | admin | `/admin/users` | 30 | 0 | color-contrast |
| A11Y-17 | admin | `/admin/posts` | 31 | 0 | color-contrast |
| A11Y-18 | admin | `/admin/reports` | 31 | 0 | color-contrast |
| A11Y-19 | admin | `/admin/moderation-actions` | 31 | 0 | color-contrast |
| A11Y-20 | user | `/c/qa-a11y-mvrgan9ersfg` | 28 | 0 | aria-prohibited-attr, color-contrast |

`role="feed"` và `role="tablist"` từng bị lạm dụng đã được sửa (xem mục 4) nên không còn xuất hiện trong kết quả.

## 3. Phát hiện ban đầu và nguyên nhân gốc

Lần quét đầu tiên ghi nhận **30 node vi phạm serious/critical** trên các route chính, thuộc 4 nhóm:

1. **`color-contrast`** — nhiều biến thể:
   - `.text-muted` (#64748b) và `.text-secondary` (#475569) bị Bootstrap utility `!important` ghi đè, rơi xuống dưới 4.5:1 trên nền `#f8fafc`.
   - Badge `bg-*-subtle` dùng màu chữ đậm tiêu chuẩn (#0d6efd, #198754…) chỉ đạt ~3.4:1 trên nền tint.
   - Active nav pill nền xanh dùng chữ trắng mờ `rgba(255,255,255,.9)` → 3.94:1.
   - Nút `btn-outline-secondary` kế thừa `#6c757d` → 4.48:1 (sát ngưỡng, vẫn trượt).
   - Badge admin (`admin-badge-success/danger/secondary`) và `btn-outline-warning` (#ffc107 trên trắng ≈ 1.6:1).
2. **`label-content-name-mismatch` (WCAG 2.5.3)** — `aria-label` của nút like/comment/repost/quote/trang kế tiếp/navbar không chứa nội dung nhìn thấy (số đếm, chữ “Sau”, tên người dùng).
3. **`aria-prohibited-attr`** — icon `<i aria-label="…">` thiếu `role="img"` (verified badge, poll check, v.v.).
4. **ARIA role không đúng ngữ nghĩa** — `role="feed"` cho danh sách thông báo không có `article`, `role="tablist"` cho nhóm link điều hướng và cho cả nút đăng xuất.

## 4. Sửa chữa đã thực hiện

| Vấn đề | Tệp | Thay đổi |
|---|---|---|
| Tên truy cập nút tương tác lệch nội dung hiển thị | `templates/post/_interaction-bar.html`, `static/js/features/post/post-like.js` | Bỏ `aria-label` tĩnh/động ở like/comment/repost/quote; thêm `.visually-hidden` mô tả + số đếm để tên truy cập chứa nội dung nhìn thấy |
| Navbar dropdown thiếu tên người dùng | `templates/fragments/navbar.html` | `aria-label` động: “Menu tài khoản của {displayName/username}” |
| Icon trang trí mang nhãn | `templates/post/_post-card.html`, `user/profile.html`, `user/friends.html`, `community/manage/members.html` | Thêm `role="img"` cho icon có `aria-label` |
| `role="feed"` sai | `templates/notification/index.html` | Đổi sang `role="region"` |
| `role="tablist"` sai | `templates/admin/reports/index.html` | Bỏ `role="tablist"` khỏi nhóm link lọc trạng thái |
| Tablist chứa nút đăng xuất | `templates/settings/index.html` | Đóng `</nav>` trước `<hr>`, đưa nút đăng xuất ra ngoài; thêm `w-100` |
| Nhãn “Trang tiếp” lệch chữ “Sau” | `templates/fragments/pagination.html`, `fragments/admin/pagination.html` | Đổi `aria-label="Trang sau"` |
| Nút “Đã đọc tất cả” có nhãn trùng lặp | `templates/notification/index.html` | Bỏ `aria-label`, giữ nội dung hiển thị |
| Tương phản chữ mờ | `static/css/tokens.css`, `static/css/app.css` | `--aloute-text-muted: #5b6779`; ép token bằng `!important`; màu emphasis cho badge subtle; chữ trắng trên nav pill active; `btn-outline-secondary` dùng token |
| Tương phản badge/nút admin | `static/css/admin/admin.css` | success #166534, danger #b91c1c, secondary #475569; bổ sung biến `btn-outline-warning` (#664d03) |
| Skip link trang auth | `templates/layouts/auth.html` | Thêm skip link + `id="main-content"` |

Tất cả tệp đã được đồng bộ sang `target/classes` trước khi chạy lại (server chạy từ `target/classes`, Thymeleaf cache tắt).

## 5. Kiểm tra thủ công và giới hạn

Đã kiểm tra:
- Thứ tự tab: skip link là tab stop đầu tiên; `Enter` đưa focus vào `<main id="main-content">` trên cả `/feed` và `/login` (auth layout có `tabindex="-1"`).
- Cấu trúc: `lang="vi"`, title khác rỗng, đúng 1 `<main>`, có `<h1>`, skip link hiện diện trên layout chính và layout auth.
- Không còn console error/page error trong các phiên quét.
- Touch target ≥44×44 px ở navbar, thanh tương tác, guest CTA và sidebar tại 360×800/1440×900.

**Chưa kiểm tra (không được tính PASS):**
- Trình đọc màn hình thực tế (NVDA/JAWS/VoiceOver) và luồng screen-reader cho feed động.
- Zoom 200%, text spacing 1.4.12, reduced motion, forced colors.
- Contrast của nội dung do người dùng tải lên (ảnh bìa, avatar) và màu tùy biến.
- Các trang không nằm trong 20 route ở trên (ví dụ trang quản lý cộng đồng, admin detail, OTP fallback).
- `incomplete` của axe: các node có nền ảnh/overlay cần đo thủ công; hiện không thấy chênh lệch thị giác nhưng chưa có phép đo pixel.

## 6. Kết luận

Trong phạm vi 20 route đại diện cho guest/user/admin, bề mặt WCAG A/AA tự động kiểm tra được đã sạch vi phạm serious/critical. Đây là **PASS có giới hạn công cụ**, không phải tuyên bố tuân thủ WCAG đầy đủ. Các hạng mục screen reader, zoom và trang ngoài danh sách cần bổ sung trước khi công bố “accessible”.
