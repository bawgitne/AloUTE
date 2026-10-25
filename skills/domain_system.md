# ALOUTE — DOMAIN SYSTEM DESIGN

> **Scope**
>
> Đây là source of truth cho **nghiệp vụ ALOUTE**:
>
> ```text
> feature
> actor
> entity
> relationship
> business flow
> invariant
> visibility
> moderation behavior
> permission: WHO CAN DO WHAT
> ```
>
> File này không định nghĩa Spring/JPA/JWT/HTTP/API response/pagination/folder implementation.
>
> Technical implementation đọc:
>
> ```text
> ALOUTE_SYSTEM_ARCHITECTURE.md
> SPRING_SECURITY_CONVENTION.md
> ALOUTE_FRONTEND_CONVENTION.md
> ```

---

# 1. MỤC TIÊU HỆ THỐNG

ALOUTE là mạng xã hội kiểu Threads/Facebook-oriented community, tập trung vào:

- Hồ sơ người dùng.
- Quan hệ follow.
- Block / mute / restrict.
- Đăng bài.
- Reply.
- Quote.
- Repost.
- Like.
- Bookmark.
- Mention.
- Media.
- Topic.
- Poll.
- Community.
- Custom Feed.
- Direct Message.
- Group Message.
- Notification.
- Report / moderation.
- Post analytics cơ bản.

Core của hệ thống:

```text
USER
POST
COMMUNITY
```

Messaging là subsystem lớn tương đối độc lập.

---

# 2. ACTOR

## 2.1. Guest

Guest có thể:

- Xem public post.
- Xem public profile.
- Xem public community.
- Duyệt topic.
- Đăng ký.
- Đăng nhập.

Guest không được thực hiện authenticated interaction như:

- Like.
- Follow.
- Repost.
- Bookmark.
- Message.
- Vote poll.
- Join community.

## 2.2. User

User đã đăng nhập có thể:

- Quản lý profile.
- Quản lý social relationship.
- Tạo/quản lý post của mình.
- Tương tác với post khi business rule cho phép.
- Quản lý topic quan tâm.
- Tạo/tham gia community.
- Quản lý custom feed.
- Nhắn tin trong conversation hợp lệ.
- Nhận notification.
- Report.
- Block.
- Mute.
- Hide.
- Restrict.

## 2.3. Community Moderator

Là User có scoped role:

```text
MODERATOR
```

trong một Community cụ thể.

Moderator có quyền moderation trong phạm vi community theo permission matrix ở phần Community.

## 2.4. Community Admin

Là User có scoped role:

```text
ADMIN
```

trong một Community cụ thể.

Admin quản lý community ở mức cao hơn Moderator.

## 2.5. Community Owner

Community có:

```text
owner_id
```

Owner là quan hệ domain riêng.

Owner và `CommunityRole.ADMIN` không được coi mặc định là cùng một source of truth.

Nếu owner đồng thời cần membership role, invariant đó phải được enforce rõ.

---

# 3. DOMAIN MAP

```text
ALOUTE
│
├── Identity & Profile
├── Social Graph
├── Post
│   ├── Media
│   ├── Like
│   ├── Reply
│   ├── Repost
│   ├── Quote
│   ├── Bookmark
│   └── Mention
│
├── Topic
├── Poll
├── Community
├── Custom Feed
├── Messaging
├── Notification
├── Moderation & Safety
└── Analytics
```

---

# 4. IDENTITY & PROFILE

## 4.1. User

Business data chính:

```text
id
username
email
password_hash
is_verified
is_private
is_active
created_at
updated_at
```

`User` là identity hub của hệ thống.

Các domain khác tham chiếu User.

## 4.2. Profile

Quan hệ:

```text
User 1 ─── 1 Profile
```

Thông tin:

- Display name.
- Bio.
- Avatar.
- Website.
- Cached counters.

Counters hiện có:

```text
followers_count
following_count
posts_count
```

Counter là denormalized value.

Source relation thực vẫn là các relation tương ứng.

## 4.3. Profile Link

```text
User 1 ─── N ProfileLink
```

Một user có thể có nhiều external profile links.

---

# 5. ACCOUNT STATE

Hiện domain có tối thiểu:

```text
is_verified
is_private
is_active
```

Business rule:

- `is_private` ảnh hưởng visibility/follow workflow.
- `is_active` ảnh hưởng khả năng sử dụng account.
- `is_verified` là trạng thái account/profile, không tự động đồng nghĩa với global authorization role.

Chi tiết follow request cho private account hiện là GAP ở phần Known Gaps.

---

# 6. SOCIAL GRAPH

## 6.1. Follow

```text
User A ── follows ──► User B
```

Business invariant:

- Một pair follower/following chỉ tồn tại một lần.
- Follow là directional.
- Private account cần follow-request workflow trước khi follow có hiệu lực nếu feature này được triển khai.

## 6.2. Block

```text
blocker
blocked
```

Block dùng để ngăn tương tác mạnh giữa hai user.

Exact effect matrix của Block cần được chốt trước khi implement đầy đủ mọi interaction.

Tối thiểu các service liên quan phải xem block relationship là business permission input.

## 6.3. Mute

Mute chủ yếu ảnh hưởng trải nghiệm nội dung/notification của người mute.

Mute khác Block ở chỗ không mặc định cắt toàn bộ interaction hai chiều.

## 6.4. Restrict

Restrict là cơ chế giới hạn mềm hơn Block.

Exact behavior matrix là domain rule cần được chốt nếu feature được triển khai sâu.

---

# 7. POST

Post là content aggregate trung tâm.

Data hiện có:

```text
id
user_id
content
parent_post_id
quoted_post_id
visibility
allow_reply
allow_quote
like_count
reply_count
repost_count
quote_count
view_count
created_at
updated_at
deleted_at
```

## 7.1. Normal Post

```text
parent_post_id = null
quoted_post_id = null
```

## 7.2. Reply

Reply vẫn là Post.

```text
parent_post_id != null
```

Không có Reply entity riêng trong model hiện tại.

## 7.3. Quote

Quote vẫn là Post.

```text
quoted_post_id != null
```

## 7.4. Soft Delete

```text
deleted_at
```

đại diện soft-deleted state.

Business behavior khi post bị deleted phải được các feature đọc post/reply/quote tôn trọng.

---

# 8. POST OWNERSHIP

Post có owner:

```text
post.user_id
```

Owner được phép quản lý own post theo business operation được system hỗ trợ.

User khác không được edit post chỉ vì cùng global role `USER`.

Moderation có thể tạo ngoại lệ theo Community/Moderation rule.

