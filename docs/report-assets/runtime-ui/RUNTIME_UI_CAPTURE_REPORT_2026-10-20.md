# Báo cáo thu thập ảnh giao diện thực tế (Runtime UI Capture)

**Ngày thực hiện:** 2026-10-20 (giờ máy: 23:29 – 00:20, 20–21/10/2026)
**Nhánh thực hiện:** `zcode/ui-runtime-capture-20261020`
**Commit baseline:** `4891554b80fd37d0a9544a49770fd88af7534764`
**Môi trường chạy:** local, profile `dev`, CSDL H2 in-memory (`jdbc:h2:mem:aloutedb`), cổng `8098`
**Công cụ chụp:** trình duyệt Chromium thật ở viewport 1440×900 — ZCode in-app browser (Playwright surface) cho phần lớn màn hình, `chrome-headless-shell` qua CDP cho màn hình `/friends` bị treo bộ dựng hình
**Tổng số ảnh chụp thành công:** **25**

---

## 1. Mục tiêu và nguyên tắc

Mục tiêu là tạo bộ ảnh giao diện **chạy thật** để đưa vào báo cáo đồ án, thay cho bộ SVG wireframe Tuần 1 (vốn chỉ là bằng chứng thiết kế, không phải UI thật). Bộ SVG `docs/ui/wireframes/01-10` được dùng **duy nhất như danh mục màn hình cần kiểm tra**, không dùng làm ảnh báo cáo.

Nguyên tắc đã tuân thủ:

- Không dựng lại UI bằng tay, không dùng ảnh AI sinh.
- Ảnh chụp từ chính ứng dụng đang chạy, không cắt ghép.
- Màn hình lỗi/chưa xong thì ghi nhận rồi đi tiếp, không chặn tiến độ.
- Không sửa logic backend/nghiệp vụ; chỉ tạo dữ liệu rác tối thiểu qua API công khai.

## 2. Môi trường và tài khoản

Khởi động bằng JAR đóng gói từ chính commit baseline:

```bash
mvn -q -o package -DskipTests
PORT=8098 SPRING_PROFILES_ACTIVE=dev \
  ALOUTE_REGISTRATION_OTP_SECRET="<base64 48 byte ngẫu nhiên>" \
  java -jar target/aloute-app-0.0.1-SNAPSHOT.jar
```

Biến `ALOUTE_REGISTRATION_OTP_SECRET` là bắt buộc: không đặt thì `RegistrationOtpServiceImpl` ném `IllegalStateException` và ứng dụng không khởi động được. Đây là hành vi đúng theo thiết kế, không phải lỗi.

Tài khoản dùng để chụp (mật khẩu lấy từ `DevTestDataInitializer`, mật khẩu dev hiển nhiên nên không cần che):

| Vai trò | Email | Ghi chú |
| :--- | :--- | :--- |
| Sinh viên | `user@aloute.local` | Dùng cho các màn hình người dùng; đăng nhập thật qua biểu mẫu `/login` |
| Quản trị | `admin@aloute.local` | Dùng cho các màn hình `/admin/**` và để tạo tương tác chéo |

Dữ liệu rác tạo qua API thật (`uicleanup-qa-evidence/seed-report-data.mjs`): 5 bài viết công khai, 3 phản hồi, 2 cộng đồng công khai kèm 1 bài viết trong cộng đồng, 1 hội thoại 4 tin nhắn, cùng lượt theo dõi/lưu/thích để sinh thông báo. Toàn bộ nằm trong H2 nên không ảnh hưởng dữ liệu thật.

## 3. Danh mục màn hình đối chiếu wireframe

