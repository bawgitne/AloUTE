# KẾ HOẠCH TRIỂN KHAI PHÂN HỆ CÀI ĐẶT (SETTINGS) — ALOUTE

> **Tài liệu đặc tả & Kế hoạch triển khai kỹ thuật chi tiết**  
> **Dự án:** Mạng xã hội sinh viên AloUTE (HCMUTE)  
> **Phiên bản:** 1.0 (21/10/2026)  
> **Quy chuẩn kiến trúc:** các file quy tắc `skills/` gốc đã bị loại khỏi repository ngày 28/10/2026; hợp đồng API hiện hành là `docs/api/openapi.yaml`, quy ước phân lớp tại `README.md` §4  
> **Nguyên tắc cốt lõi:** `ONE RULE → ONE OWNER → ONE SOURCE OF TRUTH`

---

## 1. TỔNG QUAN & HIỆN TRẠNG (CURRENT STATE VS. TARGET)

### 1.1. Hiện trạng hệ thống
- **Controller hiện có:** `SettingsPageController.java` đã có sẵn route `@GetMapping("/settings")` và `@GetMapping("/settings/{section}")`, inject `CurrentUserProvider` và truyền `currentUser`, `currentSection` vào view.
- **View hiện có:** `src/main/resources/templates/settings/index.html` hiện tại chỉ là trang tĩnh đơn giản với 4 card placeholder, chưa có form tương tác, chưa có navigation chia tab và chưa kết nối REST API.
- **API đã có sẵn:**
  - `GET /api/users/me/profile` (lấy thông tin hồ sơ)
  - `PATCH /api/users/me/profile` (cập nhật `displayName`, `bio`, `website`, `isPrivate`, `avatarMediaId`)
  - `POST /api/media` (upload avatar lên Firebase Storage với `purpose=USER_AVATAR`)
  - `POST /api/auth/logout` (đăng xuất, hủy cookie `aloute_access_token`)
  - `PUT/DELETE /api/social/blocks/{userId}`, `PUT/DELETE /api/social/mutes/{userId}` (chặn / bỏ chặn, tắt tiếng)

---

## 2. PHÂN LOẠI TÍNH NĂNG THEO MỨC ĐỘ KHẢ THI (FEASIBILITY MATRIX)

Dựa trên cấu trúc database, domain model và backend hiện có, phân hệ Settings được phân nhóm rõ ràng:

| Nhóm | Tên tính năng | Khả thi | Mô tả kỹ thuật |
| :--- | :--- | :---: | :--- |
| **Nhóm 1 (Làm ngay)** | **Giao diện Tab Layout Settings** | ✅ 100% | Bố cục 2 cột (Menu danh mục bên trái + Form nội dung bên phải), responsive mobile dạng pills navigation. |
| **Nhóm 1 (Làm ngay)** | **Chỉnh sửa Hồ sơ cá nhân** | ✅ 100% | Cập nhật `displayName`, `bio`, `website`. Validate real-time, gọi `PATCH /api/users/me/profile`. |
| **Nhóm 1 (Làm ngay)** | **Upload Avatar Firebase trực tiếp** | ✅ 100% | Chọn ảnh -> Xem trước (preview) -> Gọi `POST /api/media` (upload Firebase Storage có download token) -> Cập nhật `avatarUrl` vào Profile. |
| **Nhóm 1 (Làm ngay)** | **Bật/Tắt Tài khoản Riêng tư (`isPrivate`)** | ✅ 100% | Toggle switch gọi `PATCH /api/users/me/profile` với `{ "isPrivate": true/false }`. |
| **Nhóm 1 (Làm ngay)** | **Chủ đề Giao diện (Theme Switcher)** | ✅ 100% | Chuyển đổi Giảng đường Sáng (Light Campus) & Chế độ Tối (Dark Campus), lưu trạng thái vào `localStorage`. |
| **Nhóm 1 (Làm ngay)** | **Đăng xuất & Thông tin Tài khoản** | ✅ 100% | Hiển thị Email, Username, Huy hiệu xác thực (Verified badge). Nút Đăng xuất an toàn gọi `POST /api/auth/logout`. |
| **Nhóm 2 (Cần API)** | **Đổi mật khẩu tài khoản** | ⚠️ Cần thêm API | Form: Mật khẩu cũ, Mật khẩu mới, Xác nhận mật khẩu. Cần bổ sung endpoint `POST /api/users/me/password` (so khớp `PasswordEncoder`, update `passwordHash` & tăng `authVersion`). |
| **Nhóm 2 (Cần API)** | **Quản lý danh sách Chặn / Ẩn** | ⚠️ Cần thêm API | Hiển thị danh sách user đã bị chặn/mute và nút Unblock. Cần endpoint `GET /api/social/blocks` và `GET /api/social/mutes`. |
| **Nhóm 3 (Mở rộng)** | **Tùy chọn Thông báo đẩy (FCM)** | 🕒 Mở rộng | Bật/tắt thông báo Web Push qua FCM Token và các tùy chọn email tương tác (Like, Comment, Follow). |
| **Nhóm 3 (Mở rộng)** | **Tạm khóa / Hủy tài khoản** | 🕒 Mở rộng | Xác nhận mật khẩu -> set `is_active = false` hoặc soft-delete. |

