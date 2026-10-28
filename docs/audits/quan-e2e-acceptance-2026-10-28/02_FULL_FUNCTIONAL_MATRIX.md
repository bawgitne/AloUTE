# 02 — Full Functional Acceptance Matrix

**Truy vết:** Yêu cầu/chức năng → UI → route → API → test case → bằng chứng → trạng thái.

- Trạng thái: `PASS`, `FAIL`, `BLOCKED`, `NOT RUN`, `PASS-WL` (PASS nhưng còn case con BLOCKED/NOT RUN), `GAP` (chưa có case chuyên biệt).
- Bằng chứng gốc: thư mục `test-results/quan-e2e-acceptance/<run>/` (results.json + screenshots).
- Run chuẩn theo module: auth `full-run-3`; social/community/resilience `full-run-1`; messaging/admin `full-run-4`; cross-a11y `cross-run-2`.

## 1. Xác thực & tài khoản (auth)

| # | Chức năng | UI | Route | API | Case | Trạng thái |
|---|---|---|---|---|---|---|
| 1.1 | Guest xem feed công khai, thấy CTA đăng nhập | feed + CTA | `/`, `/feed` | `GET /api/feed` | AUTH-01 | PASS |
| 1.2 | Trang login render form, skip link, nhãn | form đăng nhập | `/login` | — | AUTH-02 | PASS |
| 1.3 | Đăng nhập đúng → `/feed`, cookie HttpOnly | form + redirect | `/login` → `/feed` | `POST /api/auth/login` | AUTH-03, AUTH-08 | PASS |
| 1.4 | Sai mật khẩu/rỗng báo lỗi tại chỗ | `#auth-status` | `/login` | `POST /api/auth/login` | AUTH-04, AUTH-05 | PASS |
| 1.5 | Guest vào route bảo vệ → `/login` | redirect | `/messages`, `/notifications`, `/saved`, `/settings`, `/friends`, `/profile` | — | AUTH-06 | PASS |
| 1.6 | Guest vào page bảo vệ nhận HTML/redirect | — | `/post/create`, `/admin` | — | AUTH-15, AUTH-16 | **FAIL** (BL-22: 401 JSON) |
| 1.7 | Guest gọi API riêng tư → JSON 401 | — | — | `/api/auth/me`, `/api/notifications`, `/api/conversations` | AUTH-07, MSG-01, NTF-01 | PASS |
| 1.8 | Đăng xuất qua menu, cookie thu hồi | menu tài khoản | `/feed` | `POST /api/auth/logout` | AUTH-09 | PASS |
| 1.9 | Back sau logout không lộ nội dung | — | — | — | AUTH-10 | PASS |
| 1.10 | Đa tab dùng chung phiên; logout chặn tab còn lại | — | — | — | AUTH-11 | PASS |
| 1.11 | JWT bị sửa bị coi là guest | — | — | `GET /api/auth/me` | AUTH-12 | PASS |
| 1.12 | JWT hết hạn bị từ chối | — | `/settings` | `GET /api/auth/me` | AUTH-13 | PASS |
| 1.13 | Route công khai không 500 khi guest | — | `/`, `/feed`, `/posts/1`, `/topics/hoc-tap`, `/search`, `/communities` | — | AUTH-14 | PASS |
| 1.14 | Đăng ký + validation + email trùng | form đăng ký | `/register` | `POST /api/auth/register` | AUTH-17 | PASS |
| 1.15 | OTP đăng ký đúng/sai | form OTP | `/verify-otp` | `POST /api/auth/verify-registration-otp` | AUTH-18 | PASS |
| 1.16 | Resend OTP có cooldown | nút gửi lại | `/verify-otp` | `POST /api/auth/resend-registration-otp` | AUTH-19 | PASS |
| 1.17 | Quên mật khẩu trả thông báo generic | form | `/forgot-password` | `POST /api/auth/forgot-password` | AUTH-20 | PASS |
| 1.18 | Reset password thiếu token bị chặn | form | `/reset-password` | `POST /api/auth/reset-password` | AUTH-21 | PASS |
| 1.19 | JS tắt: login/register/feed vẫn dùng được | form + link thật | `/login`, `/register` | — | AUTH-22, AUTH-23 | PASS |
| 1.20 | CSRF: POST thiếu `X-XSRF-TOKEN` bị từ chối | — | — | mọi POST cookie-auth | AUTH-24, RES-05 | PASS |
| 1.21 | Không rò rỉ token/OTP trong HTML công khai | — | — | — | AUTH-25 | PASS |
| 1.22 | 2FA: seed login trực tiếp, không lộ section | — | `/login` | — | AUTH-26 | PASS |
| 1.23 | 2FA setup/verify đầy đủ | — | — | ngoài OpenAPI canonical | AUTH-26b | NOT RUN |

