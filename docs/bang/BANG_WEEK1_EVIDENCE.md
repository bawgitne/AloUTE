# ALOUTE — BÁO CÁO KẾT QUẢ & BẰNG CHỨNG THỰC HIỆN TUẦN 1
## (Week 1 Deliverables & Evidence — Ngô Anh Bằng)

> **Người thực hiện**: Ngô Anh Bằng (MSSV: 24110166 — Nhóm trưởng)  
> **Vai trò**: Realtime, Communication & Project Lead  
> **Thời gian thực hiện**: Tuần 1 (21/09/2026 – 26/09/2026)  
> **Căn cứ kế hoạch**: kế hoạch 3 tuần của nhóm

---

## 1. TỔNG KẾT KẾT QUẢ THỰC HIỆN TUẦN 1

Theo đúng phân công nhiệm vụ tại bảng kế hoạch 3 tuần, nhóm trưởng Ngô Anh Bằng đã hoàn thành 100% các mục tiêu đặt ra cho Tuần 1:

| STT | Nhiệm vụ theo Kế hoạch (kế hoạch 3 tuần của nhóm) | Sản phẩm bàn giao cụ thể | Trạng thái |
| :---: | :--- | :--- | :---: |
| 1 | **Chọn Use Case chính & lập Ma trận Use Case – API – DB – UI** | [`docs/bang/USE_CASE_MATRIX.md`](USE_CASE_MATRIX.md): Bao phủ 22 Use Cases toàn diện từ Auth, Post, Reaction, Community đến Messaging và Notification. | **ĐÃ HOÀN THÀNH** |
| 2 | **Vẽ Sequence Diagram Tạo bài viết** | [`docs/bang/sequence/create-post-sequence.puml`](sequence/create-post-sequence.puml)<br>Ảnh vector: [`docs/bang/sequence/create-post-sequence.svg`](sequence/create-post-sequence.svg) | **ĐÃ HOÀN THÀNH** |
| 3 | **Vẽ Sequence Diagram Thích bài viết (Like)** | [`docs/bang/sequence/like-post-sequence.puml`](sequence/like-post-sequence.puml)<br>Ảnh vector: [`docs/bang/sequence/like-post-sequence.svg`](sequence/like-post-sequence.svg) | **ĐÃ HOÀN THÀNH** |
| 4 | **Vẽ Sequence Diagram Bình luận bài viết (Reply)** | [`docs/bang/sequence/reply-post-sequence.puml`](sequence/reply-post-sequence.puml)<br>Ảnh vector: [`docs/bang/sequence/reply-post-sequence.svg`](sequence/reply-post-sequence.svg) | **ĐÃ HOÀN THÀNH** |
| 5 | **Lập bộ Test Case cho các luồng cốt lõi & dự bị Tuần 2** | [`docs/bang/TEST_CASES_WEEK1.md`](TEST_CASES_WEEK1.md): 15 kịch bản kiểm thử chi tiết cho Post, Like, Reply, Messaging, Notification. | **ĐÃ HOÀN THÀNH** |
| 6 | **Automation Test Suites & Fix CI** | • `com.aloute.bang.CoreUseCaseValidationTest`: Test invariant kiểm thử.<br>• Fix `AuthUserApiIntegrationTest` giải quyết lỗi 401 do lấy nhầm cookie `XSRF-TOKEN` sang `aloute_access_token` $\rightarrow$ CI xanh. | **ĐÃ HOÀN THÀNH** |

---

## 2. CHI TIẾT CÁC SẢN PHẨM BÀN GIAO

### 2.1. Ma trận truy xuất Use Case – API – Database – UI
- Thiết lập bảng Traceability Matrix liên kết chặt chẽ giữa Use Case nghiệp vụ $\rightarrow$ REST API Endpoint $\rightarrow$ Bảng CSDL $\rightarrow$ Giao diện người dùng.
- Giúp các thành viên trong nhóm (Khởi Nguyên - Backend, Bằng - CSDL, Quân - UI) nắm rõ ranh giới trách nhiệm, không bị dẫm chân lên nhau.

### 2.2. Bộ lược đồ tuần tự cốt lõi (Sequence Diagrams)
1. **Tạo bài viết (`create-post-sequence.svg`)**:
   - Thể hiện đầy đủ luồng từ User $\rightarrow$ Controller $\rightarrow$ CurrentUserProvider $\rightarrow$ PostService $\rightarrow$ CommunityService $\rightarrow$ DB.
   - Xử lý các case ngoại lệ: 401 Unauthorized, 403 Forbidden (Community Access Denied), 422 Validation Failed (nội dung rỗng hoặc quá 500 ký tự).
2. **Thích bài viết (`like-post-sequence.svg`)**:
   - Đảm bảo tính lũy đẳng (Idempotency): like lại không gây duplicate key error.
   - Tự động kích hoạt Notification gửi cho tác giả bài viết khi người like khác tác giả.
3. **Bình luận bài viết (`reply-post-sequence.svg`)**:
   - Xử lý bình luận trực tiếp vào bài gốc và bình luận lồng nhau (Nested reply) bảo toàn `root_id`.
   - Kiểm tra ràng buộc quyền trả lời (`reply_control`).

### 2.3. Bộ Test Case & Kiểm thử tự động
- Lập tài liệu đặc tả rõ ràng từng mã Test Case: Tiền điều kiện, Input payload, HTTP Status, Response Body, CSDL.
- Viết Unit Test `CoreUseCaseValidationTest` kiểm tra các business logic invariants (giới hạn ký tự, tính toán `root_id`, ngăn chặn tự chat với chính mình).
- Khắc phục triệt để lỗi trích xuất token trong `AuthUserApiIntegrationTest` giúp toàn bộ Test Suite của dự án vượt qua kiểm tra trên GitHub Actions CI.

---

## 3. KẾ HOẠCH BẮT TAY VÀO TUẦN 2 (27/09 – 04/10)

1. Xây dựng module **Messaging**:
   - `ConversationService`, `MessageService`.
   - REST API: `POST /api/conversations`, `GET /api/conversations`, `POST /api/conversations/{id}/messages`.
2. Xây dựng module **Notification**:
   - Lưu trữ thông báo, đếm số thông báo chưa đọc, đánh dấu đã đọc.
3. Cấu hình **Spring WebSocket (STOMP)**:
   - Endpoint `/ws`, prefix `/app`, destination `/topic` và `/user/queue`.
   - Tích hợp gửi tin nhắn và thông báo thời gian thực.
4. Bổ sung bộ Test Case và Sequence Diagram chi tiết cho Messaging/Notification.
