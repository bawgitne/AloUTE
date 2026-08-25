# Hồ sơ người dùng

Profile thể hiện danh tính xã hội và các thiết lập riêng tư của User.

## Chức năng

- Xem profile công khai.
- Sửa display name, bio, ngày sinh, giới tính, trường/khoa và sở thích.
- Upload avatar và ảnh bìa lên Firebase Storage.
- Xem số bài viết, bạn bè, follower và following.
- Xem bài viết theo quyền riêng tư.
- Đổi mật khẩu và quản lý phiên đăng nhập.
- Cấu hình ai được gửi lời mời kết bạn, nhắn tin và xem thông tin cá nhân.

## Dữ liệu chính

| Trường | Quy tắc |
| --- | --- |
| `username` | 3–30 ký tự, duy nhất, chỉ chữ/số/dấu chấm/gạch dưới |
| `displayName` | 2–80 ký tự |
| `bio` | Tối đa 300 ký tự |
| `avatarUrl` | URL Firebase đã được hệ thống xác nhận |
| `profileVisibility` | `PUBLIC`, `FRIENDS`, `PRIVATE` |

## Endpoint dự kiến

| Method | Path | Chức năng |
| --- | --- | --- |
| GET | `/users/{username}` | Xem profile theo quyền |
| PATCH | `/me/profile` | Cập nhật hồ sơ |
| POST | `/me/avatar` | Upload avatar |
| POST | `/me/cover` | Upload ảnh bìa |
| PATCH | `/me/privacy` | Cập nhật quyền riêng tư |
| GET | `/me/sessions` | Xem các refresh session |
| DELETE | `/me/sessions/{id}` | Đăng xuất một thiết bị |

## Acceptance criteria

- User chỉ sửa được profile của mình.
- File cũ được đánh dấu/xóa an toàn sau khi upload file mới thành công.
- Profile phản ánh đúng quan hệ block và visibility.
- Không hiển thị email, ngày sinh hoặc dữ liệu nhạy cảm nếu chưa được cho phép.

