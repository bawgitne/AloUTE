-- BL-15 (issue #92): one-off backfill of the lifetime post counters.
-- Engine: MySQL 8.0+. MANUAL OPS SCRIPT — run by a DBA, never by automation.
--
-- Semantics (issue #92 decision): both counters are LIFETIME root-post counters.
--   profiles.posts_count  = root posts ever created by the user
--                           (quotes and community posts included, replies excluded,
--                           soft-deleted rows still counted).
--   communities.post_count = root posts ever created in the community (same convention).
-- The recomputation below IS the definition, so the script is idempotent: running it
-- again always converges to the same values.
--
-- Runbook (disposable staging first, production last, inside a maintenance window):
--   1. BACKUP (per table, adjust the suffix date):
--        CREATE TABLE profiles_post_count_bak_20261029 AS
--          SELECT user_id, posts_count FROM profiles;
--        CREATE TABLE communities_post_count_bak_20261029 AS
--          SELECT id, post_count FROM communities;
--   2. PRE-CHECK (record the drift before fixing):
--        SELECT p.user_id, p.posts_count AS stored,
--               (SELECT COUNT(*) FROM posts WHERE posts.user_id = p.user_id
--                  AND parent_post_id IS NULL) AS lifetime_root
--        FROM profiles p
--        WHERE p.posts_count <> (SELECT COUNT(*) FROM posts
--                                  WHERE posts.user_id = p.user_id AND parent_post_id IS NULL);
--   3. APPLY this script (single transaction):
--        START TRANSACTION;
--        SOURCE mysql8_backfill_post_counters.sql   -- or paste the two UPDATEs below
--        -- verify row counts look sane, then:
--        COMMIT;   -- ROLLBACK instead if anything looks wrong
--   4. POST-CHECK (must return zero rows):
--        SELECT p.user_id FROM profiles p
--        WHERE p.posts_count <> (SELECT COUNT(*) FROM posts
--                                  WHERE posts.user_id = p.user_id AND parent_post_id IS NULL);
--   ROLLBACK plan: restore the stored values from the backup tables:
--        UPDATE profiles p JOIN profiles_post_count_bak_20261029 b ON b.user_id = p.user_id
--          SET p.posts_count = b.posts_count;
--        UPDATE communities c JOIN communities_post_count_bak_20261029 b ON b.id = c.id
--          SET c.post_count = b.post_count;
--
-- Do NOT run against production until the operator confirms the maintenance window.

START TRANSACTION;

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

COMMIT;
