# PR Body — QA acceptance stack on PR #85

> Dùng làm `--body-file` khi mở PR xếp tầng. Không phải tài liệu acceptance chính.

## Summary

- Adds an independent Playwright E2E acceptance suite for AloUTE: **165 cases** across auth/guest, social, community, messaging/admin, cross-a11y and resilience.
- Final local result on this branch: **155 PASS / 4 FAIL / 3 BLOCKED / 3 NOT RUN**.
- The four failures are the same backend defect **BL-22**: page routes return JSON 401/403/404 instead of HTML/redirect. Ticket with exact expected/actual and reproduction is in `docs/audits/quan-e2e-acceptance-2026-10-28/14_BL22_BACKEND_HANDOFF.md`.
- Frontend/a11y hardening: **13 defects fixed** (accessible names, contrast, ARIA roles, auth form method, Vietnamese error mapping, skip link, 44×44 touch targets on desktop/mobile). Axe: **0 serious/critical on 20 routes**.
- No backend, security, DTO, migration, production config or ZCode worktree changes.

## Test plan

- [x] `mvn -q -DskipTests -DskipITs package`
- [x] `BASE_URL=http://127.0.0.1:8084 node run.mjs --suite auth-guest,cross-a11y` after touch-target fix
- [x] Full 6-suite run on seeded dev/H2 server
- [x] `git diff --check`; secret/PII scan of committed evidence
- [ ] Reviewer rerun on CI/local (commands in `04_BROWSER_AUTOMATION.md`)

## Known limitations

- 4 FAIL: BL-22 backend content negotiation (AUTH-15/16, ADM-05, RES-02).
- 3 BLOCKED: Firebase upload, private-community join (product decision), Firefox environment crash.
- 3 NOT RUN: 2FA setup outside canonical OpenAPI, poll expiry, custom feed.
- Browser coverage: Chrome/Edge/WebKit; Firefox BLOCKED. Local H2 only; no load test.

## Evidence

- `docs/audits/quan-e2e-acceptance-2026-10-28/` — 17 reports + demo scripts.
- `evidence/*-results.json` — per-case results; `evidence/screenshots/` — curated synthetic-account screenshots.
- No credentials, session tokens or private messages committed; `test-results/` stays local.
