# ALOUTE Private Community Discovery — Proposed Strict Privacy Decision

**Status:** IMPLEMENTED FOR REVIEW ONLY — approval required from Khôi (Security) and Ngọc (Community/Domain).

**Base:** `fix/admin-mvp-hardening` (PR #28 is not yet merged). This is a stacked patch, not a release approval.

## Proposed policy (Option A)

| Resource | Guest / nonmember | Member / owner | Public Community |
| --- | --- | --- | --- |
| Search results & totals | Private Communities excluded in SQL, including count | Own private Communities included | Visible to all |
| Direct ID / slug detail | 404 (same as absent) | Full existing CommunityResponse | Visible to all |
| Membership endpoint | 404 without identities, counts or roles | Existing CommunityMembershipResponse fields | Preserve existing public read |
| Deleted Community by ID/slug | 404 | 404 | 404 |
| Posts of deleted Communities | Excluded from query lists and denied on direct read | Also excluded / denied | Same |
| Existing private-community self-join | Denied by existing policy | Existing membership stays idempotent | Public self-join unchanged |
| SYSTEM_ADMIN | No automatic private read override | Must also be owner/member | Existing global Admin policy unaffected |

## Implementation notes

- `CommunityRepository.searchVisibleCommunities` applies viewer predicate before sorting, counting and pagination.
- `CommunityServiceImpl` enforces server-side access and does not fall back to soft-deleted Communities.
- Member-list service requires explicit viewer identity; legacy no-viewer service overload defaults to guest.
- SSR detail returns 404 with the existing generic not-found UI and does not query Posts when the Community is concealed.
- Post visibility queries and direct permission checks exclude Posts in deleted Communities.

## Explicit limits and decisions for owners

1. **Member list fields:** This proposal allows existing member-list fields to any authorized Community member or owner. Khôi/Ngọc should affirm whether member identity/role visibility needs a narrower scope. No new DTO is introduced.
2. **Deletion:** Deleted Communities and their Posts are not readable via normal public/member routes. Admin/audit restoration workflow, if any, must be specified separately.
3. **Concealment:** Unauthorized GET/read produces 404. Existing private self-join may still return 403 `COMMUNITY_ACCESS_DENIED`, revealing a community's existence to someone with a valid ID; preserving this existing write-error contract is a documented residual decision rather than a claim of perfect existence concealment.
4. **N+1 / DB:** Existing `toResponse` membership lookup remains per result. Check query plans on real MySQL and optimize in a distinct, measured change if needed.
5. **Follow-on surfaces:** Review Admin/community moderation exports, nested DTO references, caches, CDN/private media URLs and event payloads during release hardening.
6. **QA:** GitHub Actions/H2 verification is not a substitute for MySQL, hosted storage or browser visual QA.

**Required before merge:** Independent QA, green CI, Khôi + Ngọc approval, review potential semantic/API impacts, and confirmation that PR #28 has merged (or rebase/retarget correctly).

This document intentionally does not alter canonical Domain, OpenAPI or permission specifications before owner approval.
