-- BL-15 (issue #92): H2 twin of mysql8_backfill_post_counters.sql.
-- Recomputes the lifetime root-post counters from the posts table.
-- The recomputation IS the definition, so the script is idempotent.
-- Verified via ProfilePostCounterBackfillIntegrationTest (H2 engine).

UPDATE profiles p
SET p.posts_count = (
    SELECT COUNT(*) FROM posts
    WHERE posts.user_id = p.user_id
      AND posts.parent_post_id IS NULL
);

UPDATE communities c
SET c.post_count = (
    SELECT COUNT(*) FROM posts
    WHERE posts.community_id = c.id
      AND posts.parent_post_id IS NULL
);
