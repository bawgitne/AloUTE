# ALOUTE — BÁO CÁO KẾT QUẢ & BẰNG CHỨNG THỰC HIỆN TUẦN 3
## (Week 3 Deliverables & 3-Week Final Summary — Ngô Anh Bằng)

> **Người thực hiện**: Ngô Anh Bằng (MSSV: 24110166 — Nhóm trưởng)  
> **Vai trò**: Realtime, Communication & Project Lead  
> **Thời gian thực hiện**: Tuần 3 (05/10/2026 – 11/10/2026)  
> **Căn cứ kế hoạch**: kế hoạch 3 tuần của nhóm

---

## 1. TỔNG KẾT KẾT QUẢ THỰC HIỆN TUẦN 3

Theo đúng phân công và kế hoạch chi tiết trong kế hoạch 3 tuần của nhóm, nhóm trưởng Ngô Anh Bằng đã hoàn thành 100% các mục tiêu và tính năng đề ra cho Tuần 3:

| STT | Nhiệm vụ theo Kế hoạch (kế hoạch 3 tuần của nhóm) | Sản phẩm bàn giao cụ thể | Trạng thái |
| :---: | :--- | :--- | :---: |
| 1 | **Nghiên cứu & Thiết kế Thuật toán Gợi ý Bảng tin (Feed Recommendation)** | • Báo cáo khoa học: [`docs/bang/FEED_RECOMMENDATION_ALGORITHM.md`](FEED_RECOMMENDATION_ALGORITHM.md)<br>• Hiện thực Service: `FeedRecommendationService.java`<br>• 4 tham số chuẩn hóa: Recency Decay (Half-life 12h), Social Graph, Topic Matching, Engagement Scaling | **ĐÃ HOÀN THÀNH** |
| 2 | **Bảo mật Kết nối STOMP & Realtime Interceptor** | • Bộ chặn kênh STOMP: `StompAuthChannelInterceptor.java`<br>• Xác thực JWT Bearer Token tại sự kiện STOMP CONNECT<br>• Gán `UsernamePasswordAuthenticationToken` vào `SimpAttributesContextHolder` / Principal | **ĐÃ HOÀN THÀNH** |
| 3 | **Trạng thái Thời gian thực (Typing Indicator & Read Receipts)** | • Mở rộng `ChatWebSocketController.java`:<br>  - `@MessageMapping("/chat.typing/{conversationId}")`<br>  - `@MessageMapping("/chat.read/{conversationId}")`<br>• Phát sóng realtime đến `/topic/conversations/{id}/typing` và `/read` | **ĐÃ HOÀN THÀNH** |
| 4 | **Thiết kế Sequence Diagrams tuần 3** | • [`docs/bang/sequence/feed-recommendation-sequence.puml`](sequence/feed-recommendation-sequence.puml) & [`.svg`](sequence/feed-recommendation-sequence.svg)<br>• [`docs/bang/sequence/websocket-auth-handshake-sequence.puml`](sequence/websocket-auth-handshake-sequence.puml) & [`.svg`](sequence/websocket-auth-handshake-sequence.svg) | **ĐÃ HOÀN THÀNH** |
| 5 | **Bộ đặc tả Kiểm thử & Automation Unit Tests Tuần 3** | • Bảng đặc tả: [`docs/bang/TEST_CASES_WEEK3.md`](TEST_CASES_WEEK3.md)<br>• Bộ unit test: `FeedRecommendationServiceTest.java` (100% test cases xanh) | **ĐÃ HOÀN THÀNH** |

---

## 2. CHI TIẾT KỸ THUẬT CÁC TÍNH NĂNG TUẦN 3

### 2.1. Thuật toán Gợi ý Bảng tin Feed (`FeedRecommendationService`)
- **Mô hình tính điểm Relevance Score**:
  $$\text{Score}(P, U) = w_r \cdot R(t) + w_s \cdot S(U, A) + w_t \cdot T(U, P) + w_e \cdot E(P)$$
  Trong đó:
  - $w_r = 0.25$ (Độ mới - Recency)
  - $w_s = 0.35$ (Mối quan hệ mạng xã hội - Social)
  - $w_t = 0.25$ (Độ phù hợp chủ đề - Topic Similarity)
  - $w_e = 0.15$ (Điểm tương tác cộng đồng - Engagement)
- **Hàm phân rã thời gian (Exponential Half-Life Decay)**:
  $$R(\Delta t) = \exp\left(-\frac{\ln(2)}{12} \cdot \Delta t\right)$$
  - Ngay khi đăng ($\Delta t = 0$): Điểm $R = 1.0$.
  - Sau 12 giờ ($\Delta t = 12$): Điểm $R = 0.5$.
  - Sau 24 giờ ($\Delta t = 24$): Điểm $R = 0.25$.
- **Hàm làm mượt tương tác (Logarithmic Smoothing)**:
  Tránh tình trạng các bài viết "viral" áp đảo các nội dung học thuật cá nhân hóa bằng cách chuẩn hóa logarit cơ số 10.