---

## 3. THIẾT KẾ KIẾN TRÚC GIAO DIỆN (UI/UX DESIGN SPECIFICATION)

### 3.1. Bố cục tổng thể (Master Layout)
Sử dụng chuẩn `templates/layouts/default.html` (3 cột của AloUTE). Panel trung tâm (`col-xl-9` hoặc `col-xl-8`) được cấu trúc thành giao diện Settings 2 cột hiện đại:

```
+---------------------------------------------------------------------------------------------------+
| Top Navigation Bar (Logo AloUTE, Thanh tìm kiếm, Thông báo, Avatar menu)                          |
+---------------------------------------------------------------------------------------------------+
|  [Sidebar Điều hướng]  |                    KHUNG CÀI ĐẶT (SETTINGS CONTAINER)                    |
|                        | +----------------------------+------------------------------------------+ |
|  - Bảng tin            | | Danh mục Cài đặt (Nav)     | Nội dung chi tiết (Content Pane)         | |
|  - Hồ sơ cá nhân       | |                            |                                          | |
|  - Cộng đồng           | | 👤 Hồ sơ cá nhân            | [Card Header: Tiêu đề & mô tả ngắn]     | |
|  - Bạn bè & Kết nối    | | 🔒 Tài khoản & Quyền riêng |                                          | |
|  - Bài viết đã lưu     | | 🛡️ Mật khẩu & Bảo mật       | [Form nhập liệu, Avatar preview, Switch] | |
|                        | | 🎨 Giao diện & Trải nghiệm |                                          | |
|                        | | 🚫 Danh sách chặn          |                                          | |
|                        | | 🚪 Đăng xuất               | [Nút Lưu thay đổi] [Nút Hủy bỏ]          | |
|                        | +----------------------------+------------------------------------------+ |
+---------------------------------------------------------------------------------------------------+
```

### 3.2. Chi tiết từng màn hình danh mục (Tabs Specification)

#### Tab 1: Hồ sơ cá nhân (`/settings` hoặc `/settings/profile`)
- **Avatar Editor Card**:
  - Avatar tròn lớn (100x100px) có lớp overlay camera icon khi hover.
  - Nút "Tải ảnh mới" (`<input type="file" accept="image/*">`) và nút "Xóa ảnh".
  - Hiển thị badge định dạng cho phép: JPG, PNG, WEBP tối đa 5MB.
- **Thông tin cơ bản**:
  - `Họ và tên hiển thị`: Input text (max 100 ký tự), hỗ trợ hiển thị tên sinh viên.
  - `Tên người dùng (Username)`: Input disabled/read-only kèm tiền tố `@` và badge "Cố định".
  - `Tiểu sử (Bio)`: Textarea (max 500 ký tự) có bộ đếm số ký tự còn lại (`42/500`).
  - `Liên kết cá nhân (Website)`: Input url kèm icon `bi-link-45deg` (hỗ trợ link Facebook, Github, Portfolio).
- **Hành động**: Nút "Lưu thay đổi" (primary rounded-pill) có spinner loading khi submit.

#### Tab 2: Tài khoản & Quyền riêng tư (`/settings/privacy`)
- **Thông tin tài khoản**:
  - `Email sinh viên`: Input disabled (vd: `bang***@gmail.com` hoặc `...@student.hcmute.edu.vn`).
  - `Trạng thái xác minh`: Badge "Đã xác thực email" màu xanh (`bi-patch-check-fill`).
  - `Thời gian tham gia`: Hiển thị ngày tạo tài khoản.
- **Thiết lập quyền riêng tư**:
  - **Chế độ tài khoản riêng tư (`isPrivate`)**:
    - Toggle Switch Bootstrap kích thước lớn.
    - Chú thích: *"Khi bật chế độ riêng tư, chỉ những người bạn chấp nhận theo dõi mới có thể xem các bài viết ở chế độ Người theo dõi của bạn."*
  - **Liên kết hồ sơ**: Nút "Sao chép liên kết hồ sơ của bạn" vào clipboard kèm Toast thông báo.

#### Tab 3: Mật khẩu & Bảo mật (`/settings/security`)
- **Đổi mật khẩu**:
  - `Mật khẩu hiện tại`: Input type password có nút toggle ẩn/hiện mắt (`bi-eye`/`bi-eye-slash`).
  - `Mật khẩu mới`: Kiểm tra độ mạnh mật khẩu (tối thiểu 8 ký tự, có chữ hoa, số).
  - `Xác nhận mật khẩu mới`: Kiểm tra khớp mật khẩu real-time trước khi bấm nút.
  - Nút "Cập nhật mật khẩu".
- **Quản lý phiên đăng nhập**:
  - Hiển thị thông tin phiên: *"Phiên làm việc hiện tại trên trình duyệt này"*.
  - Nút "Đăng xuất tài khoản" (màu đỏ viền, gọi `POST /api/auth/logout`).
  - Nút "Đăng xuất khỏi tất cả thiết bị" (tăng `authVersion` để vô hiệu hóa token cũ).

