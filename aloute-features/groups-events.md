# Nhóm và sự kiện

Feature tạo không gian cộng đồng theo sở thích và hoạt động sinh viên.

## Nhóm

- Tạo nhóm công khai hoặc riêng tư.
- Nhóm riêng tư có thể `VISIBLE` hoặc `HIDDEN`; nhóm hidden không xuất hiện với non-member.
- Role trong nhóm: `OWNER`, `MODERATOR`, `MEMBER`.
- Tham gia/rời nhóm; nhóm riêng cần duyệt yêu cầu.
- Đăng bài trong nhóm.
- Owner/Moderator quản lý thành viên và nội dung nhóm.
- Owner cấu hình ai được đăng bài, chat, mời thành viên, ghim bài và sửa thông tin.

## Form tạo group nhiều bước

| Bước | Trường |
| --- | --- |
| 1. Thông tin | Tên 3–100 ký tự, mô tả tối đa 2.000, avatar, cover |
| 2. Chủ đề | Category, tối đa 5 tag, ngôn ngữ |
| 3. Privacy | `PUBLIC`/`PRIVATE`; private chọn `VISIBLE`/`HIDDEN` |
| 4. Thành viên | Mời bạn bè, bật/tắt duyệt member |
| 5. Onboarding | Tối đa 5 câu text/single/multiple choice, required/optional |
| 6. Permission/AutoMod | Ai được post/chat/invite; keyword, mention, link rule |
| 7. Xác nhận | Preview và chấp nhận community rules |

Form lưu draft server-side, cho quay lại bước trước và chỉ tạo Group trong transaction ở bước xác nhận.

## Public/private behavior

| Khả năng | Public | Private visible | Private hidden |
| --- | --- | --- | --- |
| Xuất hiện search | Có | Có | Chỉ member/invitee |
| Xem tên/mô tả | Mọi người | Mọi người | Chỉ member/invitee |
| Xem bài/member | Theo cấu hình | Chỉ member | Chỉ member |
| Tham gia | Join hoặc theo cấu hình | Request/approval | Invite/link có hạn |

## Onboarding và quyền nhóm

- Applicant điền câu hỏi; Moderator xem câu trả lời và Accept/Reject.
- Khi vào nhóm, User accept rules và chọn topic muốn theo dõi.
- Announcement mode: chỉ Owner/Moderator đăng hoặc chat; member xem và reaction.
- Invite link có expiry, usage limit và có thể revoke.
- Group rule có thể chặt hơn global rule nhưng không được vô hiệu hóa global safety.

## Sự kiện

- Tạo sự kiện với tên, mô tả, thời gian, địa điểm và ảnh bìa.
- Trạng thái tham gia: `INTERESTED`, `GOING`, `NOT_GOING`.
- Sự kiện có thể thuộc một nhóm hoặc độc lập.
- Không cho tạo sự kiện có thời gian kết thúc trước thời gian bắt đầu.

## Quy tắc

- Chỉ Owner chuyển quyền sở hữu nhóm.
- Nhóm phải luôn còn ít nhất một Owner.
- User bị ban khỏi nhóm không thể xem nội dung riêng tư hoặc gửi yêu cầu lại cho đến khi được mở ban.
- Post trong nhóm đồng thời tuân theo quyền thành viên và trạng thái kiểm duyệt.

## Acceptance criteria

- Kiểm tra role nhóm tại Service, không chỉ tại UI.
- Danh sách nhóm, thành viên, bài nhóm và sự kiện đều phân trang.
- Thông báo được gửi cho yêu cầu tham gia và thay đổi quan trọng.
- Public/private/hidden không làm rò rỉ post, member list hoặc onboarding answers.
- Form giữ draft và validate server-side ở từng bước.
