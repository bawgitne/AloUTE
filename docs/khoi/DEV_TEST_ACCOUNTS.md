# Tài khoản test local (profile `dev`)

Hai tài khoản này chỉ được tự tạo khi Spring chạy với profile `dev`.

| Quyền | Email | Mật khẩu | Role |
|---|---|---|---|
| USER | `user@aloute.local` | `User@123` | `USER` |
| ADMIN | `admin@aloute.local` | `Admin@123` | `SYSTEM_ADMIN` |

- Trang đăng nhập: `http://localhost:8080/login`
- Trang đăng ký: `http://localhost:8080/register`
- Sau khi đăng nhập thành công: chuyển tới `/feed`.
- Các nút giao diện chưa có backend sẽ hiện thông báo thay vì im lặng hoặc chuyển sang URL lỗi.
- Secret JWT trong `application-dev.yml` chỉ phục vụ local/dev. Khi deploy thật phải override bằng biến môi trường.
