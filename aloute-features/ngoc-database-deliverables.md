# Bàn giao nhiệm vụ của Ngọc

Tài liệu này gom các phần Ngọc phụ trách trong kế hoạch 3 tuần cho đề tài **Xây dựng trang mạng xã hội ALOUTE**. Phạm vi hiện tại là thiết kế `.md`, chưa triển khai code.

## Phạm vi tuần 1

| Nhiệm vụ | Kết quả |
| --- | --- |
| Rà soát CSDL/ERD | Hoàn thành danh sách bảng, PK/FK, quan hệ 1-N, N-N, self-reference và soft delete trong `database.md` |
| Chọn Hệ QTCSDL | Chọn PostgreSQL; Firebase Storage chỉ lưu file media |
| Chuẩn bị schema | Xác định bảng cốt lõi, constraint, index và rule dữ liệu |
| Trigger/stored procedure | Lập danh sách trigger và function cần xây dựng |
| Entity JPA | Lập bảng mapping entity dự kiến để nhóm backend hiện thực hóa |
| Dữ liệu mẫu | Xác định seed data tối thiểu cho demo và test |

## Phạm vi tuần 2

| Module | Thiết kế dữ liệu cần hỗ trợ |
| --- | --- |
| Post | `posts`, `post_media`, `hashtags`, `post_hashtags`, `post_topics`, `post_revisions` |
| Reaction | `post_reactions`, `comments`, `comment_reactions`, counter trên `posts` |
| Topic | `topics`, mapping với post/group để phục vụ feed và community |
| Community | `groups`, `group_members`, `group_join_requests`, `group_topics` |
| Firebase | PostgreSQL lưu metadata media, Firebase lưu binary |
| DB processing | Trigger counter, revision, audit; procedure tạo post, upsert reaction, duyệt join request |

## Ma trận Use Case - API - DB phần Ngọc

| Use case | API dự kiến | Bảng chính | Trigger/procedure liên quan |
| --- | --- | --- | --- |
| Tạo bài viết | `POST /posts` | `posts`, `post_media`, `hashtags`, `post_hashtags`, `post_topics` | `create_post_with_media`, `trg_set_updated_at` |
| Sửa bài viết | `PATCH /posts/{id}` | `posts`, `post_revisions` | `trg_post_revision` |
| Xóa bài viết | `DELETE /posts/{id}` | `posts`, `post_media`, `post_reactions`, `comments` | `soft_delete_post` |
| Like/đổi reaction | `PUT /posts/{id}/reaction` | `post_reactions`, `posts` | `upsert_post_reaction`, `trg_post_reaction_count` |
| Bỏ reaction | `DELETE /posts/{id}/reaction` | `post_reactions`, `posts` | `trg_post_reaction_count` |
| Comment/reply | `POST /posts/{id}/comments`, `POST /comments/{id}/replies` | `comments`, `posts`, `notifications` | `trg_post_comment_count` |
| Tạo community | `POST /groups` | `groups`, `group_members`, `group_topics` | `trg_group_keep_owner` |
| Tham gia community | `POST /groups/{id}/join` | `group_join_requests`, `group_members`, `notifications` | `accept_group_join_request` |
| Tìm theo topic | `GET /topics/{slug}/posts` | `topics`, `post_topics`, `posts` | Index `post_topics`, `posts(created_at)` |

## Thiết kế schema dạng rút gọn

```text
users 1--1 user_profiles
users 1--N posts
users 1--N post_reactions
users 1--N comments
users N--N groups qua group_members
users N--N users qua follows, friend_requests, friendships, blocks

posts 1--N post_media
posts N--N hashtags qua post_hashtags
posts N--N topics qua post_topics
posts 1--N post_reactions
posts 1--N comments
comments 1--N comments qua parent_id

groups 1--N posts
groups N--N topics qua group_topics
groups 1--N group_join_requests
```

## SQL DDL dự kiến ở mức thiết kế

