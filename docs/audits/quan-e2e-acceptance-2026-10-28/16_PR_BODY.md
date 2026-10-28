# PR Body — QA acceptance stack on PR #85

> Dùng làm `--body-file` khi cập nhật PR #90. Không phải tài liệu acceptance chính.

## Summary

- Adds an independent Playwright E2E acceptance suite for AloUTE: **165 cases** across auth/guest, social, community, messaging/admin, cross-a11y and resilience.
- Latest sign-off run on integration HEAD `25e7595` (16 PR HEADs, including #88 BL-22) with this branch's tests: **165/165 IDs — 159 PASS / 0 FAIL / 3 BLOCKED / 3 NOT RUN**.
- Historical result on this branch alone (`4d00b6a`, base PR #85 without #88): **155 PASS / 4 FAIL / 3 BLOCKED / 3 NOT RUN**; the four failures are BL-22, now PASS on the integration HEAD.
- Poll fixture stabilization: SOC-P40/41/42/43 no longer depend on seeded post ID `4` or shared `globalThis` state. Each case creates a poll through `POST /api/posts`, verifies it via `GET /api/posts/{id}`, and deletes it in `finally`. A/B on the same shifted-ID database: 3 FAIL + 1 BLOCKED → 0 FAIL.
- Frontend/a11y hardening: **13 defects fixed** (accessible names, contrast, ARIA roles, auth form method, Vietnamese error mapping, skip link, 44×44 touch targets on desktop/mobile). Axe: **0 serious/critical on 20 routes**.
- No backend, security, DTO, migration, production config or ZCode worktree changes; the poll fix is test-only.

## Scope

- Base: `fix/quan-frontend-hardening-20261027` (PR #85) — stack dependency intentionally unchanged.
- Frontend source: 23 files (3 CSS, 4 JS, 16 templates).
- Tests: `test/e2e/quan-acceptance/` (165 cases) + `test/frontend/` (396 Node tests) + E2E fixture fix in `suites/social.mjs` (+111 / −47).
- Documentation/evidence under `docs/audits/quan-e2e-acceptance-2026-10-28/`; historical runs are preserved and the new sign-off run is additive.
- Evidence is curated; raw `test-results/`, logs and temporary credentials are not committed.

## Test plan

- [x] `node --test test/frontend/*.test.mjs` — 396/396 PASS
- [x] Maven full suite on integration `25e7595` — 723 tests, 0 failures, 0 errors, 9 skipped (`mvn -q test`, exit 0)
- [x] Full browser E2E on isolated integration `25e7595` + fixture patch — 165 unique cases, 159 PASS / 0 FAIL / 3 BLOCKED / 3 NOT RUN; run-integrity OK
- [x] Targeted A/B for poll fixture on shifted-ID H2 — old suite 3 FAIL + 1 BLOCKED; fixed suite 0 FAIL
- [x] `git diff --check`; secret/PII scan of committed evidence
- [ ] GitHub Actions on the new head SHA (checked after push)

## Known limitations

- 3 BLOCKED: Firebase upload (missing service account), private-community join (product decision), Firefox environment crash.
- 3 NOT RUN: 2FA setup outside canonical OpenAPI, poll expiry, custom feed.
- BL-22 PASS is only valid on integration HEAD `25e7595` (contains #88). Until #85 + #88 merge and this branch is rebased, the four BL-22 cases still fail on the PR branch itself.
- JS-off login renders and submits POST without leaking the password, but authentication still requires JavaScript because the backend has no form-login fallback.
- Browser coverage: Chrome/Edge/WebKit; Firefox BLOCKED. Local H2 only; no production/PostgreSQL load claim.

## Evidence

- `docs/audits/quan-e2e-acceptance-2026-10-28/01_ACCEPTANCE_SUMMARY.md` — summary and sign-off run.
- `docs/audits/quan-e2e-acceptance-2026-10-28/02_FULL_FUNCTIONAL_MATRIX.md` — 165-case matrix.
- `docs/audits/quan-e2e-acceptance-2026-10-28/04_BROWSER_AUTOMATION.md` — run commands and A/B fixture results.
- `docs/audits/quan-e2e-acceptance-2026-10-28/evidence/final-signoff-integration-25e7595-results.json` — final per-case results.
- `docs/audits/quan-e2e-acceptance-2026-10-28/evidence/baseline-fixture-repro-old-suite-results.json`, `evidence/fixed-poll-fixture-social-results.json` — fixture root-cause A/B.
- No credentials, session tokens or private messages committed; screenshots use local synthetic/seed accounts only.
