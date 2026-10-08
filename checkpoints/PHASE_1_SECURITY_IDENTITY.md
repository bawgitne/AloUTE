# CHECKPOINT PHASE 1: SECURITY, AUTHENTICATION & USER IDENTITY

> **Chịu trách nhiệm chính**: Nguyễn Đoàn Minh Khôi (24110259)  
> **Trạng thái**: 🟢 **ĐẠT (90%)**  
> **Mức độ rủi ro**: Thấp  

---

## 1. CÁC TẦNG ĐÃ ĐỐI SOÁT

### 1.1. Tầng Controller
- `AuthController.java` (`/api/auth`):
  - `POST /register`: Nhận `RegisterRequest`, mã hóa mật khẩu, tạo đồng thời User và Profile ban đầu -> **Chuẩn**.
  - `POST /login`: Kiểm tra thông tin đăng nhập, sinh JWT, trả Set-Cookie HttpOnly (`SameSite=Lax`, `Path=/`) và dữ liệu `UserResponse` -> **Chuẩn**.
  - `POST /logout`: Xóa HttpOnly Cookie (maxAge = 0) và yêu cầu `requireUserId` -> **Chuẩn**.
  - `GET /me`: Lấy thông tin user hiện tại từ SecurityContext -> **Chuẩn**.
- `UserController.java` (`/api/users`):
  - `GET /{userId}`: Lấy profile theo ID (bao gồm `isFollowing`, `followersCount`, `followingCount`) -> **Chuẩn**.
  - `GET /by-username/{username}`: Lấy profile theo username -> **Chuẩn**.
  - `PATCH /me/profile`: Cập nhật `displayName`, `bio`, `website`, `avatarMediaId`, `coverMediaId` -> **Chuẩn**.
  - `GET /{userId}/followers` & `GET /{userId}/following`: Phân trang 0-indexed theo chuẩn `PageRequest` -> **Chuẩn**.

### 1.2. Tầng Service & Xử lý nghiệp vụ
- `AuthServiceImpl.java`:
  - Kiểm tra duplicate username & email không phân biệt hoa thường (`existsByUsernameIgnoreCase`, `existsByEmailIgnoreCase`).
  - Gán quyền mặc định `Role.USER`, tài khoản kích hoạt `active = true`.
  - Bọc `@Transactional` đảm bảo tính nguyên tử khi tạo đồng thời bảng `users` và `profiles`.
- `UserServiceImpl.java`:
  - Validate định dạng website URL qua `validateUri()` chống XSS/malformed link.
  - Sử dụng `@Transactional(readOnly = true)` tối ưu cho các thao tác đọc.

### 1.3. Tầng Security & JWT
- `SecurityConfig.java`:
  - Vô hiệu hóa Session (`SessionCreationPolicy.STATELESS`).
  - Tắt `httpBasic` và `formLogin` mặc định của Spring Boot.
  - Áp dụng `CsrfCookieFilter` và cấu hình CORS chặt chẽ.
- `SecurityWhitelist.java`:
  - Định nghĩa rõ ràng các route public không cần token theo đúng OpenAPI: `/api/auth/login`, `/api/auth/register`, `/api/feed/explore`, `/api/posts/{id}`, `/h2-console/**`, static assets.
- `JwtAuthenticationFilter.java`:
  - Hỗ trợ ưu tiên đọc từ HttpOnly Cookie (`aloute_access_token`) và fallback sang Header `Authorization: Bearer <token>` phục vụ test/mobile.

---

## 2. NHỮNG ĐIỂM CÒN THIẾU HOẶC SAI LỆCH VỚI SKILLS

| Thành phần | Đặc tả trong `skills/domain_system.md` & `openapi` | Thực tế trong Code | Đánh giá & Hướng xử lý |
| :--- | :--- | :--- | :--- |
| **Trạng thái tài khoản** | Phải có enum `AccountStatus`: `ACTIVE`, `SUSPENDED`, `DEACTIVATED`, `LOCKED`. Khi suspended thì không cho đăng nhập. | Bảng `users` chỉ có boolean `active`. Không có enum trạng thái. | **Thiếu trường dữ liệu**: Nên bổ sung enum `AccountStatus` hoặc map tạm `active = false` tương đương `SUSPENDED`. |
| **Đổi mật khẩu** | OpenAPI có `PUT /api/auth/password` để đổi mật khẩu. | Chưa cài đặt endpoint này trong `AuthController`. | **Thiếu endpoint**: Cần bổ sung khi hoàn thiện tính năng Settings. |
| **Bật/tắt tài khoản riêng tư** | `User.isPrivateAccount` cho phép user chuyển sang chế độ tài khoản riêng tư. | Có cột trong DB và Entity, nhưng chưa có endpoint `PATCH /api/users/me/privacy`. | **Thiếu API cấu hình**: Cần bổ sung vào `UserController`. |

---

## 3. KẾT LUẬN CHECKPOINT PHASE 1
Tầng Security và Identity của Khôi được viết rất chuẩn mực, tuân thủ đúng nguyên tắc phân lớp (Controller -> Service -> Repository), bảo mật tốt bằng HttpOnly Cookie và tách whitelist sạch sẽ. Đạt điều kiện nghiệm thu.
