# Quan hệ xã hội

Feature quản lý kết bạn, theo dõi, gợi ý bạn và chặn tài khoản.

## Chức năng

- Gửi, chấp nhận, từ chối và hủy lời mời kết bạn.
- Hủy kết bạn.
- Follow/unfollow không yêu cầu xác nhận với profile công khai.
- Xem bạn chung.
- Gợi ý bạn dựa trên bạn chung và sở thích.
- Block/unblock người dùng.

## Trạng thái lời mời

```text
PENDING → ACCEPTED
        → REJECTED
        → CANCELLED
```

## Quy tắc nghiệp vụ

- Không thể gửi lời mời cho chính mình.
- Không tạo lời mời trùng hoặc lời mời chéo; lời mời chéo có thể chuyển thành kết bạn.
- Block tự động hủy friendship, follow, lời mời chờ và chặn chat mới.
- Unblock không tự khôi phục các quan hệ cũ.
- Danh sách bạn bè tuân theo privacy của chủ profile.

## Endpoint dự kiến

| Method | Path | Chức năng |
| --- | --- | --- |
| POST | `/friend-requests/{userId}` | Gửi lời mời |
| POST | `/friend-requests/{id}/accept` | Chấp nhận |
| POST | `/friend-requests/{id}/reject` | Từ chối |
| DELETE | `/friends/{userId}` | Hủy kết bạn |
| POST | `/users/{id}/follow` | Follow |
| DELETE | `/users/{id}/follow` | Unfollow |
| POST | `/users/{id}/block` | Block |
| DELETE | `/users/{id}/block` | Unblock |
| GET | `/users/{id}/mutual-friends` | Bạn chung |

## Acceptance criteria

- Unique constraint ngăn quan hệ trùng ở database.
- Các thao tác quan hệ chạy trong transaction.
- Block có hiệu lực trên feed, search, profile, notification và chat.

