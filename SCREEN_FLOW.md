# AloUTE Screen Flow

## Guest Flow

### Login

```
Guest
  ↓
Login Screen
  ↓
Enter account information
  ↓
Validate
  ↓
Success → Home Feed
Failure → Show error
```

### Register

```
Guest
  ↓
Register Screen
  ↓
Enter user information
  ↓
Validate data
  ↓
Create account
  ↓
Login
```

## User Flow

### Home Feed

```
User
 ↓
Home Feed
 ↓
View posts
 ↓
Interact or create post
```

Main elements:

- Navigation.
- User summary.
- Activity feed.
- Community information.

### Profile

```
User
 ↓
Profile Screen
 ↓
View information
 ↓
Update profile information
```

### Create Post

```
Home Feed
 ↓
Create Post
 ↓
Enter content
 ↓
Submit
 ↓
Post appears in Feed
```

### Post Detail

```
Feed
 ↓
Select Post
 ↓
Post Detail
 ↓
View content and interactions
```

### Interaction Flow

```
View Post
 ↓
Choose action
 ↓
Like / Comment / Other supported interaction
 ↓
Update displayed state
```

## Scope Boundary

These are PLANNED screen interactions, not proof of functioning routes or successful tests. The original six screens are only part of the Word Week 1 design assignment. Profile editing and post interactions remain hooks; outcomes below depend on confirmed backend contracts.


## Word reconciliation and screen flow evidence

Owner: **Phạm Minh Quân**, one of four members in phân công MODULE_ASSIGNMENT (không commit trong repo). Source: Word hiện hành `Nhom13_KeHoachCodeTest.docx` (không commit trong repo).

- Week 1: **21/09/2026 – 26/09/2026** — Thiết kế giao diện cho toàn bộ 10 màn hình chính (bao gồm Search, Community, Messaging và Notification). Bộ wireframe vector SVG chuẩn dark theme đã hoàn thiện tại `docs/ui/wireframes/`.
- Week 2: **27/09/2026 – 04/10/2026** — Hiện thực hóa 6 trang tĩnh chính / layout / fragments.
- Week 3: **05/10/2026 – 11/10/2026** — Tích hợp frontend với backend, hoàn thiện UI Messaging, Notification, Report/Moderation, validation/E2E/demo.

Bằng chứng thiết kế giao diện (Wireframe Artifacts) đã được đối chiếu cho tất cả 10 màn hình, trong khi trạng thái runtime / backend controller tiếp tục chờ hoàn tất theo kế hoạch:

| Màn hình / Luồng tương tác | Bằng chứng thiết kế (Week 1 Wireframe) | Trạng thái triển khai / Phụ thuộc backend |
|---|---|---|
| Login / Authentication: Nhập thông tin → validate → Home feed hoặc hiển thị lỗi | [`01-login.svg`](docs/ui/wireframes/01-login.svg) | Template `auth/login.html` đã có; chờ tích hợp xác thực JWT từ Khôi |
| Register: Nhập thông tin SV → kiểm tra định dạng → tạo tài khoản → Login | [`02-register.svg`](docs/ui/wireframes/02-register.svg) | Template `auth/register.html` đã có; chờ API đăng ký từ Khôi |
| Home Feed: Xem danh sách bài viết → tương tác Like/Reply/Repost → tạo bài viết | [`03-home-feed.svg`](docs/ui/wireframes/03-home-feed.svg) | Template `feed/index.html` đã có; dynamic feed & ranking pending backend |
| Profile: Xem thông tin cá nhân → Follow/Message hoặc chỉnh sửa hồ sơ | [`04-profile.svg`](docs/ui/wireframes/04-profile.svg) | Template `user/profile.html` đã có; API quan hệ Follow/Unfollow từ Khôi |
| Create Post: Soạn thảo nội dung → đính kèm media/poll → chọn phạm vi → đăng | [`05-create-post.svg`](docs/ui/wireframes/05-create-post.svg) | Template `post/create.html` đã có; xử lý tạo bài viết & media từ Ngọc |
| Post Detail: Xem chi tiết bài viết → tương tác, sửa/xóa (tác giả) hoặc report → bình luận | [`06-post-detail.svg`](docs/ui/wireframes/06-post-detail.svg) | Template `post/detail.html` đã có; luồng bình luận & report pending |
| Search: Nhập từ khóa → chuyển tab (People/Posts/Communities/Topics) → xem kết quả / empty state | [`07-search.svg`](docs/ui/wireframes/07-search.svg) | Đã có wireframe thiết kế đầy đủ; Search backend contract & template kết quả thuộc Week 3 Q3-02 |
| Community: Xem trang CLB → tham gia/rời nhóm → xem vai trò Admin/Mod/Member → đăng bài CLB | [`08-community.svg`](docs/ui/wireframes/08-community.svg) | Đã có wireframe thiết kế đầy đủ; Community backend contract & template chi tiết thuộc Week 3 Q3-02 |
| Messaging: Danh sách hội thoại → xem lịch sử chat 1-1 → soạn/gửi tin nhắn | [`09-messaging.svg`](docs/ui/wireframes/09-messaging.svg) | Đã có wireframe thiết kế đầy đủ; Messaging HTTP/STOMP realtime contract từ Bằng; UI thuộc Week 3 Q3-03 |
| Notification: Xem danh sách thông báo (Like/Reply/Follow/Community) → đánh dấu đã đọc | [`10-notification.svg`](docs/ui/wireframes/10-notification.svg) | Đã có wireframe thiết kế đầy đủ; Notification backend contract từ Bằng; UI thuộc Week 3 Q3-03 |
| Report / Moderation: Chọn nội dung → nhập lý do → gửi báo cáo xem xét | Thể hiện trên [`06-post-detail.svg`](docs/ui/wireframes/06-post-detail.svg) | POST report endpoint do Khôi phụ trách; UI review/moderation thuộc Week 3 Q3-04 |

**Nguyên tắc phân định ranh giới (Status Distinction)**:
- *Wireframe tồn tại (Artifact exists)*: Chứng minh cấu trúc giao diện, phân cấp thông tin và luồng thao tác đã được thiết kế đúng trách nhiệm Tuần 1 của Quân.
- *Tính năng hiện thực (Feature implemented)*: Cần mã nguồn template hoàn chỉnh, controller xử lý, service logic, JPA và database đã kiểm thử. Wireframe thiết kế **không** thay thế việc triển khai chức năng.
- Toàn bộ kịch bản kiểm thử runtime/browser E2E tiếp tục tuân thủ theo [FE_INTEGRATION_PLAN.md](FE_INTEGRATION_PLAN.md).
