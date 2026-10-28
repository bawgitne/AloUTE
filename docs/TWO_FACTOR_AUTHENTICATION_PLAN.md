# KẾ HOẠCH THIẾT KẾ & TRIỂN KHAI BẢO MẬT 2 LỚP (2FA) — ALOUTE

> **Tài liệu Đặc tả Kiến trúc & Kế hoạch Kỹ thuật Toàn diện**  
> **Dự án:** Mạng xã hội học thuật sinh viên AloUTE (HCMUTE)  
> **Phạm vi:** Backend (Spring Boot), Web Frontend (Thymeleaf/JS), Mobile App (Flutter)  
> **Phiên bản:** 1.0 (21/10/2026)  
> **Nguyên tắc cốt lõi:** `ONE RULE → ONE OWNER → ONE SOURCE OF TRUTH` (các file quy tắc `skills/` gốc đã bị loại khỏi repository ngày 28/10/2026; hợp đồng API hiện hành là `docs/api/openapi.yaml`)  
> **Trạng thái:** 📋 BẢN KẾ HOẠCH KIẾN TRÚC & THIẾT KẾ (CHƯA VIẾT CODE)

---

## 1. TỔNG QUAN & BỐI CẢNH (PROBLEM & VISION)

### 1.1. Vấn đề thực tế
AloUTE là mạng xã hội nội bộ dành cho sinh viên và giảng viên Trường Đại học Sư phạm Kỹ thuật TP.HCM (HCMUTE). Hiện tại, hệ thống xác thực người dùng dựa trên:
1. Xác thực bằng Email UTE (`@student.hcmute.edu.vn`) + Mật khẩu.
2. Mã xác thực kích hoạt đăng ký (Email OTP 6 số).
3. Đổi mật khẩu / Khôi phục mật khẩu qua Email OTP.

**Nguy cơ bảo mật:**
- Sinh viên thường có thói quen đặt mật khẩu đơn giản hoặc dùng chung mật khẩu giữa nhiều nền tảng, dễ bị lộ lọt qua phishing hoặc rò rỉ dữ liệu ngoài trường.
- Khi tài khoản bị chiếm đoạt, kẻ xấu có thể mạo danh sinh viên đăng bài sai sự thật trên bảng tin, truy cập nhóm thảo luận bảo mật hoặc gửi tin nhắn rác.
- Môi trường đăng nhập đa dạng: Máy tính phòng lab trường, trình duyệt cá nhân, ứng dụng điện thoại (Mobile App).

### 1.2. Mục tiêu của tính năng Bảo mật 2 lớp (2FA)
- Cung cấp cơ chế **Xác thực 2 yếu tố (Two-Factor Authentication - 2FA)** chuẩn công nghiệp:
  - **Yếu tố 1 (Something you know):** Mật khẩu tài khoản.
  - **Yếu tố 2 (Something you have):** Mã số động TOTP từ ứng dụng Authenticator (Google Authenticator, Microsoft Authenticator) hoặc Email OTP dự phòng.
  - **Cứu hộ khẩn cấp:** Bộ mã dự phòng dùng 1 lần (Emergency Backup Codes).
  - **Trải nghiệm di động (Mobile UX):** Hỗ trợ Deep-link mở thẳng Authenticator App và tích hợp Khóa sinh trắc học cục bộ (Biometrics: Vân tay / Face ID) cho ứng dụng Flutter.

---

## 2. PHÂN TÍCH SO SÁNH CÁC PHƯƠNG ÁN 2FA (METHOD ANALYSIS)

