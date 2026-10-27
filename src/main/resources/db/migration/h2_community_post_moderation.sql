-- =========================================================================
-- AloUTE Safe Migration: H2 DDL for Community Post Approval & Moderation
-- =========================================================================
-- Applies on top of the legacy schema (communities / posts / moderation_actions).
-- Idempotent: safe to run more than once.

-- 1. Community approval switch (default FALSE preserves existing behavior)
ALTER TABLE communities ADD COLUMN IF NOT EXISTS require_post_approval BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Community post approval lifecycle on posts
--    NULL = not subject to approval (non-community post or legacy row) and
--    is treated as APPROVED by every read path.
ALTER TABLE posts ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(16);
ALTER TABLE posts ADD COLUMN IF NOT EXISTS moderation_reason VARCHAR(500);

-- Backfill: every existing community post is already published.
UPDATE posts SET moderation_status = 'APPROVED' WHERE community_id IS NOT NULL AND moderation_status IS NULL;

CREATE INDEX IF NOT EXISTS idx_posts_community_moderation ON posts (community_id, moderation_status);

-- 3. Community scope on the moderation audit trail
ALTER TABLE moderation_actions ADD COLUMN IF NOT EXISTS community_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_moderation_actions_community ON moderation_actions (community_id, created_at);
