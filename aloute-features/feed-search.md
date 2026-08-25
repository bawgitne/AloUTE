# Bảng tin, tìm kiếm và khám phá

Feature cung cấp home feed, tìm kiếm người/bài/hashtag/nhóm và nội dung thịnh hành.

## Feed cho Guest

- Hiển thị ít nhất 10 bài công khai nổi bật.
- Sắp xếp theo điểm tương tác giảm dần, sau đó theo thời gian mới nhất.
- Không hiển thị nội dung bị ẩn, xóa hoặc từ tài khoản bị cấm.

## Feed cho User

- Tối đa 20 bài mỗi trang hoặc mỗi lần lazy loading.
- Kết hợp bài của bạn bè, người đang follow, nhóm đã tham gia và bài khám phá.
- Loại bỏ nội dung không đúng privacy hoặc thuộc quan hệ block.

## Công thức xếp hạng đề xuất

```text
score = reactionCount * 2
      + commentCount * 3
      + shareCount * 4
      + relationshipWeight
      + interestWeight
      - ageInHours * 0.5
```

Đây là heuristic có thể giải thích khi báo cáo, không tuyên bố là AI/ML.

## Tìm kiếm và lọc

- Tìm User theo username/display name.
- Tìm Post theo nội dung hoặc hashtag.
- Tìm Group/Event theo tên và chủ đề.
- Lọc bài theo mới nhất, nhiều tương tác, ảnh hoặc video.
- Debounce input và phân trang kết quả.

## Endpoint dự kiến

| Method | Path | Chức năng |
| --- | --- | --- |
| GET | `/feed` | Feed cá nhân hóa |
| GET | `/explore` | Nội dung công khai nổi bật |
| GET | `/search?q=&type=&page=` | Tìm kiếm hợp nhất |
| GET | `/hashtags/trending` | Hashtag thịnh hành 24 giờ |
| GET | `/suggestions/users` | Gợi ý kết nối |

## Acceptance criteria

- Pagination có thứ tự ổn định, không lặp bản ghi khi tải thêm.
- Search không trả tài khoản/nội dung mà người tìm đã block hoặc không có quyền xem.
- Query chính có index và tránh N+1 query.

