# ALOUTE — MA TRẬN TRUY XUẤT USE CASE – API – DATABASE – UI
## (Use Case Traceability Matrix)

> **Người thực hiện**: Ngô Anh Bằng (MSSV: 24110166 — Nhóm trưởng)  
> **Giai đoạn**: Tuần 1 (21/09/2026 – 26/09/2026)  
> **Căn cứ tài liệu chuẩn (Source of Truth)**:
> - Kế hoạch 3 tuần: `Nhom13_KeHoachCodeTest.docx`
> - Quy chuẩn kiến trúc & Routing: `skills/INDEX.md`, `skills/system_architecture.md`
> - Đặc tả Domain & CSDL: `skills/domain_system.md`
> - Hợp đồng API: `skills/aloute-openapi.yaml`
> - Luồng màn hình & Wireframe UI: `SCREEN_FLOW.md`, `docs/ui/wireframes/`

---

## 1. MỤC TIÊU & NGUYÊN TẮC THIẾT KẾ

Ma trận truy xuất này thiết lập mối liên kết chặt chẽ **1-1** hoặc **1-N** giữa:
1. **Nghiệp vụ (Use Case)**: Yêu cầu chức năng người dùng cần thực hiện.
2. **Giao tiếp mạng (HTTP / REST API & WebSocket STOMP)**: Endpoint, method, status code.
3. **Cơ sở dữ liệu (Database Schema)**: Bảng quan hệ, khóa chính/ngoại, ràng buộc unique/index.
4. **Giao diện người dùng (UI / Template / Wireframe)**: Màn hình hiển thị và tương tác trực tiếp.

---

## 2. BẢNG MA TRẬN CHI TIẾT (TRACEABILITY MATRIX)

