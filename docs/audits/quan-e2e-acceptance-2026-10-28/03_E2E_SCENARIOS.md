# 03 — E2E Scenarios A–G

Mỗi kịch bản là một hành trình người dùng thật trên browser (Chrome 153 headless), có precondition, bước thực hiện, kỳ vọng và bằng chứng. Trạng thái kịch bản chỉ PASS khi tất cả case cốt lõi PASS; case BLOCKED/NOT RUN được ghi rõ.

| Kịch bản | Phạm vi | Kết quả | Case chính |
|---|---|---|---|
| A | Guest → đăng nhập → phiên → đăng xuất | **PASS WITH LIMITATIONS** (2 FAIL BL-22) | AUTH-01..12, 14, 22, 23 |
| B | Sinh viên: hồ sơ, bài viết, tương tác, poll, feed | **PASS WITH LIMITATION** (upload BLOCKED, 2 NOT RUN) | SOC-P01..P05, P10..P23, P30..P33, P40..P46, P50/51 |
| C | Cộng đồng đa vai trò + kiểm duyệt | **PASS WITH LIMITATION** (private join BLOCKED) | COM-01..19 |
| D | Tin nhắn realtime + thông báo | **PASS** | MSG-01..13, NTF-01..09 |
| E | Admin: báo cáo → quyết định → gỡ/khôi phục → audit | **PASS WITH LIMITATION** (ADM-05 BL-22) | ADM-01..12 |
| F | Chất lượng: accessibility, responsive, performance, cross-browser | **PASS WITH LIMITATION** (Firefox BLOCKED) | A11Y-01..22, RESP-01..06, XB-01/02/04, PERF-01..03 |
| G | Bảo mật phiên, CSRF, mạng lỗi, XSS, route lỗi | **PASS WITH LIMITATION** (RES-02 BL-22) | RES-01..11, AUTH-15/16, MSG-07/09, NTF-09, SOC-P21 |

---

## A — Guest khám phá, đăng nhập, duy trì phiên, đăng xuất

**Precondition:** H2 dev đã seed; guest chưa có cookie.

**Các bước:**
1. Mở `/feed` khi chưa đăng nhập → thấy bài công khai + CTA đăng nhập, không thấy composer.
2. Mở `/login`, kiểm tra form, skip link, nhãn; đăng nhập sai → lỗi tại chỗ, không chuyển trang.
3. Đăng nhập đúng → chuyển `/feed`, cookie `aloute_access_token` có `HttpOnly`.
4. F5 → phiên giữ; mở `/login` khi đã đăng nhập → chuyển `/feed`.
5. Đăng xuất qua menu → về `/login`; Back không lộ nội dung; API riêng tư trả 401.
6. Tab 2 dùng chung phiên; logout tab 1 → tab 2 bị chặn.
7. Cookie JWT bị sửa/hết hạn → coi là guest; JWT hết hạn ký bằng dev secret → 401.
8. Guest vào `/admin`, `/post/create` → kỳ vọng chuyển login.

**Kết quả:** bước 1–7 PASS. Bước 8 FAIL: server trả `401 application/json` thay vì redirect (BL-22).
**Bằng chứng:** `evidence/full-run-3-results.json`; ảnh `evidence/screenshots/auth-03-feed-after-login.png`, `auth-15-guest-post-create.png`, `auth-16-guest-admin.png`.

---

## B — Sinh viên: hồ sơ, bài viết, tương tác, poll, feed

**Precondition:** đăng nhập `user`; có bài seed của `demoan`.

**Các bước:**
1. Xem/sửa hồ sơ (tên hiển thị, bio) → giữ sau reload.
2. Xem hồ sơ người khác → không có nút sửa; follow/unfollow cập nhật trạng thái + bộ đếm.
3. Chuyển tab Bài viết/Phản hồi/Ảnh/Đã lưu.
4. Tạo bài text tiếng Việt → chuyển trang chi tiết.
5. Like/unlike, bookmark/unbookmark, repost, quote; kiểm tra bộ đếm, `aria-pressed`, API và `/saved`.
6. Bình luận; sửa/xóa bài của mình; báo cáo bài người khác.
7. Like nhanh liên tiếp → không tạo trạng thái sai; nháp được khôi phục sau khi rời trang.
8. Tạo poll nhiều lựa chọn, vote 2 đáp án; vote lặp bị từ chối; poll một lựa chọn không cho chọn 2.
9. Feed home/following/explore; phân trang 0-indexed; trang ngoài phạm vi có empty state.
10. Bài FOLLOWERS ẩn với guest/người không theo dõi; bài đã xóa không còn trong tìm kiếm.

**Kết quả:** PASS. Upload avatar/cover BLOCKED (thiếu Firebase service account); poll hết hạn và custom feed NOT RUN (xem 09).
**Bằng chứng:** `evidence/full-run-1-results.json`; ảnh `evidence/screenshots/social-post-created.png`, `social-quote.png`, `social-poll-result.png`.

---

## C — Cộng đồng đa vai trò và kiểm duyệt

**Precondition:** 3 phiên: owner, member, outsider; cộng đồng public/private.

