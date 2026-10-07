# ALOUTE — BÁO CÁO KẾT QUẢ & BẰNG CHỨNG THỰC HIỆN TUẦN 2
## (Week 2 Deliverables & Evidence — Ngô Anh Bằng)

> **Người thực hiện**: Ngô Anh Bằng (MSSV: 24110166 — Nhóm trưởng)  
> **Vai trò**: Realtime, Communication & Project Lead  
> **Thời gian thực hiện**: Tuần 2 (27/09/2026 – 04/10/2026)  
> **Căn cứ kế hoạch**: `Nhom13_KeHoachCodeTest.docx` (Nộp GVHD Th.S Nguyễn Trần Thi Văn)

---

## 1. TỔNG KẾT KẾT QUẢ THỰC HIỆN TUẦN 2

Theo đúng bảng kế hoạch 3 tuần chính thức nộp GVHD, nhóm trưởng Ngô Anh Bằng đã hoàn thành 100% các hạng mục Tuần 2:

| STT | Nhiệm vụ theo Kế hoạch (`Nhom13_KeHoachCodeTest.docx`) | Sản phẩm bàn giao cụ thể | Trạng thái |
| :---: | :--- | :--- | :---: |
| 1 | **Vẽ các Sequence Diagram chính còn lại** | • [`docs/bang/sequence/send-message-sequence.puml`](sequence/send-message-sequence.puml) & [`send-message-sequence.svg`](sequence/send-message-sequence.svg)<br>• [`docs/bang/sequence/notification-flow-sequence.puml`](sequence/notification-flow-sequence.puml) & [`notification-flow-sequence.svg`](sequence/notification-flow-sequence.svg) | **ĐÃ HOÀN THÀNH** |
| 2 | **Xây dựng module Messaging (Direct/Group)** | • Entities: `Conversation`, `ConversationMember`, `ConversationType`, `Message`<br>• Repositories: `ConversationRepository`, `ConversationMemberRepository`, `MessageRepository`<br>• DTOs: `CreateConversationRequest`, `SendMessageRequest`, `ConversationResponse`, `MessageResponse`<br>• Service: `ConversationService`<br>• REST API: `ConversationController` (`/api/conversations`) | **ĐÃ HOÀN THÀNH** |
| 3 | **Xây dựng module Notification** | • Entity: `Notification`<br>• Repository: `NotificationRepository`<br>• DTOs: `NotificationResponse`, `UnreadCountResponse`<br>• Service: `NotificationService`<br>• REST API: `NotificationController` (`/api/notifications`) | **ĐÃ HOÀN THÀNH** |
| 4 | **Cấu hình Spring WebSocket STOMP (Realtime Transport)** | • `pom.xml`: Tích hợp `spring-boot-starter-websocket`<br>• `com.aloute.config.WebSocketConfig`: STOMP Broker `/topic`, `/queue`, app prefix `/app`, user prefix `/user`, endpoint `/ws`<br>• `ChatWebSocketController`: `@MessageMapping("/chat.send/{id}")`<br>• Cập nhật `SecurityWhitelist` cho route `/ws/**` | **ĐÃ HOÀN THÀNH** |
| 5 | **Bổ sung Test Cases & Automation Unit Test Suites** | • [`docs/bang/TEST_CASES_WEEK2.md`](TEST_CASES_WEEK2.md): 19 kịch bản test chi tiết<br>• `MessagingAndNotificationServiceTest.java`: Unit tests bao phủ toàn bộ invariant nghiệp vụ | **ĐÃ HOÀN THÀNH** |

---

## 2. CHI TIẾT CÁC SẢN PHẨM BÀN GIAO

### 2.1. Lược đồ tuần tự (Sequence Diagrams)
1. **Gửi tin nhắn & Realtime Chat (`send-message-sequence.svg`)**:
   - Thể hiện chi tiết quy trình gửi tin qua HTTP POST hoặc STOMP SEND.
   - Kiểm tra tư cách thành viên cuộc trò chuyện (`existsByConversationIdAndUserId`).
   - Tuân thủ nguyên tắc **Persist-Before-Publish**: Lưu tin nhắn vào CSDL SQL trước, sau đó mới dùng `SimpMessagingTemplate` đẩy tới topic riêng `/user/{recipientId}/queue/messages`.
2. **Thông báo thời gian thực & Đánh dấu đã đọc (`notification-flow-sequence.svg`)**:
   - Tự động kích hoạt khi có sự kiện nghiệp vụ (Like, Reply, Follow).
   - Ngăn chặn tự thông báo cho chính mình (`recipientId != actorId`).
   - Đẩy thông báo thời gian thực tới `/user/{recipientId}/queue/notifications`.

### 2.2. Kiến trúc Module Messaging & Notification
- Tách biệt rõ ràng các tầng: Entity $\rightarrow$ Repository $\rightarrow$ Service $\rightarrow$ Controller $\rightarrow$ WebSocket Handler.
- Chuẩn hóa mã phản hồi theo envelope thống nhất `ApiResponse<T>` và `PageMeta`.
- Endpoint chuẩn OpenAPI 100%:
  - `GET /api/conversations`, `POST /api/conversations`, `GET /api/conversations/{id}`, `GET /api/conversations/{id}/messages`, `POST /api/conversations/{id}/messages`.
  - `GET /api/notifications`, `GET /api/notifications/unread-count`, `PATCH /api/notifications/{id}/read`, `PATCH /api/notifications/read-all`, `DELETE /api/notifications/{id}`.

### 2.3. Kiểm thử tự động (Unit Test Suites)
- Bộ test `MessagingAndNotificationServiceTest` kiểm tra:
  - Ngăn tạo cuộc trò chuyện DIRECT với bản thân (`TC_CONV_03`).
  - Lũy đẳng khi tạo cuộc trò chuyện đã tồn tại (`TC_CONV_02`).
  - Chặn người lạ gửi tin nhắn (`TC_MSG_03`).
  - Ngăn tạo thông báo khi tự tương tác với chính mình (`TC_NOTIF_02`).
  - Tính toán số lượng thông báo chưa đọc chính xác (`TC_NOTIF_03`).

---

## 3. KẾ HOẠCH BẮT TAY VÀO TUẦN 3 (05/10 – 11/10)

1. **Tích hợp Realtime toàn diện**:
   - Hoàn thiện xử lý trạng thái tin nhắn (đã xem, typing indicator).
   - Tích hợp toast notification và realtime counter badge lên giao diện Thymeleaf của Quân.
2. **Kiểm thử E2E & Tải**:
   - Kiểm thử đồng thời WebSocket connections và tái kết nối (reconnect).
3. **Nghiên cứu thuật toán gợi ý bài viết (Feed Recommendation)**:
   - Nghiên cứu thuật toán tính điểm relevance score dựa trên: mối quan hệ follow, tương tác gần nhất, chủ đề quan tâm (Topics/Tags), thời gian đăng bài (recency decay).
   - Viết báo cáo đề xuất hướng áp dụng vào hệ thống.
