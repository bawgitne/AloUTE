# 13 — Demo Script 8–10 phút (phương án thay thế)

**Mục tiêu:** bản demo ngắn cho 8–10 phút, chỉ dùng luồng đã PASS, không phụ thuộc Firebase, không mở route bảo vệ khi guest. Tài khoản là seed local; mật khẩu lấy từ `docs/DEMO_CHECKLIST_VI.md`, không đưa lên slide.

## 0. Preflight (trước khi bấm giờ)

| Kiểm tra | Kỳ vọng |
|---|---|
| Server local + seed (`node tools/demo-data.mjs`) | Feed có bài demo, không lỗi 500 |
| Đăng nhập thử `user`, `admin`, `demoan` | Vào được `/feed`, `/admin`, `/notifications` |
| Chrome 1440×900, zoom 100% | Không tràn ngang |
| Tab dự phòng `demoan` | Dùng cho realtime/thông báo |
| Firebase | Nếu thiếu service account → bỏ qua mọi bước upload |

## 1. 0:00–1:00 — Giới thiệu và guest

“AloUTE là mạng xã hội học thuật cho sinh viên UTE. Demo hôm nay gồm: bảng tin, tương tác, cộng đồng, tin nhắn/thông báo và quản trị. Tất cả chạy local với dữ liệu seed.”

1. Mở `/feed` khi chưa đăng nhập.
2. Chỉ ra bài công khai, nút **Đăng nhập/Đăng ký**, không có composer.
3. Bấm **Đăng nhập**.

## 2. 1:00–2:30 — Đăng nhập và tạo bài

1. Đăng nhập `user` → chuyển `/feed`.
2. Nói: “Phiên dùng cookie HttpOnly; refresh vẫn giữ đăng nhập.”
3. Tạo bài: “Chào AloUTE! Demo 8 phút.” → **Đăng**.
4. Trên bài mới: **Thích** → bộ đếm +1; bình luận một câu; **Lưu** → mở `/saved` thấy bài.
5. Nói: “Like/bookmark/comment đều xác nhận qua API và giữ sau reload.”

**Không demo:** upload ảnh nếu Firebase chưa cấu hình.

## 3. 2:30–4:00 — Cộng đồng

1. Vào **Cộng đồng** → **Tạo cộng đồng** → tên “CLB Demo 8 phút”, slug có timestamp.
2. Vào cộng đồng vừa tạo → đăng một bài ngắn.
3. Mở **Quản lý** → giới thiệu vai trò Trưởng nhóm và tab thành viên.
4. Nói: “Cộng đồng riêng tư đã ẩn với người ngoài; luồng xin tham gia đang chờ chốt đặc tả nên không demo.”

## 4. 4:00–5:30 — Tin nhắn realtime và thông báo

1. Tab `user`: **Tin nhắn** → mở hội thoại với admin → gửi “Demo realtime 8 phút”.
2. Tab ẩn danh `demoan`: mở **Thông báo** → chỉ badge chưa đọc → **Đã đọc tất cả**.
3. Quay lại tab `user`, gửi thêm một tin; nếu tab `demoan` đang mở hội thoại, tin xuất hiện không cần reload.
4. Nói: “Tin nhắn escape HTML; mất mạng hiện lỗi tiếng Việt và giữ nháp.”

## 5. 5:30–7:30 — Quản trị

1. Đăng nhập `admin` → `/admin`: KPI, danh sách user/bài.
2. **Báo cáo** → chọn report PENDING → **Nhận xử lý**.
3. **Gỡ bài** kèm lý do → kiểm tra bài biến mất khỏi feed công khai.
4. **Khôi phục** bài → bài hiện lại; mở **Nhật ký kiểm duyệt** thấy cả hai hành động.

**Không demo:** user thường mở `/admin` (BL-22 trả JSON) hoặc gọi API trực tiếp.

## 6. 7:30–9:00 — Chất lượng và accessibility

1. Thu nhỏ cửa sổ 360 px: menu mobile, sidebar ẩn, không tràn ngang.
2. Tab qua skip link → focus vào nội dung chính.
3. Đo nhanh nút navbar/interaction: hit area 44×44 px.
4. Nói: “Đã quét axe 20 route, 0 vi phạm serious/critical; Chrome/Edge/WebKit đạt, Firefox chưa kết luận do môi trường.”

## 7. 9:00–10:00 — Kết thúc

“Các luồng chính đã chạy end-to-end. Bốn lỗi còn lại là xử lý lỗi phân quyền phía backend (BL-22) — page route trả JSON thay vì HTML/redirect — đã có ticket bàn giao, không phải lỗi giao diện.”

## Fallback

| Tình huống | Cách xử lý |
|---|---|
| Login seed lỗi | Dùng `admin`; khởi động lại app + seed |
| Realtime không nhận | Refresh tab; kiểm tra WebSocket trong Network |
| Upload lỗi | Bỏ qua, nói rõ phụ thuộc Firebase |
| Hết thời gian | Bỏ mục 3 hoặc 5; giữ login, tương tác, realtime, kết luận |
| Trang trắng/500 | Mở tab mới; tránh route bảo vệ khi guest; ghi nhận BL-22 |

**Câu chốt:** “Demo ngắn này chỉ dùng các luồng đã PASS; các giới hạn đã biết được nêu rõ, không tuyên bố production-ready.”
