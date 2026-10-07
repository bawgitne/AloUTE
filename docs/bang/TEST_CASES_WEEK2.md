# ALOUTE — TÀI LIỆU ĐẶC TẢ TEST CASE TUẦN 2
## (Messaging, Notification & Realtime STOMP Test Specification)

> **Người thực hiện**: Ngô Anh Bằng (MSSV: 24110166 — Nhóm trưởng)  
> **Giai đoạn**: Tuần 2 (27/09/2026 – 04/10/2026)  
> **Căn cứ kế hoạch**: `Nhom13_KeHoachCodeTest.docx` (Nộp GVHD Th.S Nguyễn Trần Thi Văn)  
> **Source of Truth**: `skills/domain_system.md`, `skills/aloute-openapi.yaml`, `skills/system_architecture.md`

---

## 1. PHẠM VI & MỤC TIÊU KIỂM THỬ TUẦN 2

Tuần 2 tập trung kiểm thử toàn diện 3 module do Bằng xây dựng:
1. **Module Nhắn tin (Messaging - Direct & Group)**: Tạo cuộc trò chuyện, kiểm soát quyền thành viên, gửi tin nhắn, lưu trữ lịch sử tin nhắn.
2. **Module Thông báo (Notifications)**: Tự động phát sinh thông báo khi có tương tác (Like, Reply, Follow), đếm số lượng chưa đọc, đánh dấu đã đọc.
3. **Cơ chế Realtime WebSocket STOMP**: Đảm bảo nguyên tắc **Persist-Before-Publish** (Lưu CSDL trước khi đẩy tin qua STOMP Broker tới `/user/queue/messages` và `/user/queue/notifications`).

---

## 2. BỘ TEST CASES CHI TIẾT (WEEK 2 TEST SUITES)

### SUITE 1: QUẢN LÝ CUỘC TRÒ CHUYỆN (CONVERSATION MANAGEMENT)

| Test Case ID | Mục tiêu kịch bản | Tiền điều kiện | Payload / Request | Kết quả mong đợi (HTTP & Database) |
| :--- | :--- | :--- | :--- | :--- |
| **TC_CONV_01** | Tạo cuộc trò chuyện trực tiếp (DIRECT) giữa 2 người dùng | User 1 & User 2 tồn tại, chưa chặn nhau | `POST /api/conversations`<br>`{"type": "DIRECT", "targetUserId": 2}` | • HTTP 201 Created<br>• `data.type == "DIRECT"`<br>• Bảng `conversations` có 1 bản ghi mới<br>• Bảng `conversation_members` có 2 bản ghi liên kết. |
| **TC_CONV_02** | Tính lũy đẳng: Tạo lại cuộc trò chuyện DIRECT đã tồn tại giữa 2 người | User 1 & User 2 đã có cuộc trò chuyện DIRECT | `POST /api/conversations`<br>`{"type": "DIRECT", "targetUserId": 2}` | • HTTP 200 OK / 201 Created<br>• Trả về conversation ID cũ đã tồn tại<br>• Không tạo bản ghi trùng lặp trong CSDL. |
| **TC_CONV_03** | Từ chối tạo cuộc trò chuyện DIRECT với chính mình | User 1 đăng nhập | `POST /api/conversations`<br>`{"type": "DIRECT", "targetUserId": 1}` | • HTTP 400 Bad Request<br>• `code == "VALIDATION_FAILED"`<br>• Message: "Cannot create a direct conversation with yourself". |
| **TC_CONV_04** | Lấy danh sách các cuộc trò chuyện của người dùng hiện tại | User 1 có 3 cuộc trò chuyện | `GET /api/conversations?page=0&size=20` | • HTTP 200 OK<br>• `data.length == 3`<br>• Sắp xếp theo `updatedAt` giảm dần. |
| **TC_CONV_05** | Xem chi tiết cuộc trò chuyện mà mình không phải thành viên | User 3 không thuộc conversation 10 | `GET /api/conversations/10` | • HTTP 403 Forbidden<br>• `code == "FORBIDDEN"`<br>• Message: "You are not a member of this conversation". |

---

### SUITE 2: GỬI VÀ NHẬN TIN NHẮN (MESSAGE HANDLING)

