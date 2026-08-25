# Xác thực

Feature này quản lý đăng ký, OTP email, đăng nhập mật khẩu, Sign in with Google, đăng xuất, refresh token và quên mật khẩu.

## Use case

| Actor | Chức năng |
| --- | --- |
| Guest | Đăng ký, xác minh email, đăng nhập, gửi yêu cầu quên mật khẩu |
| User | Đăng xuất phiên hiện tại hoặc tất cả thiết bị, đổi mật khẩu |
| Admin | Thu hồi toàn bộ refresh token khi khóa tài khoản |

## Luồng đăng ký

1. Guest nhập email, username, display name và mật khẩu.
2. Server kiểm tra email/username duy nhất và chính sách mật khẩu.
3. Mật khẩu được hash bằng BCrypt hoặc Argon2.
4. Server tạo OTP 6 chữ số, chỉ lưu hash OTP và thời hạn 5 phút.
5. Email OTP được gửi; tài khoản ở trạng thái `PENDING_VERIFICATION`.
6. Guest nhập OTP; đúng thì chuyển trạng thái sang `ACTIVE`.
7. OTP hết hạn, đã dùng hoặc sai quá 5 lần đều bị từ chối.

## Luồng đăng nhập và token

- Access token sống 15 phút.
- Refresh token sống 7 ngày, được lưu hash trong database.
- Token gửi cho trình duyệt qua cookie `HttpOnly`, `Secure`, `SameSite=Lax`.
- Khi refresh, token cũ bị thu hồi và token mới được phát hành.
- Đăng xuất xóa cookie và thu hồi refresh token.

## Luồng quên mật khẩu

1. Guest nhập email; response luôn giống nhau dù email có tồn tại hay không.
2. Nếu tài khoản hợp lệ, hệ thống gửi OTP reset có thời hạn 5 phút.
3. OTP đúng tạo reset session dùng một lần.
4. Mật khẩu mới không được trùng mật khẩu hiện tại.
5. Sau khi đổi mật khẩu, thu hồi tất cả refresh token.

## Endpoint dự kiến

| Method | Path | Chức năng |
| --- | --- | --- |
| POST | `/auth/register` | Tạo tài khoản chờ xác minh |
| POST | `/auth/verify-email` | Xác minh OTP đăng ký |
| POST | `/auth/resend-otp` | Gửi lại OTP có rate limit |
| POST | `/auth/login` | Đăng nhập |
| POST | `/auth/refresh` | Đổi refresh token lấy access token |
| POST | `/auth/logout` | Đăng xuất phiên hiện tại |
| POST | `/auth/forgot-password` | Yêu cầu OTP reset |
| POST | `/auth/reset-password` | Xác minh và đặt mật khẩu mới |

## Sign in with Google

ALOUTE dùng Spring Security OAuth2 Client và OpenID Connect Authorization Code flow. Chỉ yêu cầu scope `openid`, `email`, `profile`; không xin quyền Gmail, Drive hoặc Calendar.

1. User bấm “Tiếp tục với Google”.
2. Server tạo `state`/`nonce` chống CSRF và replay rồi chuyển tới Google.
3. Google trả authorization code về redirect URI đã đăng ký.
4. Backend đổi code lấy ID token; xác minh chữ ký, issuer, audience, nonce và expiry.
5. Định danh bằng `(provider=GOOGLE, providerSubject=sub)`, không dùng email làm ID bất biến.
6. Nếu là User mới, tạo account `ACTIVE` vì email đã được provider xác minh.
7. Nếu email đã thuộc account local, không tự gộp: yêu cầu login account cũ hoặc OTP để link.
8. ALOUTE phát access/refresh token riêng; Google token không phải session của ALOUTE.

### Endpoint và dữ liệu

| Method | Path | Chức năng |
| --- | --- | --- |
| GET | `/oauth2/authorization/google` | Bắt đầu Google OIDC flow |
| GET | `/login/oauth2/code/google` | Callback do Spring Security xử lý |

`external_identities(id, user_id, provider, provider_subject, email_at_link_time, created_at)` có unique `(provider, provider_subject)`.

### Edge cases

- Email không được Google xác minh: từ chối.
- Account ALOUTE suspended/banned: vẫn từ chối dù Google thành công.
- Link/unlink provider yêu cầu recent authentication; chỉ unlink nếu còn cách đăng nhập khác.
- Không lưu Google access token nếu chỉ dùng Google để đăng nhập.

## Acceptance criteria

- Email và username không thể trùng, không phân biệt hoa thường.
- Tài khoản chưa xác minh, bị khóa hoặc bị đình chỉ không thể đăng nhập.
- Không lưu OTP, refresh token hoặc mật khẩu dạng rõ.
- Có rate limit theo email và IP cho login/OTP.
- Không để lộ email có tồn tại qua luồng quên mật khẩu.
- Google login validate `state`, `nonce`, `iss`, `aud`, `exp`, `sub`.
- Không thể account-takeover bằng cách tạo Google account trùng email local.
