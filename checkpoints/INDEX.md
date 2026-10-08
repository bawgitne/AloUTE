# ALOUTE — MASTER AUDIT CHECKPOINTS & PROGRESS TRACKING

> **Thời điểm cập nhật**: 08/10/2026  
> **Mục tiêu**: Rà soát toàn bộ các tầng Backend (Controller, Service, Repository, Entity, DTO, Security, CSDL), đối soát với `skills/` và hợp đồng API OpenAPI, phát hiện sai lệch, thiếu sót và đồng bộ hệ thống trước ngày nghiệm thu (11/10/2026).

---

## 1. TỔNG QUAN TIẾN ĐỘ THEO CÁC PHASE (ĐÃ CẬP NHẬT SAU KHI SỬA LỖI)

| Phase | Phạm vi nghiệp vụ & Kỹ thuật | Trạng thái | Mức hoàn thành | Mức độ lỗi / Sai lệch | File Checkpoint chi tiết |
| :---: | :--- | :---: | :---: | :---: | :--- |
| **Phase 1** | **Security, Authentication & User Identity** | 🟢 ĐẠT | 95% | Rất thấp (Đã fix CSRF 403 & STOMP HttpOnly) | [`PHASE_1_SECURITY_IDENTITY.md`](./PHASE_1_SECURITY_IDENTITY.md) |
| **Phase 2** | **Content, Post, Reactions & Media Storage** | 🟢 ĐẠT | 95% | Thấp (Đã fix viewerId, feed recommend, post detail) | [`PHASE_2_CONTENT_POST_MEDIA.md`](./PHASE_2_CONTENT_POST_MEDIA.md) |
| **Phase 3** | **Community & Topic Management** | 🟢 ĐẠT | 90% | Thấp (Đã nạp template & Community Controller) | [`PHASE_3_COMMUNITY_TOPIC.md`](./PHASE_3_COMMUNITY_TOPIC.md) |
| **Phase 4** | **Social Graph & Moderation (Follow, Block, Mute, Report)** | 🟢 ĐẠT | 95% | Rất thấp (Đã thêm đầy đủ Block/Mute/Restrict API) | [`PHASE_4_SOCIAL_MODERATION.md`](./PHASE_4_SOCIAL_MODERATION.md) |
| **Phase 5** | **Realtime Messaging, Notification & Feed Recommendation** | 🟢 ĐẠT | 100% | Rất thấp (Đã nối Feed Rec, Read Receipt, Unread Count) | [`PHASE_5_REALTIME_FEED.md`](./PHASE_5_REALTIME_FEED.md) |
| **Phase 6** | **Database Sync, Page Controllers vs UI, Docker & DevOps** | 🟢 ĐẠT | 95% | Rất thấp (Đã có Dockerfile, docker-compose, Postgres init) | [`PHASE_6_DATABASE_API_CROSSCUTTING.md`](./PHASE_6_DATABASE_API_CROSSCUTTING.md) |

---

## 2. TỔNG HỢP CÁC LỖI QUAN TRỌNG ĐÃ ĐƯỢC KHẮC PHỤC TRIỆT ĐỂ

1. ✅ **CSRF 403 trên AJAX/Mutation**: Cấu hình `setCsrfRequestAttributeName(null)` trong `SecurityConfig.java` để cho phép header thô `X-XSRF-TOKEN` hoạt động trơn tru.
2. ✅ **Viewer State trong PostController**: Chuyển hardcoded `null` sang `currentUserProvider.getCurrentUserId().orElse(null)` cho `explore()`, `get()`, `replies()`.
3. ✅ **Thuật toán Feed Recommendation**: Triển khai `GET /api/feed/recommended` kết nối trực tiếp với thuật toán `FeedRecommendationService.rankCandidates(...)`.
4. ✅ **Kích hoạt Notification Tự động**: Bổ sung trigger thông báo khi Like bài viết (`POST_LIKE`), Reply bài viết (`POST_REPLY`) và Follow người dùng (`FOLLOW`).
5. ✅ **Xác thực STOMP qua HttpOnly Cookie**: Tạo `JwtHandshakeInterceptor.java` trích xuất JWT cookie từ HTTP Handshake đưa vào WebSocket session attributes, fallback an toàn trong `StompAuthChannelInterceptor.java`.
6. ✅ **Đánh dấu Đã đọc Tin nhắn (Read Receipt) & Unread Badge**:
   - Bổ sung `POST /api/conversations/{id}/read` trong `ConversationController.java`.
   - Tính toán `unreadCount` động theo `lastReadMessageId` trong `ConversationMember`.
   - Hiển thị badge số tin chưa đọc trên danh sách hội thoại và tự xóa khi mở xem.
7. ✅ **Hoàn thiện API Block, Mute, Restrict**:
   - Đưa `Block.java`, `Mute.java`, `Restriction.java` cùng Repositories vào nhánh chính `current/`.
   - Cài đặt đầy đủ các endpoint `PUT/DELETE` trong `SocialController.java` và `SocialServiceImpl.java`, tự động hủy quan hệ follow 2 chiều khi block.
8. ✅ **Đồng bộ toàn bộ Page Controller & Giao diện Thymeleaf SSR**:
   - `FeedPageController.java` nạp bài viết vào model cho `feed/index.html`.
   - `PostPageController.java` mở trang chi tiết bài viết `/posts/{id}`.
   - Thêm `MessagingPageController.java` (`/messages`), `NotificationPageController.java` (`/notifications`), `CommunityPageController.java` (`/communities`).
   - Cập nhật Navbar và Sidebar điều hướng trực quan.
9. ✅ **Đóng gói Docker & CSDL PostgreSQL Production**:
   - Tạo `Dockerfile` multi-stage build (Maven 17 + Temurin 17 JRE).
   - Tạo `docker-compose.yml` chạy full stack: `postgres:16-alpine`, `app` (Spring Boot), `nginx` (reverse proxy cổng 80/443).
   - Tạo `init-db.sh` khởi tạo CSDL PostgreSQL tự động theo đúng thứ tự: `schema.sql` -> `procedures.sql` -> `triggers.sql` -> `data-seed.sql`.
10. ✅ **Biên dịch & Test xanh 100%**:
   - Biên dịch 146 file Java không còn lỗi nào.
   - Toàn bộ 25 bài kiểm thử unit & integration test chạy thành công (`BUILD SUCCESS`).

---

## 3. DANH SÁCH FILE CHECKPOINT CHI TIẾT
- [x] [PHASE_1_SECURITY_IDENTITY.md](./PHASE_1_SECURITY_IDENTITY.md)
- [x] [PHASE_2_CONTENT_POST_MEDIA.md](./PHASE_2_CONTENT_POST_MEDIA.md)
- [x] [PHASE_3_COMMUNITY_TOPIC.md](./PHASE_3_COMMUNITY_TOPIC.md)
- [x] [PHASE_4_SOCIAL_MODERATION.md](./PHASE_4_SOCIAL_MODERATION.md)
- [x] [PHASE_5_REALTIME_FEED.md](./PHASE_5_REALTIME_FEED.md)
- [x] [PHASE_6_DATABASE_API_CROSSCUTTING.md](./PHASE_6_DATABASE_API_CROSSCUTTING.md)