| # | Màn hình (theo wireframe) | Route | Trạng thái | Ảnh | Ghi chú |
| :-- | :--- | :--- | :--- | :--- | :--- |
| 01 | Đăng nhập | `/login` | CAPTURED | `01-login.png` | Form email/mật khẩu, nút đăng nhập, liên kết quên mật khẩu và đăng ký. Chụp ở trạng thái khách. |
| 02 | Đăng ký | `/register` | CAPTURED | `02-register.png`, `02-register-b.png` | Bản trống và bản đã điền (username, email trường, mật khẩu, xác nhận). |
| 03 | Bảng tin | `/feed` | CAPTURED | `03-home-feed-a.png`, `03-home-feed-b.png` | Khung nhìn chuẩn và bản toàn trang; có composer, post card, sidebar, cột phải. |
| 04 | Hồ sơ | `/profile`, `/users/2` | CAPTURED | `04-profile-a.png`, `04-profile-b.png`, `04-profile-peer.png` | Chủ tài khoản (nút Chỉnh sửa hồ sơ, tab Bài viết/Phản hồi/Media/Đã lưu) và góc nhìn người khác (Theo dõi/Nhắn tin/Báo cáo). |
| 05 | Tạo bài viết | `/post/create` | CAPTURED | `05-create-post-a.png`, `05-create-post-b.png` | Biểu mẫu trống và đã nhập nội dung; có chọn phạm vi, chủ đề, đính kèm, bình chọn. |
| 06 | Chi tiết bài viết | `/posts/3` | CAPTURED | `06-post-detail-a.png`, `06-post-detail-b.png` | Bài viết có 3 bình luận thật; bản toàn trang thể hiện ô phản hồi và cây bình luận. |
| 07 | Tìm kiếm | `/search?q=lập trình` | CAPTURED | `07-search-a.png` | Truy vấn có dấu trả về kết quả thật ở tab Cộng đồng và Bài viết, kèm các tab phân loại. |
| 08 | Cộng đồng | `/communities`, `/c/<slug>` | CAPTURED | `08-community-a.png`, `08-community-b.png`, `08-community-c.png`, `08-community-d.png` | Danh sách cộng đồng; trang chi tiết có bài viết, số thành viên; trạng thái đã tham gia ("Rời cộng đồng") và chưa tham gia ("Tham gia"). |
| 09 | Nhắn tin | `/messages` | CAPTURED | `09-messaging-a.png` | Bố cục hai cột: danh sách hội thoại và khung tin nhắn bong bóng; hội thoại được chọn tự động. |
| 10 | Thông báo | `/notifications` | CAPTURED | `10-notifications-a.png`, `10-notifications-b.png` | Trạng thái có 6 thông báo chưa đọc và trạng thái đã đánh dấu đọc toàn bộ (dùng `PATCH /api/notifications/read-all`). |
| 11 | Bảng điều khiển quản trị | `/admin` | CAPTURED | `11-admin-dashboard.png` | Thẻ thống kê thật, bảng hoạt động 7 ngày, liên kết hàng đợi báo cáo. |
| 12 | Quản lý người dùng (admin) | `/admin/users` | CAPTURED | `12-admin-users.png` | Bảng người dùng kèm bộ lọc. |
| 13 | Hàng đợi báo cáo (admin) | `/admin/reports` | CAPTURED | `13-admin-reports.png` | Hàng đợi báo cáo theo trạng thái; hàng đợi hiện rỗng nên chủ yếu là bố cục. |
| 14 | Bạn bè & kết nối | `/friends` | **BROKEN** | `14-friends-broken.png` | Trang trả HTTP 200 nhưng HTML bị cắt giữa chừng, xem mục 4.1. |
| 15 | Bài viết đã lưu | `/saved` | CAPTURED | `15-saved.png` | Tab lưu bài viết, có bài đã lưu từ dữ liệu rác. |

**Tổng: 25 tệp ảnh, 14/15 màn hình dùng được.** Màn hình 07 và 08 mỗi màn hình dùng nhiều biến thể để thể hiện trạng thái tương tác.

## 4. Vấn đề ghi nhận

### 4.1 `/friends` bị cắt trang (mức HIGH)

- **Route:** `/friends` (tab mặc định `following`)
- **Triệu chứng:** HTTP 200, `Content-Length` 18.065 byte, HTML **không có thẻ đóng** `</div>`, `</body>`, `</html>` nào. Trình duyệt không bao giờ đạt `readyState = complete`, bộ dựng hình của in-app browser treo nên không chụp được ảnh thường; ảnh đã lấy qua `chrome-headless-shell` sau khi `window.stop()`.
- **Nguyên nhân gốc (đã xác định chính xác):**

  ```
  org.thymeleaf.exceptions.TemplateProcessingException: Exception evaluating SpringEL
  expression: "u.bio != null and !u.bio.isEmpty()" (template: "user/friends" - line 63, col 24)

  Caused by: org.springframework.expression.spel.SpelEvaluationException: EL1008E:
  Property or field 'bio' cannot be found on object of type
  'com.aloute.dto.response.UserSummary'
  ```

  `src/main/resources/templates/user/friends.html:63` đọc `u.bio`, nhưng `UserSummary` chỉ có `id`, `username`, `displayName`, `avatarUrl`, `isVerified` — không có `bio`.