| Phương thức | Ưu điểm | Nhược điểm | Đánh giá & Áp dụng cho AloUTE |
| :--- | :--- | :--- | :--- |
| **1. TOTP (RFC 6238)<br>(Google / Microsoft Authenticator)** | • Chuẩn quốc tế, bảo mật tuyệt đối.<br>• **Hoạt động hoàn toàn Offline** (không cần sóng 4G/Wifi vẫn sinh mã).<br>• Chi phí vận hành = 0đ (không tốn phí SMS). | • Sinh viên phải cài thêm app Authenticator.<br>• Mất điện thoại có thể bị kẹt nếu không có mã dự phòng. | ⭐️ **PHƯƠNG ÁN CHÍNH (PRIMARY)**<br>Chuẩn mực cho mạng xã hội sinh viên CNTT/kỹ thuật. |
| **2. Email OTP (Gmail SMTP)** | • Tận dụng hạ tầng Email Service sẵn có của AloUTE.<br>• Sinh viên nào cũng có email UTE.<br>• Không cần cài thêm app ngoài. | • Phụ thuộc vào tốc độ mạng và độ trễ chuyển phát mail của Google.<br>• Nếu email UTE bị hack thì mất luôn lớp 2. | ⭐️ **PHƯƠNG ÁN DỰ PHÒNG (FALLBACK)**<br>Dùng làm kênh cứu trợ khi mất điện thoại. |
| **3. SMS OTP** | • Tiện lợi, sinh viên quen thuộc. | • Tốn chi phí viễn thông rất cao (hàng trăm đồng/tin nhắn).<br>• Nguy cơ SIM-swap, trễ mạng viễn thông. | ❌ **KHÔNG SỬ DỤNG** (Không khả thi về chi phí cho đồ án/sinh viên). |
| **4. Sinh trắc học Mobile<br>(Vân tay / Face ID qua `local_auth`)** | • Đăng nhập cực nhanh, trải nghiệm mượt mà trên iPhone / Android.<br>• Dữ liệu sinh trắc lưu an toàn trong phần cứng thiết bị (Secure Enclave / Keystore). | • Chỉ có tác dụng cục bộ trên thiết bị đó (Local Device Authentication), không thể gửi vân tay lên Server để verify. | ⭐️ **ÁP DỤNG TRÊN MOBILE APP**<br>Làm lớp mở khóa nhanh (Quick App Unlock / Re-authentication) sau khi đã đăng nhập 2FA lần đầu. |
| **5. Backup Codes<br>(Mã khôi phục 8-10 ký tự)** | • Độc lập hoàn toàn, in ra giấy hoặc lưu an toàn.<br>• Cứu tài khoản 100% khi mất cả điện thoại lẫn hỏng ứng dụng. | • Chỉ dùng được 1 lần, người dùng có thể quên chỗ lưu. | ⭐️ **BẮT BUỘC ĐI KÈM VỚI 2FA**<br>Cấp 8 mã dùng một lần khi kích hoạt 2FA. |

---

## 3. KIẾN TRÚC GIAO THỨC & LUỒNG DỮ LIỆU (PROTOCOL & DATA FLOW)

### 3.1. Thiết kế Giao thức Đăng nhập 2 bước (2-Step Authentication Protocol)
Hệ thống phải đảm bảo **Backward Compatibility (Tương thích ngược)** tuyệt đối với các client hiện tại (Postman, Mobile App, Web):