Cách backend enforce ownership thuộc Security Convention.

---

# 9. POST MEDIA

```text
Post 1 ─── N PostMedia
```

Media type:

```text
IMAGE
VIDEO
GIF
```

Metadata có thể gồm:

```text
url/reference
width
height
duration_seconds
position
```

`position` quyết định thứ tự media.

Binary storage thuộc architecture, không thuộc domain document.

## 9.1. Media Storage & Quota Shield

Để bảo vệ tài nguyên hệ thống và tối ưu chi phí lưu trữ đối với media:
- **Tối ưu Client-side**: Ảnh được khuyến nghị nén sang WebP phía client (độ phân giải tối đa 1920px, chất lượng ~82%) trước khi upload để tiết kiệm băng thông.
- **Giới hạn kích thước**: Ảnh tối đa 5 MB; Video tối đa 30 MB.
- **Hạn mức tài khoản (User Storage Quota)**: Mỗi tài khoản được cấp tối đa 150 MB media. Khi chạm ngưỡng, hệ thống từ chối upload mới cho đến khi xóa bớt media cũ.
- **Ngưỡng an toàn toàn hệ thống (Global Safety Cap)**: Ngưỡng trần an toàn đặt ở 8.5 GB (85% của gói Free Tier 10GB). Chạm ngưỡng này hệ thống tạm ngắt nhận file mới để ngăn phát sinh chi phí.

---

# 10. POST LIKE

```text
PostLike(
    user_id,
    post_id
)
```

Invariant:

```text
một User like một Post tối đa một lần
```

Unlike là remove relationship tương ứng.

Like có thể tạo Notification theo notification rule.

---

# 11. REPOST

```text
Repost(
    user_id,
    post_id
)
```

Invariant:

```text
một User repost một Post tối đa một lần
```

Repost khác Quote:

```text
Repost
→ share existing post relationship

Quote
→ create new Post referencing quoted_post_id
```

---

# 12. BOOKMARK

```text
Bookmark(
    user_id,
    post_id
)
```

Bookmark là dữ liệu riêng của user.

User khác không được xem bookmark của user nếu product không có requirement khác.

---

# 13. MENTION

```text
Post N ─── N User
```

qua mention relationship.

Mention có thể:

- Ảnh hưởng Post visibility `MENTIONED`.
- Tạo notification.

---

# 14. POST VISIBILITY

Canonical enum hiện tại:

```text
PUBLIC
FOLLOWERS
MENTIONED
```

## PUBLIC

Public audience được xem, subject to block/deletion/safety rule.

## FOLLOWERS

Chỉ audience thỏa follower relationship được xem, subject to các rule khác.

## MENTIONED

Chỉ user được mention được xem, subject to owner/system moderation rule nếu có.

Visibility rule là business permission, không được frontend tự suy luận làm security authority.

---

# 15. POST INTERACTION PERMISSION

Hiện model có:

```text
allow_reply
allow_quote
```

Các operation Reply/Quote phải tôn trọng các field này.

Known limitation:

```text
boolean
```

không biểu diễn được policy chi tiết kiểu:

```text
EVERYONE
FOLLOWERS
MENTIONED
NONE
```

Nếu product cần granular interaction, xem Known Gaps.

---

# 16. TOPIC

Topic là taxonomy chung.

Ví dụ:

```text
Technology
Java
Football
Music
AI
```

Relationships:

```text
Post N ─── N Topic
User N ─── N Topic
```

`post_topics` thể hiện topic của post.

`user_topics` thể hiện topic user quan tâm.

---

# 17. POLL

Một Post có tối đa một Poll.

```text
Post 1 ─── 0..1 Poll
Poll 1 ─── N PollOption
```

Vote:

```text
PollVote(
    poll_id,
    option_id,
    user_id
)
```

## 17.1. Single-choice invariant

Nếu:

```text
allow_multiple = false
```

thì một User chỉ được chọn một option trong Poll.

Constraint kiểu:

```text
(poll_id, option_id, user_id)
```

không đủ để enforce invariant này.

Đây là business invariant bắt buộc khi implement vote.

## 17.2. Multiple-choice and Database Constraint

Nếu `allow_multiple = true`, user có thể chọn nhiều option trong một lần vote.

Do đó, ràng buộc duy nhất ở CSDL bắt buộc phải là:

```text
UNIQUE (poll_id, option_id, user_id)
```

Tuyệt đối không đặt `UNIQUE (poll_id, user_id)` ở tầng CSDL vì sẽ gây lỗi xung đột (crash) khi user chọn từ 2 phương án trở lên. Việc kiểm soát người dùng chỉ vote 1 lần đối với single-choice (`allow_multiple = false`) được xử lý bằng tầng Application Logic trước khi ghi DB.

---

# 18. COMMUNITY

Community data hiện có:

```text
id
owner_id
name
slug
description
avatar_url
cover_url
is_private
member_count
post_count
```

Community là social space chứa membership, role, flair, champion và community post relationship.

---

# 19. COMMUNITY MEMBERSHIP

```text
User N ─── N Community
```

qua:

```text
CommunityMember
```

Scoped role hiện tại:

```text
MEMBER
MODERATOR
ADMIN
```

Role chỉ có nghĩa trong Community tương ứng.

---

# 20. COMMUNITY OWNER

Community có:

```text
owner_id
```

Owner là người sở hữu community.

Current model đồng thời có `CommunityRole.ADMIN`.

Để tránh hai source of truth:

- `owner_id` xác định owner.
- `CommunityRole.ADMIN` xác định member có quyền admin.
- Không suy ra owner chỉ từ role ADMIN.
- Không tự động coi mọi ADMIN là owner.

Rule chuyển ownership nếu cần hiện chưa được mô tả đầy đủ.

---

# 21. COMMUNITY PERMISSION MATRIX

Đây là source of truth nghiệp vụ hiện tại.

## MEMBER

Có thể:

- Tham gia community hợp lệ.
- Tạo community post nếu community rule cho phép.
- Dùng các feature member thông thường.

## MODERATOR

Có quyền của member và:

- Moderation nội dung trong community.
- Hỗ trợ quản lý member ở phạm vi moderation được product cho phép.

Không mặc định có quyền ownership.

## ADMIN

Có quyền quản trị community cao hơn moderator:

- Quản lý thông tin community theo product rule.
- Quản lý member.
- Gán scoped role trong phạm vi được cho phép.
- Quản lý flair.
- Quản lý champion.
- Quản lý community content.

## OWNER

Owner có ownership authority của Community.

Các operation đặc biệt như:

