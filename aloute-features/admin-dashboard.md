# Quản trị và dashboard

Admin quản lý hệ thống; Manager tập trung vào vận hành và kiểm duyệt nội dung.

## Quản lý tài khoản

- Tìm kiếm theo username, email, role và trạng thái.
- Xem lịch sử vi phạm và phiên đăng nhập.
- Khóa/mở khóa tài khoản.
- Gán hoặc thu hồi role Manager.
- Thu hồi toàn bộ refresh token.

## Cấu hình

- Danh mục lý do report.
- Từ khóa cấm/cần cảnh báo.
- Giới hạn upload ảnh/video.
- Thời hạn OTP và giới hạn gửi lại trong phạm vi an toàn.
- Bật/tắt đăng ký tài khoản khi bảo trì.

## Dashboard

| Chỉ số | Cách tính |
| --- | --- |
| User mới | Số tài khoản active tạo theo ngày/tháng |
| User hoạt động | User có login hoặc hành động trong kỳ |
| Bài đăng | Tổng bài hợp lệ theo thời gian |
| Engagement | Reaction + comment + share |
| Report | Số report theo trạng thái và loại |
| Moderation SLA | Thời gian trung bình từ OPEN đến kết quả |

## Quy tắc

- Dashboard dùng aggregate query, không tải toàn bộ Entity vào memory.
- Admin action quan trọng yêu cầu nhập lý do.
- Không cho xóa cứng audit log từ giao diện.
- Không hiển thị secret hoặc hash trong dashboard.

## Acceptance criteria

- Biểu đồ có bộ lọc 7 ngày, 30 ngày và tùy chỉnh khoảng ngày.
- Export CSV chỉ chứa trường được phép và được ghi audit.
- Manager không truy cập được màn hình role/configuration của Admin.