```
+--------------------------------------------------------------------------------------------------+
|                                LUỒNG ĐĂNG NHẬP 2 BƯỚC (2FA LOGIN FLOW)                           |
+--------------------------------------------------------------------------------------------------+

  Client (Web / Mobile)                           Backend API (Spring Boot)
       |                                                    |
       |  1. POST /api/auth/login { email, password }      |
       |--------------------------------------------------->|
       |                                                    |-- Kiểm tra email, passwordHash
       |                                                    |-- Kiểm tra user.isTwoFactorEnabled
       |                                                    |
       |  [TRƯỜNG HỢP A: 2FA = FALSE]                       |
       |  <-- HTTP 200 { user: {...} }                      |
       |      Set-Cookie: aloute_access_token (Web)         |-- Cấp JWT hoàn tất
       |      hoặc token trong JSON (Mobile)                |
       |                                                    |
       |  [TRƯỜNG HỢP B: 2FA = TRUE]                        |
       |  <-- HTTP 200 OK                                   |
       |      {                                             |
       |        "twoFactorRequired": true,                  |-- KHÔNG cấp session JWT!
       |        "twoFactorTicket": "eyJhbGciOi...",         |-- Sinh ticket tạm thời (hạn 5 phút)
       |        "twoFactorType": "TOTP",                    |   chứa { sub: userId, type: "2FA" }
       |        "maskedEmail": "n***@student.hcmute.edu.vn" |
       |      }                                             |
       |                                                    |
       |  2. Người dùng nhập mã 6 số (từ Google Auth / Email)|
       |     POST /api/auth/2fa/verify-login                |
       |     { twoFactorTicket: "...", code: "123456" }     |
       |--------------------------------------------------->|
       |                                                    |-- Verify chữ ký & hạn twoFactorTicket
       |                                                    |-- Kiểm tra Rate Limit (tối đa 5 lần thử)
       |                                                    |-- Tính TOTP (±30s tolerance)
       |                                                    |   hoặc so khớp Backup code
       |                                                    |-- Hủy Ticket (chống replay)
       |                                                    |
       |  <-- HTTP 200 OK { user: {...} }                   |
       |      Set-Cookie: aloute_access_token (Web)         |-- CHÍNH THỨC CẤP JWT PHIÊN ĐĂNG NHẬP!
       |      + accessToken: "jwt..." (Mobile)              |
       v                                                    v
```

---

## 4. ĐẶC TẢ THIẾT KẾ CƠ SỞ DỮ LIỆU (DATABASE SCHEMA)

Nguyên tắc thiết kế DDL (từ bộ quy tắc `skills/` gốc, nay nằm trong entity/ và `src/main/resources/db/migration/`):

### 4.1. Bổ sung trường trong bảng `users`
```sql
-- Migration bổ sung các cột bảo mật 2 lớp
ALTER TABLE users 
    ADD COLUMN two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN two_factor_secret VARCHAR(255) NULL,         -- Base32 secret mã hóa AES-256
    ADD COLUMN two_factor_type VARCHAR(32) NOT NULL DEFAULT 'TOTP', -- 'TOTP' hoặc 'EMAIL_OTP'
    ADD COLUMN two_factor_temp_secret VARCHAR(255) NULL,    -- Lưu tạm thời trong lúc chờ xác nhận kích hoạt
    ADD COLUMN two_factor_confirmed_at TIMESTAMP NULL;
```

> [!IMPORTANT]
> **Nguyên tắc bảo vệ Khóa bí mật (Secret Encryption):**
> Trường `two_factor_secret` **tuyệt đối không lưu dạng plain text** trong database.
> Hệ thống sử dụng thuật toán mã hóa đối xứng `AES-256-GCM` với khóa chủ `ALOUTE_2FA_MASTER_KEY` lấy từ biến môi trường (`.env`). Nếu database bị dump, kẻ tấn công vẫn không thể giải mã được secret để sinh mã TOTP giả mạo.

### 4.2. Bảng mới: `user_backup_codes` (Mã khôi phục khẩn cấp)
```sql
CREATE TABLE user_backup_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    code_hash VARCHAR(100) NOT NULL,                        -- SHA-256 hoặc BCrypt hash của mã
    used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_backup_codes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_backup_codes_user_id (user_id)
);
```
- Khi user kích hoạt 2FA, hệ thống sinh ngẫu nhiên **8 mã dự phòng** (mỗi mã gồm 8 ký tự an toàn dạng `XXXX-XXXX`).
- Chỉ lưu `code_hash` vào bảng `user_backup_codes`.
- Mỗi mã chỉ được dùng đúng 1 lần (`used = true`). Sau khi dùng, hệ thống gửi email cảnh báo người dùng.

---

## 5. THIẾT KẾ HỆ THỐNG REST API (BACKEND CONTRACT)

Theo chuẩn `docs/api/openapi.yaml` và quy ước envelope trong `README.md` §4:

