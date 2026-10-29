# Khởi Nguyên Backend Test UI — Boundary & Business Rules

## Mục đích
`/khoi-test` là test harness dành riêng cho môi trường `dev` để kiểm tra backend Khởi Nguyên: Auth, User/Profile, Follow/Unfollow, JWT/phân quyền và Report liên quan quyền người dùng.

## Ranh giới ownership
- Không sửa hoặc phụ thuộc vào `controller/page/*` của Quân.
- Không sửa giao diện chính trong `resources/templates/{auth,feed,user,post,...}` của Quân.
- Không sửa CSS/JS dùng chung trong `resources/static/css` và `resources/static/js` của Quân.
- Test UI của Khởi Nguyên chỉ được đặt trong:
  - `controller/dev/KhoiBackendTestController.java`
  - `templates/khoi-test/**`
  - `static/khoi-test/**`
- Test UI chỉ hoạt động khi profile Spring `dev` được bật.

## Quy tắc nghiệp vụ
1. Test UI không tự quyết định quyền. Quyền phải được enforce ở Spring Security/Service.
2. Test UI không truy cập Repository/Entity trực tiếp.
3. `userId` của actor cho mutation phải lấy từ JWT/CurrentUserProvider, không nhận từ form test.
4. Register/Login dùng đúng validation của API; test UI không thay thế backend validation.
5. Update Profile chỉ gửi dữ liệu; privacy/validation do UserService xử lý.
6. Follow/Unfollow chỉ gửi target user; self-follow, duplicate, target invalid và privacy rule do SocialService xử lý.
7. Report chỉ gửi target/reason/description; quyền và trạng thái moderation do ReportService/Security xử lý.
8. CSRF token được đọc từ cookie `XSRF-TOKEN`; JWT auth cookie vẫn do backend phát và kiểm tra.
9. Không thêm API giả cho Post/Community/Messaging/Notification vì đó là phạm vi thành viên khác.
10. Khi merge, thay đổi frontend chính của Quân phải độc lập với `/khoi-test`.

## Cách dùng
Chạy profile `dev`, sau đó mở:

`http://localhost:8080/khoi-test`

Có thể dùng hai tài khoản dev seed nếu `DevTestDataInitializer` đang bật:
- ADMIN: `admin@aloute.local` / `Admin@123`
- USER: `user@aloute.local` / `User@123`