#### Tab 4: Giao diện & Trải nghiệm (`/settings/appearance`)
- **Lựa chọn chủ đề (Theme Mode)**:
  - Card 1: **Giảng đường Sáng (Light Campus)** — Tông trắng xanh học thuật HCMUTE (Mặc định).
  - Card 2: **Chế độ Tối (Dark Campus)** — Tông than xám dịu mắt khi học tập ban đêm.
  - Tự động áp dụng và lưu vào `localStorage.getItem('aloute-theme')`.
- **Hiển thị & Tiện ích**:
  - Switch thu gọn thanh bên (Compact Sidebar).
  - Lựa chọn kích thước phông chữ (Mặc định / Lớn).

#### Tab 5: Danh sách Chặn & Tắt tiếng (`/settings/blocking`)
- Tab con: **Đã chặn (Blocks)** và **Đã tắt tiếng (Mutes)**.
- Danh sách thẻ người dùng: Avatar, Tên hiển thị, Username, Ngày chặn.
- Nút "Bỏ chặn" / "Bỏ tắt tiếng" (Action AJAX cập nhật ngay không cần load lại trang).

---

## 4. QUY TRÌNH DỮ LIỆU & GIAO THỨC API (DATA FLOW)

### 4.1. Cập nhật Profile (Thông tin text)
```
User nhập form -> Submit
-> JS gọi: PATCH /api/users/me/profile
   Body: { "displayName": "...", "bio": "...", "website": "...", "isPrivate": boolean }
   Headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": "..." }
<- Backend: 200 OK + ProfileResponse
-> JS hiển thị Toast "Cập nhật hồ sơ thành công" & cập nhật tên trên sidebar/header
```

### 4.2. Upload Avatar mới
```
User chọn file ảnh -> JS preview ngay lên thẻ <img>
-> JS gọi: POST /api/media
   FormData: file=<image.png>, purpose="USER_AVATAR"
<- Backend: 201 Created + { id: 123, url: "https://firebasestorage.googleapis.com/...&token=..." }
-> JS lấy id vừa tạo gọi tiếp: PATCH /api/users/me/profile
   Body: { "avatarMediaId": 123 }
<- Backend lưu avatar_url mới vào bảng profiles
-> Cập nhật avatar trên toàn bộ trang
```

### 4.3. Đổi mật khẩu
```
User nhập Mật khẩu cũ & Mật khẩu mới -> Submit
-> JS gọi: POST /api/users/me/password (hoặc /api/auth/change-password)
   Body: { "currentPassword": "...", "newPassword": "...", "confirmPassword": "..." }
<- Backend: So khớp BCrypt, update password_hash, tăng auth_version
<- Trả về: 200 OK
-> JS hiển thị Toast "Đổi mật khẩu thành công" và reset form
```

---

## 5. LỘ TRÌNH TRIỂN KHAI THEO TỪNG GIAI ĐOẠN

### Giai đoạn 1: Xây dựng UI Settings Chuẩn & Các tính năng Nhóm 1 (Làm ngay)
1. Cải tiến [templates/settings/index.html](../src/main/resources/templates/settings/index.html):
   - Tạo Tab Sidebar bên trái: Hồ sơ, Quyền riêng tư, Bảo mật, Giao diện.
   - Tạo các Form chi tiết cho từng Section tương ứng.
2. Viết file JS điều khiển [static/js/features/settings/settings.js](../src/main/resources/static/js/features/settings/settings.js):
   - Xử lý chuyển tab mượt mà (URL hash / click).
   - Xử lý lưu Profile qua `httpClient.patch('/api/users/me/profile', data)`.
   - Xử lý upload avatar trực tiếp lên Firebase Storage qua `POST /api/media`.
   - Xử lý chuyển đổi Theme Sáng/Tối.
3. Hoàn thiện Backend `UserServiceImpl`:
   - Xử lý `request.hasAvatarMediaId()` để cập nhật avatar thật vào bảng `profiles` thay vì ném exception.

### Giai đoạn 2: Bổ sung API Đổi mật khẩu & Quản lý Chặn (Nhóm 2)
1. Thêm DTO `ChangePasswordRequest.java` (`currentPassword`, `newPassword`, `confirmPassword`).
2. Thêm method `changePassword` vào `UserService` / `AuthService`.
3. Bổ sung Controller endpoint `POST /api/users/me/password` (hoặc `POST /api/auth/change-password`).
4. Thêm endpoint `GET /api/social/blocks` để lấy danh sách người dùng đã bị chặn.
5. Gắn UI hoàn chỉnh cho Tab Bảo mật & Tab Danh sách chặn.

### Giai đoạn 3: Kiểm thử & Tối ưu hóa (Verification)
1. Viết Unit & Integration Test cho chức năng đổi mật khẩu và cập nhật profile.
2. Kiểm tra giao diện trên Mobile (responsive Bootstrap).
3. Kiểm tra bảo mật CSRF và phân quyền người dùng.