| Test Case ID | Mục tiêu kịch bản | Tiền điều kiện | Payload / Request | Kết quả mong đợi (HTTP & Database) |
| :--- | :--- | :--- | :--- | :--- |
| **TC_MSG_01** | Gửi tin nhắn hợp lệ vào cuộc trò chuyện | User 1 là thành viên conversation 5 | `POST /api/conversations/5/messages`<br>`{"content": "Xin chào bạn!"}` | • HTTP 201 Created<br>• Record mới trong bảng `messages`<br>• `conversations.updated_at` được cập nhật thời gian mới nhất<br>• Bắn event STOMP tới `/user/2/queue/messages`. |
| **TC_MSG_02** | Validate nội dung tin nhắn rỗng hoặc chỉ có khoảng trắng | User 1 là thành viên conversation 5 | `POST /api/conversations/5/messages`<br>`{"content": "   "}` | • HTTP 400 Bad Request<br>• Báo lỗi validation trường `content`. |
| **TC_MSG_03** | Người lạ cố tình gửi tin nhắn vào cuộc trò chuyện người khác | User 99 không phải thành viên conv 5 | `POST /api/conversations/5/messages`<br>`{"content": "Spam"}` | • HTTP 403 Forbidden<br>• `code == "FORBIDDEN"`. |
| **TC_MSG_04** | Tải lịch sử tin nhắn phân trang (Pagination) | Cuộc trò chuyện có 50 tin nhắn | `GET /api/conversations/5/messages?page=0&size=20` | • HTTP 200 OK<br>• `data.length == 20`<br>• Tin nhắn mới nhất nằm ở đầu (`createdAt` DESC). |

---

### SUITE 3: QUẢN LÝ THÔNG BÁO (NOTIFICATION SYSTEM)

| Test Case ID | Mục tiêu kịch bản | Tiền điều kiện | Request / Trigger | Kết quả mong đợi |
| :--- | :--- | :--- | :--- | :--- |
| **TC_NOTIF_01** | Tạo thông báo tự động khi có tương tác khác chủ sở hữu | User A thích bài viết của User B | Hệ thống trigger `createNotification(recipient=B, actor=A, type=POST_LIKE)` | • Record mới trong `notifications` có `is_read = false`<br>• Bắn event STOMP tới `/user/{B}/queue/notifications`. |
| **TC_NOTIF_02** | Ngăn tạo thông báo khi tự tương tác với chính bài viết của mình | User A thích bài viết của chính User A | Trigger `createNotification(recipient=A, actor=A)` | • Không lưu bản ghi nào vào CSDL<br>• Không bắn event STOMP. |
| **TC_NOTIF_03** | Lấy số lượng thông báo chưa đọc (Unread Count) | User B có 5 thông báo chưa đọc | `GET /api/notifications/unread-count` | • HTTP 200 OK<br>• `data.unreadCount == 5`. |
| **TC_NOTIF_04** | Đánh dấu 1 thông báo là đã đọc (Mark as Read) | Notification 8 đang `is_read = false` | `PATCH /api/notifications/8/read` | • HTTP 200 OK<br>• `data.isRead == true`<br>• Số lượng unread count giảm 1. |
| **TC_NOTIF_05** | Đánh dấu tất cả thông báo là đã đọc (Mark All Read) | User có 10 thông báo chưa đọc | `PATCH /api/notifications/read-all` | • HTTP 200 OK<br>• Toàn bộ `is_read` của user chuyển thành `true`. |
| **TC_NOTIF_06** | Xóa thông báo khỏi danh sách | User sở hữu notification 8 | `DELETE /api/notifications/8` | • HTTP 200 OK<br>• Record 8 bị xóa khỏi bảng `notifications`. |

---

### SUITE 4: SPRING WEBSOCKET (STOMP) REALTIME INTEGRATION

| Test Case ID | Mục tiêu kịch bản | Destination | Mô tả kiểm thử | Kết quả mong đợi |
| :--- | :--- | :--- | :--- | :--- |
| **TC_WS_01** | Kết nối Handshake WebSocket | `/ws` (SockJS & Native) | Client gửi CONNECT frame kèm token xác thực | • Handshake thành công (HTTP 101 Switching Protocols)<br>• STOMP CONNECTED frame trả về. |
| **TC_WS_02** | Đăng ký nhận tin nhắn riêng tư | `/user/queue/messages` | User đăng ký topic cá nhân | • SUBSCRIBE frame thành công<br>• Khi có tin nhắn đến, nhận được payload `MessageResponse`. |
| **TC_WS_03** | Đăng ký nhận thông báo thời gian thực | `/user/queue/notifications` | User đăng ký topic thông báo | • Nhận payload `NotificationResponse` ngay khi có người like/reply/follow. |
| **TC_WS_04** | Gửi tin nhắn qua STOMP MessageMapping | `/app/chat.send/{conversationId}` | Client gửi SEND frame kèm `SendMessageRequest` | • Handler gọi `ConversationService.sendMessage()`<br>• Persist CSDL và broadcast realtime. |
