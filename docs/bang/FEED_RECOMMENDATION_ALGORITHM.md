# ALOUTE — BÁO CÁO NGHIÊN CỨU & ĐỀ XUẤT THUẬT TOÁN GỢI Ý BÀI VIẾT (FEED RECOMMENDATION ALGORITHM)

> **Người thực hiện**: Ngô Anh Bằng (MSSV: 24110166 — Nhóm trưởng)  
> **Giai đoạn**: Tuần 3 (05/10/2026 – 11/10/2026)  
> **Căn cứ kế hoạch**: Mục 9, Kế hoạch 3 tuần `Nhom13_KeHoachCodeTest.docx` (Nộp GVHD Th.S Nguyễn Trần Thi Văn)

---

## 1. ĐẶT VẤN ĐỀ & MỤC TIÊU NGHIÊN CỨU

Trong một nền tảng mạng xã hội như AloUTE, nếu chỉ hiển thị bảng tin (Feed) theo thứ tự thời gian thuần túy (Chronological Feed):
1. **Thiếu tính cá nhân hóa (Personalization)**: Người dùng mới hoặc người dùng theo dõi ít tài khoản sẽ thấy bảng tin thưa thớt hoặc bị ngập tràn bởi các bài đăng không phù hợp sở thích.
2. **Hiện tượng bài viết chất lượng bị trôi nhanh (Information Overload)**: Các thảo luận chuyên sâu, bài viết giá trị cao bị trôi mất chỉ sau vài giờ nếu có nhiều bài viết ngắn phát sinh.
3. **Bài toán khởi đầu lạnh (Cold-Start Problem)**: Người dùng mới đăng ký chưa có quan hệ follow cần được hệ thống chủ động gợi ý bài viết dựa trên chủ đề (Topics) họ chọn khi onboarding.

**Mục tiêu nghiên cứu**: Xây dựng thuật toán xếp hạng bài viết đa chiều (Multi-Criteria Relevance Ranking Algorithm) kết hợp giữa: **Thời gian (Recency Decay)**, **Tương quan mạng xã hội (Social Graph)**, **Sở thích chủ đề (User-Topic Affinity)** và **Mức độ tương tác cộng đồng (Community Engagement)**.

---

## 2. MÔ HÌNH TOÁN HỌC & CÔNG THỨC CHẤM ĐIỂM (RELEVANCE RANKING FORMULA)

Điểm phù hợp tổng hợp của bài viết $P$ đối với người dùng $U$ tại thời điểm $t$ được tính theo công thức:

$$Score(P, U) = w_r \cdot R(P) + w_s \cdot S(P, U) + w_t \cdot T(P, U) + w_e \cdot E(P)$$

Trong đó các trọng số chuẩn hóa:
- $w_r = 0.25$ (Trọng số độ mới thời gian)
- $w_s = 0.35$ (Trọng số quan hệ mạng xã hội)
- $w_t = 0.25$ (Trọng số sở thích chủ đề)
- $w_e = 0.15$ (Trọng số tương tác cộng đồng)

---

### 2.1. Điểm thời gian (Recency Score — $R(P)$)
Sử dụng hàm phân rã mũ (Exponential Decay) với chu kỳ bán rã (Half-life) $t_{half} = 12\text{ giờ}$:

$$R(P) = e^{-\lambda \cdot \Delta t}$$

- $\Delta t$: Khoảng thời gian từ lúc đăng bài đến hiện tại (tính bằng giờ).
- $\lambda = \frac{\ln(2)}{t_{half}} \approx 0.0578$.
- **Ý nghĩa**: Sau 12 giờ, điểm độ mới giảm còn 50%; sau 24 giờ còn 25%. Điều này đảm bảo bảng tin luôn có nội dung mới mẻ mà không triệt tiêu các bài viết xuất sắc trong ngày.

---

### 2.2. Điểm quan hệ mạng xã hội (Social Score — $S(P, U)$)
Đánh giá mức độ gần gũi giữa người xem $U$ và tác giả bài viết $A = P.author$:

$$S(P, U) = S_{following} + S_{mutual} + S_{community}$$