## 2. Hồ sơ, quan hệ, bạn bè

| # | Chức năng | UI | Route | API | Case | Trạng thái |
|---|---|---|---|---|---|---|
| 2.1 | Hồ sơ cá nhân hiển thị tên/username/tab | profile | `/profile` | `GET /api/users/me/profile` | SOC-P01 | PASS |
| 2.2 | Chỉnh sửa tên hiển thị + bio, giữ sau reload | form edit | `/profile` | `PATCH /api/users/me/profile` | SOC-P02 | PASS |
| 2.3 | Hồ sơ người khác: không nút sửa, có follow/nhắn tin | profile | `/users/demoan` | `GET /api/users/{id}` | SOC-P03 | PASS |
| 2.4 | Follow/unfollow cập nhật trạng thái + bộ đếm | nút follow | `/users/demoan` | `PUT/DELETE /api/social/follows/{id}` | SOC-P04 | PASS |
| 2.5 | Tab Bài viết/Phản hồi/Ảnh/Đã lưu | tab hồ sơ | `/profile` | `GET .../posts`, `/api/posts/bookmarks` | SOC-P05 | PASS |
| 2.6 | Upload avatar/cover | form upload | `/profile` | Firebase Storage | SOC-P06 | BLOCKED |
| 2.7 | Trang bạn bè render, không lỗi | `/friends` | `/friends` | — | AUTH-06, A11Y-12 | PASS (render/a11y) |
| 2.8 | Hành vi kết bạn/xóa bạn chuyên biệt | — | `/friends` | — | — | GAP (chưa có case riêng) |

## 3. Bài viết & tương tác

| # | Chức năng | UI | Route | API | Case | Trạng thái |
|---|---|---|---|---|---|---|
| 3.1 | Tạo bài text tiếng Việt | composer | `/feed`, `/post/create` | `POST /api/posts` | SOC-P10 | PASS |
| 3.2 | Like/unlike + bộ đếm + aria-pressed | interaction bar | `/feed`, `/posts/{id}` | `PUT/DELETE /api/posts/{id}/likes/me` | SOC-P11 | PASS |
| 3.3 | Like nhanh liên tiếp không sai trạng thái | interaction bar | `/feed` | like API | SOC-P12 | PASS |
| 3.4 | Bookmark/unbookmark + `/saved` | interaction bar | `/feed`, `/saved` | `PUT/DELETE .../bookmarks/me` | SOC-P13 | PASS |
| 3.5 | Bình luận, tăng bộ đếm, giữ sau reload | reply form | `/posts/{id}` | `POST /api/posts/{id}/replies` | SOC-P14 | PASS |
| 3.6 | Quote post + quoteCount + bài gốc | modal quote | `/feed` | `POST /api/posts/{id}/quotes` | SOC-P15 | PASS |
| 3.7 | Repost bật/tắt | interaction bar | `/feed` | `PUT/DELETE .../reposts/me` | SOC-P16 | PASS |
| 3.8 | Sao chép permalink | nút link | `/feed` | — | SOC-P17 | PASS |
| 3.9 | Sửa bài của mình, giữ sau reload | menu bài | `/posts/{id}` | `PATCH /api/posts/{id}` | SOC-P18 | PASS |
| 3.10 | Báo cáo bài qua modal | modal report | `/posts/{id}` | `POST /api/reports` | SOC-P19 | PASS |
| 3.11 | Nháp soạn bài khôi phục | composer | `/feed` | localStorage | SOC-P20 | PASS |
| 3.12 | XSS nội dung bài được escape | feed/detail | `/posts/{id}` | — | SOC-P21 | PASS |
| 3.13 | Xóa bài của mình | menu bài | `/posts/{id}` | `DELETE /api/posts/{id}` | SOC-P22 | PASS |
| 3.14 | Guest không tương tác qua API | — | — | like/bookmark/repost/vote | SOC-P23 | PASS |
| 3.15 | Bài deleted not found | detail | `/posts/999999` | `GET /api/posts/999999` | SOC-P51, RES-03 | PASS |
| 3.16 | Visibility FOLLOWERS ẩn đúng | detail/feed | `/posts/{id}` | feed | SOC-P50 | PASS |