### 5.1. Danh sách Endpoint
| HTTP Method | Đường dẫn API | Xác thực | Mục đích |
| :--- | :--- | :---: | :--- |
| `POST` | `/api/auth/login` | Public | Đăng nhập bước 1. Nếu tài khoản đã bật 2FA, trả về `twoFactorRequired: true` và `twoFactorTicket`. |
| `POST` | `/api/auth/2fa/verify-login` | Public (Ticket) | Xác minh bước 2 (Mã Authenticator, Email OTP hoặc Mã dự phòng) để lấy JWT chính thức. |
| `POST` | `/api/auth/2fa/send-email-fallback` | Public (Ticket) | Gửi mã OTP 6 số qua Gmail khi user bấm "Gửi mã qua Email dự phòng". |
| `POST` | `/api/users/me/2fa/setup` | Authenticated | Khởi tạo secret Base32, trả về `secretKey`, `otpAuthUri` và mã QR (SVG/Base64) để quét. |
| `POST` | `/api/users/me/2fa/enable` | Authenticated | Nhập mã 6 số từ Google Auth để kiểm tra và kích hoạt chính thức. Trả về 8 mã dự phòng. |
| `POST` | `/api/users/me/2fa/disable` | Authenticated | Tắt 2FA. Yêu cầu nhập mật khẩu hiện tại + mã TOTP hợp lệ để tránh người khác dùng máy tắt trộm. |
| `POST` | `/api/users/me/2fa/backup-codes` | Authenticated | Sinh mới 8 mã dự phòng (hủy bộ mã cũ). Yêu cầu mật khẩu tài khoản. |

### 5.2. Cấu trúc Payload chi tiết
#### A. Phản hồi Đăng nhập khi có 2FA (`POST /api/auth/login`)
```json
{
  "success": true,
  "data": {
    "twoFactorRequired": true,
    "twoFactorTicket": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIyNDExMDE2NiIsInB1cnBvc2UiOiIyRkFfQ0hBTExFTkdFIiwiZXhwIjoxNzk...",
    "twoFactorType": "TOTP",
    "maskedEmail": "24110166@student.hcmute.edu.vn"
  }
}
```

#### B. Xác minh đăng nhập (`POST /api/auth/2fa/verify-login`)
**Request Body:**
```json
{
  "twoFactorTicket": "eyJhbGciOiJIUzI1NiJ9...",
  "code": "849201",
  "isBackupCode": false
}
```
**Response Body (Thành công):**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "user": {
      "id": 24110166,
      "username": "nguyenvanan",
      "email": "24110166@student.hcmute.edu.vn"
    }
  }
}
```

#### C. Khởi tạo 2FA Setup (`POST /api/users/me/2fa/setup`)
**Response Body:**
```json
{
  "success": true,
  "data": {
    "secretKey": "JBSWY3DPEHPK3PXP",
    "otpAuthUri": "otpauth://totp/AloUTE:24110166@student.hcmute.edu.vn?secret=JBSWY3DPEHPK3PXP&issuer=AloUTE",
    "qrCodeBase64": "data:image/svg+xml;base64,PHN2ZyB4bWxucz0..."
  }
}
```

---

## 6. THIẾT KẾ CHI TIẾT TRÊN MOBILE APP (`aloute_mobile` — FLUTTER)

Đây là phân hệ người dùng di động cực kỳ quan trọng, cần xử lý trải nghiệm UX tinh tế để không làm khó sinh viên:

### 6.1. Vấn đề cốt lõi trên Mobile: "Không thể dùng camera quét màn hình chính mình"
Khi kích hoạt 2FA trên ứng dụng di động, người dùng không thể cầm điện thoại để quét mã QR hiển thị ngay trên điện thoại đó.
**Giải pháp UX chuẩn quốc tế:**
1. **Nút "Mở nhanh Authenticator" (One-Tap Deep Link):**
   - Sử dụng package `url_launcher` để kích hoạt URI scheme:
     `otpauth://totp/AloUTE:24110166?secret=JBSWY3DPEHPK3PXP&issuer=AloUTE`
   - Android và iOS sẽ tự động nhận diện và hỏi người dùng mở bằng **Google Authenticator** hoặc **Microsoft Authenticator** cài sẵn trên máy, tự động điền Secret mà không cần gõ tay.
