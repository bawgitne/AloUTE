# Git, phân công, lộ trình và demo

Tài liệu này giúp lấy điểm quản lý GitHub, báo cáo và trả lời câu hỏi.

## Git workflow

- `main`: phiên bản ổn định để demo.
- `develop`: nhánh tích hợp.
- Feature branch: `feature/authentication`, `feature/posts`, `feature/chat`.
- Fix branch: `fix/otp-expiration`, `fix/post-authorization`.
- Mỗi feature có GitHub Issue, acceptance criteria và người phụ trách.
- Merge qua Pull Request; ít nhất một thành viên khác review.
- Commit nhỏ, có ý nghĩa: `feat(auth): verify registration OTP`.

## Cách chia theo số thành viên

| Nhóm việc | Feature phù hợp |
| --- | --- |
| Identity | Authentication, authorization, security |
| Content | Profile, posts, engagement, Firebase media |
| Social | Social graph, feed/search, groups/events |
| Realtime | Chat, notifications, WebSocket |
| Governance | Moderation, admin dashboard, testing và seed data |

Nếu nhóm ít hơn năm người, gộp Social với Content và gộp Governance với Identity. Mỗi thành viên vẫn phải có commit backend, UI và test ở phần được phân công.

## Lộ trình đề xuất

| Giai đoạn | Kết quả |
| --- | --- |
| 1. Nền tảng | Project skeleton, PostgreSQL, Security, layout, CI |
| 2. Identity | Register/OTP/login/Google OIDC/refresh/logout/forgot password, role |
| 3. Core social | Profile, posts, media, reaction, comment, friendship |
| 4. Discovery/realtime | Feed score, search, presence, typing, chat, notification |
| 5. Community/governance | Group form, public/private, AutoMod, report, dashboard |
| 6. Security/showcase | Security Center, 2FA, Privacy Checkup, test, báo cáo |

Thứ tự chi tiết và quality gate bắt buộc nằm tại [delivery-stages.md](delivery-stages.md). Không làm feature stage sau khi stage hiện tại còn test đỏ hoặc lỗi security mức cao.

## Kịch bản demo tối đa điểm

1. Guest xem hơn 10 bài công khai nổi bật và tìm kiếm.
2. Guest đăng ký bằng OTP; một Guest khác dùng Sign in with Google.
3. User đăng nhập, sửa profile và đăng bài có media.
4. User khác tìm thấy tài khoản, kết bạn, reaction và comment.
5. Hai browser show online, typing, delivered/read và message request realtime.
6. Tạo private group bằng form onboarding; account khác xin vào và được duyệt.
7. Giải thích recommendation score và “Vì sao tôi thấy bài này?”.
8. Đăng keyword/mention spam để AutoMod warn hoặc quarantine.
9. Manager xử lý alert/report; Admin xem dashboard/audit log.
10. User mở Security Center, revoke session và chạy Privacy Checkup.
11. Mở mobile để chứng minh responsive.
12. Chạy test suite từng stage và mở lịch sử GitHub/PR.

## Ánh xạ tiêu chí chấm điểm

| Tiêu chí | Minh chứng |
| --- | --- |
| GitHub và phân công — 1 điểm | Issue, branch, PR, review, commit đều theo thành viên |
| Cấu trúc ba tầng — 0,5 điểm | Controller → Service → Repository, DTO rõ ràng |
| Logic/validation/security — 6 điểm | Workflow hoàn chỉnh, ownership, role, OTP/JWT, test |
| UI responsive — 1 điểm | Mobile-first, loading/error/empty states, accessibility |
| Sáng tạo — 1 điểm | WebSocket, feed scoring, JWT, moderation/audit |
| Báo cáo — 0,5 điểm | Demo xuyên suốt, sơ đồ, test result, mỗi người hiểu phần mình |

## Nội dung báo cáo theo vai trò

- Guest + User: đăng ký/OTP, profile, post, interaction, social graph, feed, chat.
- Manager: report workflow, moderation action, thống kê nội dung.
- Admin: role, account lifecycle, configuration, dashboard và audit.
- Phần chung: kiến trúc, database, security, UI responsive, testing và GitHub process.

## Câu hỏi bảo vệ cần trả lời được

- Vì sao chọn PostgreSQL và Firebase Storage thay vì nhiều database?
- Tại sao JWT đặt trong HttpOnly cookie và vẫn cần CSRF protection?
- Service kiểm tra ownership như thế nào để chống IDOR?
- Message được lưu và phát WebSocket theo thứ tự nào để không mất dữ liệu?
- Feed score hoạt động ra sao và xử lý quyền riêng tư ở đâu?
- Manager khác Admin ở quyền và trách nhiệm nào?
- Vì sao dùng soft delete và audit log cho nội dung bị kiểm duyệt?