## 4. Feed, tìm kiếm, chủ đề

| # | Chức năng | UI | Route | API | Case | Trạng thái |
|---|---|---|---|---|---|---|
| 4.1 | Feed home/following/explore đúng quyền | feed tabs | `/feed` | `/api/feed/home`, `/following`, `/explore` | SOC-P30 | PASS |
| 4.2 | Phân trang 0-indexed | pagination | `/feed?page=` | `GET /api/posts?page=` | SOC-P31 | PASS |
| 4.3 | Composer cho user, CTA cho guest | feed header | `/feed` | — | SOC-P32 | PASS |
| 4.4 | Trang ngoài phạm vi → empty state | pagination | `/feed?page=99` | GET page | SOC-P33 | PASS |
| 4.5 | Trang chủ đề render + a11y | topic | `/topics/hoc-tap` | `GET /api/topics/{id}/posts` | A11Y-11, AUTH-14 | PASS |
| 4.6 | Tìm kiếm cơ bản + a11y | search | `/search?q=aloute` | `GET /api/posts?q=` | A11Y-14, SOC-P51 | PASS |
| 4.7 | Bộ lọc tìm kiếm nâng cao (loại nội dung, người dùng) | — | `/search` | — | — | GAP |
| 4.8 | Custom feed đầy đủ | — | — | `GET /api/feeds` (stub) | SOC-P52 | NOT RUN |

## 5. Poll

| # | Chức năng | UI | Route | API | Case | Trạng thái |
|---|---|---|---|---|---|---|
| 5.1 | Tác giả chưa vote thấy form | poll card | `/posts/{id}` | `GET /api/polls/{id}` | SOC-P40 | PASS |
| 5.2 | Vote thành công, giữ sau refresh | poll card | `/posts/{id}` | `POST /api/polls/{id}/votes` | SOC-P40 | PASS |
| 5.3 | Vote lặp bị từ chối | — | — | vote API | SOC-P41 | PASS |
| 5.4 | Guest vote bị chặn | — | — | vote API | SOC-P42 | PASS |
| 5.5 | Option không tồn tại bị từ chối | — | — | vote API | SOC-P43 | PASS |
| 5.6 | Tạo poll nhiều lựa chọn, vote 2 đáp án | composer poll | `/feed` | `POST /api/posts` | SOC-P44 | PASS |
| 5.7 | Poll thiếu phương án bị validation | composer poll | `/feed` | — | SOC-P45 | PASS |
| 5.8 | Poll một lựa chọn không cho chọn 2 | poll UI | `/feed` | — | SOC-P46 | PASS |
| 5.9 | Poll hết hạn | — | — | — | SOC-P47 | NOT RUN |

## 6. Cộng đồng

