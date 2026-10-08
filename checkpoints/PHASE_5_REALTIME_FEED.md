# CHECKPOINT PHASE 5: REALTIME MESSAGING, NOTIFICATION & FEED RECOMMENDATION

> **Chịu trách nhiệm chính**: Ngô Anh Bằng (24110166)  
> **Trạng thái**: 🟢 **HOÀN THÀNH (100%)**  
> **Mức độ rủi ro**: Rất thấp (Đã nối kết toàn diện Backend, STOMP Interceptor, UI và Thuật toán gợi ý)  

---

## 1. CÁC TẦNG ĐÃ ĐỐI SOÁT & HOÀN THIỆN

### 1.1. Tầng Controller & WebSocket STOMP
- `ConversationController.java` (`/api/conversations`):
  - `GET /`: Danh sách cuộc trò chuyện của user kèm phân trang, tin nhắn mới nhất, và bộ đếm `unreadCount` thực tế.
  - `POST /`: Tạo cuộc trò chuyện mới (hỗ trợ DIRECT 1-1 và GROUP).
  - `GET /{conversationId}/messages`: Danh sách tin nhắn phân trang.
  - `POST /{conversationId}/messages`: Gửi tin nhắn qua REST API, tự động cập nhật `lastReadMessageId` của người gửi, phát STOMP realtime `convertAndSendToUser`.
  - `POST /{conversationId}/read` — [MỚI]: Đánh dấu đã đọc cuộc trò chuyện, cập nhật con trỏ `lastReadMessageId` và reset `unreadCount` trên UI.
- `NotificationController.java` (`/api/notifications`):
  - `GET /`: Lấy thông báo (lọc theo ALL/UNREAD, phân trang).
  - `GET /unread-count`: Đếm nhanh số thông báo chưa đọc hiển thị badge đỏ trên navbar.
  - `PATCH /{id}/read`: Đánh dấu đã đọc từng thông báo.
  - `PATCH /read-all`: Đánh dấu đã đọc tất cả.
  - `DELETE /{id}`: Xóa thông báo với kiểm tra quyền sở hữu recipient.

### 1.2. WebSocket STOMP Xác thực HttpOnly Cookie — [ĐÃ GIẢI QUYẾT]
- `JwtHandshakeInterceptor.java`: Đọc cookie HttpOnly `aloute_access_token` ngay trong HTTP Handshake request ban đầu từ trình duyệt và lưu vào attributes của WebSocket session.
- `StompAuthChannelInterceptor.java`: Đọc token từ header `Authorization: Bearer <token>`, header STOMP `aloute_access_token`, và fallback sang session attributes thu thập từ `JwtHandshakeInterceptor`.
- Trình duyệt không cần đọc HttpOnly cookie từ JavaScript vẫn duy trì phiên STOMP realtime an toàn 100%.

### 1.3. Thuật toán Gợi ý Bảng tin (Feed Recommendation - Tuần 3) — [ĐÃ NỐI DÂY]
- Đã thêm endpoint `GET /api/feed/recommended` trong `PostController.java`.
- Cài đặt `PostServiceImpl.getRecommendedFeed(...)`:
  - Lấy ứng viên bài viết công khai.
  - Ánh xạ sang `PostCandidate` với các điểm tương tác (`likeCount`, `replyCount`, `repostCount`, `quoteCount`, `isFollowingAuthor`).
  - Gọi thuật toán xếp hạng đa mục tiêu `feedRecommendationService.rankCandidates(...)`.
  - Trả về danh sách bài viết đã sắp xếp theo độ phù hợp và độ mới (Recency decay).

### 1.4. Tự động Kích hoạt Thông báo (Notification Triggers)
- Khi Like bài viết: Kích hoạt tạo thông báo `POST_LIKE` tới tác giả.
- Khi Reply bài viết: Kích hoạt tạo thông báo `POST_REPLY` tới tác giả bài viết cha.
- Khi Follow người dùng: Kích hoạt tạo thông báo `FOLLOW` tới người được theo dõi.

### 1.5. Giao diện & Điều hướng SSR
- Tích hợp `templates/messaging/index.html` và `static/js/features/messaging/messaging.js`.
- Tích hợp `templates/notification/index.html` và `static/js/features/notification/notification.js`.
- Đã tạo `MessagingPageController.java` (`/messages`) và `NotificationPageController.java` (`/notifications`).
- Cập nhật Navbar và Sidebar liên kết trực tiếp tới các trang Tin nhắn và Thông báo.
