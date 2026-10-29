# ALOUTE — TÀI LIỆU ĐẶC TẢ TEST CASE TUẦN 1
## (Core Use Cases & Realtime Test Specification)

> **Người thực hiện**: Ngô Anh Bằng (MSSV: 24110166 — Nhóm trưởng)  
> **Giai đoạn**: Tuần 1 (21/09/2026 – 26/09/2026)  
> **Căn cứ tài liệu chuẩn**:
> - Kế hoạch 3 tuần: kế hoạch 3 tuần của nhóm
> - Kiến trúc & quy ước: [README.md](../../README.md) §4, [ARCHITECTURE_TEMPLATE.md](../../ARCHITECTURE_TEMPLATE.md)
> - API contract: [docs/api/openapi.yaml](../api/openapi.yaml)
> - Traceability Matrix: `docs/bang/USE_CASE_MATRIX.md`

---

## 1. PHẠM VI & MỤC TIÊU KIỂM THỬ

Tài liệu này đặc tả chi tiết các bộ Test Case phục vụ kiểm thử cho:
1. **Các Use Case cốt lõi Tuần 1**: Tạo bài viết (UC08), Thích bài viết (UC10), Bình luận/Trả lời (UC11).
2. **Bộ Test Case dự bị cho Tuần 2**: Nhắn tin trực tiếp/nhóm (UC18, UC19), Thông báo (UC20), Kết nối WebSocket.
3. Mỗi test case xác định rõ:
   - **Mã định danh (ID)**
   - **Tên kịch bản (Scenario / Description)**
   - **Điều kiện tiên quyết (Pre-conditions)**
   - **Dữ liệu đầu vào (Input / Payload)**
   - **Kết quả mong đợi (Expected HTTP Status, Response Body, DB State)**

---

## 2. BỘ TEST CASES CỐT LÕI (CORE TEST SUITES)

### SUITE 1: TẠO BÀI VIẾT (POST CREATION) — UC08

| Test Case ID | Mục tiêu kiểm thử | Điều kiện tiên quyết | Dữ liệu đầu vào (Payload) | Kết quả mong đợi (HTTP & DB) |
| :--- | :--- | :--- | :--- | :--- |
| **TC_POST_01** | Tạo bài viết hợp lệ (Text thông thường, chế độ PUBLIC) | User đã đăng nhập, tài khoản active | `{"content": "Chào mừng đến với AloUTE!", "visibility": "PUBLIC"}` | • HTTP 201 Created<br>• `ApiResponse.success == true`<br>• `data.content == "Chào mừng đến với AloUTE!"`<br>• Record mới trong bảng `posts` có `status=ACTIVE`. |
| **TC_POST_02** | Từ chối tạo bài viết khi chưa đăng nhập (Anonymous) | Không đính kèm cookie hoặc Bearer JWT | `{"content": "Anonymous post"}` | • HTTP 401 Unauthorized<br>• `ApiResponse.success == false`<br>• Không có bản ghi nào được ghi vào CSDL. |
| **TC_POST_03** | Validate nội dung bài viết rỗng hoặc chỉ có khoảng trắng | User đã đăng nhập | `{"content": "   "}` | • HTTP 400 hoặc 422 Unprocessable Entity<br>• `ErrorCode.VALIDATION_FAILED`<br>• Báo lỗi trường `content`. |
| **TC_POST_04** | Validate độ dài nội dung vượt quá giới hạn ( > 500 ký tự) | User đã đăng nhập | `{"content": "<chuỗi 501 ký tự>"}` | • HTTP 400 hoặc 422<br>• Báo lỗi nội dung tối đa 500 ký tự. |
| **TC_POST_05** | Tạo bài viết kèm danh sách chủ đề (Topics/Hashtags) | User đã đăng nhập, các topic ID tồn tại | `{"content": "Học Spring Boot #java", "topicIds": [1, 2]}` | • HTTP 201 Created<br>• Bản ghi liên kết trong bảng `post_topics`. |
| **TC_POST_06** | Tạo bài viết trong Community nhưng người dùng không phải thành viên | User đã đăng nhập, không nằm trong community 99 | `{"content": "Spam community", "communityId": 99}` | • HTTP 403 Forbidden<br>• `ErrorCode.COMMUNITY_ACCESS_DENIED`<br>• Từ chối lưu bài viết. |

---

### SUITE 2: THÍCH BÀI VIẾT (POST LIKE) — UC10