| # | Chức năng | UI | Route | API | Case | Trạng thái |
|---|---|---|---|---|---|---|
| 6.1 | Guest xem danh sách, không nút tạo/quản lý | list | `/communities` | `GET /api/communities` | COM-01 | PASS |
| 6.2 | Tạo cộng đồng public | form tạo | `/communities` | `POST /api/communities` | COM-02 | PASS |
| 6.3 | Slug trùng → 409 + thông báo Việt | form tạo | `/communities` | `POST /api/communities` | COM-03 | PASS |
| 6.4 | Validation tên/slug rỗng | form tạo | `/communities` | — | COM-04 | PASS |
| 6.5 | Guest xem community public + CTA login | detail | `/c/{slug}` | `GET /api/communities/{id}` | COM-05 | PASS |
| 6.6 | Join/leave, đếm thành viên | nút join | `/c/{slug}` | `POST/DELETE .../members/me` | COM-06 | PASS |
| 6.7 | Đăng bài trong community | composer | `/c/{slug}` | `POST /api/communities/{id}/posts` | COM-07 | PASS |
| 6.8 | Bật duyệt bài, member thấy chờ duyệt | settings/manage | `/communities/{id}/manage` | `PATCH /api/communities/{id}` | COM-08 | PASS |
| 6.9 | Thăng member lên ADMIN | manage members | `/communities/{id}/manage/members` | `PATCH .../members/{userId}/role` | COM-09 | PASS |
| 6.10 | Member thường bị chặn manage | — | manage | — | COM-10 | PASS |
| 6.11 | Duyệt/từ chối bài chờ | manage pending | `/communities/{id}/manage/pending` | approve/reject | COM-11, COM-12 | PASS |
| 6.12 | Gỡ bài đã duyệt | manage posts | `/communities/{id}/manage/posts` | remove | COM-13 | PASS |
| 6.13 | Private community ẩn với guest/ngoài | detail | `/c/{slug}` | `GET /api/communities/{id}` | COM-14 | PASS |
| 6.14 | Join private community | — | — | `POST .../join` | COM-14b | BLOCKED |
| 6.15 | Owner xóa thành viên | manage members | manage members | `DELETE .../members/{userId}` | COM-15 | PASS |
| 6.16 | XSS tên/mô tả escape | list/detail | `/communities` | — | COM-16 | PASS |
| 6.17 | Tìm kiếm cộng đồng | search | `/communities?q=` | `GET /api/communities?q=` | COM-17 | PASS |
| 6.18 | Guest join qua API bị chặn | — | — | join API | COM-18 | PASS |
| 6.19 | Owner xóa cộng đồng → 404 | detail | `/c/{slug}` | `DELETE /api/communities/{id}` | COM-19 | PASS |

## 7. Tin nhắn

| # | Chức năng | UI | Route | API | Case | Trạng thái |
|---|---|---|---|---|---|---|
| 7.1 | API hội thoại chặn guest | — | — | `GET /api/conversations` | MSG-01 | PASS |
| 7.2 | Danh sách hội thoại tải qua REST | list pane | `/messages` | `GET /api/conversations` | MSG-02 | PASS |
| 7.3 | Deep link mở đúng hội thoại | chat pane | `/messages/{id}` | `GET .../messages` | MSG-03 | PASS |
| 7.4 | Gửi tin, bubble outgoing, input xóa | composer | `/messages/{id}` | `POST .../messages` | MSG-04 | PASS |
| 7.5 | Submit rỗng không gửi | composer | `/messages/{id}` | — | MSG-05 | PASS |
| 7.6 | Giới hạn 2000 ký tự | composer | `/messages/{id}` | — | MSG-06 | PASS |
| 7.7 | XSS tin nhắn escape | bubble | `/messages/{id}` | — | MSG-07 | PASS |
| 7.8 | Lọc hội thoại phía client | search box | `/messages` | — | MSG-08 | PASS |
| 7.9 | Hội thoại không tồn tại báo lỗi Việt | error state | `/messages/999999` | GET | MSG-09 | PASS |
| 7.10 | Realtime WebSocket | chat pane | `/messages/{id}` | WS `/ws` | MSG-10 | PASS |
| 7.11 | Unread + read receipt | badge | `/messages` | `PATCH /read` | MSG-11 | PASS |
| 7.12 | Cách ly hội thoại | — | — | GET/POST messages | MSG-12 | PASS |
| 7.13 | Không lỗi console trên luồng chính | — | `/messages` | — | MSG-13 | PASS |

