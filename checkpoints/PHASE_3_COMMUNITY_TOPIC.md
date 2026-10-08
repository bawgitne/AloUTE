# CHECKPOINT PHASE 3: COMMUNITY & TOPIC MANAGEMENT

> **Chịu trách nhiệm chính**: Nguyễn Duy Ngọc (22110381)  
> **Trạng thái**: 🟡 **CẦN SỬA (70%)**  
> **Mức độ rủi ro**: Trung bình đến Cao  

---

## 1. CÁC TẦNG ĐÃ ĐỐI SOÁT

### 1.1. Tầng Controller
- `CommunityController.java` (`/api/communities`):
  - Hỗ trợ đầy đủ các REST API theo OpenAPI:
    - `POST /`: Tạo cộng đồng mới (người tạo tự động trở thành `OWNER`).
    - `GET /`: Danh sách cộng đồng (hỗ trợ tìm kiếm từ khóa `keyword`, phân trang).
    - `GET /{communityId}`: Xem chi tiết cộng đồng.
    - `PATCH /{communityId}`: Cập nhật thông tin cộng đồng (kiểm tra quyền OWNER/ADMIN).
    - `DELETE /{communityId}`: Xóa cộng đồng (chỉ OWNER mới được xóa).
    - `POST /{communityId}/join` & `POST /{communityId}/leave`: Tham gia / Rời nhóm.
    - `PATCH /{communityId}/members/{targetUserId}/role`: Cập nhật vai trò thành viên (`ADMIN`, `MODERATOR`, `MEMBER`).
- `TopicController.java` (`/api/topics`):
  - `GET /`: Danh sách chủ đề thảo luận.
  - `GET /{topicId}`: Chi tiết chủ đề.
  - `POST /{topicId}/follow` & `DELETE /{topicId}/follow`: Theo dõi / Hủy theo dõi chủ đề.
- `UserTopicController.java`:
  - `GET /api/users/{userId}/topics`: Lấy danh sách các chủ đề mà người dùng đang quan tâm.

### 1.2. Tầng Service & Nghiệp vụ Vai trò (Role Hierarchy)
- `CommunityServiceImpl.java`:
  - Quản lý 4 cấp độ vai trò: `OWNER`, `ADMIN`, `MODERATOR`, `MEMBER`.
  - Nghiệp vụ bảo vệ:
    - Chỉ OWNER mới có quyền chuyển giao quyền sở hữu hoặc xóa nhóm.
    - OWNER không thể tự ý rời nhóm nếu chưa chuyển giao quyền sở hữu cho người khác.
    - Thành viên bị cấm không được đăng bài hoặc tương tác.
- **Vấn đề CSDL**: Tương tự như Phase 2, `CommunityRepository` và `CommunityMemberRepository` phụ thuộc hoàn toàn vào Stored Procedures (`sp_join_community`, `sp_update_community_role`, `sp_list_communities`), nên không chạy được với cấu hình H2 mặc định.

### 1.3. Khoảng trống Giao diện (Frontend Gap)
- **Thiếu `CommunityPageController.java`**: Chưa có controller điều hướng các trang HTML SSR (`/communities`, `/c/{name}`).
- **Template rỗng**: Thư mục `src/main/resources/templates/community/` chỉ có duy nhất file giữ chỗ `.gitkeep`. Người dùng trên giao diện web hiện chưa có trang để bấm vào xem hoặc tham gia nhóm.

---

## 2. HÀNH ĐỘNG CẦN LÀM CHO PHASE 3
1. Bổ sung `CommunityPageController.java` map route `/communities` và `/communities/{id}`.
2. Viết 2 file template Thymeleaf:
   - `templates/community/index.html`: Danh sách các nhóm và nút "Tham gia".
   - `templates/community/detail.html`: Trang chi tiết nhóm hiển thị bài viết thuộc nhóm.
3. Đồng bộ CSDL PostgreSQL qua Docker để các Stored Procedure của Community hoạt động mượt mà.
