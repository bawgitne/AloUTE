# Kịch bản demo AloUTE — Buổi thuyết trình 29/10/2026

> Checklist trình chiếu. Chạy `node tools/demo-data.mjs` trước buổi demo (idempotent — chạy lại an toàn).
> Nếu demo trên môi trường đã có dữ liệu thật, chỉ dùng bước 1 và 2 với tài khoản có sẵn; đừng seed thêm.

## Tài khoản

| Vai trò | Tài khoản | Mật khẩu | Dùng cho |
| :--- | :--- | :--- | :--- |
| Quản trị viên | `admin@aloute.local` | `Admin@123` | Luồng admin, nhận báo cáo, gỡ/khôi phục bài |
| Sinh viên 1 | `user@aloute.local` | `User@123` | Tương tác, bình chọn, nhắn tin |
| Sinh viên 2 | `demoan@aloute.test` | `Test@12345` | Bài viết có chủ đề, poll, nhận follow |
| Sinh viên 3 | `demobinh@aloute.test` | `Test@12345` | Bài bị báo cáo, tương tác chéo |

## 1. Đăng nhập & hồ sơ (2 phút)

1. Mở trang chủ `/` → bấm **Đăng nhập**.
2. Đăng nhập `user@aloute.local` → **Kết quả**: về Bảng tin, thấy bài viết + "Chủ đề nổi bật" ở sidebar.
3. Vào **Hồ sơ cá nhân** → **Kết quả**: tên, bio, ảnh đại diện hiển thị.
4. *Fallback:* nếu đăng nhập lỗi trên môi trường mới, dùng tài khoản `admin@aloute.local` (mọi luồng dưới vẫn chạy được với admin trừ phần quyền riêng tư của người thường).

## 2. Bài viết & tương tác (4 phút)

1. **Soạn bài viết** → gõ nội dung, chọn phạm vi **Công khai** → Đăng bài → **Kết quả**: chuyển đến trang chi tiết bài viết.
2. Thích, bình luận, Lưu bài viết, Chia sẻ lại → **Kết quả**: bộ đếm tăng ngay, nút chuyển trạng thái (Bỏ thích / Bỏ lưu / Hủy chia sẻ lại).
3. **Trích dẫn** bài viết → chọn phạm vi trong dropdown → Đăng trích dẫn → **Kết quả**: lượt trích dẫn +1.
4. Bấm **Sao chép liên kết bài viết** → dán sang tab mới → **Kết quả**: mở đúng bài viết (permalink hoạt động).
5. Soạn bài mới, gõ vài chữ rồi **rời trang**, quay lại → **Kết quả**: hiện nháp cũ (draft recovery).
6. *Fallback:* nếu upload ảnh lỗi — hạ tầng Firebase thuộc phần deploy (cần `serviceAccountKey.json`); bài text + mọi luồng khác vẫn demo trọn vẹn.

## 3. Bình chọn — Poll (3 phút)

1. Vào bài **"Bình chọn chủ đề demo"** của `demoan` → **Kết quả**: thấy 3 phương án, kết quả 2 phiếu (user chọn A, demobinh chọn B).
2. (Nếu dùng tài khoản chưa bỏ phiếu) chọn 1 phương án → **Bình chọn** → **Kết quả**: hiện "Lựa chọn của bạn" + tỷ lệ %.
3. F5 → **Kết quả**: kết quả giữ nguyên (persistent).
4. Tạo poll mới từ **Soạn bài viết → Thêm bình chọn**: câu hỏi + 3 phương án + bật "Cho phép chọn nhiều" → đăng → **Kết quả**: poll nhiều lựa chọn dạng checkbox.

## 4. Chủ đề nổi bật (2 phút)

1. Bấm badge **#hoc-tap** ở sidebar → **Kết quả**: trang chủ đề `#Học tập` với bài viết đã gắn chủ đề (bài của demoan).
2. Quay lại, thử badge khác → **Kết quả**: trang chủ đề riêng, hiện empty state nếu chưa có bài.
3. *Fallback:* nếu rỗng, chạy lại `node tools/demo-data.mjs` (phần gắn chủ đề nằm trong bài đầu của demoan).

## 5. Cộng đồng (3 phút)

1. Vào **Cộng đồng** → tạo cộng đồng công khai "CLB Demo" → **Kết quả**: chuyển tới trang cộng đồng, vai trò Trưởng nhóm.
2. Đăng bài trong cộng đồng → **Kết quả**: bài nằm trong cộng đồng.
3. Đăng xuất, đăng nhập `user@aloute.local`, vào trang cộng đồng khác (nếu có nhóm riêng tư từ trước) → **Kết quả**: người ngoài không thấy nội dung nhóm riêng ("Không tìm thấy cộng đồng").

## 6. Tin nhắn & thông báo (3 phút)

1. Đăng nhập `user@aloute.local` → **Tin nhắn** → hội thoại với admin (đã có tin demo) → trả lời 1 tin → **Kết quả**: tin hiển thị tức thì.
2. Đăng nhập `demoan@aloute.test` ở tab ẩn danh → chuông **Thông báo** → **Kết quả**: badge hiện số chưa đọc (follow/like/bình luận từ các bước trước).
3. Bấm **Đã đọc tất cả** → **Kết quả**: badge về 0.
4. *Fallback:* realtime (WebSocket) cần cấu hình STOMP trên môi trường deploy; nếu không có, bấm **Làm mới danh sách** vẫn thấy dữ liệu mới.

## 7. Quản trị viên (4 phút)

1. Đăng nhập `admin@aloute.local` → vào **Quản trị → Hàng đợi báo cáo** → **Kết quả**: thấy báo cáo demo (SPAM, chờ xử lý).
2. Bấm **Nhận** → xác nhận → **Kết quả**: trạng thái "Đang xử lý", gán cho Admin #1.
3. Vào chi tiết → **Giải quyết báo cáo** + tick **gỡ bài viết** → xác nhận → **Kết quả**: báo cáo "Đã giải quyết", bài của demobinh chuyển "Đã gỡ (Admin)".
4. Vào **Quản trị → Bài viết** → tìm bài đã gỡ → **Khôi phục bài viết** → **Kết quả**: bài trở lại "Bình thường".
5. **Kết quả mong đợi tổng:** quyền admin chỉ đọc/kiểm duyệt; mọi thao tác gỡ/khôi phục có ghi chú và lịch sử kiểm duyệt.

## Ghi chú vận hành

- **Seed dữ liệu demo:** `node tools/demo-data.mjs` (chỉ máy dev local, H2). Trên môi trường Cloud Run **không chạy seeder** — dùng tài khoản thật đăng ký qua email OTP.
- **OTP đăng ký khi demo trực tiếp:** mã gửi qua email thật (SMTP môi trường). Nếu SMTP chưa cấu hình ở môi trường demo, dùng 4 tài khoản có sẵn ở bảng trên — đủ cho toàn bộ kịch bản.
- **Reset sạch:** khởi động lại app (H2 in-memory) + chạy seeder.
