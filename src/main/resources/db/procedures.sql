create or replace function sp_visible_post_predicate(p_post_id bigint, p_viewer_id bigint)
returns boolean language sql stable as $$
    select exists (
        select 1
        from posts p
        where p.id = p_post_id
          and p.deleted_at is null
          and (
              p.visibility = 'PUBLIC'
              or p.user_id = p_viewer_id
              or (p.visibility = 'FOLLOWERS' and exists (
                    select 1 from follows f
                    where f.follower_id = p_viewer_id and f.following_id = p.user_id
              ))
          )
    );
$$;

create or replace function sp_get_visible_post(p_post_id bigint, p_viewer_id bigint)
returns setof posts language sql stable as $$
    select *
    from posts p
    where p.id = p_post_id
      and p.deleted_at is null
      and sp_visible_post_predicate(p.id, p_viewer_id);
$$;

create or replace function sp_list_public_posts(p_offset integer, p_limit integer)
returns setof posts language sql stable as $$
    select *
    from posts
    where deleted_at is null and parent_post_id is null and visibility = 'PUBLIC'
    order by created_at desc
    offset greatest(p_offset, 0)
    limit least(greatest(p_limit, 1), 100);
$$;

create or replace function sp_count_public_posts()
returns bigint language sql stable as $$
    select count(*) from posts
    where deleted_at is null and parent_post_id is null and visibility = 'PUBLIC';
$$;

create or replace function sp_list_post_replies(p_post_id bigint, p_offset integer, p_limit integer)
returns setof posts language sql stable as $$
    select *
    from posts
    where deleted_at is null and parent_post_id = p_post_id
    order by created_at asc
    offset greatest(p_offset, 0)
    limit least(greatest(p_limit, 1), 100);
$$;

create or replace function sp_count_post_replies(p_post_id bigint)
returns bigint language sql stable as $$
    select count(*) from posts where deleted_at is null and parent_post_id = p_post_id;
$$;

create or replace function sp_list_community_posts(p_community_id bigint, p_offset integer, p_limit integer)
returns setof posts language sql stable as $$
    select *
    from posts
    where deleted_at is null and community_id = p_community_id and visibility = 'PUBLIC'
    order by created_at desc
    offset greatest(p_offset, 0)
    limit least(greatest(p_limit, 1), 100);
$$;

create or replace function sp_count_community_posts(p_community_id bigint)
returns bigint language sql stable as $$
    select count(*) from posts where deleted_at is null and community_id = p_community_id and visibility = 'PUBLIC';
$$;

create or replace function sp_list_topic_posts(p_topic_id bigint, p_offset integer, p_limit integer)
returns setof posts language sql stable as $$
    select p.*
    from posts p
    join post_topics pt on pt.post_id = p.id
    where p.deleted_at is null and p.visibility = 'PUBLIC' and pt.topic_id = p_topic_id
    order by p.created_at desc
    offset greatest(p_offset, 0)
    limit least(greatest(p_limit, 1), 100);
$$;

create or replace function sp_count_topic_posts(p_topic_id bigint)
returns bigint language sql stable as $$
    select count(*)
    from posts p
    join post_topics pt on pt.post_id = p.id
    where p.deleted_at is null and p.visibility = 'PUBLIC' and pt.topic_id = p_topic_id;
$$;

create or replace function sp_create_post(
    p_user_id bigint,
    p_content text,
    p_visibility varchar,
    p_allow_reply boolean,
    p_allow_quote boolean,
    p_parent_post_id bigint,
    p_quoted_post_id bigint,
    p_community_id bigint,
    p_media_ids varchar,
    p_topic_ids varchar
) returns bigint language plpgsql as $$
declare
    v_post_id bigint;