| Mã UC | Tên Use Case | Tác tử (Actor) & Quyền hạn | REST API Endpoint / STOMP Destination | Bảng CSDL liên quan (Tables & Constraints) | Màn hình UI & Wireframe liên quan | Trách nhiệm thực hiện |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **UC01** | Đăng ký tài khoản | Khách vãng lai (Anonymous) | `POST /api/auth/register` | `users`, `profiles`<br>*(UQ: username, email)* | `templates/auth/register.html`<br>`02-register.svg` | **Khôi** (Backend)<br>**Quân** (UI) |
| **UC02** | Đăng nhập hệ thống | Khách vãng lai (Anonymous) | `POST /api/auth/login` | `users`<br>*(Cookie: aloute_access_token)* | `templates/auth/login.html`<br>`01-login.svg` | **Khôi** (Backend)<br>**Quân** (UI) |
| **UC03** | Đăng xuất | Người dùng đã đăng nhập (USER, ADMIN) | `POST /api/auth/logout` | `users`<br>*(Clear JWT cookie)* | Navbar User Menu<br>`fragments/navbar.html` | **Khôi** (Backend)<br>**Quân** (UI) |
| **UC04** | Lấy thông tin cá nhân hiện tại | Người dùng đã đăng nhập (USER, ADMIN) | `GET /api/auth/me` | `users`, `profiles` | Client State / Header Profile Card | **Khôi** (Backend)<br>**Quân** (UI) |
| **UC05** | Xem hồ sơ người dùng | Bất kỳ ai (Public Whitelist) | `GET /api/users/{id}`<br>`GET /api/users/by-username/{username}` | `users`, `profiles`, `follows` | `templates/user/profile.html`<br>`04-profile.svg` | **Khôi** (Backend)<br>**Quân** (UI) |
| **UC06** | Cập nhật hồ sơ cá nhân | Chủ sở hữu tài khoản (Owner only) | `PUT /api/users/{id}` | `profiles`<br>*(FK: user_id)* | `templates/user/profile.html`<br>(Edit Profile Modal) | **Khôi** (Backend)<br>**Quân** (UI) |
| **UC07** | Theo dõi / Bỏ theo dõi người dùng | Người dùng đã đăng nhập (khác targetUser) | `PUT /api/social/follows/{id}`<br>`DELETE /api/social/follows/{id}` | `follows`<br>*(PK: follower_id, following_id)* | `templates/user/profile.html`<br>`templates/user/friends.html` | **Khôi** (Backend)<br>**Quân** (UI) |
| **UC08** | **Tạo bài viết mới** *(Text, Media, Poll, Topic, Community)* | Người dùng đã đăng nhập (Tài khoản không bị cấm) | `POST /api/posts` | `posts`, `post_media`, `media_assets`, `polls`, `poll_options`, `post_topics`, `community_posts` | `templates/post/create.html`<br>`05-create-post.svg` | **Ngọc** (Entity/Post)<br>**Bằng** (Sequence/Test)<br>**Quân** (UI) |
| **UC09** | **Xem chi tiết bài viết & danh sách bình luận** | Người dùng thỏa mãn Post Visibility | `GET /api/posts/{id}`<br>`GET /api/posts/{id}/replies` | `posts`, `post_media`, `users`, `profiles` | `templates/post/detail.html`<br>`06-post-detail.svg` | **Ngọc** (Service/Repo)<br>**Quân** (UI) |
| **UC10** | **Thích / Bỏ thích bài viết** | Người dùng đã đăng nhập (USER, ADMIN) | `PUT /api/posts/{id}/like`<br>`DELETE /api/posts/{id}/like` | `post_likes`<br>*(PK: post_id, user_id)* | Post Card Interaction Bar<br>`_post-card.html` | **Ngọc** (Service/Repo)<br>**Bằng** (Sequence/Test)<br>**Quân** (UI) |
| **UC11** | **Bình luận / Trả lời bài viết (Reply)** | Người dùng thỏa mãn quyền bình luận | `POST /api/posts/{id}/replies` | `posts`<br>*(parent_id, root_id)* | `templates/post/detail.html`<br>`06-post-detail.svg` | **Ngọc** (Service/Repo)<br>**Bằng** (Sequence/Test)<br>**Quân** (UI) |
| **UC12** | Đăng lại / Trích dẫn bài viết (Repost / Quote) | Người dùng đã đăng nhập | `POST /api/posts/{id}/repost`<br>`POST /api/posts/{id}/quote` | `reposts`, `posts`<br>*(repost_of_post_id, quote_of_post_id)* | Post Card Action Dropdown<br>`_post-card.html` | **Ngọc** (Backend)<br>**Quân** (UI) |
| **UC13** | Lưu / Bỏ lưu bài viết (Bookmark) | Người dùng đã đăng nhập | `PUT /api/posts/{id}/bookmark`<br>`DELETE /api/posts/{id}/bookmark` | `bookmarks`<br>*(PK: user_id, post_id)* | `templates/post/saved.html`<br>`_post-card.html` | **Ngọc** (Backend)<br>**Quân** (UI) |
| **UC14** | Xem bảng tin trang chủ (Home Feed / Explore Feed) | Mọi người (Public) hoặc Người dùng đăng nhập | `GET /api/feed`<br>`GET /api/feed/following`<br>`GET /api/feed/trending` | `posts`, `follows`, `post_topics`, `user_topics` | `templates/feed/index.html`<br>`03-home-feed.svg` | **Ngọc** (Query/Feed)<br>**Bằng** (Thuật toán gợi ý)<br>**Quân** (UI) |
| **UC15** | Tìm kiếm tổng hợp (Search) | Người dùng vãng lai hoặc đăng nhập | `GET /api/search?q={keyword}&type={type}` | `users`, `profiles`, `posts`, `topics`, `communities` | `templates/search/index.html`<br>`07-search.svg` | **Ngọc** (Backend)<br>**Quân** (UI) |
| **UC16** | Khám phá & theo dõi chủ đề (Topic) | Người dùng đã đăng nhập | `GET /api/topics`<br>`PUT /api/topics/{id}/follow`<br>`DELETE /api/topics/{id}/follow` | `topics`, `user_topics`<br>*(PK: user_id, topic_id)* | `templates/topic/index.html`<br>Right Sidebar Widgets | **Ngọc** (Backend)<br>**Quân** (UI) |
| **UC17** | Tạo & tham gia cộng đồng (Community) | Người dùng đã đăng nhập | `POST /api/communities`<br>`POST /api/communities/{id}/join` | `communities`, `community_members`<br>*(role: OWNER, ADMIN, MODERATOR, MEMBER)* | `templates/community/index.html`<br>`08-community.svg` | **Ngọc** (Backend)<br>**Quân** (UI) |
| **UC18** | **Tạo cuộc trò chuyện trực tiếp / nhóm (Messaging)** | Người dùng đã đăng nhập (không bị chặn) | `POST /api/conversations` | `conversations`, `conversation_members`<br>*(type: DIRECT, GROUP)* | `templates/messaging/index.html`<br>`09-messaging.svg` | **Bằng** (Backend & Realtime)<br>**Quân** (UI) |
| **UC19** | **Gửi & nhận tin nhắn thời gian thực** | Thành viên trong cuộc trò chuyện | `POST /api/conversations/{id}/messages`<br>STOMP: `/app/chat.send`<br>SUB: `/user/queue/messages` | `messages`<br>*(FK: conversation_id, sender_id)* | `templates/messaging/index.html`<br>`09-messaging.svg` | **Bằng** (Backend & Realtime)<br>**Quân** (UI) |
| **UC20** | **Xem thông báo & Đánh dấu đã đọc** | Người dùng nhận thông báo (Recipient) | `GET /api/notifications`<br>`PUT /api/notifications/{id}/read`<br>STOMP SUB: `/user/queue/notifications` | `notifications`<br>*(FK: recipient_id, actor_id)* | `templates/notification/index.html`<br>`10-notification.svg` | **Bằng** (Backend & Realtime)<br>**Quân** (UI) |
| **UC21** | Báo cáo vi phạm (Report User/Post) | Người dùng đăng nhập | `POST /api/reports` | `reports`<br>*(status: PENDING, REVIEWED, RESOLVED)* | Report Modal trên Post & Profile | **Khôi** (Backend)<br>**Quân** (UI) |
| **UC22** | Bỏ phiếu bình chọn (Vote Poll) | Người dùng đăng nhập (chưa vote hoặc multi-choice) | `POST /api/polls/{id}/vote` | `polls`, `poll_options`, `poll_votes`<br>*(PK: option_id, user_id)* | Post Card Poll Widget | **Ngọc** (Backend)<br>**Quân** (UI) |

---

## 3. ĐÁNH GIÁ ĐỘ PHỦ NGHIỆP VỤ (COVERAGE SUMMARY)

- **Tổng số Use Case cốt lõi được định nghĩa**: 22 Use Cases.
- **Phân công theo nhiệm vụ Tuần 1**:
  - **Khôi**: Đăng nhập (UC02), Cập nhật hồ sơ (UC06), Xác thực & Phân quyền.
  - **Quân**: 10 Mockup Wireframe tương ứng từ UC01 đến UC20.
  - **Ngọc**: Thiết kế CSDL (tables, PK/FK, trigger, index) bao phủ toàn bộ 22 Use Cases.
  - **Bằng**:
    - Chủ trì thiết kế ma trận Use Case này.
    - Vẽ 2 lược đồ Sequence Diagram cốt lõi: **Tạo bài viết (UC08)** và **Tương tác Like/Reply (UC10, UC11)**.
    - Lập bộ Test Cases chuẩn bị cho kiểm thử các luồng bài viết và hệ thống Realtime Messaging / Notification.
