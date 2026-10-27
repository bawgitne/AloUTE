-- =========================================================================
-- AloUTE Safe Migration: MySQL 8.x DDL for Community Post Approval & Moderation
-- =========================================================================
-- NOTE (MySQL 8): MySQL does not support `ADD COLUMN IF NOT EXISTS`.
-- Run each statement once and skip the ones whose column/index already exists
-- (check with SHOW COLUMNS FROM <table> / SHOW INDEX FROM <table>).
-- Applied manually by the DBA, consistent with the repo migration README.

-- 1. Community approval switch (default 0 preserves existing behavior)
ALTER TABLE communities ADD COLUMN require_post_approval TINYINT(1) NOT NULL DEFAULT 0;

-- 2. Community post approval lifecycle on posts
--    NULL = not subject to approval (non-community post or legacy row) and
--    is treated as APPROVED by every read path.
ALTER TABLE posts ADD COLUMN moderation_status VARCHAR(16) NULL;
ALTER TABLE posts ADD COLUMN moderation_reason VARCHAR(500) NULL;

-- Backfill: every existing community post is already published.
UPDATE posts SET moderation_status = 'APPROVED' WHERE community_id IS NOT NULL AND moderation_status IS NULL;

CREATE INDEX idx_posts_community_moderation ON posts (community_id, moderation_status);

-- 3. Community scope on the moderation audit trail
ALTER TABLE moderation_actions ADD COLUMN community_id BIGINT NULL;
CREATE INDEX idx_moderation_actions_community ON moderation_actions (community_id, created_at);
