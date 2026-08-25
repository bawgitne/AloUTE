# Tổng quan và kiến trúc

ALOUTE là mạng xã hội cộng đồng có bảng tin, kết bạn/theo dõi, chat, thông báo, nhóm, sự kiện và kiểm duyệt nội dung.

## Kiến trúc ba tầng

```text
Browser / Thymeleaf / JavaScript
             ↓
Controller (MVC, REST, WebSocket)
             ↓
Service (business rules, transaction, authorization)
             ↓
Repository (Spring Data JPA)
             ↓
PostgreSQL
```

Firebase Storage là dịch vụ ngoài để lưu media; URL và metadata của file được lưu trong PostgreSQL.

## Cấu trúc package

```text
com.aloute
├── config
├── controller
├── dto
├── entity
├── repository
├── service
├── security
├── websocket
├── validation
├── exception
└── util
```

## Quy tắc phụ thuộc

- Controller chỉ nhận request, validate input, gọi Service và chọn view/response.
- Service chứa nghiệp vụ, transaction, permission và chuyển đổi DTO.
- Repository chỉ truy vấn dữ liệu.
- Entity không chứa logic phụ thuộc HTTP hoặc giao diện.
- Tích hợp Firebase và email nằm sau interface để có thể mock khi test.

## Module nghiệp vụ

| Module | Trách nhiệm |
| --- | --- |
| Identity | Tài khoản, OTP, JWT, role |
| Social | Profile, friendship, follow, block |
| Content | Post, media, hashtag, reaction, comment |
| Discovery | Feed, search, trending, suggestion |
| Realtime | Conversation, message, notification, presence |
| Community | Group, member, event, participant |
| Trust & Safety | Report, moderation action, audit log |

## Yêu cầu phi chức năng

- Phân trang mặc định 20 phần tử; giới hạn tối đa 100.
- Transaction cho các thao tác nhiều bảng.
- Index cho khóa ngoại và trường tìm kiếm phổ biến.
- Log có correlation ID nhưng không ghi JWT, OTP hoặc mật khẩu.
- Media upload có giới hạn dung lượng và loại MIME.
- Giao diện hoạt động tốt từ 360 px trở lên.

