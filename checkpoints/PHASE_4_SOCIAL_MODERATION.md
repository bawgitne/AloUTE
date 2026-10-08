# CHECKPOINT PHASE 4: SOCIAL GRAPH & MODERATION (FOLLOW, BLOCK, MUTE, REPORT)

> **Chịu trách nhiệm chính**: Nguyễn Đoàn Minh Khôi (24110259)  
> **Trạng thái**: 🟢 **HOÀN THÀNH (95%)**  
> **Mức độ rủi ro**: Thấp (Đã bổ sung đầy đủ Entity, Repository, Service và Controller theo OpenAPI)  

---

## 1. CÁC TẦNG ĐÃ ĐỐI SOÁT & TRIỂN KHAI

### 1.1. Quan hệ Follow / Unfollow
- `SocialController.java` & `SocialServiceImpl.java`:
  - `PUT /api/social/follows/{userId}`: Follow người dùng khác.
    - Chặn tự follow chính mình (`FOLLOW_SELF_NOT_ALLOWED`).
    - Chặn follow trùng lặp (`FOLLOW_ALREADY_EXISTS`).
    - Cập nhật bộ đếm `followingCount` và `followersCount` ở `Profile` của cả 2 phía.
    - Kích hoạt notification loại `FOLLOW` gửi tới đối phương.
  - `DELETE /api/social/follows/{userId}`: Unfollow người dùng khác và giảm bộ đếm tương ứng.
  - Tầng Controller & Service của Follow hoạt động hoàn hảo, đã có test tích hợp `AuthUserApiIntegrationTest`.

### 1.2. Chặn người dùng (Block) & Tắt tiếng (Mute) & Giới hạn (Restrict) — [ĐÃ HOÀN TẤT]
- **Entity & Repository**:
  - `Block.java` & `BlockRepository.java`: Lưu quan hệ blocker/blocked, ràng buộc unique pair và index truy vấn nhanh.
  - `Mute.java` & `MuteRepository.java`: Lưu quan hệ muter/muted.
  - `Restriction.java` & `RestrictionRepository.java`: Lưu quan hệ restricter/restricted.
- **Service Implementation** (`SocialServiceImpl.java`):
  - `block(currentUserId, targetUserId)`: Kiểm tra `BLOCK_SELF_NOT_ALLOWED`, kiểm tra user tồn tại, tự động hủy follow cả 2 chiều và giảm `followingCount`/`followersCount`, lưu bản ghi block.
  - `unblock(currentUserId, targetUserId)`: Xóa quan hệ block, ném 404 nếu không tồn tại.
  - `mute(currentUserId, targetUserId)` & `unmute(...)`: Cài đặt đầy đủ theo chuẩn domain.
  - `restrict(currentUserId, targetUserId)` & `unrestrict(...)`: Cài đặt đầy đủ theo chuẩn domain.
- **REST API Endpoints** (`SocialController.java`):
  - `PUT /api/social/blocks/{userId}` & `DELETE /api/social/blocks/{userId}`
  - `PUT /api/social/mutes/{userId}` & `DELETE /api/social/mutes/{userId}`
  - `PUT /api/social/restrictions/{userId}` & `DELETE /api/social/restrictions/{userId}`
  - Trả về đúng `ApiResponse<SocialRelationResponse>` theo hợp đồng OpenAPI `skills/aloute-openapi.yaml`.

### 1.3. Báo cáo Vi phạm (Report & Moderation)
- `ReportController.java` & `ReportServiceImpl.java`:
  - `POST /api/reports`: Người dùng gửi báo cáo vi phạm bài viết (`reportedPostId`) hoặc tài khoản (`reportedUserId`), lý do (`ReportReason`: `SPAM`, `HARASSMENT`, `HATE_SPEECH`, `NUDITY`, `VIOLENCE`, `OTHER`).
  - Trạng thái mặc định khởi tạo là `ReportStatus.PENDING`.
  - Phân quyền: Cấp quyền `REPORT_REVIEW` và `USER_SUSPEND` cho role `SYSTEM_ADMIN`.

---

## 2. KẾT QUẢ ĐỐI SOÁT OPENAPI & DOMAIN

| Miền nghiệp vụ | Quy định trong Skills & OpenAPI Contract | Thực tế trong Code nhánh `master` | Đánh giá |
| :--- | :--- | :--- | :--- |
| **Follow / Unfollow** | `PUT/DELETE /api/social/follows/{userId}` | Đã triển khai đầy đủ + Notification trigger | ✅ Khớp 100% |
| **Chặn người dùng (Block)** | `PUT/DELETE /api/social/blocks/{userId}` | Đã thêm Entity, Repo, Service, Controller, hủy follow 2 chiều | ✅ Khớp 100% |
| **Tắt tiếng (Mute)** | `PUT/DELETE /api/social/mutes/{userId}` | Đã thêm Entity, Repo, Service, Controller | ✅ Khớp 100% |
| **Hạn chế (Restrict)** | `PUT/DELETE /api/social/restrictions/{userId}` | Đã thêm Entity, Repo, Service, Controller | ✅ Khớp 100% |
| **Tạo báo cáo (Report)** | `POST /api/reports` | Đã triển khai đầy đủ + Phân quyền Admin | ✅ Khớp 100% |