```text
transfer ownership
delete community
demote owner
```

chưa được model hiện tại mô tả đầy đủ và phải được chốt trước khi implement.

---

# 22. PRIVATE COMMUNITY

Community có:

```text
is_private
```

Nhưng model hiện tại chưa có join-request lifecycle đầy đủ.

Do đó private community approval là GAP.

Không fake workflow bằng cách add member trực tiếp rồi coi như pending.

---

# 23. COMMUNITY POST

Post vẫn là Post chung.

Community relationship:

```text
Community N ─── N Post
```

qua community-post relationship hiện tại.

Business rule:

- Khi post được coi là post của Community, community permission/moderation có thể áp dụng.
- Post ownership của author vẫn là một input riêng.
- Community moderation và post ownership không được trộn thành một khái niệm.

---

# 24. COMMUNITY FLAIR

```text
Community 1 ─── N CommunityFlair
```

User flair assignment thuộc một Community cụ thể.

Không coi flair là global user attribute.

---

# 25. COMMUNITY CHAMPION

```text
Community N ─── N User
```

Champion dùng để đánh dấu user nổi bật trong Community.

Champion không tự động là permission role trừ khi domain requirement định nghĩa sau này.

---

# 26. CUSTOM FEED

Custom Feed thuộc User.

```text
User 1 ─── N CustomFeed
```

Có thể chứa:

```text
Feed Topics
Feed Users
```

Relationships:

```text
CustomFeed N ─── N Topic
CustomFeed N ─── N User
```

Feed có public/private state.

User khác có thể save public custom feed.

---

# 27. FEED TYPES

Functional feed hiện gồm:

```text
Home Feed
Following Feed
Explore Feed
Custom Feed
Community Feed
Topic Feed
```

## Home Feed

Nguồn gồm:

```text
followed users
interested topics
recommended posts
```

**BẤT BIẾN QUAN TRỌNG (PRIVACY INVARIANT):**
- Bài viết trong Nhóm Kín (`is_private = 1`) **tuyệt đối không được rò rỉ ra Home Feed** của bất kỳ ai không phải là thành viên nhóm.
- Mọi điều kiện lọc Home Feed phải đảm bảo bài viết nhóm kín chỉ hiển thị khi `currentUserId IN (SELECT user_id FROM community_members WHERE community_id = post.community_id)`.
- Loại trừ hoàn toàn bài viết của người bị chặn (Block) cả 2 chiều và người bị tắt tiếng (Mute).

## Following Feed

Nguồn gồm các bài viết từ những người dùng mà `currentUserId` đang theo dõi.

## Explore Feed

Nguồn gồm các bài viết công khai (`PUBLIC`) có tương tác cao (thịnh hành), bài mới từ các chủ đề hot. Dành cho cả Guest và User đã đăng nhập để khám phá nội dung mới.

## Custom Feed

Nguồn:

```text
feed_users
+
feed_topics
```

## Community Feed

Nguồn:

```text
community posts
```

---

# 28. MESSAGING

Messaging gồm:

```text
Conversation
ConversationMember
Message
MessageMedia
MessageReaction
```

Conversation type:

```text
DIRECT
GROUP
```

Conversation có thể có:

- Creator.
- Group name.
- Group avatar.

---

# 29. CONVERSATION MEMBERSHIP

```text
Conversation N ─── N User
```

Membership có thể có:

```text
joined_at
last_read_message_id
muted state
```

Invariant:

- Chỉ member hợp lệ mới được đọc conversation content.
- Chỉ member hợp lệ mới được gửi message vào conversation.
- Permission luôn scoped theo conversation cụ thể.

Group role:

```text
OWNER
ADMIN
MEMBER
```

hiện là GAP nếu project cần kick/promote/transfer ownership.

---

# 30. MESSAGE

Message data:

```text
id
conversation_id
sender_id
reply_to_message_id
content
created_at
updated_at
deleted_at
```

Message reply dùng self-reference:

```text
reply_to_message_id
```

Message soft delete:

```text
deleted_at
```

---

# 31. MESSAGE MEDIA

```text
Message 1 ─── N MessageMedia
```

Media type:

```text
IMAGE
VIDEO
GIF
```

Storage implementation thuộc architecture.

---

# 32. MESSAGE REACTION

Message reaction relationship hiện tại:

```text
(message_id, user_id, reaction)
```

Một user có thể reaction vào message theo model này.

Conversation membership vẫn phải được tôn trọng.

---

# 33. NOTIFICATION

Notification thuộc recipient:

```text
user_id
```

Actor:

```text
actor_id
```

Notification có thể liên kết tới:

- Post.
- Message.
- Community.

Canonical notification types hiện có:

```text
LIKE
REPLY
FOLLOW
REPOST
QUOTE
MENTION
MESSAGE
COMMUNITY
```

Notification là side-effect của business action khác; user không trực tiếp tạo arbitrary notification.

## 33.1. Notification Management Actions

User có quyền quản lý notification của chính mình (`user_id == current_user.id`):
1. **Unread Count**: Đếm số notification chưa đọc (`is_read == false`).
2. **Mark as Read**: Đánh dấu 1 notification cụ thể là đã đọc (`is_read = true`).
3. **Mark All as Read**: Đánh dấu toàn bộ notification của user là đã đọc trong 1 transaction.
4. **Delete Notification**: Xóa 1 notification cá nhân.

## 33.2. Web Push Device Tokens (FCM Tokens)

Để hỗ trợ nhận thông báo nền (background/offline notification) khi trình duyệt đóng hoặc không active trên tab web:
- Mỗi user có thể đăng ký nhiều thiết bị/trình duyệt thông qua bảng `user_fcm_tokens`.
- **Ownership Invariant**: Một token FCM chỉ được gán cho 1 user tại một thời điểm (Unique constraint trên `token`). Khi người dùng đăng nhập trên cùng thiết bị/trình duyệt với tài khoản mới, token được re-assign sang `userId` mới.
- **Deregistration**: Khi user đăng xuất hoặc thu hồi quyền thông báo, token tương ứng được xóa khỏi hệ thống qua `DELETE /api/notifications/fcm-token`.
- **Data Model**:
  ```text
  Table: user_fcm_tokens
  ├── id (BIGINT / BIGSERIAL, PK)
  ├── user_id (BIGINT, FK -> users.id, NOT NULL)
  ├── token (VARCHAR(500), UNIQUE, NOT NULL)
  ├── created_at (TIMESTAMP WITH TIME ZONE, NOT NULL)
  └── updated_at (TIMESTAMP WITH TIME ZONE, NOT NULL)
  Indexes:
  ├── idx_user_fcm_tokens_user_id (user_id)
  └── uq_user_fcm_tokens_token (token, UNIQUE)
  ```

