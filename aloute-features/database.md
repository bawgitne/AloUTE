# Thiết kế dữ liệu

PostgreSQL là nguồn dữ liệu nghiệp vụ duy nhất; Firebase Storage chỉ giữ binary media.

## Nhóm bảng chính

| Nhóm | Bảng |
| --- | --- |
| Identity | `users`, `roles`, `user_roles`, `otp_verifications`, `refresh_tokens` |
| External identity/security | `external_identities`, `login_events`, `trusted_devices`, `backup_codes` |
| Profile | `user_profiles`, `user_interests` |
| Social graph | `friend_requests`, `friendships`, `follows`, `blocks` |
| Content | `posts`, `post_media`, `hashtags`, `post_hashtags`, `post_revisions` |
| Engagement | `post_reactions`, `comments`, `comment_reactions`, `saved_posts`, `post_views`, `post_shares` |
| Realtime | `conversations`, `conversation_members`, `messages`, `message_receipts`, `notifications` |
| Community | `groups`, `group_members`, `group_join_requests`, `events`, `event_participants` |
| Safety | `reports`, `moderation_actions`, `audit_logs` |
| Recommendation | `user_events`, `user_topic_preferences`, `post_scores`, `recommendation_impressions` |
| Auto moderation | `moderation_rules`, `keyword_entries`, `content_scan_results`, `moderation_appeals` |

## Quy ước chung

- Primary key dùng UUID.
- Mọi bảng nghiệp vụ có `created_at`; bảng mutable có `updated_at`.
- Entity cần xóa mềm có `deleted_at` và `deleted_by`.
- Dùng optimistic locking (`@Version`) cho dữ liệu dễ cập nhật đồng thời.
- Enum lưu dạng string để dễ đọc và tránh lỗi khi thay đổi thứ tự.
- Foreign key có chiến lược delete rõ ràng; không cascade xóa toàn bộ dữ liệu xã hội.

## Unique constraint quan trọng

- `users(lower(email))`, `users(lower(username))`.
- Một friendship cho một cặp User đã chuẩn hóa thứ tự ID.
- Một follow cho `(follower_id, following_id)`.
- Một block cho `(blocker_id, blocked_id)`.
- Một reaction cho `(user_id, post_id)`.
- Một saved post cho `(user_id, post_id)`.
- Một report đang mở cho `(reporter_id, target_type, target_id)`.

## Index quan trọng

- `posts(author_id, created_at desc)`.
- `posts(visibility, created_at desc)` với điều kiện chưa xóa.
- `comments(post_id, created_at)`.
- `messages(conversation_id, created_at desc)`.
- `notifications(user_id, is_read, created_at desc)`.
- Full-text/trigram index cho username, display name, post content và hashtag.

## Transaction quan trọng

- Xác minh OTP và kích hoạt tài khoản.
- Rotate refresh token.
- Chấp nhận kết bạn/block.
- Tạo post cùng media metadata.
- Gửi message rồi publish sau commit.
- Xử lý report, moderation action và audit log.
- Link/unlink Google identity và bảo đảm User luôn còn một phương thức đăng nhập.
- Quarantine nội dung, tạo scan result và moderator alert.

## Rà soát ERD theo yêu cầu tuần 1

| Yêu cầu | Thiết kế áp dụng | Ghi chú kiểm soát |
| --- | --- | --- |
| PK | UUID cho tất cả bảng nghiệp vụ | Sinh bằng application hoặc `gen_random_uuid()` khi dùng PostgreSQL |
| FK | Không cascade xóa dữ liệu xã hội quan trọng | Dùng soft delete để giữ audit và thống kê |
| Quan hệ 1-1 | `users` - `user_profiles` | `user_profiles.user_id` vừa là PK vừa là FK |
| Quan hệ 1-N | `users` - `posts`, `posts` - `comments`, `groups` - `posts` | Index FK để tránh chậm khi phân trang |
| Quan hệ N-N | `users` - `roles`, `posts` - `hashtags`, `posts` - `topics`, `users` - `groups` qua member | Bảng nối có unique composite |
| Self-reference | `comments.parent_id`, `friend_requests`, `friendships`, `follows`, `blocks` | Có check không cho quan hệ với chính mình |
| Soft delete | `posts`, `comments`, `groups`, `messages`, `reports`, `moderation_actions` | Có `deleted_at`, `deleted_by`; query mặc định lọc `deleted_at is null` |
| Visibility/privacy | `posts.visibility`, `user_profiles.profile_visibility`, `groups.privacy`, `conversation_members.role` | Service vẫn là nơi quyết định quyền cuối cùng |
| Phân quyền | `roles`, `user_roles`, role nhóm và owner/moderator | Role hệ thống tách khỏi role trong group |
| Audit | `audit_logs`, `post_revisions`, `moderation_actions` | Lưu actor, action, target và snapshot cần thiết |