begin
    if not exists (select 1 from users where id = p_user_id and is_active = true) then
        raise exception 'USER_NOT_FOUND';
    end if;
    if p_parent_post_id is not null and not exists (select 1 from posts where id = p_parent_post_id and deleted_at is null and allow_reply = true) then
        raise exception 'REPLY_NOT_ALLOWED';
    end if;
    if p_quoted_post_id is not null and not exists (select 1 from posts where id = p_quoted_post_id and deleted_at is null and allow_quote = true) then
        raise exception 'QUOTE_NOT_ALLOWED';
    end if;
    if p_community_id is not null and not exists (select 1 from community_members where community_id = p_community_id and user_id = p_user_id) then
        raise exception 'COMMUNITY_MEMBERSHIP_REQUIRED';
    end if;

    insert into posts (user_id, content, visibility, allow_reply, allow_quote, parent_post_id, quoted_post_id, community_id)
    values (p_user_id, p_content, p_visibility, coalesce(p_allow_reply, true), coalesce(p_allow_quote, true),
            p_parent_post_id, p_quoted_post_id, p_community_id)
    returning id into v_post_id;

    if p_media_ids is not null and length(trim(p_media_ids)) > 0 then
        insert into post_media (post_id, media_id, position)
        select v_post_id, value::bigint, ordinality::integer - 1
        from unnest(string_to_array(p_media_ids, ',')) with ordinality as media(value, ordinality);

        update media_assets
        set attached_at = now()
        where id in (select value::bigint from unnest(string_to_array(p_media_ids, ',')) as ids(value));
    end if;

    if p_topic_ids is not null and length(trim(p_topic_ids)) > 0 then
        insert into post_topics (post_id, topic_id)
        select v_post_id, value::bigint
        from unnest(string_to_array(p_topic_ids, ',')) as ids(value)
        on conflict do nothing;
    end if;

    return v_post_id;
end;
$$;

create or replace function sp_update_post(
    p_post_id bigint,
    p_user_id bigint,
    p_content text,
    p_visibility varchar,
    p_allow_reply boolean,
    p_allow_quote boolean
) returns bigint language plpgsql as $$
begin
    update posts
    set content = coalesce(p_content, content),
        visibility = coalesce(p_visibility, visibility),
        allow_reply = coalesce(p_allow_reply, allow_reply),
        allow_quote = coalesce(p_allow_quote, allow_quote)
    where id = p_post_id and user_id = p_user_id and deleted_at is null;
    if not found then
        raise exception 'POST_NOT_FOUND_OR_FORBIDDEN';
    end if;
    return p_post_id;
end;
$$;

create or replace function sp_soft_delete_post_cascade(p_post_id bigint, p_user_id bigint)
returns bigint language plpgsql as $$
begin
    update posts set deleted_at = now()
    where (id = p_post_id or parent_post_id = p_post_id)
      and deleted_at is null
      and (user_id = p_user_id or exists (select 1 from posts root where root.id = p_post_id and root.user_id = p_user_id));
    if not found then
        raise exception 'POST_NOT_FOUND_OR_FORBIDDEN';
    end if;
    return p_post_id;
end;
$$;

create or replace function sp_like_post(p_post_id bigint, p_user_id bigint)
returns bigint language plpgsql as $$
begin
    if not exists (select 1 from posts where id = p_post_id and deleted_at is null) then
        raise exception 'POST_NOT_FOUND';
    end if;
    insert into post_likes (post_id, user_id) values (p_post_id, p_user_id)
    on conflict do nothing;
    return p_post_id;
end;
$$;

create or replace function sp_unlike_post(p_post_id bigint, p_user_id bigint)
returns bigint language plpgsql as $$
begin
    delete from post_likes where post_id = p_post_id and user_id = p_user_id;
    return p_post_id;
end;
$$;

create or replace function sp_bookmark_post(p_post_id bigint, p_user_id bigint)
returns bigint language plpgsql as $$
begin
    if not exists (select 1 from posts where id = p_post_id and deleted_at is null) then
        raise exception 'POST_NOT_FOUND';
    end if;
    insert into bookmarks (post_id, user_id) values (p_post_id, p_user_id)
    on conflict do nothing;
    return p_post_id;
end;
$$;

create or replace function sp_remove_bookmark(p_post_id bigint, p_user_id bigint)
returns bigint language plpgsql as $$
begin
    delete from bookmarks where post_id = p_post_id and user_id = p_user_id;
    return p_post_id;
end;
$$;

create or replace function sp_repost_post(p_post_id bigint, p_user_id bigint)
returns bigint language plpgsql as $$
begin
    if not exists (select 1 from posts where id = p_post_id and deleted_at is null) then
        raise exception 'POST_NOT_FOUND';
    end if;
    insert into reposts (post_id, user_id) values (p_post_id, p_user_id)
    on conflict do nothing;
    update posts set repost_count = (select count(*) from reposts where post_id = p_post_id) where id = p_post_id;
    return p_post_id;
end;
$$;

create or replace function sp_remove_repost(p_post_id bigint, p_user_id bigint)
returns bigint language plpgsql as $$
begin
    delete from reposts where post_id = p_post_id and user_id = p_user_id;
    update posts set repost_count = (select count(*) from reposts where post_id = p_post_id) where id = p_post_id;
    return p_post_id;
end;
$$;

create or replace function sp_has_liked_post(p_post_id bigint, p_user_id bigint)
returns boolean language sql stable as $$
    select p_user_id is not null and exists (select 1 from post_likes where post_id = p_post_id and user_id = p_user_id);