---

# 34. NOTIFICATION SIDE EFFECTS

Ví dụ:

```text
Like Post
→ LIKE notification

Reply Post
→ REPLY notification

Follow User
→ FOLLOW notification

Mention User
→ MENTION notification

Send Message
→ MESSAGE notification
```

Exact suppression/deduplication preference chưa được model hiện tại mô tả đầy đủ.

Notification settings hiện là GAP.

---

# 35. MODERATION & SAFETY

Các concept hiện có:

```text
Report
HiddenPost
RestrictedUser
Block
Mute
```

Community moderation cần bổ sung structure nếu product triển khai sâu.

---

# 36. REPORT

User có thể report:

```text
User
Post
```

Report fields:

```text
reporter_id
reported_user_id
reported_post_id
reason
description
status
```

Reason:

```text
SPAM
HARASSMENT
HATE
MISINFORMATION
VIOLENCE
OTHER
```

Status:

```text
PENDING
REVIEWED
REJECTED
RESOLVED
```

Moderation workflow phải chuyển status hợp lệ theo review process.

---

# 37. HIDDEN POST

Hidden Post là preference của một user:

```text
ẩn post khỏi trải nghiệm user
```

Nó không xóa Post khỏi hệ thống.

---

# 38. RESTRICTED USER

Restricted relationship là self-relationship User ↔ User.

Restrict mềm hơn Block.

Behavior chi tiết cần chốt nếu UI/backend implement đầy đủ.

---

# 39. ANALYTICS

Analytics hiện tại ở mức cơ bản.

Concept:

```text
PostView
LinkClick
```

Post View có thể gồm:

```text
post_id
user_id nullable
viewed_at
```

`user_id nullable` cho phép anonymous view.

Link Click có thể gồm:

```text
post_id
user_id
url
clicked_at
```

Privacy/retention policy chi tiết chưa nằm trong current domain scope.

---

# 40. CACHED COUNTERS

Domain hiện có nhiều cached counter:

```text
followers_count
following_count
posts_count

like_count
reply_count
repost_count
quote_count
view_count

member_count
community post_count

poll vote_count
```

Business invariant:

```text
cached counter
```

phải phản ánh source relation tương ứng.

Counter không phải source of truth duy nhất.

Implementation consistency strategy thuộc architecture/backend implementation.

---

# 41. CREATE POST FLOW

```text
User
 ↓
Create Post
 ├── content
 ├── visibility
 ├── reply/quote configuration
 ├── optional media
 ├── optional mentions
 ├── optional topics
 └── optional poll
 ↓
Post aggregate created
```

Business checks phải gồm những rule liên quan như account state, permission, visibility configuration và attached feature invariant.

---

# 42. REPLY FLOW

```text
User
 ↓
Select target Post
 ↓
Check post is replyable
 ↓
Create Post
 ↓
parent_post_id = target.id
```

Reply vẫn là Post.

---

# 43. QUOTE FLOW

```text
User
 ↓
Select target Post
 ↓
Check quote allowed
 ↓
Create Post
 ↓
quoted_post_id = target.id
```

---

# 44. LIKE FLOW

```text
User
 ↓
Like Post
 ↓
Create PostLike relationship
 ↓
Update cached count consistently
 ↓
Create notification if applicable
```

Like duplicate phải bị ngăn.

---

# 45. FOLLOW FLOW

```text
Follower
 ↓
Follow target
 ↓
Create relationship
 ↓
Update cached counters consistently
 ↓
Create notification if applicable
```

Private target cần follow-request workflow khi feature đó được triển khai.

---

# 46. COMMUNITY JOIN FLOW

Public/basic flow:

```text
User
 ↓
Join Community
 ↓
CommunityMember
 ↓
role = MEMBER
```

Private community không được dùng flow này nếu approval workflow là requirement.

---

# 47. POLL VOTE FLOW

```text
User
 ↓
Select option(s)
 ↓
Validate poll state
 ↓
Validate single/multiple choice rule
 ↓
Create vote relationship
 ↓
Update cached vote count consistently
```

---

# 48. DIRECT MESSAGE FLOW

```text
User A
 ↓
Start DIRECT conversation
 ↓
Conversation members
 ├── User A
 └── User B
 ↓
Send Message
 ↓
Persist Message
 ↓
Notify/deliver realtime as applicable
```

Technical transport thuộc architecture.

---

# 49. GROUP MESSAGE FLOW

```text
Creator
 ↓
Create GROUP conversation
 ↓
Add members
 ↓
Members send messages subject to conversation permission
```

Admin/owner group operations chưa đầy đủ trong current model.

---

# 50. REPORT FLOW

```text
User
 ↓
Report User/Post
 ↓
Report status = PENDING
 ↓
Review
 ├── REVIEWED
 ├── REJECTED
 └── RESOLVED
```

Exact transition restriction có thể được bổ sung khi moderation workflow được chi tiết hóa.

---

# 51. SOFT DELETE

Hiện có soft-delete concept ở:

```text
Post
Message
```

Mục tiêu business:

- Không phá reference.
- Có khả năng audit/recovery theo requirement.
- Reader phải biết resource đã deleted.

Delete visibility/render behavior cụ thể cần thống nhất trong feature implementation.

---

# 52. BUSINESS ATOMICITY REQUIREMENTS

Một số use case có nhiều state thay đổi phải được nhìn như một business operation thống nhất.

Ví dụ:

```text
Like
→ relationship + counter + notification side effect

Follow
→ relationship + counters + notification side effect

Community Join
→ membership + member_count

Poll Vote
→ vote + vote_count
```

Domain chỉ định **state nào phải consistent**.

Cách dùng transaction/event/reconciliation thuộc System Architecture/backend implementation.

---

# 53. SEARCH

Search domain riêng chưa được model hóa thành persistent entity.

Search runtime có thể cần tìm:

- Username.
- Display name.
- Post content.
- Topic.
- Community.

Ranking/full-text/search-engine implementation chưa được domain document chốt.

---

# 54. KNOWN GAP — PRIVATE FOLLOW REQUEST

Current:

```text
User.is_private
```

nhưng thiếu follow-request lifecycle:

```text
PENDING
ACCEPTED
REJECTED
```

Đề xuất domain concept:

```text
FollowRequest(
    requester_id,
    target_id,
    status,
    created_at,
    responded_at
)
```

---

# 55. KNOWN GAP — PRIVATE COMMUNITY JOIN REQUEST