- **Phạm vi ảnh hưởng:** chỉ xảy ra khi danh sách có ít nhất một người; danh sách rỗng render bình thường. Trong phiên chụp, `user` đang theo dõi `admin` nên trang gãy.
- **Ảnh bằng chứng:** `14-friends-broken.png` — phần đầu trang (header, tab) hiển thị đúng rồi dừng giữa thẻ người dùng.

### 4.2 Ghi chú nhỏ

- `/messages` luôn tự chọn hội thoại đầu tiên, nên `/messages` và `/messages/<id>` cho ra bố cục giống nhau; bộ ảnh chỉ giữ một ảnh để tránh trùng.
- `/admin/reports` hiện rỗng vì chưa có báo cáo vi phạm nào; ảnh phản ánh đúng trạng thái rỗng chứ không phải lỗi.
- Một số thao tác bấm bằng Playwright locator bị timeout trên ứng dụng này; đã chuyển sang `evaluate(() => el.click())` trong ngữ cảnh trang cho các nút tương tác.

## 5. Kiểm chứng chất lượng ảnh

Mọi ảnh đều được kiểm tra bằng công cụ nội bộ để loại trừ ảnh trắng/ảnh hỏng:

- `uicleanup-qa-evidence/png-inspect.cjs` — đếm số màu, tỷ lệ pixel chủ đạo, kết luận `OK`/`SUSPECT_BLANK`.
- `uicleanup-qa-evidence/png-bbox.cjs` — khung bao vùng có nội dung và mật độ theo 10 dải dọc.

Kết quả: 25/25 ảnh `OK`, không có ảnh nào bị coi là trắng; không có tệp nào trùng nội dung (so khớp MD5). Ảnh toàn trang được dùng ở các màn hình có nội dung dài để thấy trọn bố cục.

Bố cục responsive cũng được kiểm tra nhanh trên bảng tin: ở 768×900 và 390×844, `scrollWidth` bằng `clientWidth` — không tràn ngang.

## 6. Ảnh hưởng tới mã nguồn

Không sửa `src/main`. Chỉ thêm tệp công cụ trong `uicleanup-qa-evidence/` (thư mục này đã có sẵn bằng chứng QA từ trước và vẫn để untracked, nên các tệp dưới đây không nằm trong commit của nhánh chụp ảnh):

- `seed-report-data.mjs` — tạo dữ liệu rác qua API.
- `capture-lib.mjs` — bootstrap trình duyệt cho phiên chụp.
- `png-inspect.cjs`, `png-bbox.cjs` — kiểm định ảnh.
- `cdp-shot.mjs` — chụp dự phòng qua `chrome-headless-shell` khi in-app browser treo.

## 7. Màn hình cần chụp lại sau

| Màn hình | Lý do | Điều kiện chụp lại |
| :--- | :--- | :--- |
| Bạn bè & kết nối (`/friends`) | HTML bị cắt do lỗi `u.bio` | Sau khi sửa `friends.html:63` hoặc bổ sung `bio` vào `UserSummary`. Cần chụp lại cả hai tab theo dõi/người theo dõi, cả trạng thái có dữ liệu và rỗng. |
| Hàng đợi báo cáo (`/admin/reports`) | Hàng đợi rỗng | Tạo báo cáo vi phạm rồi chụp để thấy bảng dữ liệu thật. |
| Cài đặt (`/settings`) | Ngoài 10 màn hình ưu tiên, chưa chụp | Chụp bổ sung nếu báo cáo cần mục cài đặt tài khoản. |

## 8. Đường dẫn tệp

- Ảnh: `docs/report-assets/runtime-ui/`
- Chú thích tiếng Việt: `docs/report-assets/runtime-ui/REPORT_CAPTIONS_VI.md`
- Mục lục ảnh: `docs/report-assets/runtime-ui/SCREENSHOT_INDEX.md`
