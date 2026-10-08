# CHECKPOINT PHASE 2: CONTENT, POST, REACTIONS & MEDIA STORAGE

> **Chịu trách nhiệm chính**: Nguyễn Duy Ngọc (22110381)  
> **Trạng thái**: 🟡 **CẦN SỬA GẤP (75%)**  
> **Mức độ rủi ro**: **Cực kỳ cao (Gây lỗi runtime sập server ở môi trường Dev/Test)**  

---

## 1. CÁC TẦNG ĐÃ ĐỐI SOÁT

### 1.1. Tầng Controller
- `PostController.java` (`/api/posts`):
  - Hỗ trợ đầy đủ các endpoint theo OpenAPI: CRUD Post, Reply, Quote, Like/Unlike, Bookmark/Remove Bookmark, Repost/Remove Repost.
  - **LỖI LOGIC NGHIÊM TRỌNG**:
    - Tại dòng 35: `explore()` gọi `postService.listPublic(page, size, null)` -> **Ép cứng viewerId = null**.
    - Tại dòng 46: `get()` gọi `postService.get(postId, null)` -> **Ép cứng viewerId = null**.
    - Tại dòng 65: `replies()` gọi `postService.replies(postId, page, size, null)` -> **Ép cứng viewerId = null**.
    - *Hậu quả*: Người dùng dù đã đăng nhập (có token) khi xem chi tiết bài hoặc xem bảng tin đều bị coi như khách vãng lai: `isLiked = false`, `isBookmarked = false`, `isReposted = false`, `canDelete = false`, `canReply = false`.
    - *Khắc phục*: Thay `null` bằng `currentUserProvider.getCurrentUserId().orElse(null)`.

- `MediaController.java` (`/api/media`):
  - `POST /upload`: Upload file multipart, kiểm tra mime type và purpose (`AVATAR`, `COVER`, `POST`, `MESSAGE`) -> **Chuẩn**.
  - `GET /quota/me`: Kiểm tra dung lượng đã dùng và hạn mức 150MB -> **Chuẩn**.

### 1.2. Tầng Service & Persistence (Xung đột CSDL H2 vs PostgreSQL)
- `PostServiceImpl.java` & `PostRepository.java`:
  - Ngọc chuyển toàn bộ logic đọc/ghi sang Stored Procedures bằng native queries:
    ```java
    @Query(value = "select * from sp_get_visible_post(:postId, :viewerId)", nativeQuery = true)
    @Query(value = "select * from sp_list_public_posts(:pOffset, :pLimit)", nativeQuery = true)
    @Query(value = "select sp_create_post(...)", nativeQuery = true)
    ```
  - **XUNG ĐỘT HỆ THỐNG CỰC KỲ NGUY HIỂM**:
    - File cấu hình `application-dev.yml` mặc định dùng H2 in-memory:
      ```yaml
      url: jdbc:h2:mem:aloutedb
      database-platform: org.hibernate.dialect.H2Dialect
      sql.init.mode: never
      ```
    - Các hàm `sp_*` được viết bằng cú pháp PostgreSQL (`returns setof posts language sql`). CSDL H2 **hoàn toàn không hỗ trợ** cú pháp này và `sql.init.mode: never` cũng không nạp file procedure!
    - *Hậu quả*: Khi bất kỳ ai chạy backend trên máy cục bộ hoặc chạy integration test, bất kỳ lệnh nào gọi đến Post sẽ ném ngoại lệ: `org.h2.jdbc.JdbcSQLSyntaxErrorException: Function "SP_GET_VISIBLE_POST" not found`.

### 1.3. Tầng Media Storage (Firebase)
- `FirebaseConfig.java` & `FirebaseMediaStorageAdapter.java`:
  - Có cờ bật/tắt `aloute.firebase.enabled`. Khi tắt (dev mode), hệ thống tự sinh mock URL mà không làm crash ứng dụng -> **Rất tốt**.

---

## 2. DANH SÁCH BẤT BIẾN NGHIỆP VỤ (INVARIANTS) CẦN ĐỒNG BỘ

| Bất biến nghiệp vụ | Quy định trong `skills/domain_system.md` | Hiện trạng Code | Trạng thái |
| :--- | :--- | :--- | :---: |
| **Quyền Reply bài viết** | Nếu bài cha có `allow_reply = false` thì cấm reply. | `PostServiceImpl` dòng 57: Đã kiểm tra `!requireVisiblePost(parentPostId, userId).isAllowReply()`. | ✅ ĐẠT |
| **Quyền Quote bài viết** | Nếu bài gốc có `allow_quote = false` thì cấm quote. | `PostServiceImpl` dòng 60: Đã kiểm tra `!requireVisiblePost(quotedPostId, userId).isAllowQuote()`. | ✅ ĐẠT |
| **Đăng bài trong Nhóm** | Chỉ thành viên nhóm mới được đăng bài vào nhóm. | `PostServiceImpl` dòng 63: Đã kiểm tra membership của user trong community. | ✅ ĐẠT |
| **Soft Delete Cascade** | Xóa bài viết phải xóa mềm (`deleted_at`), giữ nguyên tính toàn vẹn dữ liệu reply. | Viết trong `sp_soft_delete_post_cascade`. | ⚠️ Chỉ chạy trên Postgres |
| **Polls (Bình chọn)** | Cho phép tạo bình chọn đơn hoặc đa lựa chọn. | Entity `Poll` và `PollVote` chưa có trên nhánh `master`. | ❌ Chưa có trên master |

---

## 3. HÀNH ĐỘNG CẦN LÀM NGAY CHO PHASE 2
1. Sửa `PostController.java`: Lấy `currentUserProvider.getCurrentUserId().orElse(null)` truyền vào `viewerId` cho các phương thức `explore()`, `get()`, `replies()`.
2. Tạo Docker Compose chạy PostgreSQL thực tế, hoặc viết kèm câu truy vấn JPA/JPQL chuẩn dự phòng song song với Stored Procedure để H2 in-memory không bị văng lỗi.