## 8. Thông báo

| # | Chức năng | UI | Route | API | Case | Trạng thái |
|---|---|---|---|---|---|---|
| 8.1 | API chặn guest | — | — | `/api/notifications` | NTF-01 | PASS |
| 8.2 | Sinh LIKE/FOLLOW/REPLY từ tương tác thật | badge/list | `/notifications` | — | NTF-02 | PASS |
| 8.3 | Danh sách + badge + 5 bộ lọc | list | `/notifications` | `GET /api/notifications` | NTF-03 | PASS |
| 8.4 | Lọc LIKE đúng | tab | `/notifications?filter=LIKE` | GET filter | NTF-04 | PASS |
| 8.5 | Đánh dấu một đã đọc | nút | `/notifications` | `PATCH /api/notifications/{id}/read` | NTF-05 | PASS |
| 8.6 | Đánh dấu tất cả đã đọc | nút | `/notifications` | `PATCH /api/notifications/read-all` | NTF-06 | PASS |
| 8.7 | Xóa một thông báo | nút | `/notifications` | `DELETE /api/notifications/{id}` | NTF-07 | PASS |
| 8.8 | Empty state theo bộ lọc | list | `/notifications` | GET filter | NTF-08 | PASS |
| 8.9 | Preview REPLY escape HTML | list | `/notifications` | — | NTF-09 | PASS |

## 9. Admin

| # | Chức năng | UI | Route | API | Case | Trạng thái |
|---|---|---|---|---|---|---|
| 9.1 | Tổng quan KPI | dashboard | `/admin` | `GET /api/admin/overview` | ADM-01 | PASS |
| 9.2 | Danh sách + tìm user | table | `/admin/users` | `GET /api/admin/users` | ADM-02 | PASS |
| 9.3 | Danh sách bài | table | `/admin/posts` | `GET /api/admin/posts` | ADM-03 | PASS |
| 9.4 | API admin chặn user thường | — | — | admin APIs | ADM-04 | PASS |
| 9.5 | Trang admin chặn user thường đúng UX | — | `/admin` | — | ADM-05 | **FAIL** (BL-22: 403 JSON) |
| 9.6 | Xem report PENDING, nhận xử lý | detail | `/admin/reports/{id}` | `POST .../start-review` | ADM-06 | PASS |
| 9.7 | Từ chối report qua modal | modal | `/admin/reports/{id}` | `POST .../decisions` | ADM-07 | PASS |
| 9.8 | Gỡ bài kèm lý do, ẩn công khai | modal | `/admin/reports/{id}` | `POST .../remove` | ADM-08 | PASS |
| 9.9 | Khôi phục bài, hiện lại | modal | `/admin/posts` | `POST .../restore` | ADM-09 | PASS |
| 9.10 | Nhật ký moderation | list | `/admin/moderation-actions` | `GET .../moderation-actions` | ADM-10 | PASS |
| 9.11 | ID không tồn tại → empty state | detail | `/admin/reports/999999` | GET | ADM-11 | PASS |
| 9.12 | Lọc trạng thái bài | tab | `/admin/posts?status=` | GET | ADM-12 | PASS |

## 10. Resilience & bảo mật