| Test Case ID | Mục tiêu kiểm thử | Điều kiện tiên quyết | Dữ liệu đầu vào (Payload) | Kết quả mong đợi (HTTP & DB) |
| :--- | :--- | :--- | :--- | :--- |
| **TC_LIKE_01** | Thích bài viết thành công lần đầu | User đăng nhập, bài viết tồn tại, chưa like | `PUT /api/posts/{postId}/like` | • HTTP 200 OK<br>• `data.liked == true`<br>• `data.likeCount` tăng thêm 1<br>• Record được tạo trong `post_likes`.<br>• Tạo thông báo `POST_LIKE` gửi cho tác giả. |
| **TC_LIKE_02** | Tính lũy đẳng (Idempotent): Like lại bài viết đã thích | User đăng nhập, đã like bài viết trước đó | `PUT /api/posts/{postId}/like` | • HTTP 200 OK<br>• `data.liked == true`<br>• `likeCount` không bị tăng trùng lặp<br>• Không ném lỗi Duplicate Key Exception. |
| **TC_LIKE_03** | Bỏ thích bài viết (Unlike) | User đăng nhập, đã like bài viết | `DELETE /api/posts/{postId}/like` | • HTTP 200 OK<br>• `data.liked == false`<br>• `data.likeCount` giảm đi 1<br>• Record bị xóa khỏi `post_likes`. |
| **TC_LIKE_04** | Like bài viết không tồn tại (hoặc đã bị xóa mềm) | User đăng nhập, postId 999999 không tồn tại | `PUT /api/posts/999999/like` | • HTTP 404 Not Found<br>• `ErrorCode.POST_NOT_FOUND`. |

---

### SUITE 3: BÌNH LUẬN / TRẢ LỜI (POST REPLY) — UC11

| Test Case ID | Mục tiêu kiểm thử | Điều kiện tiên quyết | Dữ liệu đầu vào (Payload) | Kết quả mong đợi (HTTP & DB) |
| :--- | :--- | :--- | :--- | :--- |
| **TC_REPLY_01** | Trả lời trực tiếp bài viết gốc (Root Post) | User đăng nhập, root post tồn tại | `POST /api/posts/{rootPostId}/replies`<br>`{"content": "Bài viết rất hay!"}` | • HTTP 201 Created<br>• Reply post được lưu với `parent_id = rootPostId`, `root_id = rootPostId`.<br>• `replyCount` của root post tăng 1.<br>• Tạo thông báo `POST_REPLY` cho tác giả. |
| **TC_REPLY_02** | Trả lời lồng nhau (Nested Reply trên một bình luận khác) | User đăng nhập, reply 10 đã thuộc root 5 | `POST /api/posts/10/replies`<br>`{"content": "Đồng ý với bạn"}` | • HTTP 201 Created<br>• Reply mới có `parent_id = 10`, `root_id = 5`.<br>• Đảm bảo luồng cây bình luận được bảo toàn. |
| **TC_REPLY_03** | Từ chối bình luận khi bài viết giới hạn quyền (Reply Permission) | Root post đặt `reply_control = ACCOUNTS_YOU_FOLLOW`, user chưa được follow | `POST /api/posts/{rootPostId}/replies`<br>`{"content": "Bình luận dạo"}` | • HTTP 403 Forbidden<br>• `ErrorCode.REPLY_NOT_ALLOWED`. |

---

### SUITE 4: DỰ BỊ CHO REALTIME MESSAGING & NOTIFICATION (TUẦN 2)

| Test Case ID | Mục tiêu kiểm thử | Điều kiện tiên quyết | Endpoint & Payload | Kết quả mong đợi |
| :--- | :--- | :--- | :--- | :--- |
| **TC_MSG_01** | Tạo hội thoại 1-1 giữa 2 người dùng | 2 người dùng chưa chặn nhau (Block) | `POST /api/conversations`<br>`{"type": "DIRECT", "targetUserId": 2}` | • HTTP 201 Created<br>• Conversation mới kiểu `DIRECT`, 2 members trong bảng `conversation_members`. |
| **TC_MSG_02** | Ngăn chặn tạo hội thoại trực tiếp với chính mình | User A đăng nhập | `POST /api/conversations`<br>`{"type": "DIRECT", "targetUserId": <userId_A>}` | • HTTP 400 Bad Request<br>• Không cho phép tự chat 1-1 với chính mình. |
| **TC_MSG_03** | Gửi tin nhắn và lưu trữ CSDL trước khi bắn Socket | Người dùng là thành viên hội thoại | `POST /api/conversations/{id}/messages`<br>`{"content": "Xin chào!"}` | • HTTP 201 Created / 200 OK<br>• Tin nhắn lưu vào bảng `messages`.<br>• Bắn event STOMP tới topic `/user/{targetId}/queue/messages`. |
| **TC_NOTIF_01** | Lấy số lượng thông báo chưa đọc (Unread Count) | User có 3 thông báo chưa đọc | `GET /api/notifications/unread-count` | • HTTP 200 OK<br>• `data.unreadCount == 3`. |
| **TC_NOTIF_02** | Đánh dấu một thông báo là đã đọc | User sở hữu thông báo đó | `PUT /api/notifications/{id}/read` | • HTTP 200 OK<br>• Trường `is_read = true` trong CSDL. |

---

## 3. CHECKLIST KIỂM THỬ CHO DEVELOPER (EXECUTION CHECKLIST)

- [x] Đã map 100% các Use Case cốt lõi vào Traceability Matrix.
- [x] Đã định nghĩa HTTP method, endpoint, status code chuẩn theo OpenAPI Contract.
- [x] Đã bao hàm các trường hợp thành công (Happy Path) và ngoại lệ (Validation, Auth, Forbidden, Not Found).
- [x] Đã chuẩn bị sẵn luồng kiểm thử cho WebSocket và Realtime Messaging/Notification phục vụ Tuần 2 & Tuần 3.