### 2.2. Bảo mật WebSocket STOMP (`StompAuthChannelInterceptor`)
- Khắc phục lỗ hổng bảo mật WebSocket khi giao thức HTTP Upgrade không hỗ trợ gửi custom headers trên một số trình duyệt:
  - Đọc header `Authorization: Bearer <jwt_token>` hoặc `passcode` trực tiếp từ khung STOMP CONNECT.
  - Sử dụng `JwtTokenProvider` để kiểm tra tính hợp lệ và trích xuất `userId`, `role`.
  - Thiết lập thông tin định danh `Principal` cho toàn bộ vòng đời phiên WebSocket STOMP của người dùng.

### 2.3. Lược đồ Tuần tự (Sequence Diagrams)
1. **Thuật toán Gợi ý Bảng tin (`feed-recommendation-sequence.svg`)**:
   - Thể hiện quy trình lọc ứng viên (Candidate Retrieval) $\rightarrow$ Chấm điểm song song (Scoring) $\rightarrow$ Xếp hạng và phân trang (Ranking & Pagination) $\rightarrow$ Trả về Client.
2. **Bắt tay xác thực STOMP (`websocket-auth-handshake-sequence.svg`)**:
   - Thể hiện chu trình Handshake HTTP Upgrade $\rightarrow$ Gửi CONNECT frame với token $\rightarrow$ Interceptor xử lý $\rightarrow$ Trả về CONNECTED frame hoặc từ chối kết nối.

---

## 3. BẢNG TỔNG KẾT TOÀN DIỆN 3 TUẦN CỦA NHÓM TRƯỞNG NGÔ ANH BẰNG

Dưới đây là bức tranh tổng thể kết quả đóng góp của Ngô Anh Bằng (MSSV: 24110166) trong suốt 3 tuần của đồ án:

| Tuần | Hạng mục công việc chính | Sản phẩm bàn giao (Artifacts) | Trạng thái Git / PR |
| :---: | :--- | :--- | :---: |
| **Tuần 1** | • Phân tích & Lập Ma trận 22 Use Cases cốt lõi<br>• Thiết kế Sequence Diagrams (Post, Like, Reply)<br>• Đặc tả Test Cases & Viết Unit Tests Core<br>• Khắc phục lỗi Cookie Token chặn CI | • `docs/bang/USE_CASE_MATRIX.md`<br>• `docs/bang/sequence/*` (Post, Like, Reply)<br>• `docs/bang/TEST_CASES_WEEK1.md`<br>• `CoreUseCaseValidationTest.java`<br>• `docs/bang/BANG_WEEK1_EVIDENCE.md` | **Đã Merge PR #6** (Commit `4006db4`) |
| **Tuần 2** | • Thiết kế Sequence Diagrams (Message, Notification)<br>• Xây dựng Module Messaging (Entities, DTOs, Service, API)<br>• Xây dựng Module Notification (Entity, DTOs, Service, API)<br>• Cấu hình Spring WebSocket STOMP Broker<br>• Test Cases & Unit Tests Tuần 2 | • `docs/bang/sequence/*` (Message, Notif)<br>• Package `com.aloute.service.ConversationService`<br>• Package `com.aloute.service.NotificationService`<br>• Package `com.aloute.config.WebSocketConfig`<br>• `MessagingAndNotificationServiceTest.java`<br>• `docs/bang/BANG_WEEK2_EVIDENCE.md` | **Đã Merge PR #7** (Commit `054d7dd`) |
| **Tuần 3** | • Báo cáo & Thuật toán Feed Recommendation<br>• STOMP Security Channel Interceptor<br>• Realtime Typing & Read Receipts<br>• Sequence Diagrams (Feed, WS Auth)<br>• Test Cases & Unit Tests Tuần 3 | • `docs/bang/FEED_RECOMMENDATION_ALGORITHM.md`<br>• `FeedRecommendationService.java`<br>• `StompAuthChannelInterceptor.java`<br>• `ChatWebSocketController.java` (Typing/Read)<br>• `docs/bang/sequence/*` (Feed, WS Auth)<br>• `FeedRecommendationServiceTest.java`<br>• `docs/bang/BANG_WEEK3_EVIDENCE.md` | **Pull Request #8** (Đang nộp) |

---

## 4. KẾT LUẬN & ĐÁNH GIÁ SẴN SÀNG NGHIỆM THU

- **Khối lượng công việc**: Hoàn thành 100% mục tiêu cả 3 tuần theo bảng kế hoạch kế hoạch 3 tuần của nhóm.
- **Chất lượng mã nguồn**:
  - Tuân thủ mô hình kiến trúc tại [README.md](../../README.md) §4 và hợp đồng [docs/api/openapi.yaml](../api/openapi.yaml).
  - Tách biệt rõ ràng ranh giới nghiệp vụ giữa các thành viên, không gây xung đột hay ghi đè công việc của Quân và Khởi Nguyên.
- **Tính ổn định**: 100% Unit Tests viết mới đều được kiểm chứng và vượt qua các tiêu chuẩn CI/CD tự động của GitHub Actions.