2. **Nút "Sao chép mã khóa bí mật" (Copy Secret Key):**
   - Cho phép người dùng bấm sao chép chuỗi Base32 dạng `JBSW-Y3DP-EHPK-3PXP` vào Clipboard chỉ với 1 chạm.

### 6.2. Màn hình Xác thực 2FA khi Đăng nhập (`TwoFactorScreen.dart`)
- **Giao diện hiện đại:**
  - 6 ô nhập mã PIN rời rạc (Numeric Pin Code Fields), tự động focus ô kế tiếp, hỗ trợ dán (paste) 6 số từ clipboard một chạm.
  - Hiển thị logo ứng dụng Authenticator và đồng hồ đếm ngược hiệu lực mã.
- **Tùy chọn chuyển đổi kênh linh hoạt (Fallback Actions):**
  - Nút: *"Không mang theo điện thoại? Sử dụng mã dự phòng (Backup Code)"*.
  - Nút: *"Gặp sự cố với Authenticator? Nhận mã OTP qua Email sinh viên"*.
- **Xử lý đăng nhập thành công:**
  - Lưu `accessToken` vào `ApiClient` và `SharedPreferences`.
  - Tự động hỏi: *"Bạn có muốn kích hoạt Đăng nhập nhanh bằng Vân tay / Face ID cho lần sau không?"*.

### 6.3. Tích hợp Khóa Sinh trắc học Cục bộ (Local Biometrics - Face ID / Vân tay)
- **Kiến trúc bảo mật trên Mobile:**
  - Thư viện: `local_auth` (chính chủ Flutter) kết hợp `flutter_secure_storage`.
  - Token JWT dài hạn hoặc Refresh Token được mã hóa lưu trữ trong **Android Keystore** và **iOS Keychain**.
- **Luồng hoạt động (Lifecycle):**
  1. Khi mở lại app (Cold start hoặc Resume từ background sau 15 phút):
     - Thay vì bắt sinh viên nhập lại mật khẩu + mã OTP phiền toái, app hiển thị popup sinh trắc học hệ thống: *"Xác thực bằng Face ID / Vân tay để mở AloUTE"*.
     - Xác thực thành công -> Mở ngay bảng tin.
     - Xác thực thất bại hoặc người dùng từ chối -> Quay về màn hình Login mật khẩu + 2FA.
  2. Bắt buộc nhập lại 2FA qua Server khi:
     - Đăng xuất tài khoản.
     - Phiên đăng nhập bị hủy từ xa (Server tăng `authVersion`).
     - Thực hiện các thao tác siêu nhạy cảm: Chuyển quyền Trưởng Cộng đồng, Tắt 2FA, Đổi mật khẩu.

### 6.4. Màn hình Cài đặt 2FA trên Mobile (`TwoFactorSettingsScreen.dart`)
- Nằm trong `settings_screen.dart` mục "Bảo mật & Phiên đăng nhập".
- **Trạng thái Chưa kích hoạt:**
  - Banner giới thiệu trực quan với biểu tượng chiếc khiên xanh (`Icons.shield_rounded`).
  - Nút bấm *"Bắt đầu thiết lập bảo mật 2 lớp"*.
  - Step 1: Hiển thị Secret Key + Deep-link mở Google Auth.
  - Step 2: Nhập mã 6 số để xác nhận.
  - Step 3: Hiển thị danh sách 8 mã dự phòng với nút *"Lưu ảnh mã dự phòng"* và *"Sao chép tất cả"*.
- **Trạng thái Đã kích hoạt:**
  - Huy hiệu xanh: *"Đang bảo vệ tài khoản bằng Google Authenticator"*.
  - Tùy chọn *"Xem lại / Tạo mới bộ mã dự phòng"*.
  - Nút *"Tắt bảo mật 2 lớp"* (yêu cầu xác thực mật khẩu + sinh trắc học).

