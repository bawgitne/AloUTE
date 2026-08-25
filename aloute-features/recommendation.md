# Đề xuất bài viết

Recommendation Engine chọn bài hợp lệ, tính điểm, đa dạng hóa và ghi lý do đề xuất. Phiên bản đồ án dùng heuristic minh bạch thay vì ML khó chứng minh.

## Pipeline

```text
Candidate generation → privacy/safety filter → feature calculation
→ weighted scoring → diversity reranking → impression logging → feedback
```

## Tạo candidate

- 40% từ bạn bè và người đang follow.
- 20% từ group User đã tham gia.
- 15% từ hashtag/topic User tương tác.
- 15% bài trending trong 24 giờ.
- 10% exploration từ creator/topic mới.

Mỗi nguồn có giới hạn để một creator hoặc group không chiếm toàn bộ feed.

## Eligibility filter

Loại bỏ bài deleted/hidden/quarantined, sai visibility, từ account suspended/banned, quan hệ block, group chưa tham gia và bài User đã chọn “không quan tâm”. Permission filter chạy trước scoring và chạy lại trước response.

## Công thức

Mỗi feature chuẩn hóa về `[0,1]`:

```text
baseScore = 0.30 * affinity
          + 0.20 * engagementVelocity
          + 0.20 * recency
          + 0.15 * interestMatch
          + 0.10 * quality
          + 0.05 * exploration

finalScore = baseScore
           - 0.25 * negativeFeedback
           - 0.20 * authorSaturation
           - 0.30 * moderationRisk
```

| Feature | Cách tính |
| --- | --- |
| `affinity` | Quan hệ friend/follow và tương tác hai chiều trong 30 ngày |
| `engagementVelocity` | Reaction/comment/share hợp lệ chia tuổi bài, dùng log scale |
| `recency` | `exp(-ageHours / 36)` |
| `interestMatch` | Hashtag/topic khớp lịch sử tương tác tích cực |
| `quality` | Unique engager, nội dung hoàn chỉnh; trừ clickbait/spam |
| `exploration` | Điểm nhỏ cho creator/topic mới |
| `negativeFeedback` | Hide/report/unfollow/skip nhanh |
| `authorSaturation` | Phạt bài thứ hai trở đi của cùng author trong một page |
| `moderationRisk` | Điểm rủi ro do AutoMod trả về |

## Reranking và giải thích

- Không quá hai bài liên tiếp cùng author.
- Mỗi page có ít nhất ba topic nếu đủ candidate.
- Bài đã xem gần đây bị giảm điểm, không loại vĩnh viễn.
- Không dùng giới tính, ngày sinh hoặc dữ liệu nhạy cảm để ranking.
- Account mới dùng topic onboarding và trending an toàn.
- Lưu `reason_code`: `FRIEND_POST`, `FOLLOWING`, `GROUP`, `INTEREST`, `TRENDING`, `EXPLORE`.
- UI hiển thị “Vì bạn theo dõi #springboot” và nhận feedback Hide/Not interested/Unfollow/Report.

## Pseudocode

```java
List<PostCandidate> candidates = candidateService.generate(userId, 300);
return candidates.stream()
    .filter(c -> permissionService.canView(userId, c.postId()))
    .filter(c -> moderationService.isRecommendable(c.postId()))
    .map(featureService::calculate)
    .map(scoringService::score)
    .sorted(comparing(ScoredPost::score).reversed())
    .collect(diversityReranker.forPage(20));
```

## Kiểm thử và đo lường

- Unit test từng feature/công thức với clock cố định.
- Bài private, blocked hoặc quarantined không bao giờ lọt feed dù score cao.
- Test cold-start, thiếu candidate và pagination ổn định.
- Đo diversity theo author/topic, tỷ lệ bài cũ và interest match.
- Log impression, click, dwell time, reaction, hide, report nhưng không dùng dữ liệu nhạy cảm.