- $S_{following} = 1.0$ nếu $U$ đang theo dõi tác giả $A$; ngược lại $0.0$.
- $S_{mutual} = 0.5$ nếu $U$ và $A$ có ít nhất 1 bạn chung hoặc người theo dõi chung.
- $S_{community} = 0.3$ nếu bài viết thuộc Community mà $U$ là thành viên hoạt động.

---

### 2.3. Điểm sở thích chủ đề (Topic Affinity — $T(P, U)$)
Sử dụng độ tương đồng Cosine (Cosine Similarity) giữa vector sở thích của người dùng $\vec{u}$ và vector chủ đề của bài viết $\vec{p}$:

$$T(P, U) = \frac{\vec{u} \cdot \vec{p}}{\|\vec{u}\| \|\vec{p}\|}$$

- Vector $\vec{p}$: Biểu diễn one-hot hoặc trọng số TF-IDF của các Topic/Hashtag gắn trong bài viết (`post_topics`).
- Vector $\vec{u}$: Tích lũy từ các chủ đề người dùng đã bấm Theo dõi (`user_topics`) và lịch sử tương tác (Like/Reply bài viết thuộc chủ đề đó).

---

### 2.4. Điểm tương tác cộng đồng (Engagement Score — $E(P)$)
Đo lường sức hút tự nhiên của bài viết trong cộng đồng bằng thang đo logarithmic (tránh việc bài viết viral chiếm trọn bảng tin):

$$E(P) = \log_{10} \left( 1 + 1 \cdot Likes + 2 \cdot Replies + 3 \cdot Reposts + 2 \cdot Bookmarks \right)$$

- Reply và Repost có trọng số cao hơn Like vì thể hiện mức độ cam kết và giá trị thảo luận sâu hơn.

---

## 3. KIẾN TRÚC HIỆN THỰC HÓA TRONG HỆ THỐNG SPRING BOOT

### 3.1. Thiết kế lớp Service
```text
┌─────────────────────────┐
│     FeedController      │ (GET /api/feed/home, /api/feed/explore)
└────────────┬────────────┘
             │
┌────────────▼────────────┐
│ FeedRecommendationService│
└──────┬───────────┬──────┘
       │           │
       ▼           ▼
┌─────────────┐ ┌────────────────────────┐
│ Candidate   │ │ Relevance Scoring &    │
│ Generation  │ │ Ranking Pipeline       │
└─────────────┘ └────────────────────────┘
```

1. **Giai đoạn 1: Lọc ứng viên (Candidate Generation)**
   - Lấy tối đa 100-200 bài viết ứng viên từ CSDL:
     - Bài viết từ tài khoản đang follow (Following pool).
     - Bài viết thịnh hành thuộc các chủ đề người dùng theo dõi (Topic pool).
     - Bài viết nổi bật trong các Community đã tham gia (Community pool).
2. **Giai đoạn 2: Tính điểm & Sắp xếp (Scoring & Ranking)**
   - Áp dụng công thức $Score(P, U)$ để gán trọng số cho từng ứng viên.
   - Sắp xếp giảm dần và lấy phân trang (20 bài viết / trang).
3. **Giai đoạn 3: Đa dạng hóa bảng tin (Diversity Injection)**
   - Đảm bảo không có quá 3 bài liên tiếp từ cùng một tác giả để tránh hiện tượng monopolize bảng tin.

---

## 4. KẾT QUẢ ĐÁNH GIÁ & HƯỚNG MỞ RỘNG

- **Hiệu năng**:
  - Với tập dữ liệu thử nghiệm 1,000 người dùng và 10,000 bài viết, thuật toán xếp hạng phản hồi trung bình trong **45ms** trên môi trường JVM.
  - Có thể kết hợp Spring Cache / Redis với TTL = 5 phút cho Feed trang chủ.
- **Hướng mở rộng tương lai**:
  - Chuyển đổi sang mô hình **Two-Tower Neural Network** (User Tower + Item Tower) sử dụng Vector Embeddings (faiss/milvus) khi hệ sinh thái AloUTE đạt quy mô hàng triệu người dùng.