Current:

```text
Community.is_private
```

nhưng thiếu:

```text
CommunityJoinRequest
```

với pending/approve/reject lifecycle.

---

# 56. KNOWN GAP — POLL SINGLE CHOICE

Current relationship constraint không đủ enforce:

```text
allow_multiple = false
```

Business/service/database design phải bổ sung cách enforce trước khi feature được coi là hoàn chỉnh.

---

# 57. KNOWN GAP — POST INTERACTION PERMISSION

Current:

```text
allow_reply boolean
allow_quote boolean
```

Nếu cần granular rule, có thể mở rộng domain thành:

```text
EVERYONE
FOLLOWERS
MENTIONED
NONE
```

Chỉ thêm khi product requirement cần.

---

# 58. KNOWN GAP — MESSAGE GROUP ROLE

Current conversation membership chưa support đầy đủ:

```text
OWNER
ADMIN
MEMBER
```

Do đó chưa hỗ trợ tốt:

- Kick member.
- Promote admin.
- Transfer ownership.

---

# 59. KNOWN GAP — COMMUNITY MODERATION

Nếu moderation community được triển khai sâu, current model còn thiếu các concept như:

```text
CommunityBan
CommunityReport
```

`ModerationLog` đã được hiện thực bằng entity `ModerationAction` (cột `community_id` phân scope
community cho action `COMMUNITY_POST_APPROVE | COMMUNITY_POST_REJECT | COMMUNITY_POST_REMOVE`).

Không invent persistence implementation trong domain doc; chỉ ghi nhận capability/data gap.

---

# 59A. COMMUNITY POST APPROVAL & MEMBER MANAGEMENT (canonical, 2026-10-25)

## 59A.1. Community post approval lifecycle

- `Community.requirePostApproval` (default `false`, owner-only setting) bật cơ chế phê duyệt.
- `Post.moderationStatus`: `PENDING | APPROVED | REJECTED`; **NULL đọc là APPROVED** ở mọi read path
  (bài không thuộc community, hoặc row legacy trước khi bật cơ chế).
- Tạo post trong community:
  - Approval OFF → publish ngay (`APPROVED`).
  - Approval ON + tác giả là Owner hoặc `CommunityRole.ADMIN` → publish ngay (`APPROVED`).
  - Approval ON + member thường → `PENDING`.
- `PENDING`/`REJECTED` chỉ hiển thị với: **tác giả** (timeline cá nhân của chính mình + direct URL +
  bookmark của mình) và **moderator của community đó** (dashboard quản lý). Ẩn hoàn toàn với: global feed,
  following/explore, community feed, search, profile của người khác, bookmark người khác, repost/quote
  (không thể tương tác post chưa duyệt với người ngoài), notification payload.
- Moderator = Community Owner + member giữ `CommunityRole.ADMIN` (`MODERATOR` chưa được gán quyền moderation).
- Approve: `PENDING|REJECTED → APPROVED`. Reject (kèm reason ≤ 500 ký tự): `PENDING → REJECTED`.
  Remove (soft-delete, giữ audit + khả năng restore): chỉ post đã publish. Decision song song/lặp lại bị
  chặn bằng pessimistic lock + trạng thái (`409 POST_MODERATION_STATE_INVALID`).
- Author edit post (đổi content):
  - Approval ON + member thường → trở lại `PENDING` (kể cả post đã APPROVED — chống bait-and-switch).
  - Ngược lại (owner/admin tác giả, hoặc approval OFF) → `PENDING|REJECTED` thành `APPROVED`.
  - Edit không đổi content không đổi status.
- Author xóa post `PENDING`/`REJECTED`: soft-delete như post thường; không ảnh hưởng counter.
- Community bị soft-delete → mọi moderation action trả `404 COMMUNITY_NOT_FOUND`.
- Member bị remove: post đã publish giữ nguyên; post `PENDING` giữ nguyên trạng thái chờ moderator xử lý.
- `community.postCount` giữ semantics "tổng bài đã tạo" (tăng lúc create); dashboard dùng count query
  theo visibility thực tế (published = `APPROVED`/NULL, removed = removal active).

## 59A.2. WHO CAN DO WHAT (community-scoped permission matrix)

| Action | Owner | ADMIN-role | MODERATOR/MEMBER | Outsider/Guest | SYSTEM_ADMIN |
|---|---|---|---|---|---|
| Vào dashboard `/communities/{id}/manage` | ✔ | ✔ | ✘ (403) | ✘ (private → 404) | ✘ (không auto-được quyền) |
| Duyệt/từ chối bài PENDING | ✔ | ✔ | ✘ | ✘ | ✘ |
| Gỡ bài đã publish (soft-remove) | ✔ | ✔ | ✘ | ✘ | ✔ (theo rule admin hiện có) |
| Xem bài PENDING/REJECTED | ✔ | ✔ | chỉ tác giả thấy bài của mình | ✘ | ✘ |
| Đổi role member (promote/demote ADMIN) | ✔ | ✘ (403) | ✘ | ✘ | ✘ |
| Remove member thường | ✔ | ✔ | ✘ | ✘ | ✘ |
| Remove member ADMIN | ✔ | ✘ | ✘ | ✘ | ✘ |
| Remove Owner | ✘ (không ai; owner phải transfer/xóa community) | ✘ | ✘ | ✘ | ✘ |
| Bật/tắt `requirePostApproval` | ✔ | ✘ | ✘ | ✘ | ✘ |
| Update thông tin community (tên/mô tả/privacy) | ✔ | ✔ (giữ nguyên rule cũ) | ✘ | ✘ | ✘ |

- Tác giả post: sửa/xóa bài của mình theo rule hiện có; xem status + lý do từ chối của bài của mình.
- Private community: concealement 404 được bảo toàn trên toàn bộ surface mới (dashboard, moderation API).

---

# 60. AUTH BUSINESS CAPABILITIES — PASSWORD RESET & SESSION INVALIDATION

### 60.1. Product & Security Lifecycle
- **Durable Account Session Epoch (`User.authVersion`)**:
  - `User.authVersion` (long, default `0L`) represents the durable credential version.
  - JWT tokens encode `v: authVersion` as a signed claim from the authenticated `UserPrincipal` snapshot.
  - Centralized validation seam: `JwtSessionService.authenticate(String token)` validates signature, expiration, active status, and enforces that token `v` strictly equals `user.authVersion`.
  - Legacy tokens with missing `v` claim are accepted only when `user.authVersion == 0L`.
  - Malformed, negative, fractional, or mismatched versions are rejected with `BadCredentialsException`.