$$;

create or replace function sp_has_bookmarked_post(p_post_id bigint, p_user_id bigint)
returns boolean language sql stable as $$
    select p_user_id is not null and exists (select 1 from bookmarks where post_id = p_post_id and user_id = p_user_id);
$$;

create or replace function sp_has_reposted_post(p_post_id bigint, p_user_id bigint)
returns boolean language sql stable as $$
    select p_user_id is not null and exists (select 1 from reposts where post_id = p_post_id and user_id = p_user_id);
$$;

create or replace function sp_list_topics(p_keyword varchar, p_offset integer, p_limit integer)
returns setof topics language sql stable as $$
    select *
    from topics
    where p_keyword is null or lower(name) like '%' || lower(p_keyword) || '%' or lower(slug) like '%' || lower(p_keyword) || '%'
    order by name asc
    offset greatest(p_offset, 0)
    limit least(greatest(p_limit, 1), 100);
$$;

create or replace function sp_count_topics(p_keyword varchar)
returns bigint language sql stable as $$
    select count(*)
    from topics
    where p_keyword is null or lower(name) like '%' || lower(p_keyword) || '%' or lower(slug) like '%' || lower(p_keyword) || '%';
$$;

create or replace function sp_get_topic(p_topic_id bigint)
returns setof topics language sql stable as $$
    select * from topics where id = p_topic_id;
$$;

create or replace function sp_follow_topic(p_user_id bigint, p_topic_id bigint)
returns bigint language plpgsql as $$
begin
    insert into user_topics (user_id, topic_id) values (p_user_id, p_topic_id)
    on conflict do nothing;
    return p_topic_id;
end;
$$;

create or replace function sp_unfollow_topic(p_user_id bigint, p_topic_id bigint)
returns bigint language plpgsql as $$
begin
    delete from user_topics where user_id = p_user_id and topic_id = p_topic_id;
    return p_topic_id;
end;
$$;

create or replace function sp_list_communities(p_keyword varchar, p_offset integer, p_limit integer)
returns setof communities language sql stable as $$
    select *
    from communities
    where deleted_at is null
      and (p_keyword is null or lower(name) like '%' || lower(p_keyword) || '%' or lower(slug) like '%' || lower(p_keyword) || '%')
    order by member_count desc, created_at desc
    offset greatest(p_offset, 0)
    limit least(greatest(p_limit, 1), 100);
$$;

create or replace function sp_count_communities(p_keyword varchar)
returns bigint language sql stable as $$
    select count(*)
    from communities
    where deleted_at is null
      and (p_keyword is null or lower(name) like '%' || lower(p_keyword) || '%' or lower(slug) like '%' || lower(p_keyword) || '%');
$$;

create or replace function sp_get_community(p_community_id bigint)
returns setof communities language sql stable as $$
    select * from communities where id = p_community_id and deleted_at is null;
$$;

create or replace function sp_create_community(
    p_owner_id bigint,
    p_name varchar,
    p_slug varchar,
    p_description varchar,
    p_is_private boolean,
    p_avatar_media_id bigint,
    p_cover_media_id bigint
) returns bigint language plpgsql as $$
declare
    v_community_id bigint;
    v_avatar_url varchar;
    v_cover_url varchar;
begin
    select url into v_avatar_url from media_assets where id = p_avatar_media_id and owner_id = p_owner_id and deleted_at is null;
    select url into v_cover_url from media_assets where id = p_cover_media_id and owner_id = p_owner_id and deleted_at is null;

    insert into communities (owner_id, name, slug, description, is_private, avatar_url, cover_url)
    values (p_owner_id, p_name, p_slug, p_description, p_is_private, v_avatar_url, v_cover_url)
    returning id into v_community_id;

    insert into community_members (community_id, user_id, role)
    values (v_community_id, p_owner_id, 'ADMIN');

    return v_community_id;
end;
$$;

create or replace function sp_update_community(
    p_community_id bigint,
    p_user_id bigint,
    p_name varchar,
    p_description varchar,
    p_is_private boolean,
    p_avatar_media_id bigint,
    p_cover_media_id bigint
) returns bigint language plpgsql as $$
declare
    v_avatar_url varchar;
    v_cover_url varchar;
