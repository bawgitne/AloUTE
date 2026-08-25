# Tương tác bài viết

Feature gồm reaction, bình luận, trả lời, chia sẻ, lưu bài và lịch sử đã xem.

## Chức năng

- Reaction: `LIKE`, `LOVE`, `HAHA`, `WOW`, `SAD`, `ANGRY`.
- Mỗi User chỉ có một reaction trên một bài; chọn lại để đổi hoặc bỏ.
- Bình luận text, ảnh hoặc video; hỗ trợ một cấp trả lời.
- Chỉnh sửa/xóa mềm bình luận của mình.
- Chia sẻ bài kèm nội dung mới nếu còn quyền xem bài gốc.
- Lưu/bỏ lưu bài viết.
- Ghi nhận bài đã xem theo kiểu upsert.

## Quy tắc nghiệp vụ

- Bình luận text từ 1–2.000 ký tự; nếu không có text phải có media.
- Không tương tác với bài bị xóa, ẩn hoặc không có quyền xem.
- Reaction/comment/share tạo notification, trừ hành động trên nội dung của chính mình.
- Khi bài gốc đổi sang riêng tư, share không còn hiển thị cho người mất quyền.
- View được chống tăng ảo bằng cách gộp theo User/bài trong một khoảng thời gian.

## Endpoint dự kiến

| Method | Path | Chức năng |
| --- | --- | --- |
| PUT | `/posts/{id}/reaction` | Tạo/đổi reaction |
| DELETE | `/posts/{id}/reaction` | Bỏ reaction |
| POST | `/posts/{id}/comments` | Bình luận |
| POST | `/comments/{id}/replies` | Trả lời |
| PATCH | `/comments/{id}` | Sửa bình luận |
| DELETE | `/comments/{id}` | Xóa bình luận |
| POST | `/posts/{id}/share` | Chia sẻ |
| PUT | `/posts/{id}/save` | Lưu bài |
| DELETE | `/posts/{id}/save` | Bỏ lưu |

## Acceptance criteria

- Counter tương tác nhất quán với dữ liệu thật.
- Không thể reaction/comment bằng IDOR lên bài không được xem.
- Danh sách reaction/comment có phân trang.