---

## 7. THIẾT KẾ CHI TIẾT TRÊN WEB FRONTEND (THYMELEAF + JAVASCRIPT)

### 7.1. Cải tiến Trang Cài đặt (`/settings` - Tab Mật khẩu & Bảo mật)
Tại `src/main/resources/templates/settings/index.html` trong thẻ `pane-security`:
- Bổ sung một Card mới chuyên biệt: **"Bảo vệ tài khoản 2 lớp (2FA)"**.
- **Giao diện khi 2FA đang tắt:**
  - Nút bật dạng Toggle hoặc nút *"Kích hoạt ngay"*.
  - Mở **Modal Bootstrap 5** từng bước (Wizard):
    - **Bước 1: Quét mã QR.** Hiển thị ảnh QR Code rõ nét + Chuỗi Secret Key kèm nút Copy.
    - **Bước 2: Xác nhận.** Nhập mã 6 số hiển thị trên Authenticator app.
    - **Bước 3: Lưu mã dự phòng.** Hiển thị bảng lưới 8 mã khôi phục dạng `badge` đẹp mắt, kèm nút "Tải file văn bản (.txt)" và "Sao chép".
- **Giao diện khi 2FA đang bật:**
  - Badge trạng thái: `Đã kích hoạt` (Màu xanh `badge-success`).
  - Nút *"Tạo mới mã khôi phục dự phòng"*.
  - Nút *"Hủy kích hoạt 2FA"* (Yêu cầu nhập mật khẩu hiện tại trong modal xác nhận).

### 7.2. Cải tiến Luồng Đăng nhập Web (`/login` & `auth.js`)
- Người dùng vào trang `/login`, nhập email và mật khẩu như bình thường.
- `auth.js` gửi request `POST /api/auth/login`.
- Nếu response trả về `twoFactorRequired: true`:
  - JavaScript thực hiện hiệu ứng chuyển cảnh mượt mà trên chính Form đăng nhập (không chuyển trang để tránh mất state):
  - Ẩn ô nhập email/mật khẩu, hiển thị cụm giao diện **"Xác thực bước 2"**:
    - Tiêu đề: *"Nhập mã 6 chữ số từ ứng dụng Google Authenticator"*.
    - Input mã xác thực lớn (font mono, cách đều các số).
    - Nút bấm: *"Không có điện thoại? Nhận mã qua Email"* (gọi `POST /api/auth/2fa/send-email-fallback`).
    - Nút bấm: *"Sử dụng mã dự phòng"*.
  - Khi người dùng nhập đủ 6 ký tự, form tự động submit đến `/api/auth/2fa/verify-login`.
  - Thành công: Server set cookie `aloute_access_token`, JS chuyển hướng đến `/feed`.

---

## 8. CÁC QUY TẮC BẢO MẬT BẤT BIẾN (SECURITY INVARIANTS)

Tuân thủ nghiêm ngặt các nguyên tắc bảo mật thông tin:

1. **Chống tấn công Brute-force (Dò mã 6 số):**
   - Mã TOTP có 1,000,000 tổ hợp khả dĩ (000000 -> 999999).
   - `twoFactorTicket` chỉ cho phép **tối đa 5 lần nhập sai**.
   - Nếu nhập sai quá 5 lần, ticket lập tức bị hủy vĩnh viễn, người dùng phải nhập lại từ Bước 1 (Email & Password).
   - Tích hợp IP Rate Limiter (tối đa 10 request/phút trên endpoint verify).
2. **Xử lý lệch thời gian giữa Client và Server (Clock Skew):**
   - Thời gian trên điện thoại của sinh viên có thể nhanh hoặc chậm hơn server vài giây.
   - Thuật toán TOTP kiểm tra khung thời gian `T - 1`, `T`, và `T + 1` (sai số chấp nhận được là ±30 giây).