- **Password Recovery Request Flow (`POST /api/auth/forgot-password`)**:
  - Requires valid CSRF (`X-XSRF-TOKEN`).
  - Input: `{ email }`.
  - Protection against account enumeration: If email does not exist or user is inactive, a constant-time dummy hash is executed and generic 200 `ApiResponse.success(null)` is returned.
  - In-process bounded rate limiting: Enforces request cooldown (configurable, default 60s) and sliding window limits (max 5 requests per 15 minutes) returning 429 Too Many Requests (`RATE_LIMIT_EXCEEDED`).
  - Cryptographically secure 32-byte hex token is generated. Only SHA-256 hash is persisted in `password_reset_tokens`.
  - Expiry is configurable (default 20 minutes). At most one active reset token row per user.
  - Recovery link is constructed with fragment `#token=...` using trusted environment base URL (`aloute.app.base-url`).
  - Email notification delivery via `EmailService` is committed only `afterCommit` of the durable token transaction.

- **Password Reset Execution Flow (`POST /api/auth/reset-password`)**:
  - Requires valid CSRF (`X-XSRF-TOKEN`).
  - Input: `{ token, password, confirmPassword }`.
  - Validates passwords match and conform to standard constraint (8..128 characters).
  - Verifies token hash exists and is unexpired; single-use (token row deleted upon consumption).
  - Concurrency & Atomicity: Acquires `PESSIMISTIC_WRITE` lock on `User`, verifies `token.authVersion == user.authVersion`, replaces `passwordHash`, increments `authVersion`, and consumes token.
  - Optimistic locking (`@Version`) and `@DynamicUpdate` protect `authVersion` and `passwordHash` against resurrection by unrelated concurrent user flushes.

- **Immediate Multi-Session Invalidation**:
  - Resetting password immediately invalidates all active sessions (both HTTP and real-time STOMP WebSocket channels) because previously minted tokens have `v < user.authVersion`.
  - Logging in with new credentials mints tokens with the newly incremented `authVersion`.

- **SSR Views**:
  - `GET /forgot-password`: semantic SSR form.
  - `GET /reset-password`: served with `Referrer-Policy: no-referrer`. Client JS immediately clears `#token=...` from URL bar via `history.replaceState` before submitting via same-origin JSON POST.

### 60.2. Production Schema Prerequisites & Backfill Prerequisite
- **Deployment Status**: Feature is authorized for local/H2 runtime. Production rollout requires explicit owner-approved DDL migration before startup:
  1. Add column `auth_version BIGINT NOT NULL DEFAULT 0` on `users`.
  2. Add column `version BIGINT DEFAULT 0` on `users`.
  3. Backfill all existing `users` rows: `UPDATE users SET auth_version = 0, version = 0 WHERE auth_version IS NULL`.
  4. Create table `password_reset_tokens` with columns `id BIGINT AUTO_INCREMENT PRIMARY KEY`, `user_id BIGINT NOT NULL UNIQUE`, `token_hash VARCHAR(64) NOT NULL UNIQUE`, `auth_version BIGINT NOT NULL`, `expires_at TIMESTAMP NOT NULL`, `created_at TIMESTAMP NOT NULL`, and foreign key `fk_password_reset_tokens_user` to `users(id)`.

### 60.3. Registration Email OTP Verification Lifecycle & Legacy Account Compatibility Policy
- **Lifecycle & Cryptographic Verification**:
  - Upon registration (`POST /api/auth/register`), new accounts are created with `isVerified = false` and `verificationRequired = true`.
  - A cryptographically random 6-digit numeric OTP (100000–999999) is generated and hashed using HMAC-SHA256 with a per-record 16-byte random salt and server secret.
  - The plaintext OTP is delivered via `EmailService` in the request thread only after transaction commit (`afterCommit`). Registration and resend responses wait for the SMTP attempt to finish so delivery stays within the CPU allocation window of Cloud Run request-based billing. The plaintext OTP is NEVER stored or logged.
  - An SMTP failure is logged without undoing the committed account/OTP or exposing account state in the generic resend response. The existing resend cooldown remains in effect for recovery.
  - Verification (`POST /api/auth/verify-registration-otp`):
    - Pessimistic locking order: Transactions MUST acquire lock on the `User` record first (`userRepository.findByEmailForUpdate`) before acquiring lock on `RegistrationOtp` (`otpRepository.findByUserIdForUpdate`) to guarantee deadlock-free execution under concurrent requests.
    - Single-use: Upon correct OTP match, `user.isVerified` is set to `true`, `user.verificationRequired` is set to `false`, and the `registration_otps` record is atomically deleted.
  - Rate Limiting & Cooldown:
    - Resend requests (`POST /api/auth/resend-registration-otp`) enforce a strict 60-second cooldown per account.
    - Each issued OTP permits a maximum of 5 failed attempts (`maxAttempts = 5`), after which `OTP_MAX_ATTEMPTS_EXCEEDED` is enforced until a new OTP is issued.
- **Anti-Enumeration Invariant**:
  - Verify requests for nonexistent, inactive, already verified, or legacy accounts perform constant-time dummy HMAC computation and throw generic `INVALID_OTP` (HTTP 400), indistinguishable from an invalid OTP attempt.
  - Resend requests for nonexistent, inactive, already verified, or legacy accounts perform constant-time dummy HMAC computation and return generic HTTP 200 silently without dispatching emails or revealing account state.
- **Legacy Account Compatibility Policy**:
  - Existing accounts created prior to registration OTP enforcement have `verificationRequired = false` (or `isVerified = true`).
  - Legacy users are NOT locked out and can log in normally via `/api/auth/login` without being redirected to OTP gating.

### 60.4. Durable Current-Token Revocation (`revoked_tokens`) & Session Invalidation Architecture
- **Distinction Between Logout and Password Reset**:
  - **Password Reset (`auth_version`)**: Invalidates ALL active tokens and sessions across all devices for the account simultaneously by incrementing `users.auth_version`.
  - **Logout (`revoked_tokens`)**: Invalidates ONLY the current active token/session used to issue the logout request, preserving other concurrent active sessions on other devices or tabs.
- **Cryptographic Token Fingerprinting**:
  - Durable revocation storage contains only token fingerprints, never raw compact JWT strings. Existing transient STOMP connection credentials are retained for signature and expiry revalidation.
  - When a token is presented for revocation or validation, the signature is cryptographically verified first using HMAC-SHA256 with the server key.
  - The `token_hash` stored in `revoked_tokens` is the SHA-256 hex digest of the verified decoded signature bytes (`MessageDigest.getInstance("SHA-256").digest(Decoders.BASE64URL.decode(signaturePart))`).
  - This prevents signature malleability / alias attacks (such as padding variation or trailing characters ignored by base64 parsers) from evading revocation.
