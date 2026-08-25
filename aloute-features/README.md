# ALOUTE feature documentation

ALOUTE là mạng xã hội cộng đồng xây dựng bằng Spring Boot, Thymeleaf, Bootstrap, JPA, PostgreSQL, Firebase Storage, JWT và WebSocket.

## Mục tiêu

- Hoàn thành đúng mô hình ba tầng: Controller → Service → Repository.
- Cung cấp đủ nghiệp vụ cho Guest, User, Manager và Admin.
- Ưu tiên logic, validation, security và khả năng demo ổn định.
- Tạo điểm sáng bằng bảng tin cá nhân hóa, chat/notification thời gian thực và quy trình kiểm duyệt minh bạch.

## Phạm vi công nghệ

| Thành phần | Lựa chọn | Trách nhiệm |
| --- | --- | --- |
| Backend | Spring Boot | MVC, REST, validation và business logic |
| Giao diện | Thymeleaf + Bootstrap 5 | Server-rendered UI, responsive |
| Bảo mật | Spring Security + JWT | Xác thực, phân quyền, bảo vệ WebSocket |
| Dữ liệu | PostgreSQL + Spring Data JPA | Nguồn dữ liệu nghiệp vụ duy nhất |
| Media | Firebase Storage | Avatar, ảnh, video, file chat |
| Realtime | Spring WebSocket + STOMP | Chat, trạng thái online, notification |
| Email | JavaMailSender | OTP đăng ký và quên mật khẩu |
| Dashboard | Chart.js | Biểu đồ Manager/Admin |

Không sử dụng đồng thời MongoDB, SQL Server, MySQL và PostgreSQL. PostgreSQL lưu dữ liệu nghiệp vụ; Firebase chỉ lưu file media.

## Danh sách feature

1. [Tổng quan và kiến trúc](architecture.md)
2. [Xác thực](authentication.md)
3. [Phân quyền](authorization.md)
4. [Hồ sơ người dùng](profile.md)
5. [Bài viết và media](posts.md)
6. [Quan hệ xã hội](social-graph.md)
7. [Tương tác bài viết](engagement.md)
8. [Bảng tin, tìm kiếm và khám phá](feed-search.md)
9. [Chat thời gian thực](chat.md)
10. [Thông báo thời gian thực](notifications.md)
11. [Nhóm và sự kiện](groups-events.md)
12. [Báo cáo và kiểm duyệt](moderation.md)
13. [Quản trị và dashboard](admin-dashboard.md)
14. [Validation và security](security-validation.md)
15. [Thiết kế dữ liệu](database.md)
16. [Giao diện responsive](ui-ux.md)
17. [Kiểm thử](testing.md)
18. [Git, phân công, lộ trình và demo](project-management.md)
19. [Đối chiếu nền tảng và hướng cải tiến](platform-benchmark.md)
20. [Đề xuất bài viết](recommendation.md)
21. [Lọc từ khóa và kiểm duyệt tự động](auto-moderation.md)
22. [Trung tâm bảo mật người dùng](user-security.md)
23. [Các stage triển khai và quality gate](delivery-stages.md)

## Vai trò

| Role | Trách nhiệm chính |
| --- | --- |
| Guest | Xem nội dung công khai, tìm kiếm, đăng ký, đăng nhập, quên mật khẩu |
| User | Tạo nội dung, kết nối, tương tác, chat, tham gia nhóm/sự kiện, báo cáo vi phạm |
| Manager | Kiểm duyệt nội dung, xử lý báo cáo, quản lý nhóm/sự kiện và xem thống kê nội dung |
| Admin | Quản lý tài khoản, role, cấu hình, audit log và dashboard toàn hệ thống |

## Thứ tự triển khai

1. Authentication, authorization và database nền tảng.
2. Profile, posts, engagement và social graph.
3. Feed/search, recommendation, chat và notifications.
4. Group, AutoMod, manual moderation và admin dashboard.
5. Security Center, UI polishing, dữ liệu demo và báo cáo.

Chi tiết thứ tự và điều kiện chuyển stage nằm tại [delivery-stages.md](delivery-stages.md). Một stage chỉ hoàn thành khi code, UI, migration, test và tài liệu của stage đó đều đạt quality gate.

## Definition of done chung

- Có UI Thymeleaf responsive cho luồng chính.
- Có validation cả client và server; server là nguồn quyết định cuối cùng.
- Có kiểm tra authentication, role và ownership.
- Không trả Entity trực tiếp qua API; sử dụng DTO.
- Danh sách lớn được phân trang hoặc lazy loading.
- Có thông báo lỗi thân thiện và global exception handler.
- Có unit test cho Service và integration test cho luồng quan trọng.
- Không commit secret, token, mật khẩu hoặc Firebase private key.
