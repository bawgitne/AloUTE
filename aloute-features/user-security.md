# Trung tâm bảo mật người dùng

Security Center gom bảo mật tài khoản, session, 2FA, recovery và privacy vào một màn hình có security score giải thích được.

## Security score

| Hạng mục | Điểm | Điều kiện |
| --- | ---: | --- |
| Email verified | 20 | OTP hoặc provider verified |
| Mật khẩu mạnh | 15 | Có local password đạt policy |
| Google identity linked | 10 | Có phương thức đăng nhập dự phòng |
| TOTP 2FA enabled | 25 | Đã verify TOTP challenge |
| Backup codes saved | 10 | Đã tạo và xác nhận lưu |
| Recent session review | 10 | Xem session trong 30 ngày |
| Privacy Checkup | 10 | Hoàn tất checklist hiện hành |

Score chỉ hướng dẫn, không khẳng định tài khoản an toàn tuyệt đối.

## TOTP và recovery

- TOTP secret được mã hóa at rest; bật 2FA yêu cầu recent authentication, scan QR và nhập mã hợp lệ.
- Sinh 10 backup code dùng một lần; database chỉ lưu hash.
- Tắt 2FA yêu cầu re-auth + TOTP hoặc backup code.
- OTP email dành cho recovery; không thay TOTP thường xuyên khi 2FA đã bật.
- Rate limit challenge; không nhận lại cùng TOTP time-step đã dùng.

## Session và login activity

- Hiển thị browser/device, IP rút gọn, vị trí gần đúng, login/last active và phương thức login.
- Revoke một session hoặc tất cả thiết bị khác.
- Đổi/reset password, ban hoặc refresh-token reuse sẽ revoke toàn bộ session.
- Thiết bị/IP bất thường tạo email và in-app alert có nút “Không phải tôi”.
- Nút đó revoke session, rotate credential và dẫn tới đổi mật khẩu.

## Privacy Checkup

1. Ai xem profile, email, ngày sinh và friend list.
2. Audience mặc định và review các post public gần đây.
3. Ai gửi friend/message request và xem online/last seen.
4. Typing indicator và read receipt.
5. Blocked, restricted và muted list.
6. Google account đã link và session đang hoạt động.
7. Download data, deactivate hoặc delete account.

## Block, restrict và mute

| Chế độ | Ảnh hưởng |
| --- | --- |
| Block | Không xem profile riêng, kết bạn, mention, chat hoặc presence |
| Restrict | Comment chờ duyệt; message vào request; không thấy read/online |
| Mute | Vẫn kết nối nhưng không nhận feed/notification từ đối tượng |

## Data control và anti-abuse

- Activity Log cho post, reaction, comment, login và moderation action liên quan.
- Export bất đồng bộ bằng link ký số hết hạn và yêu cầu re-auth.
- Deactivate cho khôi phục; delete có grace period rồi anonymize/xóa theo chính sách.
- Cảnh báo link rút gọn/domain đáng ngờ; không render preview URL nguy hiểm.
- Rate limit follow, friend request, message request, mention và report.
- CAPTCHA chỉ xuất hiện sau hành vi bất thường.

## Acceptance criteria

- Action nhạy cảm yêu cầu recent authentication trong 10 phút.
- TOTP secret/backup codes không trả lại sau thiết lập.
- Revoke session ngắt refresh token và WebSocket connection.
- Privacy được enforce tại query/service, không chỉ UI.
- Audit event không chứa secret, OTP, JWT hoặc full IP không cần thiết.

