# Bài viết và media

User có thể tạo, chỉnh sửa, xóa mềm và xem bài viết gồm text, ảnh hoặc video.

## Chức năng

- Tạo bài text, ảnh, video hoặc kết hợp text và media.
- Gắn hashtag và đề cập người dùng.
- Chọn `PUBLIC`, `FRIENDS` hoặc `PRIVATE`.
- Chỉnh sửa nội dung và quyền riêng tư.
- Xóa mềm hoặc lưu trữ bài viết.
- Xem lịch sử chỉnh sửa ở mức Manager/Admin.
- Phân trang bài trên profile và bảng tin.

## Quy tắc nghiệp vụ

- Bài viết phải có text hoặc ít nhất một media.
- Text tối đa 5.000 ký tự; tối đa 10 media mỗi bài.
- Ảnh tối đa 10 MB/file; video tối đa 100 MB/file.
- Chỉ chấp nhận MIME trong allowlist.
- Hashtag được chuẩn hóa chữ thường và loại trùng.
- Mention chỉ tạo notification nếu người được nhắc có quyền xem bài.
- Xóa bài là soft delete để phục vụ audit; media được dọn bằng tác vụ định kỳ.

## Endpoint dự kiến

| Method | Path | Chức năng |
| --- | --- | --- |
| POST | `/posts` | Tạo bài |
| GET | `/posts/{id}` | Xem bài theo quyền |
| PATCH | `/posts/{id}` | Sửa bài của mình |
| DELETE | `/posts/{id}` | Xóa mềm bài của mình |
| POST | `/posts/{id}/archive` | Lưu trữ/khôi phục bài |
| GET | `/users/{username}/posts` | Danh sách bài profile |

## Acceptance criteria

- Transaction không tạo Post nếu upload/metadata thất bại không thể phục hồi.
- User không truy cập được bài không đúng visibility.
- Nội dung được escape/sanitize khi render để chống XSS.
- Thống kê reaction/comment không bị sai khi xóa mềm.

