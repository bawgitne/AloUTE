# Phân quyền

Feature này xác định ai được truy cập chức năng và dữ liệu nào.

## Ma trận quyền

| Nghiệp vụ | Guest | User | Manager | Admin |
| --- | --- | --- | --- | --- |
| Xem nội dung công khai | Có | Có | Có | Có |
| Tạo và tương tác nội dung | Không | Có | Có | Có |
| Sửa/xóa nội dung của mình | Không | Có | Có | Có |
| Xử lý báo cáo | Không | Không | Có | Có |
| Khóa tạm user do vi phạm | Không | Không | Có | Có |
| Quản lý role và Manager | Không | Không | Không | Có |
| Cấu hình hệ thống | Không | Không | Không | Có |
| Xem audit log toàn hệ thống | Không | Không | Không | Có |

## Quy tắc

- Dùng `@PreAuthorize` cho role và permission quan trọng.
- Service phải kiểm tra ownership; không chỉ ẩn nút trên giao diện.
- Manager không được sửa role, xóa Admin hoặc thay đổi cấu hình bảo mật.
- Admin không được tự hạ role của chính mình nếu hệ thống chỉ còn một Admin.
- Nội dung `FRIENDS` chỉ hiển thị với quan hệ bạn bè đã chấp nhận.
- Nội dung `PRIVATE` chỉ chủ sở hữu được xem.
- Hai tài khoản có quan hệ block không được xem profile riêng tư, kết bạn hoặc nhắn tin.

## Trạng thái tài khoản

```text
PENDING_VERIFICATION → ACTIVE → SUSPENDED → ACTIVE
                                  └────────→ BANNED
```

Mọi thao tác đình chỉ, cấm hoặc đổi role phải lưu người thực hiện, lý do và thời gian trong audit log.

## Acceptance criteria

- Truy cập sai role trả `403`; chưa đăng nhập trả `401` hoặc chuyển trang login.
- User không thể sửa/xóa tài nguyên của người khác bằng cách đổi ID.
- WebSocket subscription và message destination được kiểm tra quyền.
- Kiểm thử đầy đủ happy path và privilege-escalation path.

