# AloUTE — Deployment Readiness Audit (Cloud Run) — 2026-10-24

- **Baseline**: master `f8ce120` (CI run 39988426025 — **PASS**, re-confirmed via GitHub API today)
- **Deployment target** (from `deploy/env/README.md`): Cloud Run service **`aloute-app`**, project **`cnpm-f1a39`**, region **`asia-southeast1`**; DB = **Aiven MySQL 8**; media = **Firebase Storage** (`cnpm-f1a39.firebasestorage.app`); mail = Gmail SMTP 587.
- **Method**: static audit of deploy assets on master + **read-only** external probes (Firebase bucket HTTP probes, GitHub API). No production resource was deployed, updated, or altered. Flutter audit dropped per owner decision (mobile app is owned/being written by a teammate) — contract notes for that app are in Appendix A.

## A. Verdict summary

| Area | Verdict | Basis |
|---|---|---|
| Container image (Dockerfile) | **PASS** | `$PORT` honored, non-root user, `exec` PID 1 (SIGTERM/graceful shutdown), G1 + MaxRAMPercentage 75, prod profile default |
| HTTPS / TLS | **PASS** (platform) | Cloud Run terminates TLS; `forward-headers-strategy: native` + Tomcat remoteip config in `application-prod.yml` → `request.isSecure()` true behind proxy → `Secure` cookie flag active. JWT cookie: `HttpOnly` + `SameSite=Lax` + conditional `Secure` (`AuthController.authCookie`) |
| Secrets fail-closed (code) | **PASS** | `ALOUTE_JWT_SECRET`, `ALOUTE_REGISTRATION_OTP_SECRET` have no defaults (prod boot fails without them); `docker-compose.yml` uses `:?` guards for every secret |
| Secrets wiring on Cloud Run | **BLOCKED** | No gcloud CLI/credentials on this machine; service env/secrets cannot be inspected |
| MySQL 8 (Aiven) — runtime | **BLOCKED** | No MySQL/docker/Aiven credentials locally (unchanged since 2026-10-23 audit) |
| MySQL 8 — deploy precondition | **FAIL (runbook gap — blocker B-1)** | See B-1: `ddl-auto: update` creates tables but **not** stored procedures/triggers; `sql.init.mode: never` |
| Firebase Storage — upload path | **FAIL (blocker B-2)** | `FirebaseConfig` initializes only from a `serviceAccountKey.json` file; no Application Default Credentials fallback → Cloud Run must mount the key as a secret volume |
| Firebase Storage — read path | **FAIL (blocker B-3, needs owner decision)** | Adapter returns `firebaseStorageDownloadTokens` URLs; bucket is IAM-private (probed 403) and download tokens do **not** bypass IAM → uploaded media would be unfetchable (403) for end users |
| Bucket security posture | **PASS** | Bucket exists and is private: unauthenticated list = 403 on both XML API and Firebase API (probed 2026-10-24). Do not replace this with public IAM |
| SMTP | **PASS (config) / BLOCKED (delivery)** | Gmail 587 STARTTLS, `checkserveridentity: true`, trust-all default **off** (PR #63); OTP send is in-request (Cloud Run request-based-billing compatible). External inbox delivery unverifiable without credentials |
| Production critical E2E | **BLOCKED** | Service URL not recorded in repo/GitHub PRs; gcloud unavailable → cannot probe live login/feed/post flows |
| CI → deploy pipeline | **PASS (CI) / manual deploy by design** | CI = test + package only; deployment is owner-run. Master f8ce120 build artifacts are the deploy source |

## B. Production release blockers (must resolve before cutover)

1. **B-1 — Database initialization runbook (P0)**: all backend writes are stored-procedure based. On a **fresh** Aiven MySQL, `ddl-auto: update` will create tables (Hibernate) but never the SPs/triggers → every write returns 500. Before release the owner must apply, in order: `src/main/resources/db/schema.sql` → `procedures.sql` → `triggers.sql` → `db/migration/mysql8_*.sql` (auth/OTP/2FA/revocation), then verify: `SELECT ROUTINE_NAME FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA='<db>';` plus trigger check. Also decide: keep `ddl-auto: update` in prod (drift risk vs `schema.sql`) or lock to `validate` after init — needs owner call.
2. **B-2 — Firebase key mounting (P0 for media)**: `FirebaseConfig.java` reads only `ALOUTE_FIREBASE_CREDENTIALS_PATH` (file system/classpath) with fallback to a root `serviceAccountKey.json`. There is **no ADC/service-account-metadata fallback**, so "Cloud Run uses its service account" does not work here. Deployment must mount the key, e.g. `gcloud run services update aloute-app --set-secrets=...` volume-mounted, with `ALOUTE_FIREBASE_CREDENTIALS_PATH` pointing at it. Otherwise every upload → 503 `MEDIA_STORAGE_UNAVAILABLE`.
3. **B-3 — Media read strategy (P0, decision needed)**: `FirebaseMediaStorageAdapter` hands out `https://firebasestorage.googleapis.com/v0/b/<bucket>/o/<key>?alt=media&token=<uuid>`. With the bucket IAM-private (current state — verified), that URL 403s for browsers (re-confirmed by 2026-10-21 audit: token does not bypass IAM). Options: (a) Firebase Storage **rules** allowing public read (keeps IAM closed; standard for token'd media), or (b) backend-generated **V4 signed URLs** (code change, no rule change). Owner must choose; uploads alone are not user-visible until this is fixed.
4. **B-4 — Live-service parity unverified (P1)**: deployed revision/env of `aloute-app` cannot be compared to master f8ce120 from this machine (BLOCKED). See runbook §C-1.
5. **Open decision (carried, not new)**: JWT also returned in login response body (P2, mandatory for the mobile client; gating proposal in docs/audits/ZCODE_FULL_SYSTEM_TEST_2026-10-23.md §C) — owner sign-off advised before public launch.

## C. Owner verification runbook (what this machine could not do)

1. **Service config vs master**:
   `gcloud run services describe aloute-app --project=cnpm-f1a39 --region=asia-southeast1 --format=yaml` — check: image digest matches a build of f8ce120; env vars below present; `--cpu-throttling` + min-instances 0 (billing, per deploy/env/README.md §4); secret volume for B-2.
2. **Required runtime env** (from `application-prod.yml` + compose): `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` (Aiven MySQL, SSL), `ALOUTE_JWT_SECRET`, `ALOUTE_REGISTRATION_OTP_SECRET`, `ALOUTE_APP_BASE_URL`, `ALOUTE_MAIL_HOST/PORT/USERNAME/PASSWORD/FROM`, `ALOUTE_FIREBASE_ENABLED=true`, `ALOUTE_FIREBASE_BUCKET=cnpm-f1a39.firebasestorage.app`, `ALOUTE_FIREBASE_CREDENTIALS_PATH` (mounted secret), optional `HIKARI_*`, `ALOUTE_JWT_EXPIRATION_SECONDS`.
3. **Liveness probe** (no actuator in app): `curl -i https://<url>/api/auth/me` → 401 JSON envelope = alive; `curl -I https://<url>/` → 200/30x with `https` scheme.
4. **MySQL 8 runtime**: run the repo's MySQL verification suite (`MySql8RevocationVerificationTest` etc.) against Aiven, or minimally apply §B-1 checks + create/like/comment smoke via API.
5. **Firebase end-to-end**: upload one avatar on prod → `curl -I` the returned URL unauthenticated → 200 = B-3 resolved; 403 = B-3 confirmed. Also confirm container log "FirebaseApp initialized successfully with service account."
6. **SMTP**: register → Resend OTP → container log `Registration OTP notification dispatched` → check inbox **and spam**. Note SMTP acceptance ≠ inbox placement (deploy/env/README.md).
7. **Critical E2E on prod** (read-mostly): login → feed render → post create (text-only) → like → comment → message send → notification read. Production data policy (write tests on real accounts) is the owner's call.

## D. Environment requirements (to un-BLOCK this audit)

- `gcloud` CLI + credentials with `roles/run.viewer` (+ `roles/secretmanager.viewer` for secret *names*, not values) on project `cnpm-f1a39`, or simply share the service URL for black-box probing.
- Aiven MySQL access (read-only at minimum) for §C-4.
- A `serviceAccountKey.json` for a **non-production** Firebase project to finish the media E2E locally (per repo rule: no production credentials in QA).

## Appendix A — Backend contract notes for the mobile client (no code touched)

The teammate writing the Flutter app should code against these verified behaviors (master f8ce120):

- **Auth**: `POST /api/auth/login` sets HttpOnly cookie **and** returns `data.token` — mobile uses it as `Authorization: Bearer <token>`. All Bearer requests are CSRF-exempt (`bearerRequest` matcher); cookie requests need the XSRF-TOKEN cookie/header pair. Logout revokes sessions server-side (other sessions' STOMP sockets close 1008).
- **Media upload**: `POST /api/media` multipart with `file` + `purpose` — after PR #63 the `purpose` part is accepted both as plain text (`USER_AVATAR`) and JSON blob; responses: 201 + id/url, 422 invalid purpose, 400 missing part, 404 attaching foreign/deleted media. Attach media to the owning post via `CreatePostRequest.mediaIds` (owner-checked, `attachedAt` set → delete protected).
- **Realtime**: STOMP at `/ws`; auth via native header `aloute_access_token` (JWT). Subscribe `/user/queue/messages`, `/user/queue/notifications`; send `/app/chat.send/{conversationId}`. Persist-before-publish: REST reads remain the source of truth after reconnect.
- **Feed/home on H2 dev** returns 500 (SP-based) — use MySQL profile or the deployed service for those screens; register/login/`/me` work on H2.

## Evidence

- CI: `gh run list` → 39988426025 f8ce120 `CI completed success` (2026-10-24).
- Firebase bucket probes (2026-10-24, unauthenticated): `GET https://storage.googleapis.com/cnpm-f1a39.firebasestorage.app` → 403; `GET https://firebasestorage.googleapis.com/v0/b/cnpm-f1a39.firebasestorage.app/o` → 403 `Permission denied` (bucket exists, private).
- Static review: `Dockerfile`, `docker-compose.yml`, `deploy/env/README.md`, `application-prod.yml`, `FirebaseConfig.java`, `FirebaseMediaStorageAdapter.java`, `AuthController.java`, `SecurityConfig.java`, `src/main/resources/db/*`.
- Prior runtime evidence reused (not re-run): `docs/audits/ZCODE_FULL_SYSTEM_TEST_2026-10-23.md` (572 Maven / 329 Node / 13 browser E2E on the commits merged by PR #63), `final-validation-evidence/` (8 browser interactions on f8ce120).