begin
    if not exists (select 1 from communities where id = p_community_id and owner_id = p_user_id and deleted_at is null) then
        raise exception 'COMMUNITY_NOT_FOUND_OR_FORBIDDEN';
    end if;
    select url into v_avatar_url from media_assets where id = p_avatar_media_id and owner_id = p_user_id and deleted_at is null;
    select url into v_cover_url from media_assets where id = p_cover_media_id and owner_id = p_user_id and deleted_at is null;

    update communities
    set name = coalesce(p_name, name),
        description = coalesce(p_description, description),
        is_private = coalesce(p_is_private, is_private),
        avatar_url = coalesce(v_avatar_url, avatar_url),
        cover_url = coalesce(v_cover_url, cover_url)
    where id = p_community_id;
    return p_community_id;
end;
$$;

create or replace function sp_delete_community(p_community_id bigint, p_user_id bigint)
returns bigint language plpgsql as $$
begin
    update communities set deleted_at = now()
    where id = p_community_id and owner_id = p_user_id and deleted_at is null;
    if not found then
        raise exception 'COMMUNITY_NOT_FOUND_OR_FORBIDDEN';
    end if;
    update posts set deleted_at = now() where community_id = p_community_id and deleted_at is null;
    return p_community_id;
end;
$$;

create or replace function sp_get_community_membership(p_community_id bigint, p_user_id bigint)
returns setof community_members language sql stable as $$
    select * from community_members where community_id = p_community_id and user_id = p_user_id;
$$;

create or replace function sp_list_community_members(p_community_id bigint, p_offset integer, p_limit integer)
returns setof community_members language sql stable as $$
    select *
    from community_members
    where community_id = p_community_id
    order by joined_at asc
    offset greatest(p_offset, 0)
    limit least(greatest(p_limit, 1), 100);
$$;

create or replace function sp_count_community_members(p_community_id bigint)
returns bigint language sql stable as $$
    select count(*) from community_members where community_id = p_community_id;
$$;

create or replace function sp_join_community(p_community_id bigint, p_user_id bigint)
returns bigint language plpgsql as $$
declare
    v_member_id bigint;
begin
    if exists (select 1 from communities where id = p_community_id and is_private = true and deleted_at is null) then
        raise exception 'PRIVATE_COMMUNITY_JOIN_REQUEST_NOT_IMPLEMENTED';
    end if;
    insert into community_members (community_id, user_id, role)
    values (p_community_id, p_user_id, 'MEMBER')
    on conflict (community_id, user_id) do update set role = community_members.role
    returning id into v_member_id;
    return v_member_id;
end;
$$;

create or replace function sp_leave_community(p_community_id bigint, p_user_id bigint)
returns bigint language plpgsql as $$
begin
    delete from community_members where community_id = p_community_id and user_id = p_user_id;
    return p_community_id;
end;
$$;

create or replace function sp_update_community_role(p_community_id bigint, p_actor_id bigint, p_target_user_id bigint, p_role varchar)
returns bigint language plpgsql as $$
declare
    v_member_id bigint;
begin
    if not exists (select 1 from communities where id = p_community_id and owner_id = p_actor_id and deleted_at is null) then
        raise exception 'COMMUNITY_OWNER_REQUIRED';
    end if;
    update community_members set role = p_role
    where community_id = p_community_id and user_id = p_target_user_id
    returning id into v_member_id;
    if v_member_id is null then
        raise exception 'COMMUNITY_MEMBER_NOT_FOUND';
    end if;
    return v_member_id;
end;
$$;

create or replace function sp_search_posts_and_topics(keyword varchar, p_offset integer, p_limit integer)
returns table(result_type varchar, result_id bigint, title text, created_at timestamptz)
language sql stable as $$
    (
        select 'POST'::varchar, p.id, p.content::text, p.created_at
        from posts p
        where p.deleted_at is null and p.visibility = 'PUBLIC' and lower(p.content) like '%' || lower(keyword) || '%'
    )
    union all
    (
        select 'TOPIC'::varchar, t.id, t.name::text, t.created_at
        from topics t
        where lower(t.name) like '%' || lower(keyword) || '%' or lower(t.slug) like '%' || lower(keyword) || '%'
    )
    order by created_at desc
    offset greatest(p_offset, 0)
    limit least(greatest(p_limit, 1), 100);
$$;

create or replace function sp_calculate_trending_topics(p_hours integer, p_limit integer)
returns table(topic_id bigint, name varchar, slug varchar, post_count bigint)
language sql stable as $$
    select t.id, t.name, t.slug, count(pt.post_id) as post_count
    from topics t
    join post_topics pt on pt.topic_id = t.id
    join posts p on p.id = pt.post_id
    where p.deleted_at is null and p.created_at >= now() - make_interval(hours => greatest(p_hours, 1))
    group by t.id, t.name, t.slug
    order by count(pt.post_id) desc, t.name asc
    limit least(greatest(p_limit, 1), 100);
$$;
