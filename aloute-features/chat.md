# Chat thời gian thực

Feature hỗ trợ hội thoại cá nhân, trạng thái online, đang nhập và trạng thái tin nhắn bằng WebSocket/STOMP.

## Chức năng

- Tạo hoặc mở hội thoại giữa hai User.
- Gửi text, ảnh, video hoặc file hợp lệ.
- Nhận tin nhắn ngay qua WebSocket.
- Trạng thái `SENT`, `DELIVERED`, `READ`.
- Hiển thị online/offline và “đang nhập”.
- User được bật/tắt read receipt, typing indicator và người được xem online/last seen.
- Phân trang lịch sử tin nhắn theo cursor.
- Thu hồi tin nhắn của mình; dữ liệu vẫn giữ cho audit khi cần.

## Luồng gửi tin

1. Client kết nối WebSocket bằng access token hợp lệ.
2. Client gửi message DTO đến application destination.
3. Server kiểm tra membership, block và nội dung.
4. Server lưu message trong PostgreSQL.
5. Sau khi transaction thành công, message được publish đến người nhận.
6. Nếu người nhận offline, notification chưa đọc được tạo.

## WebSocket destination

| Destination | Mục đích |
| --- | --- |
| `/app/chat.send` | Client gửi tin nhắn |
| `/app/chat.typing` | Gửi trạng thái đang nhập |
| `/user/queue/messages` | Nhận tin cá nhân |
| `/user/queue/chat-status` | Nhận delivered/read/typing |

## Presence: isOnline và lastSeen

- `isOnline` là trạng thái tạm thời; không ghi database theo mỗi heartbeat.
- `SessionConnected` tăng connection counter; User online khi có ít nhất một connection sống.
- Client heartbeat; connection im lặng quá 45 giây được xem là stale.
- Khi connection cuối đóng, cập nhật `last_seen_at` đúng một lần.
- Nhiều tab/thiết bị dùng counter nên đóng một tab không làm User thành offline.
- Privacy: `EVERYONE`, `CONTACTS`, `NOBODY`; người không được xem chỉ thấy “hoạt động gần đây”.
- Block luôn che online/last seen cho hai phía.

## Typing: isTyping

- Client phát tối đa một typing event mỗi 2 giây, không gửi theo từng phím.
- Event gồm `conversationId`, `isTyping`, timestamp; actor lấy từ Principal, không tin client.
- Typing là ephemeral, không lưu database, tự hết hạn sau 5 giây.
- Chỉ conversation member nhận event.
- Nếu tắt typing indicator, User không phát và cũng không xem indicator theo chính sách đối ứng.

## Read receipt và message request

- User tắt read receipt thì hai phía chỉ thấy `DELIVERED`, không thấy `READ`.
- Tin từ người chưa kết nối vào `MESSAGE_REQUEST`: Accept, Delete hoặc Block.
- Trước khi Accept, sender không thấy read receipt và không được gửi media/file.
- Conversation có disappearing timer: off, 1 giờ, 1 ngày hoặc 7 ngày; không tuyên bố E2EE.

## Acceptance criteria

- Không publish trước khi database commit thành công.
- User ngoài conversation không thể subscribe hoặc gửi tin.
- Hai tài khoản block nhau không thể tạo/gửi hội thoại.
- Reconnect không làm mất tin; client đồng bộ theo message ID cuối.
- Presence đúng với nhiều tab và mất mạng đột ngột; typing được throttle/timeout, không lưu DB.