3. **Chống tấn công phát lại (Replay Attack):**
   - Mỗi mã TOTP 6 số sau khi được xác thực thành công sẽ được ghi nhận vào bộ nhớ cache trong 60 giây. Nếu kẻ xấu bắt được request và gửi lại cùng mã đó trong vòng 30 giây còn lại, hệ thống sẽ từ chối.
4. **Đồng bộ hóa vòng đời phiên với `authVersion`:**
   - Khi người dùng **Tắt 2FA**, **Đổi mật khẩu**, hoặc **Tạo lại mã dự phòng**:
   - Backend tự động tăng `user.authVersion = user.authVersion + 1`.
   - Toàn bộ các phiên làm việc (JWT) trên tất cả các trình duyệt và thiết bị khác sẽ lập tức bị vô hiệu hóa, ngăn chặn kẻ xấu chiếm quyền sử dụng phiên cũ.
5. **Mã hóa khóa bí mật (Secret at Rest):**
   - Secret key của người dùng được mã hóa bằng AES-256-GCM với Key bảo vệ cấp hệ thống `ALOUTE_2FA_MASTER_KEY` trước khi lưu vào database.

---

## 9. KẾ HOẠCH TRIỂN KHAI THEO GIAI ĐOẠN (IMPLEMENTATION ROADMAP)

```
[Giai đoạn 1] Cấu trúc Dữ liệu & Thư viện Core TOTP
     │
     ├── Thêm migration database: users (2fa fields), user_backup_codes
     ├── Tích hợp thư viện sinh/kiểm tra TOTP RFC 6238 (e.g., com.warrenstrange:googleauth)
     └── Xây dựng Service mã hóa bí mật AES-256 GCM
     │
[Giai đoạn 2] Backend Service & REST API 2FA
     │
     ├── Chỉnh sửa AuthController.login: hỗ trợ 2FA challenge & twoFactorTicket
     ├── Implement Auth2FaController: verify-login, send-email-fallback
     ├── Implement User2FaManagementController: setup, enable, disable, backup-codes
     └── Cập nhật OpenAPI spec skills/aloute-openapi.yaml
     │
[Giai đoạn 3] Giao diện Web (Thymeleaf + JavaScript)
     │
     ├── Nâng cấp templates/settings/index.html: Card 2FA + Modal Setup QR Code + Backup codes
     ├── Nâng cấp static/js/features/settings/settings.js: Xử lý gọi API 2FA
     └── Nâng cấp templates/auth/login.html & auth.js: Luồng nhập mã bước 2 mượt mà
     │
[Giai đoạn 4] Ứng dụng Di động (Flutter `aloute_mobile`)
     │
     ├── Xây dựng màn hình nhập mã 2FA: TwoFactorVerificationScreen
     ├── Tích hợp Deep-link otpauth:// mở trực tiếp Authenticator
     ├── Tích hợp thư viện local_auth (Vân tay / Face ID) mở khóa nhanh
     └── Bổ sung phân mục 2FA trong screens/settings/settings_screen.dart
     │
[Giai đoạn 5] Kiểm thử Toàn diện & Bảo mật
     │
     ├── Test Unit/Integration: Test lệch múi giờ, test brute-force 5 lần sai
     ├── Test luồng khôi phục: Dùng mã dự phòng, gửi OTP qua Gmail
     └── Kiểm tra hồi quy không ảnh hưởng luồng đăng nhập của tài khoản chưa bật 2FA
```

---

## 10. TỔNG KẾT & TRẠNG THÁI HIỆN TẠI

- Kế hoạch trên giải quyết trọn vẹn bài toán bảo mật 2 lớp cho **cả Web và Mobile App** theo đúng đặc thù sinh viên trường HCMUTE.
- **Cam kết thực hiện:** Hiện tại **CHƯA THAY ĐỔI CODE** theo đúng yêu cầu của người dùng (`"plan chưa code"`).
- Khi người dùng xem xét bản kế hoạch và đồng ý triển khai, chúng ta sẽ bắt đầu thực hiện theo tuần tự từng Giai đoạn từ 1 đến 5 để đảm bảo an toàn tuyệt đối và tính ổn định của hệ thống.
