# AloUTE — Luồng màn hình

Tài liệu mô tả luồng điều hướng hiện hành giữa các trang Thymeleaf và REST API tương ứng. Route lấy từ các Page Controller trong `src/main/java/com/aloute/controller/page/`.

## 1. Khách (guest)

```text
/feed (công khai)
  ├── xem bài viết, chủ đề, tìm kiếm, cộng đồng công khai
  ├── chọn Đăng nhập  → /login
  └── chọn Đăng ký    → /register
```

- Guest xem được feed công khai, chi tiết bài công khai, topic, search, community công khai.
- Hành động ghi (đăng bài, like, bình luận, theo dõi, nhắn tin) hiển thị CTA đăng nhập thay vì form.
- Route bảo vệ chuyển hướng về `/login`; API riêng tư trả `401` JSON.

## 2. Xác thực tài khoản

```text
/register → gửi OTP → /verify-otp → kích hoạt tài khoản → /login
/login
  ├── thành công → /feed
  ├── sai thông tin → hiển thị lỗi tại chỗ
  ├── quá số lần → 429 + thông báo khóa tạm thời
  └── tài khoản bật 2FA → nhập TOTP/backup code → /feed
/forgot-password → gửi OTP/link → /reset-password → /login
```

- Đăng nhập dùng JWT trong cookie `HttpOnly`.
- Thiết lập/ tắt 2FA và tạo backup code nằm trong `/settings/security`.

## 3. Người dùng đã đăng nhập

| Luồng | Route | Hành vi chính |
| :--- | :--- | :--- |
| Bảng tin | `/feed`, `/feed/posts` | Feed cá nhân, infinite scroll, lọc theo topic, tương tác bài viết |
| Soạn bài | `/post/create` | Nội dung, media, poll, topic, phạm vi, community |
| Chi tiết bài | `/posts/{id}` | Nội dung, poll, reply, quote, report, edit/delete theo quyền |
| Bài đã lưu | `/saved` | Danh sách bookmark của người dùng |
| Hồ sơ | `/users/{username}` | Bài viết, reply, media, follow/unfollow, block, nhắn tin |
| Bạn bè | `/friends` | Danh sách following/followers |
| Tìm kiếm | `/search` | People, posts, topics, communities |
| Cộng đồng | `/communities`, `/c/{slug}` | Danh sách, chi tiết, join/leave, feed của cộng đồng |
| Quản trị cộng đồng | `/communities/{id}/manage/*` | Tổng quan, thành viên, bài chờ duyệt, bài viết, cài đặt |
| Tin nhắn | `/messages`, `/messages/{id}` | Hội thoại, lịch sử, gửi tin qua REST + STOMP |
| Thông báo | `/notifications` | Danh sách, lọc chưa đọc, đánh dấu đã đọc/xóa |
| Cài đặt | `/settings/{section}` | Hồ sơ, bảo mật, quyền riêng tư, thông báo, giao diện, chặn/mute |
| Chủ đề | `/topics/{slug}` | Bài viết theo topic, theo dõi topic |

### Tương tác bài viết

```text
Xem bài → Like / Reply / Repost / Quote / Bookmark / Report
        → cập nhật UI và số đếm qua API
        → hành động không hợp lệ trả 401/403/404/409/422 và hiển thị lỗi
```

## 4. Quản trị hệ thống

Chỉ tài khoản `SYSTEM_ADMIN` truy cập được `/admin/**`:

| Trang | Route | Chức năng |
| :--- | :--- | :--- |
| Tổng quan | `/admin` | KPI, hoạt động gần đây |
| Người dùng | `/admin/users`, `/admin/users/{id}` | Tìm kiếm, chi tiết, đình chỉ/kích hoạt, cảnh báo, đặt lại hồ sơ, đổi global role |
| Bài viết | `/admin/posts`, `/admin/posts/{id}` | Tra cứu, gỡ/khôi phục bài, audit |
| Báo cáo | `/admin/reports`, `/admin/reports/{id}` | Nhận xử lý, quyết định, gỡ bài liên quan |
| Nhật ký | `/admin/moderation-actions` | Lịch sử kiểm duyệt |

Hành động quản trị yêu cầu lý do, được ghi audit và tuân theo capabilities/back-end policy; UI không thay thế kiểm tra quyền phía server.

## 5. Điều hướng chung

- Navbar: tìm kiếm, thông báo, tin nhắn, menu tài khoản.
- Sidebar trái: feed, profile, community, friends, saved, tạo bài.
- Sidebar phải (desktop): thông tin bổ sung.
- Mobile: offcanvas navigation, layout một cột, touch target tối thiểu 44px.
- Skip link `#main-content` là phần tử focus đầu tiên ở các layout công khai.
