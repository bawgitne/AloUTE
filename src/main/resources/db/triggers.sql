create or replace function fn_touch_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists trg_set_updated_at_users on users;
create trigger trg_set_updated_at_users before update on users
for each row execute function fn_touch_updated_at();

drop trigger if exists trg_set_updated_at_profiles on profiles;
create trigger trg_set_updated_at_profiles before update on profiles
for each row execute function fn_touch_updated_at();

drop trigger if exists trg_set_updated_at_posts on posts;
create trigger trg_set_updated_at_posts before update on posts
for each row execute function fn_touch_updated_at();

drop trigger if exists trg_set_updated_at_communities on communities;
create trigger trg_set_updated_at_communities before update on communities
for each row execute function fn_touch_updated_at();

do $$
begin
    if exists (select 1 from information_schema.tables where table_name = 'conversations') then
        drop trigger if exists trg_set_updated_at_conversations on conversations;
        create trigger trg_set_updated_at_conversations before update on conversations
        for each row execute function fn_touch_updated_at();
    end if;
end;
$$;

create or replace function fn_post_reaction_count()
returns trigger language plpgsql as $$
begin
    if tg_op = 'INSERT' then
        update posts set like_count = like_count + 1 where id = new.post_id;
        return new;
    end if;
    update posts set like_count = greatest(0, like_count - 1) where id = old.post_id;
    return old;
end;
$$;

drop trigger if exists trg_post_reaction_count_insert on post_likes;
create trigger trg_post_reaction_count_insert after insert on post_likes
for each row execute function fn_post_reaction_count();

drop trigger if exists trg_post_reaction_count_delete on post_likes;
create trigger trg_post_reaction_count_delete after delete on post_likes
for each row execute function fn_post_reaction_count();

create or replace function fn_post_reply_quote_count()
returns trigger language plpgsql as $$
begin
    if tg_op = 'INSERT' then
        if new.parent_post_id is not null then
            update posts set reply_count = reply_count + 1 where id = new.parent_post_id;
        end if;
        if new.quoted_post_id is not null then
            update posts set quote_count = quote_count + 1 where id = new.quoted_post_id;
        end if;
        if new.community_id is not null then
            update communities set post_count = post_count + 1 where id = new.community_id;
        end if;
        update profiles set posts_count = posts_count + 1 where user_id = new.user_id;
        return new;
    end if;
    if old.deleted_at is null and new.deleted_at is not null then
        if new.parent_post_id is not null then
            update posts set reply_count = greatest(0, reply_count - 1) where id = new.parent_post_id;
        end if;
        if new.quoted_post_id is not null then
            update posts set quote_count = greatest(0, quote_count - 1) where id = new.quoted_post_id;
        end if;
        if new.community_id is not null then
            update communities set post_count = greatest(0, post_count - 1) where id = new.community_id;
        end if;
        update profiles set posts_count = greatest(0, posts_count - 1) where user_id = new.user_id;
    end if;
    return new;
end;
$$;

drop trigger if exists trg_post_reply_count_insert on posts;
create trigger trg_post_reply_count_insert after insert on posts
for each row execute function fn_post_reply_quote_count();

drop trigger if exists trg_post_reply_count_soft_delete on posts;
create trigger trg_post_reply_count_soft_delete after update of deleted_at on posts
for each row execute function fn_post_reply_quote_count();

create or replace function fn_user_follow_count()
returns trigger language plpgsql as $$
begin
    if tg_op = 'INSERT' then
        update profiles set following_count = following_count + 1 where user_id = new.follower_id;
        update profiles set followers_count = followers_count + 1 where user_id = new.following_id;
        return new;
    end if;
    update profiles set following_count = greatest(0, following_count - 1) where user_id = old.follower_id;
    update profiles set followers_count = greatest(0, followers_count - 1) where user_id = old.following_id;
    return old;
end;
$$;

drop trigger if exists trg_user_follow_count_insert on follows;
create trigger trg_user_follow_count_insert after insert on follows
for each row execute function fn_user_follow_count();

drop trigger if exists trg_user_follow_count_delete on follows;
create trigger trg_user_follow_count_delete after delete on follows
for each row execute function fn_user_follow_count();

create or replace function fn_community_member_count()
returns trigger language plpgsql as $$
begin
    if tg_op = 'INSERT' then
        update communities set member_count = member_count + 1 where id = new.community_id;
        return new;
    end if;
    update communities set member_count = greatest(0, member_count - 1) where id = old.community_id;
    return old;
end;
$$;

drop trigger if exists trg_community_member_count_insert on community_members;
create trigger trg_community_member_count_insert after insert on community_members
for each row execute function fn_community_member_count();

drop trigger if exists trg_community_member_count_delete on community_members;
create trigger trg_community_member_count_delete after delete on community_members
for each row execute function fn_community_member_count();

create or replace function fn_community_prevent_owner_leave()
returns trigger language plpgsql as $$
begin
    if exists (select 1 from communities where id = old.community_id and owner_id = old.user_id) then
        raise exception 'COMMUNITY_OWNER_CANNOT_LEAVE';
    end if;
    return old;
end;
$$;

drop trigger if exists trg_community_prevent_owner_leave on community_members;
create trigger trg_community_prevent_owner_leave before delete on community_members
for each row execute function fn_community_prevent_owner_leave();