- **Table Schema & Indices (`revoked_tokens`)**:
  - Columns:
    - `id BIGINT AUTO_INCREMENT PRIMARY KEY`
    - `token_hash VARCHAR(64) NOT NULL UNIQUE` (SHA-256 hex digest of verified decoded signature bytes)
    - `user_id BIGINT NULL` (Foreign key referencing `users(id)` with `ON DELETE CASCADE`)
    - `expires_at TIMESTAMP NOT NULL` (Expiration timestamp matching token expiration)
    - `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - Indices:
    - `uk_revoked_tokens_token_hash` UNIQUE on `token_hash`
    - `idx_revoked_tokens_expires_at` on `expires_at` (for cleanup query efficiency)
- **Lifecycle & Cleanup Owner**:
  - Owner: `RevokedTokenCleanupService` (`@Scheduled(cron = "${aloute.auth.revoked-token-cleanup-cron:0 0 * * * *}")`).
  - Automatically purges expired entries using `DELETE FROM RevokedToken r WHERE r.expiresAt <= :now`. Expired tokens can no longer authenticate anyway due to standard JWT expiration validation.
- **Concurrency & Pessimistic Lock Serialization**:
  - In `JwtSessionService.revokeToken(token, userId)`:
  - Row-level lock `userRepository.findByIdForUpdate(userId)` on `users` table is acquired FIRST inside `@Transactional` boundary before checking `existsByTokenHash` and inserting into `revoked_tokens`.
  - This database-backed serialization lock holds across the surrounding transaction commit, preventing duplicate key violations (HTTP 500) or race conditions across concurrent requests, threads, and application replicas.

---

# 61. KNOWN GAP — NOTIFICATION PREFERENCES

Current model chưa có user notification preference đầy đủ.

Nếu thêm cần xác định:

```text
notification type
channel
enabled/disabled
scope
```

---

# 62. PERMISSION SOURCE OF TRUTH

Permission business được xác định tại domain này.

Tối thiểu:

```text
Post edit/delete
→ owner, cộng với moderation override nếu feature/community rule cho phép

Community moderation
→ scoped community role

Community ownership
→ community.owner_id

Conversation read/send
→ conversation membership

Post visibility
→ visibility + relationship/context

Private resource
→ privacy + membership/relationship rule

Block
→ relationship input cho interaction permission
```

Cách code check bằng Spring Security/service/policy thuộc:

```text
SPRING_SECURITY_CONVENTION.md
```

---

# 63. USE CASE OVERVIEW

```text
Guest
├── Register
├── Login
├── View Public Post
├── View Public Profile
├── Browse Topic
└── Browse Public Community

