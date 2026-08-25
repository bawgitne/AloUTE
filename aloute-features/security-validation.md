# Validation và security

Tài liệu này là checklist bắt buộc xuyên suốt mọi feature.

## Authentication security

- Hash mật khẩu bằng BCrypt/Argon2; không tự viết thuật toán mã hóa.
- OTP 6 số, hết hạn 5 phút, tối đa 5 lần thử và chỉ dùng một lần.
- Access token 15 phút; refresh token rotation và revoke.
- Cookie JWT dùng `HttpOnly`, `Secure`, `SameSite=Lax`.
- Bật CSRF cho request dùng cookie; WebSocket kiểm tra origin và principal.
- Rate limit login, OTP, reset password, search và upload.
- Google OIDC dùng Authorization Code flow; validate state/nonce/issuer/audience/expiry và link account an toàn.
- Action nhạy cảm yêu cầu recent authentication; hỗ trợ TOTP 2FA và backup code chỉ lưu hash.

## Authorization security

- Kiểm tra role bằng Spring Security.
- Kiểm tra ownership và quyền xem tại Service.
- Không tin `userId`, `role` hoặc owner từ request client.
- Tránh IDOR cho post, profile, report, message và notification.
- Quan hệ block được áp dụng thống nhất ở tất cả module.
- Privacy của online/last seen, typing và read receipt được enforce ở server/WebSocket.

## Input và output

- DTO dùng Bean Validation; không bind trực tiếp request vào Entity.
- Escape/sanitize user-generated content để chống stored XSS.
- JPA parameter binding, không nối chuỗi SQL.
- Có allowlist MIME và kiểm tra magic bytes cho upload.
- Tạo tên file ngẫu nhiên; không dùng nguyên filename của client làm storage path.
- Không trả stack trace và thông tin nội bộ cho client.

## Validation mẫu

| Input | Quy tắc |
| --- | --- |
| Email | Đúng định dạng, tối đa 254 ký tự, chuẩn hóa lowercase |
| Password | 8–72 ký tự, có chữ hoa, chữ thường, số; cho phép ký tự đặc biệt |
| Username | 3–30 ký tự, pattern cho phép, unique case-insensitive |
| Post | Có text hoặc media; text tối đa 5.000 ký tự |
| Comment | Có text hoặc media; text tối đa 2.000 ký tự |
| Upload | Đúng MIME/magic bytes và không vượt dung lượng |

## Audit và secret

- Log login thất bại, đổi role, ban/unban, moderation và export.
- Không log password, OTP, JWT, cookie hoặc private key.
- Secret lấy từ environment variables hoặc secret manager.
- `.env`, Firebase credential và file cấu hình local phải nằm trong `.gitignore`.

## Acceptance criteria

- Test 401, 403, IDOR, XSS input, upload giả MIME và token hết hạn.
- Dependency scan không có lỗ hổng nghiêm trọng chưa giải trình.
- Error response có mã lỗi ổn định và message thân thiện.
- Test OAuth account-linking, TOTP replay, WebSocket subscription, AutoMod ReDoS và recommendation privacy leak.
