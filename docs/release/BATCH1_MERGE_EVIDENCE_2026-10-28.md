# BATCH 1 MERGE EVIDENCE — 2026-10-28

Owner-authorized controlled merge of **#74, #75, #76, #77 only**. No deploy, no production changes, no branch deletion, no force-push.

## Merges (merge commits, branch kept)

| PR | Title | Head verified | Merge commit | Master after | Master CI |
|---|---|---|---|---|---|
| #74 | fix(community): media ownership on avatar/cover attach (BL-09) | `0ca3651` | `a62d5f0` | `a62d5f0` | run 40630983617 → SUCCESS |
| #75 | fix(poll): parent post visibility on poll read/vote (BL-04) | `ae2a9d2` | `8da63c6` | `8da63c6` | run 40631478935 → SUCCESS |
| #76 | fix(websocket): SEND restricted to /app (BL-02) | `ef53e6e` | `8cf1feb` | `8cf1feb` | run 40631987998 → SUCCESS |
| #77 | fix(notification): FCM token ownership on unregister (BL-10) | `1d15fd7` | `350d41f` | `350d41f` | run 40632490282 → SUCCESS |

- Pre-merge master: `99ab72ee450fd7cfb7bfae91af4f8e40d23d5ab6`.
- Post-merge master: **`350d41f93ed918f82d5acc19e0fbf2d356ecca22`**.
- History linear merge commits `99ab72e → a62d5f0 → 8da63c6 → 8cf1feb → 350d41f` (no force-push, no lost commit).

## Pre-merge gate (all pass)
master = expected; #74–#77 OPEN, base=master, heads = expected, MERGEABLE/CLEAN, CI SUCCESS at head; each diff = one production class + dedicated security test (no scaffold/CI/config). Branch protection: none (404). Workflows: only `ci.yml`, no deploy/publish trigger → no external auto-deploy evidence.

## Post-merge verification (master `350d41f`, fresh worktree)
- **Maven 663/0/0, 9 skipped — BUILD SUCCESS.**
- **Node 370/370 PASS.**
- **JAR builds** (118 MB), dev boot OK (`/login` 200, `/feed` 200).
- Security tests passed: `CommunityMediaOwnershipIntegrationTest`, `PollVisibilityIntegrationTest`, `FeedQueryCountIntegrationTest`, `StompSendDestinationAuthorizationIntegrationTest`, `FcmTokenOwnershipIntegrationTest`, `FcmTokenControllerTest`.

## Rollback (per PR)
`git revert <merge-commit>` (a62d5f0 / 8da63c6 / 8cf1feb / 350d41f) then redeploy the previous binary. No schema impact.

## Not done
No merge outside #74–#77; no deploy; no production DB/secret changes; no force-push; no branch deletion; #90 untouched; #84 Draft.