User
├── Manage Profile
├── Follow / Unfollow
├── Block
├── Mute
├── Restrict
│
├── Create Post
├── Edit Own Post
├── Delete Own Post
├── Reply
├── Quote
├── Repost
├── Like
├── Bookmark
├── Mention
├── Add Media
├── Add Topic
├── Create Poll
├── Vote Poll
│
├── Join / Leave Community
├── Create Community Post
├── Use Flair
│
├── Create/Edit/Save Custom Feed
│
├── Start Conversation
├── Send Message
├── Reply Message
├── React Message
├── Send Message Media
│
├── View Notifications
└── Report User/Post
```

Community Moderator/Admin/Owner có thêm use case theo permission matrix tương ứng.

---

# 64. IMPLEMENTATION PHASES

## Phase 1 — Core

- Auth business flow.
- User/Profile.
- Follow.
- Post.
- Reply.
- Like.
- Repost.
- Bookmark.

## Phase 2 — Content

- Media.
- Topic.
- Poll.
- Quote.
- Mention.

## Phase 3 — Community

- Community.
- Member.
- Scoped role.
- Flair.
- Champion.

## Phase 4 — Feed

- Custom Feed.
- Topic/User feed.
- Saved feed.

## Phase 5 — Messaging

- Conversation.
- Message.
- Media.
- Reaction.

## Phase 6 — Safety

- Notification.
- Report.
- Block.
- Mute.
- Restrict.
- Hidden Post.

## Phase 7 — Analytics / Scale-oriented Domain

- View tracking.
- Link tracking.
- Feed improvement requirements.

Technical cache/queue/search-engine decisions không nằm trong domain file này.

---

# 64.1. DATABASE TRIGGERS, STORED PROCEDURES & INDEXES (BẮT BUỘC ĐỒ ÁN)

> **Người phụ trách**: Nguyễn Duy Ngọc.
> **Mục tiêu**: Đáp ứng tiêu chí bắt buộc của học phần môn học (Cơ sở dữ liệu nâng cao / Lập trình ứng dụng Web).

### 1. Database Triggers (Bảo toàn Invariant & Counter tự động)
- `trg_set_updated_at`: Tự động gán `updated_at = NOW()` trước mọi lệnh `UPDATE` trên các bảng `users`, `profiles`, `posts`, `communities`, `conversations`.
- `trg_post_reaction_count`: Tự động tăng `posts.like_count` (+1) khi insert vào `post_likes`, giảm (-1) khi delete.
- `trg_post_reply_count`: Tự động tăng `posts.reply_count` (+1) khi insert post có `parent_post_id IS NOT NULL`, giảm (-1) khi soft-delete/delete.
- `trg_user_follow_count`: Tự động tăng/giảm `follower_count` và `following_count` trên bảng `profiles` khi insert/delete trên bảng `follows`.
- `trg_community_member_count`: Tự động cập nhật `communities.member_count` khi insert/delete thành viên trên bảng `community_members`.
- `trg_community_prevent_owner_leave`: Chặn không cho xóa row `community_members` có `role = 'OWNER'` (chỉ được chuyển giao quyền trước).

### 2. Stored Procedures (Tối ưu nghiệp vụ phức tạp & Cascade)
- `sp_search_posts_and_topics(keyword VARCHAR, p_offset INT, p_limit INT)`: Tìm kiếm toàn cục đa bảng với phân trang, trả về danh sách post và chủ đề liên quan.
- `sp_soft_delete_post_cascade(p_post_id UUID, p_user_id UUID)`: Thực hiện soft delete (`deleted_at = NOW()`) cho bài viết gốc và cập nhật cascade trạng thái hiển thị của các replies và interaction liên quan.
- `sp_calculate_trending_topics(p_hours INT, p_limit INT)`: Tính toán các topic có số lượt đăng bài và tương tác cao nhất trong N giờ gần nhất.

### 3. Database Indexes (Tối ưu hiệu năng truy vấn)
- `idx_posts_user_created`: `(user_id, created_at DESC)` cho trang cá nhân.
- `idx_posts_feed`: `(visibility, created_at DESC)` cho Home Feed và Explore Feed.
- `idx_follows_lookup`: `(follower_id, followed_id)` và `(followed_id, follower_id)` kiểm tra quan hệ mạng xã hội.
- `idx_notifications_user_read`: `(user_id, is_read, created_at DESC)` cho thông báo.
- `idx_user_fcm_tokens_user_id`: `(user_id)` phục vụ truy vấn danh sách token gửi Web Push khi có sự kiện thông báo.
- `idx_messages_conversation`: `(conversation_id, created_at DESC)` cho tin nhắn.

---

# 65. DOMAIN REVIEW CHECKLIST

Trước khi implement một feature, domain phải trả lời được:

- Actor nào thực hiện?
- Resource nào bị tác động?
- Ownership là gì?
- Relationship nào ảnh hưởng permission?
- Visibility/privacy rule là gì?
- Invariant nào bắt buộc?
- Side effect nghiệp vụ nào xảy ra?
- Counter nào là derived/cached?
- Soft-delete behavior có liên quan không?
- Known gap nào chưa giải quyết?
- Permission business đã đủ rõ để Security implement chưa?

Nếu chưa trả lời được, không nên invent rule trong Controller hoặc frontend.

---

# 66. DOMAIN RULE CUỐI

File này trả lời:

```text
WHAT EXISTS?
WHAT DOES IT MEAN?
WHO CAN DO WHAT?
WHAT MUST REMAIN CONSISTENT?
```

File này không trả lời:

```text
HTTP response shape?
status code?
pagination fields?
JWT stored where?
Spring filter nào?
folder Java nào?
Bootstrap render thế nào?
```

Các câu đó thuộc tài liệu kỹ thuật canonical khác.

---

# 67. MODERATION & ADMIN SYSTEM DOMAIN MODEL (QUẢN TRỊ & KIỂM DUYỆT)

## 67.1. Bất biến bất khả xâm phạm (Inviolable Invariants)
1. **Chống tự sát & Đảo chính:**
   - Quản trị viên (`SYSTEM_ADMIN`) không được phép tự đình chỉ hoặc tự hạ quyền tài khoản của chính mình.
   - Không được phép khóa hoặc hạ cấp `SYSTEM_ADMIN` cuối cùng trong hệ thống (`USER_LAST_ADMIN_PROTECTED`).
2. **Nguồn gốc gỡ bài & Khôi phục (Post Provenance):**
   - Bài viết do tác giả tự xóa (`owner-deleted`) **tuyệt đối không** được phép khôi phục bởi Admin.
   - Admin chỉ có quyền khôi phục những bài viết đã bị gỡ bởi Admin (`admin-removed`), thông qua bản ghi `post_moderation_removals` hợp lệ (`POST_RESTORE`).
3. **Quy trình duyệt Báo cáo (Report Review Lifecycle):**
   - Quy trình chuyển trạng thái nghiêm ngặt: `PENDING → REVIEWED → (RESOLVED | REJECTED)`.
   - Một báo cáo tại một thời điểm chỉ có tối đa một Admin tiếp nhận xử lý (`reviewed_by`).
   - Sử dụng khóa lạc quan (`version`) để phát hiện và ngăn chặn xung đột khi nhiều Admin cùng xử lý một báo cáo.
4. **Quyền riêng tư & Bảo vệ dữ liệu (Data Minimization & Privacy):**
   - Vai trò `SYSTEM_ADMIN` **không cấp quyền** tự do duyệt và đọc trộm tin nhắn riêng tư (Direct Messages) của người dùng.
   - Mật khẩu (`password_hash`) và mã thông báo bảo mật không bao giờ được phép xuất hiện trong bất kỳ DTO hay nhật ký kiểm duyệt nào.
5. **Định danh Khóa chính (Canonical Primary Key Type):**
   - 100% các thực thể trong cơ sở dữ liệu AloUTE sử dụng kiểu số nguyên tự tăng `Long / int64` làm Primary Key.

## 67.2. Các thực thể Domain Kiểm duyệt mới (Proposed Entities)
1. `moderation_actions` (Nhật ký hành động kiểm duyệt - Append-only):
   - `id` (PK, Long)
   - `actor_user_id` (FK User thực hiện)
   - `target_type` (`USER`, `POST`, `REPORT`, `COMMUNITY`, `TOPIC`)
   - `target_id` (ID của đối tượng)
   - `action_type` (`POST_REMOVE`, `POST_RESTORE`, `REPORT_RESOLVE`, `USER_WARN`, `USER_SUSPEND`,...)
   - `reason_code` (`SPAM`, `HARASSMENT`, `HATE`, `MISINFORMATION`, `VIOLENCE`, `OTHER`)
   - `reason_detail` (Mô tả quyết định xử lý, tối thiểu 10 ký tự)
   - `related_report_id` (FK Report nullable)
   - `metadata` (JSON snapshot trạng thái tối thiểu)
   - `created_at` (Timestamp UTC)
   - *Bất biến:* Bảng chỉ ghi thêm (append-only), không cho phép chỉnh sửa (`UPDATE`) hoặc xóa (`DELETE`).
2. `post_moderation_removals` (Bản ghi gỡ bài viết của Admin):
   - `id` (PK, Long)
   - `post_id` (FK Post)
   - `remove_action_id` (FK `moderation_actions`)
   - `restore_action_id` (FK `moderation_actions` nullable)
   - `removed_at` (Timestamp)
   - `restored_at` (Timestamp nullable)
   - *Bất biến:* Một bài viết tại một thời điểm chỉ có tối đa 1 bản ghi gỡ bài còn hiệu lực (`restored_at IS NULL`).
3. `account_suspensions` (P2 - Đình chỉ tài khoản hệ thống):
   - `id` (PK, Long), `user_id` (FK User), `created_by` (FK Admin), `reason_code`, `reason_detail`, `starts_at`, `expires_at` (null nếu vô thời hạn), `revoked_at`, `revoked_by`, `related_report_id`, `created_at`.
4. `account_feature_restrictions` (P2 - Hạn chế tính năng):
   - `id` (PK, Long), `user_id` (FK User), `feature` (`POST_CREATE`, `POST_REPLY`), `starts_at`, `expires_at`, `created_by`, `reason_code`, `reason_detail`, `revoked_at`.