## CSDL được chọn

Chọn **PostgreSQL** cho cài đặt thực tế vì:

- Hỗ trợ UUID, JSONB, enum/check constraint, partial index và full-text/trigram search tốt.
- Phù hợp với Spring Data JPA và Flyway.
- Có thể xử lý feed/search cơ bản bằng index trước khi cần hệ thống search riêng.
- Cho phép viết trigger/stored procedure để giữ counter, audit và một số invariant dữ liệu.

Firebase Storage chỉ lưu binary media. PostgreSQL lưu metadata: URL, MIME, kích thước, thứ tự hiển thị, người upload và trạng thái xóa mềm.

## Bảng cốt lõi cần hiện thực trước

| Module | Bảng ưu tiên | Lý do |
| --- | --- | --- |
| Identity | `users`, `roles`, `user_roles`, `refresh_tokens` | Nền tảng đăng nhập/JWT |
| Profile | `user_profiles`, `user_interests` | Hồ sơ và sở thích cho feed/search |
| Social | `follows`, `friend_requests`, `friendships`, `blocks` | Visibility bài viết và quan hệ người dùng |
| Content | `posts`, `post_media`, `hashtags`, `post_hashtags`, `post_revisions` | Tạo/xem/sửa/xóa bài viết |
| Engagement | `post_reactions`, `comments`, `comment_reactions`, `saved_posts`, `post_views`, `post_shares` | Like/reply/bookmark/share |
| Community | `topics`, `groups`, `group_members`, `group_join_requests`, `group_topics` | Community và phân quyền nhóm |
| Realtime | `conversations`, `conversation_members`, `messages`, `notifications` | Tin nhắn và thông báo |
| Safety | `reports`, `moderation_actions`, `audit_logs` | Báo cáo nội dung và kiểm duyệt |

## Constraint và quy tắc dữ liệu chi tiết

| Bảng | Constraint chính |
| --- | --- |
| `users` | `email` và `username` unique không phân biệt hoa thường; `username` chỉ gồm chữ, số, `.`, `_`; `status` thuộc enum hợp lệ |
| `user_profiles` | `display_name` 2-80 ký tự; `bio` tối đa 300; profile gắn đúng một user |
| `friendships` | Lưu cặp user đã chuẩn hóa `user_low_id < user_high_id`; unique cặp này |
| `follows` | Unique `(follower_id, following_id)`; không follow chính mình |
| `blocks` | Unique `(blocker_id, blocked_id)`; block phải làm mất hiệu lực follow/friend request ở tầng service |
| `posts` | Có `content` hoặc ít nhất một `post_media`; `content` tối đa 5.000; visibility enum |
| `post_media` | Tối đa 10 media/post; image tối đa 10 MB; video tối đa 100 MB; MIME thuộc allowlist |
| `hashtags` | Lưu dạng lowercase, unique theo `name` |
| `post_reactions` | Unique `(user_id, post_id)` để mỗi user chỉ reaction một lần |
| `comments` | Nội dung 1-2.000 ký tự nếu không có media; `parent_id` chỉ cho một cấp reply |
| `groups` | `name` 3-100; `description` tối đa 2.000; `privacy` gồm public/private visible/private hidden |
| `group_members` | Unique `(group_id, user_id)`; group luôn phải còn ít nhất một `OWNER` active |
| `reports` | Một report đang mở duy nhất cho `(reporter_id, target_type, target_id)` |

## Index đề xuất

| Mục đích | Index |
| --- | --- |
| Đăng nhập | `users(lower(email))`, `users(lower(username))` |
| Trang profile | `posts(author_id, created_at desc)` where `deleted_at is null` |
| Feed công khai | `posts(visibility, created_at desc)` where `deleted_at is null` |
| Bài trong group | `posts(group_id, created_at desc)` where `deleted_at is null` |
| Reaction | `post_reactions(post_id)` where `deleted_at is null` |
| Comment | `comments(post_id, created_at)` where `deleted_at is null` |
| Follow | `follows(follower_id)`, `follows(following_id)` |
| Community | `group_members(user_id, status)`, `groups(privacy, created_at desc)` |
| Notification | `notifications(user_id, is_read, created_at desc)` |
| Search | trigram/full-text cho `username`, `display_name`, `posts.content`, `hashtags.name`, `groups.name` |

## Trigger dự kiến

