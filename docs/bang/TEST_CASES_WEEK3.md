# BỘ ĐẶC TẢ KIỂM THỬ TUẦN 3 (WEEK 3 TEST SPECIFICATIONS)

> **Người thực hiện**: Ngô Anh Bằng (MSSV: 24110166)  
> **Vai trò**: Nhóm trưởng — Realtime, Communication & Lead  
> **Phạm vi kiểm thử tuần 3**:
> 1. Thuật toán gợi ý & xếp hạng bảng tin Feed (`FeedRecommendationService`).
> 2. Bộ chặn bảo mật STOMP Channel Interceptor (`StompAuthChannelInterceptor`).
> 3. Cơ chế Realtime Typing Indicators & Read Receipts (`ChatWebSocketController`).

---

## 1. MA TRẬN TEST CASES (TEST MATRIX)

| Mã Test Case | Hạng Mục / Chức Năng | Điều Kiện Đầu Vào | Kết Quả Kỳ Vọng | Mức Độ | Trạng Thái |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-W3-01** | Feed Recency Decay (0h) | Bài viết vừa tạo (t=0) | `recencyScore` xấp xỉ 1.0 (sai số < 0.001) | High | PASS |
| **TC-W3-02** | Feed Recency Half-Life (12h) | Bài viết tạo cách đây 12 giờ | `recencyScore` = 0.5 (sai số < 0.01) | High | PASS |
| **TC-W3-03** | Feed Recency 2 Half-Lives (24h) | Bài viết tạo cách đây 24 giờ | `recencyScore` = 0.25 (sai số < 0.01) | High | PASS |
| **TC-W3-04** | Social Score - None | Không follow, không mutual, không chung nhóm | `socialScore` = 0.0 | Medium | PASS |
| **TC-W3-05** | Social Score - Following Only | Đang follow tác giả | `socialScore` = 1.0 / 1.5 ≈ 0.667 | Medium | PASS |
| **TC-W3-06** | Social Score - All Connections | Follow + Mutual + Same Community | `socialScore` = min(1.0, 1.8 / 1.5) = 1.0 | High | PASS |
| **TC-W3-07** | Engagement Score - Zero | Likes = 0, replies = 0, reposts = 0, bookmarks = 0 | `engagementScore` = 0.0 | Medium | PASS |
| **TC-W3-08** | Engagement Score - Scaling | Likes = 100, replies = 20, reposts = 10, bookmarks = 5 | `engagementScore` tính theo log10(1 + 180) / 3.0 ≈ 0.752 | High | PASS |
| **TC-W3-09** | Relevance Total Score & Weights | Đầy đủ các chỉ số hợp lệ | Điểm tổng = 0.25*R + 0.35*S + 0.25*T + 0.15*E | Critical | PASS |
| **TC-W3-10** | Ranking Candidates Order | Danh sách 3 ứng viên với độ phù hợp khác nhau | Kết quả trả về sắp xếp giảm dần theo điểm số | Critical | PASS |
| **TC-W3-11** | STOMP Interceptor Bearer Token | Message CONNECT có `Authorization: Bearer <jwt>` | Giải mã thành công, thiết lập `Principal` vào session | Critical | PASS |
| **TC-W3-12** | STOMP Interceptor Invalid Token | Message CONNECT không token hoặc token sai format | Ném ngoại lệ hoặc không xác thực, từ chối kết nối | High | PASS |
| **TC-W3-13** | WebSocket Typing Indicator | Gửi typing payload qua `/app/chat.typing/{id}` | Broadcast đến `/topic/conversations/{id}/typing` | High | PASS |
| **TC-W3-14** | WebSocket Read Receipt | Gửi read receipt qua `/app/chat.read/{id}` | Broadcast đến `/topic/conversations/{id}/read` | High | PASS |

---

## 2. KỊCH BẢN KIỂM THỬ CHI TIẾT (TEST SPECIFICATION DETAILS)

### 2.1. TC-W3-01 to TC-W3-03: Kiểm thử Phân rã độ mới theo thời gian (Recency Decay)
- **Hàm kiểm thử**: `FeedRecommendationService.calculateRecencyScore(Instant createdAt, Instant now)`
- **Công thức**: $R = \exp(-\lambda \cdot \Delta t)$ với $\lambda = \frac{\ln(2)}{12.0}$.
- **Mục tiêu**: Đảm bảo bài viết cũ hơn sẽ bị suy giảm điểm độ mới chính xác theo định luật chu kỳ bán rã 12 giờ.

### 2.2. TC-W3-04 to TC-W3-06: Kiểm thử Điểm quan hệ xã hội (Social Score)
- **Hàm kiểm thử**: `FeedRecommendationService.calculateSocialScore(boolean isFollowing, boolean isMutual, boolean isSameCommunity)`
- **Mục tiêu**: Đảm bảo người dùng có mối quan hệ sâu sắc hơn với tác giả (bạn bè hai chiều, cùng nhóm học thuật) được ưu tiên bài viết hơn, giá trị luôn được chuẩn hóa trong đoạn $[0.0, 1.0]$.

### 2.3. TC-W3-07 to TC-W3-08: Kiểm thử Điểm tương tác cộng đồng (Engagement Score)
- **Hàm kiểm thử**: `FeedRecommendationService.calculateEngagementScore(int likes, int replies, int reposts, int bookmarks)`
- **Mục tiêu**: Tránh hiện tượng bài viết "viral" làm bão hòa bảng tin bằng cách áp dụng hàm logarit làm mượt phân phối.

### 2.4. TC-W3-09 to TC-W3-10: Kiểm thử Tổng điểm và Sắp xếp xếp hạng (Ranking)
- **Hàm kiểm thử**: `FeedRecommendationService.rankCandidates(List<PostCandidate> candidates, Instant now)`
- **Mục tiêu**: Đảm bảo toàn bộ ứng viên được tính điểm chính xác và trả về danh sách có thứ tự giảm dần tuyệt đối theo thuộc tính `score`.

---

## 3. MÔI TRƯỜNG VÀ TỰ ĐỘNG HÓA KIỂM THỬ
- **Framework**: JUnit 5 + Mockito + AssertJ.
- **Lớp kiểm thử**: `com.aloute.bang.FeedRecommendationServiceTest`.
- **Tích hợp CI**: Chạy tự động trong GitHub Actions workflow `Java CI with Maven` trên nhánh `feat/bang-week3` và kiểm tra 100% test xanh trước khi merge.
