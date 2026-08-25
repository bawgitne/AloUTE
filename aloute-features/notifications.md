# Thông báo thời gian thực

Notification thông báo các sự kiện xã hội và quản trị qua WebSocket, đồng thời lưu database để không mất khi offline.

## Loại thông báo

- Lời mời kết bạn và kết quả chấp nhận.
- Reaction, comment, reply và share.
- Mention trong bài/bình luận.
- Tin nhắn chưa đọc.
- Kết quả xử lý report.
- Cảnh cáo, đình chỉ hoặc thay đổi trạng thái tài khoản.
- Mời tham gia nhóm/sự kiện.

## Quy tắc

- Không tự thông báo hành động của chính mình.
- Notification chứa loại, actor, target ID và nội dung tóm tắt; không lưu HTML tùy ý.
- Có trạng thái đã đọc/chưa đọc và thời điểm đọc.
- Thao tác “đánh dấu tất cả đã đọc” chạy theo batch.
- Nếu target không còn quyền xem hoặc đã bị xóa, UI mở trang an toàn thay vì lỗi.

## Endpoint và WebSocket

| Method/Destination | Path | Chức năng |
| --- | --- | --- |
| GET | `/notifications` | Danh sách phân trang |
| PATCH | `/notifications/{id}/read` | Đánh dấu đã đọc |
| PATCH | `/notifications/read-all` | Đọc tất cả |
| DELETE | `/notifications/{id}` | Xóa khỏi hộp thông báo |
| SUBSCRIBE | `/user/queue/notifications` | Nhận realtime |

## Acceptance criteria

- Notification được lưu trước khi đẩy realtime.
- Badge chưa đọc đúng sau refresh và reconnect.
- Chỉ chủ sở hữu đọc/xóa được notification của mình.

