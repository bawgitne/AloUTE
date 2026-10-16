# ALOUTE Private Community Discovery — Proposed Strict Privacy Decision

**Status:** IMPLEMENTED PER PROJECT-INTEGRATOR DECISION — approval from Khôi (Security) and Ngọc (Community/Domain) remains pending.

**Base:** `fix/admin-mvp-hardening` (PR #28 is not yet merged). This is a stacked patch, not a release approval.

## Proposed policy (Option A with Integrator Security Follow-up)

| Resource | Guest (unauthenticated) | Authenticated Outsider | Member / Owner | Public Community |
| --- | --- | --- | --- | --- |
| Search results & totals | Private Communities excluded in SQL, including count | Excluded in SQL, count concealed | Own private Communities included | Visible to all |
| Direct ID / slug detail | 404 (same as absent) | 404 (same as absent) | Full existing CommunityResponse (Owner reads without membership) | Visible to all |
| Membership endpoint | 404 without identities, counts or roles | 404 without identities, counts or roles | Existing CommunityMembershipResponse fields | Preserve existing public read |
| Deleted Community by ID/slug | 404 | 404 | 404 | 404 |
| Posts of deleted Communities | Excluded from query lists and denied on direct read | Also excluded / denied | Also excluded / denied | Same |
| Private community mutations (POST posts, POST join, PATCH, DELETE) | 401/403 (security-layer auth failure, not concealed before auth) | 404 (same generic code and message as nonexistent) | Authorized discoverers retain existing operation permissions and 403 when privilege lacking | Public mutation behavior preserved |
| SYSTEM_ADMIN | No automatic private read override | No automatic private read override | Must also be owner/member | Existing global Admin policy unaffected |

## Implementation notes

- `CommunityRepository.searchVisibleCommunities` applies viewer predicate before sorting, counting and pagination.
- `CommunityServiceImpl` enforces server-side access and does not fall back to soft-deleted Communities.
- The four specified community mutation operations (`POST /api/communities/{id}/posts`, `POST /api/communities/{id}/join`, `PATCH /api/communities/{id}`, and `DELETE /api/communities/{id}`) gate behind private discovery access in `CommunityServiceImpl` and `PostServiceImpl` so authenticated outsiders receive the generic 404 `COMMUNITY_NOT_FOUND` code and message, matching nonexistent communities without leaking private community existence.
- Community owner (`community.owner.id`) is granted read consistency without a membership row: owner can read private community metadata, community posts, and direct post visibility (`isPostVisibleTo`). Mutation operations such as posting (`POST /posts`), editing community (`PATCH`), and self-joining (`POST /join`) strictly require a membership row; baseline owner privilege to delete their own community (`DELETE /api/communities/{id}`) is preserved.
- Member-list service requires explicit viewer identity; legacy no-viewer service overload defaults to guest.
- SSR detail returns 404 with the existing generic not-found UI and does not query Posts when the Community is concealed.
- Post visibility queries and direct permission checks exclude Posts in deleted Communities.

## Explicit limits and decisions for owners

1. **Member list fields:** This proposal allows existing member-list fields to any authorized Community member or owner. Khôi/Ngọc should affirm whether member identity/role visibility needs a narrower scope. No new DTO is introduced.
2. **Deletion:** Deleted Communities and their Posts are not readable via normal public/member routes. Admin/audit restoration workflow, if any, must be specified separately.
3. **Concealment & Mutation Gating:** Per explicit project-integrator decision (overriding the prior residual 403 proposal), authenticated outsiders who cannot discover a private community receive 404 `COMMUNITY_NOT_FOUND` with the generic message "Community not found" on the four specified operations (`POST /api/communities/{id}/posts`, `POST /api/communities/{id}/join`, `PATCH /api/communities/{id}`, `DELETE /api/communities/{id}`), matching nonexistent communities exactly. This records an integrator policy decision, NOT formal approval by Khôi or Ngọc (approvals remain pending).
4. **N+1 / DB:** Existing `toResponse` membership lookup remains per result. Owner post visibility is scoped directly to the target community ID in `communityPosts` without any table scan or global feed specification changes, preserving existing `PostSpecifications` SQL predicate structure and pagination counts.
5. **Follow-on surfaces:** Review Admin/community moderation exports, nested DTO references, caches, CDN/private media URLs and event payloads during release hardening.
6. **QA:** GitHub Actions/H2 verification is not a substitute for MySQL, hosted storage or browser visual QA.

**Required before merge:** Independent QA, green CI, Khôi + Ngọc approval, review potential semantic/API impacts, and confirmation that PR #28 has merged (or rebase/retarget correctly).

This document records the integrator decision and intentionally does not claim consensus approval from domain/security owners prior to formal review.
