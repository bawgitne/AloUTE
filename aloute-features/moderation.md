# Báo cáo và kiểm duyệt

Feature cho phép User báo cáo vi phạm và Manager xử lý theo quy trình có truy vết.

## Đối tượng báo cáo

- User.
- Post.
- Comment.
- Message chỉ khi người báo cáo là thành viên hội thoại.
- Group hoặc Event.

## Luồng xử lý

```text
OPEN → IN_REVIEW → RESOLVED
                 → REJECTED
```

1. User chọn lý do, nhập mô tả và gửi report.
2. Hệ thống chống report trùng từ cùng User cho cùng đối tượng đang mở.
3. Manager nhận xử lý và chuyển `IN_REVIEW`.
4. Manager chọn từ chối, ẩn nội dung, cảnh cáo hoặc đình chỉ tài khoản.
5. Hệ thống lưu ModerationAction + AuditLog và gửi kết quả cho người báo cáo.

## Quyền Manager

- Tìm kiếm và lọc report theo loại, trạng thái, mức độ và thời gian.
- Xem nội dung tại thời điểm bị report dưới dạng snapshot an toàn.
- Ẩn/khôi phục nội dung.
- Cảnh cáo hoặc đình chỉ User có thời hạn.
- Không được đổi role hoặc xóa audit log.

## Acceptance criteria

- Mỗi quyết định có lý do bắt buộc.
- Không cho Manager xử lý report liên quan chính tài khoản của mình.
- Hành động kiểm duyệt chạy trong transaction và luôn tạo audit log.
- User được thông báo kết quả nhưng không thấy dữ liệu riêng của người xử lý.

