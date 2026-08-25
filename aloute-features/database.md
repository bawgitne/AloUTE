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
