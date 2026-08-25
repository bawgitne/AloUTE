# Kiểm thử

Mục tiêu là chứng minh business logic, validation, security và realtime hoạt động ổn định.

## Chiến lược

| Loại test | Phạm vi | Công cụ |
| --- | --- | --- |
| Unit test | Service, ranking, validator, permission | JUnit 5, Mockito |
| Repository test | Query, constraint, pagination | `@DataJpaTest`, Testcontainers PostgreSQL |
| MVC/API test | Validation, status, CSRF, role | MockMvc, Spring Security Test |
| Integration test | OTP, friendship, moderation, message | Spring Boot Test, Testcontainers |
| UI smoke test | Luồng demo chính | Playwright/Selenium nếu nhóm đủ thời gian |

## Test case bắt buộc

- Đăng ký → OTP đúng/sai/hết hạn/dùng lại.
- Login tài khoản active/pending/suspended; refresh token rotation.
- Google OIDC state/nonce, callback, account linking an toàn và email trùng.
- User A không sửa/xóa post của User B.
- Visibility public/friends/private và block.
- Lời mời kết bạn trùng/chéo và block đang có friendship.
- Reaction idempotent; comment vào bài không có quyền.
- Feed không lộ nội dung private/blocked/deleted.
- WebSocket không cho subscribe hội thoại của người khác.
- Reconnect lấy đủ message/notification bị lỡ.
- Online nhiều tab, heartbeat stale, typing throttle/timeout và read-receipt privacy.
- Group public/private/hidden, onboarding form và invite link hết hạn.
- Recommendation không lộ private/blocked/quarantined post dù score cao.
- AutoMod Unicode/regex/spam, false positive, quarantine và appeal.
- TOTP setup/replay/backup-code/recovery và recent authentication.
- Manager xử lý report tạo moderation action và audit log.
- Manager không đổi role; Admin có thể đổi role hợp lệ.
- Upload sai MIME, quá dung lượng và filename nguy hiểm.

## Dữ liệu test và demo

- Seed tối thiểu 30 User, 100 Post, 10 hashtag, 5 nhóm và 5 sự kiện.
- Có tài khoản riêng cho Guest flow, User, Manager và Admin.
- Dữ liệu không dùng email/mật khẩu thật.
- Script seed có thể chạy lại mà không tạo dữ liệu trùng.

## Điều kiện đạt

- Test nghiệp vụ cốt lõi chạy tự động trong CI.
- Không có test phụ thuộc thứ tự chạy.
- Báo cáo test thể hiện số test pass/fail; lỗi nghiêm trọng phải được xử lý trước demo.
