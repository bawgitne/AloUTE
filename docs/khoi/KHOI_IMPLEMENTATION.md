# Phần Khôi — Auth/JWT, User/Profile/Follow

Tài liệu này chỉ mô tả các file được bổ sung cho phần Khôi. Không thay đổi file gốc của repository.

## Phạm vi

- Spring Security + JWT access credential.
- Cookie xác thực: `aloute_access_token` (HttpOnly).
- Auth API theo `skills/aloute-openapi.yaml`:
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `POST /api/auth/logout`
  - `GET /api/auth/me`
- User/Profile API:
  - `GET /api/users/{userId}`
  - `PATCH /api/users/me/profile`
- Follow API:
  - `PUT /api/social/follows/{userId}`
  - `DELETE /api/social/follows/{userId}`
  - `GET /api/users/{userId}/followers`
  - `GET /api/users/{userId}/following`
- Sequence:
  - `sequence/login-sequence.puml`
  - `sequence/update-profile-sequence.puml`

## Source of truth đã dùng

- `skills/INDEX.md`
- `skills/domain_system.md`
- `skills/system_architecture.md`
- `skills/backend_rule.md`
- `skills/author_rule.md`
- `skills/aloute-openapi.yaml`

Code mới được đặt theo `feature/auth`, `feature/user`, `feature/social` như project structure trong `system_architecture.md`.

## Cấu hình JWT chưa được repository chốt

Không tự đặt secret hoặc lifetime mặc định.

Khi chạy cần cung cấp:

- `ALOUTE_JWT_SECRET`: Base64, giải mã tối thiểu 32 byte.
- `ALOUTE_JWT_EXPIRATION_SECONDS`: số nguyên dương.

Ví dụ giá trị cụ thể phải do nhóm tự thống nhất; file này không đặt số thay nhóm.

## Các điểm cố ý không làm thay thành viên khác

- Không tạo trigger/stored procedure/index tổng thể.
- Không triển khai Post/Like/Reply.
- Không triển khai UI/template.
- Không triển khai Media/Firebase.
- Không sửa OpenAPI, wireframe hoặc file `skills`.

`avatarMediaId` có trong OpenAPI nhưng Media nằm ngoài phạm vi Khôi. Request vẫn giữ đúng field contract; nếu client gửi giá trị khác null thì backend trả validation error thay vì tự tạo Media/FK giả.

## Display name khi đăng ký

OpenAPI `RegisterRequest` chỉ có `username`, `email`, `password`, trong khi `ProfileResponse` yêu cầu `displayName`. Vì không được sửa OpenAPI hoặc UI của người khác, profile mới dùng chính `username` làm giá trị hiển thị ban đầu; đây là giá trị có sẵn từ request, không phải dữ liệu tự sinh ngẫu nhiên.
