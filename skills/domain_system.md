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

Functional feed hiện có thể gồm:

```text
Home Feed
Following Feed
Custom Feed
Community Feed
Topic Feed
```

## Home Feed

Nguồn có thể gồm:

```text
followed users
interested topics
recommended posts
```

Ranking algorithm chưa được domain document hiện tại chốt.

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
ModerationLog
```

Không invent persistence implementation trong domain doc; chỉ ghi nhận capability/data gap.

---

# 60. KNOWN GAP — AUTH BUSINESS CAPABILITIES

Domain hiện chưa mô tả đầy đủ lifecycle:

- Refresh session/token lifecycle.
- Password reset.
- Email verification token flow.
- Session/device management.

JWT/token implementation thuộc Security Convention.

Domain chỉ cần chốt product behavior khi các feature này được đưa vào scope.

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