| Trigger | Bảng | Mục đích |
| --- | --- | --- |
| `trg_set_updated_at` | Các bảng mutable | Tự cập nhật `updated_at` khi update |
| `trg_post_reaction_count` | `post_reactions` | Tăng/giảm `posts.reaction_count` khi reaction được tạo/xóa mềm/khôi phục |
| `trg_post_comment_count` | `comments` | Tăng/giảm `posts.comment_count` khi comment được tạo/xóa mềm/khôi phục |
| `trg_post_revision` | `posts` | Ghi `post_revisions` khi sửa content/visibility |
| `trg_group_keep_owner` | `group_members` | Chặn thao tác làm group mất owner active cuối cùng |
| `trg_report_audit` | `reports`, `moderation_actions` | Ghi audit log cho xử lý report/kiểm duyệt |

## Stored procedure/function dự kiến

| Tên | Input chính | Output | Trách nhiệm |
| --- | --- | --- | --- |
| `create_post_with_media` | `author_id`, `content`, `visibility`, `group_id`, danh sách media | `post_id` | Tạo post và metadata media trong một transaction |
| `upsert_post_reaction` | `post_id`, `user_id`, `reaction_type` | `reaction_id` | Tạo hoặc đổi reaction, khôi phục nếu từng xóa mềm |
| `soft_delete_post` | `post_id`, `actor_id` | void | Xóa mềm post, ghi người xóa và cập nhật trạng thái |
| `accept_group_join_request` | `request_id`, `moderator_id` | `member_id` | Duyệt yêu cầu vào group, tạo member, gửi notification |
| `record_post_view` | `post_id`, `viewer_id`, `session_id` | void | Upsert view để chống tăng ảo |
| `resolve_report` | `report_id`, `moderator_id`, `action`, `note` | `moderation_action_id` | Đóng report, tạo moderation action và audit log |

## Mapping Entity JPA dự kiến

| Entity | Bảng | Quan hệ chính | Ghi chú annotation |
| --- | --- | --- | --- |
| `User` | `users` | `@OneToOne UserProfile`, `@ManyToMany Role` | `@Version`, enum `AccountStatus`, không expose password hash |
| `UserProfile` | `user_profiles` | `@MapsId User` | Enum `ProfileVisibility` lưu `STRING` |
| `Post` | `posts` | `@ManyToOne User author`, `@ManyToOne Group`, `@OneToMany PostMedia` | Không cascade remove; query lọc soft delete |
| `PostMedia` | `post_media` | `@ManyToOne Post` | Lưu Firebase URL và metadata |
| `Hashtag` | `hashtags` | `@ManyToMany Post` qua `post_hashtags` | Normalize lowercase ở service |
| `Topic` | `topics` | `@ManyToMany Post`, `@ManyToMany Group` | Dùng cho gợi ý và community |
| `PostReaction` | `post_reactions` | `@ManyToOne Post`, `@ManyToOne User` | Unique `(user_id, post_id)` |
| `Comment` | `comments` | `@ManyToOne Post`, `@ManyToOne User`, self `parent` | Giới hạn một cấp reply bằng validation service |
| `Group` | `groups` | `@ManyToOne owner`, `@OneToMany GroupMember` | Đặt tên class `CommunityGroup` nếu tránh đụng từ khóa SQL |
| `GroupMember` | `group_members` | `@ManyToOne Group`, `@ManyToOne User` | Enum role/status |
| `Notification` | `notifications` | `@ManyToOne User`, `@ManyToOne actor` | Target dùng `target_type`, `target_id` để linh hoạt |

## Dữ liệu mẫu tuần 1-2

- 3 user: `ngoc`, `khoi`, `bang` với profile đầy đủ.
- 3 topic: `hoc-tap`, `cau-lac-bo`, `tuyen-dung`.
- 1 public group: `aloute-dev-community`, owner là `ngoc`.
- 3 post mẫu: public, friends-only và group post.
- Reaction/comment mẫu để kiểm tra counter.
- Notification mẫu cho reaction/comment/follow.

## Checklist bàn giao phần Ngọc

- [x] Chọn PostgreSQL làm Hệ QTCSDL chính.
- [x] Rà soát nhóm bảng, PK/FK, 1-N, N-N, self-reference.
- [x] Xác định trường soft delete, visibility và phân quyền.
- [x] Xác định unique constraint và index quan trọng.
- [x] Xác định trigger và stored procedure/function dự kiến.
- [x] Chuẩn bị mapping Entity JPA dự kiến cho User/Profile/Post/Reaction/Topic/Community.
- [x] Chuẩn bị dữ liệu mẫu phục vụ demo và kiểm thử API sau này.