| # | Chức năng | UI | Route | API | Case | Trạng thái |
|---|---|---|---|---|---|---|
| 10.1 | Guest feed → CTA → login | feed | `/feed` | — | RES-01 | PASS |
| 10.2 | 404 page HTML có link feed | error page | `/khong-ton-tai-*` | — | RES-02 | **FAIL** (BL-22: JSON) |
| 10.3 | API 404 JSON envelope | — | — | `/api/posts/999999` | RES-03 | PASS |
| 10.4 | Phiên hết hạn → 401 JSON | — | — | `/api/auth/me` | RES-04 | PASS |
| 10.5 | Thiếu CSRF → 403 | — | — | POST | RES-05 | PASS |
| 10.6 | Mất mạng tải thông báo → lỗi Việt + retry | notifications | `/notifications` | GET | RES-06 | PASS |
| 10.7 | Mất mạng tải hội thoại → lỗi Việt + retry | messages | `/messages` | GET | RES-07 | PASS |
| 10.8 | Mất mạng tải lịch sử → lỗi Việt + retry | chat | `/messages/{id}` | GET | RES-08 | PASS |
| 10.9 | Gửi tin lỗi mạng giữ nháp, retry | composer | `/messages/{id}` | POST | RES-09 | PASS |
| 10.10 | Validation server 422 không tạo dữ liệu | — | — | POST | RES-10 | PASS |
| 10.11 | Logout qua UI chặn API sau đó | menu | — | logout | RES-11 | PASS |

## 11. Chất lượng phi chức năng

| # | Hạng mục | Phạm vi | Case | Trạng thái |
|---|---|---|---|---|
| 11.1 | Axe WCAG 2.0/2.1/2.2 AA | 20 route guest/user/admin | A11Y-01..20 | PASS (0 vi phạm) |
| 11.2 | Cấu trúc tài liệu | lang/title/main/h1/skip link | A11Y-21 | PASS |
| 11.3 | Bàn phím skip link | Tab/Enter | A11Y-22 | PASS |
| 11.4 | Responsive 360/768/1440 | feed, messages, admin | RESP-01..05 | PASS |
| 11.5 | Touch target ≥24 px | navbar + interaction bar | RESP-06 | PASS |
| 11.6 | Cross-browser | Chrome/Edge/WebKit | XB-01, XB-02, XB-04 | PASS |
| 11.7 | Cross-browser | Firefox | XB-03 | BLOCKED |
| 11.8 | Feed performance | DCL/load/LCP/transfer | PERF-01 | PASS |
| 11.9 | API latency p95 | feed, notifications, unread | PERF-02 | PASS |
| 11.10 | Static CSS | 200, content-type, cache | PERF-03 | PASS |

## 12. Tổng hợp trạng thái

| Trạng thái | Số case | Ghi chú |
|---|---|---|
| PASS | 155 | Toàn bộ luồng chính + phụ đã chạy browser thật |
| FAIL | 4 | AUTH-15, AUTH-16, ADM-05, RES-02 — cùng gốc BL-22 backend |
| BLOCKED | 3 | SOC-P06 (Firebase), COM-14b (đặc tả), XB-03 (Firefox) |
| NOT RUN | 3 | AUTH-26b (2FA contract), SOC-P47 (poll hết hạn), SOC-P52 (custom feed stub) |
| **Tổng** | **165** | |

## 13. Khoảng trống bao phủ (không tính là PASS)

- Hành vi kết bạn/xóa bạn, lời mời kết bạn trên `/friends` chưa có case chuyên biệt.
- Bộ lọc tìm kiếm nâng cao (theo người dùng/bài viết/hashtag) chưa có case.
- Upload media (avatar/cover/ảnh bài viết) bị chặn bởi Firebase.
- Trang quản lý cộng đồng chi tiết (settings, pending) mới kiểm tra qua luồng chính, chưa quét a11y toàn bộ.
- Trang admin detail/moderation-action detail chưa quét axe riêng.
- Trình đọc màn hình, zoom 200%, text spacing, reduced motion chưa kiểm tra.
- Tải đồng thời/nhiều người dùng thật, PostgreSQL, mạng throttled chưa kiểm tra.