**Các bước:**
1. Guest xem danh sách cộng đồng, không thấy nút tạo/quản lý.
2. Tạo cộng đồng public → chuyển trang chi tiết; slug trùng → 409 + thông báo tiếng Việt.
3. Member join/leave; đếm thành viên đổi; member đăng bài gắn đúng community.
4. Owner bật duyệt bài; member thấy trạng thái chờ; ADMIN duyệt/từ chối kèm lý do; gỡ bài đã duyệt.
5. Owner thăng member lên ADMIN; member thường bị chặn khỏi trang quản lý.
6. Owner xóa thành viên; thành viên mất quyền.
7. Private community ẩn với guest/outsider.
8. XSS trong tên/mô tả được escape; tìm kiếm cộng đồng theo từ khóa.
9. Owner xóa cộng đồng → trang chi tiết 404.

**Kết quả:** PASS. Bước private join BLOCKED: chưa có API/đặc tả join-request.
**Bằng chứng:** `evidence/full-run-1-results.json`; ảnh `evidence/screenshots/com-02-created.png`, `com-11-approved.png`.

---

## D — Tin nhắn realtime và trung tâm thông báo

**Precondition:** `user` và `admin` có hội thoại seed.

**Các bước:**
1. Guest gọi API hội thoại → 401/403 JSON.
2. `/messages` tải danh sách qua REST; deep link `/messages/{id}` mở đúng lịch sử.
3. Gửi tin qua UI → bubble outgoing, input xóa; submit rỗng/vượt 2000 ký tự bị chặn.
4. XSS trong tin nhắn được escape; không có `img` chèn hay script chạy.
5. Tin mới xuất hiện qua WebSocket không reload; unread tăng khi rời trang, giảm khi đọc.
6. Người ngoài không đọc/gửi được; hội thoại không tồn tại báo lỗi tiếng Việt.
7. Thông báo LIKE/FOLLOW/REPLY sinh từ tương tác thật; lọc, đánh dấu đọc, xóa, empty state; preview REPLY escape HTML.

**Kết quả:** PASS (sau khi sửa flake locator MSG-07 trong bộ test; sản phẩm không lỗi).
**Bằng chứng:** `evidence/full-run-4-results.json`; ảnh `evidence/screenshots/msg-04-sent.png`, `msg-10-realtime.png`, `ntf-03-notifications.png`.

---

## E — Admin kiểm duyệt nội dung

**Precondition:** `admin`; có report PENDING từ `demobinh`.

**Các bước:**
1. `/admin` hiển thị KPI; `/admin/users` tìm theo username; `/admin/posts` tải danh sách.
2. User thường gọi API admin → 403; vào trang `/admin` → kỳ vọng HTML/redirect.
3. Mở report PENDING, nhận xử lý; từ chối qua modal; gỡ bài kèm lý do → bài ẩn công khai.
4. Khôi phục bài → hiển thị lại; nhật ký moderation ghi cả hai hành động.
5. ID không tồn tại → trạng thái rỗng; lọc trạng thái bài hoạt động.

**Kết quả:** PASS, trừ ADM-05: non-admin nhận `403 application/json` thay vì trang HTML (BL-22).
**Bằng chứng:** `evidence/full-run-4-results.json`; ảnh `evidence/screenshots/adm-01-overview.png`, `adm-08-post-removed.png`.

---

## F — Accessibility, responsive, hiệu năng, cross-browser

**Các bước:**
1. Axe quét 20 route (guest/user/admin) theo WCAG 2.0/2.1/2.2 AA.
2. Kiểm tra cấu trúc (lang, title, một main, h1, skip link) và bàn phím (Tab/Enter).
3. Đo 360×800, 768×1024, 1440×900: tràn ngang, sidebar, offcanvas, touch target 44×44.
4. Smoke 4 engine: Chrome, Edge, Firefox, WebKit.
5. Đo DCL/load/LCP 3 lần; p95 API; tài nguyên CSS.

**Kết quả:** axe 0 vi phạm serious/critical; responsive PASS; Chrome/Edge/WebKit PASS; Firefox BLOCKED; performance đạt ngân sách local.
**Bằng chứng:** `evidence/axe-results.json`, `evidence/cross-run-2-results.json`, `evidence/screenshots/resp-*`.

---

## G — Bảo mật phiên, CSRF, lỗi mạng, route lỗi

**Các bước:**
1. Guest vào feed thấy CTA, bấm đăng nhập → `/login`.
2. Route không tồn tại khi đã đăng nhập → kỳ vọng trang 404 HTML có link về feed.
3. API tài nguyên không tồn tại → JSON envelope 404.
4. Phiên hết hạn → API riêng tư 401 JSON.
5. POST thiếu CSRF header → 403; không tạo dữ liệu.
6. Mất mạng khi tải thông báo/hội thoại/lịch sử/gửi tin → thông báo tiếng Việt, giữ nháp, thử lại thành công.
7. Validation server → envelope 422, không tạo dữ liệu.
8. Đăng xuất → API sau đó bị chặn.

**Kết quả:** PASS, trừ RES-02 (404 page trả JSON — BL-22).
**Bằng chứng:** `evidence/full-run-1-results.json`; ảnh `evidence/screenshots/res-06-notifications-offline.png`.

---

## Tổng hợp

- 7/7 kịch bản đã chạy thực tế, không có kịch bản nào chỉ dừng ở kế hoạch.
- 4 FAIL đều cùng gốc BL-22 (backend content negotiation), đã có ticket.
- 3 BLOCKED do môi trường/đặc tả (Firebase, Firefox, private community).
- 3 NOT RUN do thiếu công cụ/contract (2FA setup, poll hết hạn, custom feed).
