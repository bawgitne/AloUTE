-- Canonical PostgreSQL schema for Ngoc-owned Content, Reaction, Topic, Community and Media modules.
-- The application uses Long/BIGINT IDs, matching the existing User/Profile backend.

alter table profiles add column if not exists updated_at timestamptz not null default now();

create table if not exists topics (
    id bigserial primary key,
    name varchar(100) not null unique,
    slug varchar(120) not null unique,
    created_at timestamptz not null default now()
);

create table if not exists media_assets (
    id bigserial primary key,
    owner_id bigint not null references users(id),
    type varchar(16) not null check (type in ('IMAGE', 'VIDEO', 'GIF')),
    purpose varchar(32) not null check (purpose in ('POST', 'MESSAGE', 'USER_AVATAR', 'COMMUNITY_AVATAR', 'COMMUNITY_COVER')),
    url varchar(2048) not null,
    storage_path varchar(512) not null,
    mime_type varchar(120) not null,
    file_size bigint not null check (file_size >= 0),
    width integer,
    height integer,
    duration_seconds double precision,
    attached_at timestamptz,
    deleted_at timestamptz,
    created_at timestamptz not null default now()
);

create table if not exists communities (
    id bigserial primary key,
    owner_id bigint not null references users(id),
    name varchar(100) not null,
    slug varchar(100) not null unique,
    description varchar(2000),
    avatar_url varchar(2048),
    cover_url varchar(2048),
    is_private boolean not null default false,
    member_count bigint not null default 0,
    post_count bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create table if not exists community_members (
    id bigserial primary key,
    community_id bigint not null references communities(id) on delete cascade,
    user_id bigint not null references users(id),
    role varchar(32) not null check (role in ('MEMBER', 'MODERATOR', 'ADMIN')),
    joined_at timestamptz not null default now(),
    constraint uk_community_members_user unique (community_id, user_id)
);

create table if not exists posts (
    id bigserial primary key,
    user_id bigint not null references users(id),
    content varchar(4000) not null,
    parent_post_id bigint references posts(id),
    quoted_post_id bigint references posts(id),
    community_id bigint references communities(id),
    visibility varchar(32) not null check (visibility in ('PUBLIC', 'FOLLOWERS', 'MENTIONED')),
    allow_reply boolean not null default true,
    allow_quote boolean not null default true,
    like_count bigint not null default 0,
    reply_count bigint not null default 0,
    repost_count bigint not null default 0,
    quote_count bigint not null default 0,
    view_count bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create table if not exists post_media (
    id bigserial primary key,
    post_id bigint not null references posts(id) on delete cascade,
    media_id bigint not null unique references media_assets(id),
    position integer not null default 0
);

create table if not exists post_topics (
    id bigserial primary key,
    post_id bigint not null references posts(id) on delete cascade,
    topic_id bigint not null references topics(id),
    constraint uk_post_topics_post_topic unique (post_id, topic_id)
);

create table if not exists user_topics (
    id bigserial primary key,
    user_id bigint not null references users(id) on delete cascade,
    topic_id bigint not null references topics(id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint uk_user_topics_user_topic unique (user_id, topic_id)
);

create table if not exists post_likes (
    id bigserial primary key,
    user_id bigint not null references users(id) on delete cascade,
    post_id bigint not null references posts(id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint uk_post_likes_user_post unique (user_id, post_id)
);

create table if not exists bookmarks (
    id bigserial primary key,
    user_id bigint not null references users(id) on delete cascade,
    post_id bigint not null references posts(id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint uk_bookmarks_user_post unique (user_id, post_id)
);

create table if not exists reposts (
    id bigserial primary key,
    user_id bigint not null references users(id) on delete cascade,
    post_id bigint not null references posts(id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint uk_reposts_user_post unique (user_id, post_id)
);

create index if not exists idx_posts_user_created on posts (user_id, created_at desc);
create index if not exists idx_posts_feed on posts (visibility, created_at desc);
create index if not exists idx_posts_parent_created on posts (parent_post_id, created_at);
create index if not exists idx_posts_community_created on posts (community_id, created_at desc);
create index if not exists idx_post_topics_topic on post_topics (topic_id, post_id);
create index if not exists idx_media_owner_created on media_assets (owner_id, created_at desc);
create index if not exists idx_community_members_user on community_members (user_id, community_id);
do $$
begin
    if exists (select 1 from information_schema.tables where table_name = 'follows') then
        create index if not exists idx_follows_lookup on follows (follower_id, following_id);
        create index if not exists idx_follows_reverse_lookup on follows (following_id, follower_id);
    end if;
    if exists (select 1 from information_schema.tables where table_name = 'notifications') then
        create index if not exists idx_notifications_user_read on notifications (user_id, is_read, created_at desc);
    end if;
    if exists (select 1 from information_schema.tables where table_name = 'messages') then
        create index if not exists idx_messages_conversation on messages (conversation_id, created_at desc);
    end if;
end;
$$;
