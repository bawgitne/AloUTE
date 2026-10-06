# Khôi — Week 2 & Week 3 implementation scope

Căn cứ `Nhom13_KeHoachCodeTest.docx` và bộ rules trong `skills/`.

## Week 2

Đã triển khai theo package MVC hiện có của master, không dùng `feature/*`:

- `controller/api`: AuthController, UserController, SocialController.
- `service` + `service/impl`: AuthService, UserService, SocialService.
- `dto/request`, `dto/response`.
- `security/*`: JWT HttpOnly Cookie + Bearer fallback, CurrentUserProvider, handlers, role/authority mapping.
- Whitelist được tách riêng tại `security/config/SecurityWhitelist.java` và bám các operation `security: []` trong OpenAPI.
- User/Profile, Follow/Unfollow, followers/following.
- Integration test Auth/User/Follow + public/protected authorization.

## Week 3

Đã bổ sung phần có đủ source-of-truth hiện tại:

- `POST /api/reports` theo OpenAPI.
- Global roles `USER`, `SYSTEM_ADMIN`.
- Authorities `REPORT_REVIEW`, `USER_SUSPEND` cho `SYSTEM_ADMIN` theo security convention.
- Test mapping quyền moderation.
- Nginx reverse-proxy template, `.env.example`, render script.
- GitHub Actions CI test/build bằng Java 21.

## Không tự ý invent contract

Các phần sau chưa có đủ dữ liệu/contract để hardcode production:

1. OpenAPI hiện chỉ định nghĩa `POST /api/reports`; chưa có endpoint review/update moderation, nên không tự tạo API admin mới.
2. `reportedPostId` được giữ đúng contract, nhưng Post entity/repository thuộc phần Ngọc và chưa có trong master, vì vậy phần Khôi không tự tạo Post module.
3. Domain thật, VPS IP/host, SSH user/key, TLS certificate và GitHub Secrets chưa được cung cấp nên không ghi giá trị production vào repo.
4. Docker/Compose thuộc phần Ngọc theo ownership matrix; reverse proxy template giả định service nội bộ tên `app:8080` đúng architecture convention, nhưng deploy production chỉ hoàn tất sau khi Docker/Compose chính thức được merge.