```sql
create table users (
    id uuid primary key,
    email varchar(320) not null,
    username varchar(30) not null,
    password_hash varchar(255) not null,
    status varchar(32) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    deleted_at timestamp,
    deleted_by uuid,
    version bigint not null
);

create unique index uk_users_email_lower on users (lower(email));
create unique index uk_users_username_lower on users (lower(username));

create table posts (
    id uuid primary key,
    author_id uuid not null references users(id),
    group_id uuid references groups(id),
    content text,
    visibility varchar(16) not null,
    status varchar(24) not null,
    reaction_count int not null default 0,
    comment_count int not null default 0,
    share_count int not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null,
    deleted_at timestamp,
    deleted_by uuid references users(id),
    version bigint not null
);

create index ix_posts_author_created_at on posts (author_id, created_at desc);
create index ix_posts_visibility_created_at on posts (visibility, created_at desc);

create table post_reactions (
    id uuid primary key,
    post_id uuid not null references posts(id),
    user_id uuid not null references users(id),
    reaction_type varchar(16) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    deleted_at timestamp,
    deleted_by uuid references users(id),
    version bigint not null,
    unique (user_id, post_id)
);
```

DDL trên là bản rút gọn để đưa vào báo cáo. Khi code thật, nhóm sẽ chuyển thành Flyway migration và bổ sung toàn bộ check constraint, partial index, trigger/function.

## Trigger/function cần viết khi bước sang code

```sql
-- Tự cập nhật updated_at
create trigger trg_set_updated_at
before update on <mutable_table>
for each row execute function set_updated_at();

-- Giữ counter reaction nhất quán
create trigger trg_post_reaction_count
after insert or update of deleted_at or delete on post_reactions
for each row execute function adjust_post_reaction_count();

-- Giữ counter comment nhất quán
create trigger trg_post_comment_count
after insert or update of deleted_at or delete on comments
for each row execute function adjust_post_comment_count();

-- Không cho group mất owner cuối cùng
create trigger trg_group_keep_owner
before update of role, status, deleted_at or delete on group_members
for each row execute function prevent_group_without_owner();
```

## Gợi ý Entity JPA cho Ngọc chuẩn bị

| Entity | Thuộc tính chính | Quan hệ |
| --- | --- | --- |
| `Post` | `id`, `content`, `visibility`, `status`, `reactionCount`, `commentCount`, `shareCount` | `author`, `group`, `media`, `hashtags`, `topics` |
| `PostMedia` | `id`, `mediaType`, `storageUrl`, `mimeType`, `fileSize`, `sortOrder` | `post` |
| `PostReaction` | `id`, `reactionType` | `post`, `user` |
| `Comment` | `id`, `content`, `deletedAt` | `post`, `author`, `parent` |
| `Topic` | `id`, `name`, `slug` | `posts`, `groups` |
| `CommunityGroup` | `id`, `name`, `slug`, `privacy`, `requiresApproval` | `owner`, `members`, `topics` |
| `GroupMember` | `id`, `role`, `status`, `joinedAt` | `group`, `user` |

## Test case dữ liệu nên chuẩn bị

| Mã | Kịch bản | Kỳ vọng |
| --- | --- | --- |
| DB-01 | Tạo hai user trùng email khác hoa/thường | Bị chặn bởi unique lower(email) |
| DB-02 | Một user reaction hai lần cùng post | Lần sau update reaction cũ, không tạo dòng mới |
| DB-03 | Xóa mềm reaction | `reaction_count` giảm đúng |
| DB-04 | Comment vào post đã soft delete | Service từ chối, DB không phát sinh comment |
| DB-05 | Tạo post không content và không media | Bị từ chối |
| DB-06 | Group chỉ còn một owner, owner bị xóa/đổi role | Bị trigger chặn |
| DB-07 | User bị block tìm profile/post của blocker | Service lọc không trả dữ liệu |
| DB-08 | Tìm post theo hashtag/topic | Có index hỗ trợ, kết quả loại bài private không đủ quyền |

## Ghi chú bàn giao cho nhóm

- Khôi có thể dùng phần `users`, `user_profiles`, `follows`, `blocks` để nối Auth/User/Profile/Follow.
- Bằng có thể dùng bảng `notifications`, trigger/function liên quan comment/reaction và ma trận use case để vẽ sequence Like/Reply.
- Quân có thể lấy dữ liệu mẫu user/post/group để dựng UI Feed, Profile, Community.
- Khi chuyển sang code, ưu tiên tạo migration nền trước rồi mới tạo Entity JPA để `ddl-auto=validate`.
