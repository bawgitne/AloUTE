# 10 — Demo Script (Tiếng Việt)

**Mục tiêu:** demo 20 phút, tuyến tính, không phụ thuộc mạng ngoài, không thao tác phá dữ liệu. Mọi tài khoản là seed local; mật khẩu lấy từ `docs/DEMO_CHECKLIST_VI.md`, không dán vào slide.

## 0. Preflight (5 phút trước demo)

| Kiểm tra | Cách làm | Kỳ vọng |
|---|---|---|
| Server | mở `http://127.0.0.1:8082/feed` | Feed tải, không lỗi 500 |
| Seed | chạy `node tools/demo-data.mjs` nếu DB mới | In “already present” hoặc tạo dữ liệu, không nhân bản |
| Tài khoản | đăng nhập thử `user` và `admin` | Vào `/feed`, `/admin` thành công |
| H2 sạch | khởi động lại app nếu dữ liệu QA còn sót | Không còn bài “QA …” trên feed |
| Trình duyệt | Chrome, zoom 100%, cửa sổ 1440×900 | Không tràn ngang |
| Tab dự phòng | 1 tab ẩn danh cho `demoan` | Dùng cho realtime/thông báo |
| Firebase | kiểm tra upload avatar | Nếu thiếu service account → **bỏ qua bước upload** |

## 1. Dẫn nhập (1 phút)

“AloUTE là mạng xã hội học thuật cho sinh viên UTE. Hôm nay mình demo 4 nhóm: bảng tin và tương tác, cộng đồng, tin nhắn/thông báo, quản trị. Toàn bộ chạy trên môi trường local với dữ liệu seed.”

## 2. Guest → đăng nhập (2 phút)

1. Mở `/` hoặc `/feed` khi chưa đăng nhập.
   - Kỳ vọng: thấy bài công khai, nút **Đăng nhập/Đăng ký**, không thấy ô soạn bài.
2. Bấm **Đăng nhập** → trang `/login`.
   - Kỳ vọng: form gọn, có link **Quên mật khẩu**, **Đăng ký**.
3. Đăng nhập `user` (seed local).
   - Kỳ vọng: chuyển `/feed`, thấy composer và sidebar chủ đề.
4. Nói: “Phiên dùng cookie HttpOnly; refresh vẫn giữ đăng nhập.”

**Fallback:** nếu login lỗi do môi trường, dùng `admin` (mọi luồng dưới vẫn chạy, trừ privacy của user thường). Không demo đường dẫn bảo vệ khi là guest — đó là BL-22 đã biết.

## 3. Bài viết & tương tác (4 phút)

1. Ở composer, nhập: “Chào AloUTE! Mình đang thử nghiệm tính năng tạo bài viết.” → **Đăng**.
   - Kỳ vọng: chuyển trang chi tiết bài mới.
2. Bấm **Thích** → bộ đếm +1, icon đổi; bấm lại → trở về.
3. Bấm **Lưu** → mở `/saved`, thấy bài; bỏ lưu → bài biến mất khỏi `/saved`.
4. Nhập bình luận → gửi; F5 → bình luận còn.
5. Bấm **Trích dẫn** → nhập nội dung → đăng; quay lại bài gốc, `quoteCount` +1.
6. Bấm **Sao chép liên kết** → dán tab mới → mở đúng bài.
7. (Tuỳ chọn) Viết bài mới, gõ vài chữ, rời trang, quay lại `/post/create` → nháp được khôi phục.

**Không demo:** upload ảnh/avatar nếu Firebase chưa cấu hình.

## 4. Poll (2 phút)

1. Tạo bài mới → **Thêm bình chọn** → câu hỏi + 3 phương án → bật **Cho phép chọn nhiều** → đăng.
2. Chọn 2 phương án → **Bình chọn**.
   - Kỳ vọng: hiện “Lựa chọn của bạn”, tỷ lệ %; F5 giữ kết quả.
3. Nói: “Vote lặp bị từ chối; poll một lựa chọn chỉ cho chọn một.”

## 5. Hồ sơ & quan hệ (2 phút)

1. Vào **Hồ sơ** → **Chỉnh sửa** → đổi tên hiển thị/bio → lưu; F5 giữ nguyên.
2. Mở hồ sơ `demoan` → bấm **Theo dõi** → nhãn đổi; bấm lại → trở về.
3. Chuyển tab **Bài viết / Phản hồi / Ảnh / Đã lưu**.

## 6. Cộng đồng (3 phút)

1. Vào **Cộng đồng** → **Tạo cộng đồng** → tên “CLB Demo”, slug `clb-demo-<thời gian>` → tạo.
   - Kỳ vọng: vào trang cộng đồng, vai trò Trưởng nhóm.
2. Đăng một bài trong cộng đồng.
3. (Nếu có thời gian) Vào **Quản lý** → bật **Duyệt bài**; dùng tab ẩn danh `demoan` join và đăng bài → bài ở trạng thái chờ; quay lại owner duyệt → bài hiển thị.
4. Nói: “Cộng đồng riêng tư ẩn với người ngoài; cơ chế xin vào đang chờ chốt đặc tả.”

## 7. Tin nhắn & thông báo (3 phút)

1. Tab `user`: **Tin nhắn** → mở hội thoại với admin → gửi “Demo realtime”.
2. Tab ẩn danh `demoan`: mở **Thông báo** → thấy badge chưa đọc; bấm **Đã đọc tất cả** → badge về 0.
3. Quay lại tab `user`, gửi thêm một tin.
   - Kỳ vọng: tab `demoan` (nếu đang mở hội thoại) nhận tin không cần reload.
4. Nói: “Tin nhắn escape HTML; mất mạng hiển thị lỗi tiếng Việt và giữ nháp.”

## 8. Quản trị (3 phút)

1. Đăng nhập `admin` → `/admin` → chỉ KPI, danh sách user/bài.
2. Mở **Báo cáo** → chọn report PENDING → **Nhận xử lý**.
3. **Gỡ bài** kèm lý do → kiểm tra bài biến mất khỏi feed công khai.
4. **Khôi phục** bài → bài hiện lại.
5. Mở **Nhật ký kiểm duyệt** → thấy cả hai hành động.

**Không demo:** dùng user thường truy cập `/admin` (BL-22 trả JSON); dùng API trực tiếp.

## 9. Chất lượng (1 phút)

1. Thu nhỏ cửa sổ 360 px: menu mobile, sidebar ẩn, không tràn ngang.
2. Tab qua skip link: “Bỏ qua điều hướng đến nội dung chính”.
3. Nói: “Đã quét axe 20 route, 0 vi phạm serious/critical; Chrome/Edge/Safari engine đạt, Firefox chưa kết luận do môi trường.”

## 10. Kết thúc & fallback

| Tình huống | Cách xử lý |
|---|---|
| Login seed lỗi | Dùng `admin`; hoặc khởi động lại app + seed |
| Upload lỗi | Bỏ qua, nói rõ phụ thuộc Firebase deploy |
| Realtime không nhận | Refresh tab; kiểm tra WebSocket trong Network |
| Trang trắng/500 | Mở tab mới, tránh route bảo vệ khi guest; ghi nhận BL-22 |
| Hết thời gian | Ưu tiên mục 3, 6, 7, 8; bỏ mục 4 và 9 |

**Câu chốt:** “Bản demo đã đạt các luồng chính; 4 lỗi còn lại thuộc xử lý lỗi phân quyền phía backend (BL-22) và đang có ticket bàn giao, không phải lỗi giao diện.”
