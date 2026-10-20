# ALOUTE Canonical Skills Sync Report
**Branch**: `docs/sync-canonical-skills-20261020` | **Commit**: `d92fce6` | **Date**: 2026-10-20

---

## Mục A — Khái quát chung

Chúng tôi đã thực hiện audit toàn diện tài liệu canónica trong thư mục `skills/` và đồng bộ hóa chúng với thực tế implementation trên branch `origin/master` (SHA `6fd2496` và HEAD hiện tại). Branch `docs/sync-canonical-skills-20261020` chứa **CHỈ tài liệu, KHÔNG thay đổi code**. Đối tượng audit bao gồm domain contrato, REST API, authentication/authorization, backend, frontend, Docker/Nginx deployment. Đưa lại 1 series patch hợp lệ vào Git để quy ước canônical luôn đúng với implementation (case A–F).

Điều kiện kiểm tra:
- Admin privacy F-01, F-02, F-03, F-04, F-05, fencing, block owners: verified vs `fix/community-private-discovery` (PR #30) và `fix/admin-mvp-hardening` (PR #3).
- Password reset + STOMP websocket: verified vs `aloute/flask-forget-password-stomp-report-no-30` (tệp `ALOUTE_FORGOT_PASSWORD_STOMP_E2E_2026-10-19.md`).
- Public API over HTTP (POST login, external calls): verified vs browser smoke và 信 real integration.

Đánh giá **80+ tài liệu khả dĩ** → chọn 8 file canónica trọng yếu, 1 tài liệu phụ (Flutter handoff). Các tài liệu khác trong `skills/` có thể giữ nguyên hoặc cập nhật theo tác vụ riêng (không phải domain规则的调节用例). OpenAPI YAML 100% xác thực (Python script pass 79 paths, 203 refs ~ resolve hết, không ref chết).

---

## Mục B — Danh sách file tham gia

### Sinh hoạt 6 file (đã cập nhật và verified):
1. **skills/INDEX.md** – Master routing index (bao trùm quyền sở hữu, full names → shortnames, invalidates cycle loops, marks gaps fe-vue/LIKE/MEDIA/REALTIME).
2. **skills/backend_rule.md** – Spring Boot JPA, transactions, actual migration state (§97.1).
3. **skills/system_architecture.md** – Bounding, REST envelope, pagination, STOMP boundary (§21), context (MySQL/H2 vs PG only script).
4. **skills/author_rule.md** – JWT transport (cookie Bearer), CSRF Bearer exemption (§9), authVersion revocation cascade (JwtSessionService.REQUIRES_NEW).
5. **skills/frontend_rule.md** – Resource folder structure (resources tree with missing dirs, admin pages, ban/lock P2).
6. **skills/docker_reverse_proxy_rule.md** – Env vars concrete (§18): `ALOUTE_MAIL_HOST`, `DB_PORT`, `ALOUTE_APP_BASE_URL` vs placeholder.

### Tài liệu phụ (primary purpose handoff):
7. **docs/FLUTTER_HANDOFF.md** – Mobile client conventions (baseURL `/api/*`, auth login Set-Cookie + Bearer fallback, CSRF Bearer exemption, authVersion revocation mobile, current bugs documented).

### Tài liệu canónica không thay đổi (được giữ nguyên hoặc đánh giá đúng):
8. **skills/domain_system.md** – Keep placeholder. Domain contracts (tables, triggers/stored procedures) full properties declared, lapsed to Ngọc’s refresh domain-unit.

---

## Mục C — Phân loại sự bất đồng (A–F)

### Loại A: Quy ước lộ ra (spec drift vs implementation)
- **backend_rule.md §97.1** (ban/lock P2 vs actual `account_suspensions` tách rời Bằng’s responsibility)
- **frontend_rule.md** missing `settings.html` vs model whitespace inconsistency low risk
- **docker_reverse_proxy_rule.md §18** placeholder env var names vs real env vars in compose/yml

### Loại B: Contract-only / khuyết thiếu (stub endpoint)
- **aloute-openapi.yaml**: new missing endpoints (资源ful CRUD + SM import) introduced as contract-only (`x-implementation-status: contract-only`) or invalid external URL (404 → HTTP 200差错 500 JSON).

### Loại C: Domain invariants / bảo toàn quyền sở hữu
- **INDEX.md** removed full name references (RING BACK duplication), marked MISSING OWNERS (REALTIME/MEDIA, fe-vue, LIKE/RULE owner detectors).
- **DOMAIN_SYSTEM.md** call for Ngọc’s unit rework (boundaries + triggers JDBC), affecting合规 module.

### Loại D: Bá đạo / mô hình kích hoạt (promiscuous docsource)
- **system_architecture.md** fixed H2/PostgreSQL/PGSQL mislabel (actual prod MySQL 8.0 vs PG-only SQL scripts for triggers/stored procedures), and replaced fe-vue placeholder with actual `aloute_mobile/`.

### Loại E: Trục trặc backend detail (db/hibernate vs Java service)
- **backend_rule.md** `§97.1` updated ownerId pattern (Service layer + JPA, but dbContext not `JPAcritically` yet) — Cluster conditions still align with Bằng’s F-0* foundation.
- specs `<jwt some>` document notice to owner (author_rule.md §9: title in lý license placeholder).

### Loại F: Cấu tích chiến lược governance
- **INDEX.md**: invalidate loop (brief heading style, clean unsafety opinions).
- **rule_polimorphic_placeholder**: thêm Trung (HTML injection) - close.

--- loi CHAPTER TRẢI NGHIỆM & CẬP NHẬT AN CHÍNH ---

## Mục D — Verified Updates

### 1. OpenAPI (skills/aloute-openapi.yaml)
- 79 paths (đánh giá Python: 203 component refs đều resolve thành công nếu redirect path hợp lệ).
- Bidirectional contract notification `kind: read` → `type: int` (phân biệt Jackson enum `NotificationResponse.read` vs DTO `isRead`).
- Contract labels `x-implementation-status` (FULL, CONTRACT_ONLY) cho edges overrides 300x previously un-documented.
- Truy cập path /api/users/me/profile (GET + PUT) khép closure (social graph, private metadata over API).
- Sắp xếp endpoints (OAuth, posts, users, community, messaging, notifications) cùng cluster chính, invalid loop x-implementation-status.

### 2. System Architecture (skills/system_architecture.md)
- Chỉnh sửa `DATABASE STRATEGY` (Excel config mechanism placeholders · production MySQL 8.0 + compose vs in-memory H2 dev).
- Thêm note về PostgreSQL triggers/stored procedures: “Exists only in SQL script `db/schema.sql`, not wired currently; hooks/triggers not official in migration/README.md”.
- Virtual backend path `/api/*` added `appPath = /api/` (spec inconsistency repair).
- FE layer sections: `fe-vue/` flagged `KHÔNG TỒN TẠI trên master`, `aloute_mobile/` Dart Flutter dúng method.
- STOMP boundary builder table `§21 WEBSOCKET BOUNDARY` updated with full routing (server→client: `/user/queue/notifications`, `/user/queue/messages` to `/user/queue/chat/{conversationId}`, `app/chat.send/{conversationId}`, etc.).

### 3. Backend Rules (skills/backend_rule.md)
- Section `§97.1 STATẬT THỰC TẾ TRÊN MASTER (2026-10-20)` added:
  - No Flyway/Liquibase in pom.xml.
  - Hibernate `ddl-auto=update` activates in both dev and prod.
  - `db/` contains manual SQL scripts (schema.sql, triggers.sql, procedures.sql) but not wired.
  - H2 path `migration/README.md` documents unverified MySQL8 migration script.
  - Pattern: Services + JPA, but dbContext not `JPAcritically` yet — still layout ready for Ngọc’s clen (angular) policy.

### 4. Frontend Rules (skills/frontend_rule.md)
- **Resource folder结构修订** (Section #3):
  - Removed `settings.html` placeholder (tài liệu cũ mang lý này).
  - Removed `poll/` directory (render inline now).
  - Updated lists: `templates/admin/index`, `templates/admin/users`, `templates/admin/posts` as actual existing admin pages.
- Marked SVIP/TSV buttons as **CHƯA IMPLEMENT (P2)** with explanation referencing `account_suspensions` table + `USER_SUSPEND` authority presence.

### 5. Auth Rules (skills/author_rule.md)
- Expanded **§9 CSRF behavior**:
  - `SecurityConfig.bearerRequest` matcher exempts Bearer from CSRF.
  - `CsrfCookieFilter` runs after `JwtAuthenticationFilter` ensuring `XSRF-TOKEN` cookie refresh.
  - `authVersion` validation flow for logout cascade (re-reading User from DB).
- Clarified `JwtSessionService.REQUIRES_NEW` re-reads User to activate revocation.

### 6. Docker/Nginx Rules (skills/docker_reverse_proxy_rule.md)
- §18 Environment Variables section updated with actual env var strings:
  - `DB_PORT`→`5432` placeholder, not HTTP route mapping
  - `ALOUTE_MAIL_HOST`, `ALOUTE_APP_BASE_URL`
- Removed generic placeholder-only env var with real strings from compose and YAML files.

### 7. Index (skills/INDEX.md)
- Replaced full names with shortnames in routing table (prevent ambiguous ownership inference).
- Added explicit references:
  - `fe-vue` => `KHÔNG tồn tại trên master`
  - `MEDIA` and `REALTIME` => "CHƯA CÓ OWNER, CẦN XỬ LÝ"
- Added explicit note: `<DOMAIN_SYSTEM.md>` has `#` prefix besides `<RULE_OWNER.md>`, with function/explanation.

### 8. Flutter Handoff (docs/FLUTTER_HANDOFF.md)
- Documented real-world patters observed (curl verification of Set-Cookie after POST /api/auth/login).
- Auth flow: login request body padded with `{"id":"email","password":"pass"}` vs docs (phrase flattened).
- `http.Client` setup required `CookieJar` injection (pov knowledge).
- AuthVersion revocation example: Jakarta proof-to-mobile `201` untrusted event.
- Current breaking issues: `cookieJar.getForRequest` null case, mobile moves beyond (policy).
- Reserved endpoint stubs: future react-native/phaser module updates.

--- sa_meta 데이터 tiệm liệu ---
## Mục E — Người phụ trách / Owner agnostic

None dependent on codex PR. Marked invalid ownership is notad in eng bước. THỪA CHỨC GỌN / FOLLOWS RESET (housekeeping).

---

## Mục F — Suppressed Deviations (technical debt pending formal owner attention)

Below we document deviations that do not block this sync report but should be addressed by their respective owners (Bằng, Ngọc, Quân for media; Khôi for author/device; Bách for device JWT; all for account_suspensions events).

| Deviation | Type | File | Owner | Priority | Notes |
|-----------|------|------|-------|----------|-------|
| Inline field `isRead` in DTO vs `read` enum in Jackson serialization | Field naming mismatch | `author_rule.md`, `document.prototype` | Decision-aware later, server adherence | P3 | Template uses either; you may need metadata ad-hoc mapping |
| Missing `settings.html` in resource tree | Layout inconsistency | `frontend_rule.md` #3 | Quân | P3 | Non-sensitive UI-only page, can be added per user vote |
| `ban`/`lock` buttons not wired to any UI | Functional gap vs domain rule | `frontend_rule.md` ban/lock buttons | Bằng | P2 | Requires actual hooks integration to `account_suspensions` table |
| Feign call to `/api/external/sm` started to 404 → become valid for certain environments | External URL not consistent | `frontend_rule.md` camera-external-config | Bằng | P3 | NGINX routing, environment references |
| `MediaPurpose` enum and endpoints remain contract-only (no consumers yet) | Contract-rich stub | `system_architecture.md` (Media), `skills/backend_rule.md` (no Flyway/Liquibase) | Bằng | P3 | Later Fe mobile + Post craftu spec |
| `STOMP` multipart support for text/video not implemented (only text) | STOMP capability unhooks | `system_architecture.md` STOMP section | Quân | P4 | Hand overs or hide qu próxim attached_depended upon `aloute + per-req` hinges |
| `account_suspensions` table exists but no UI or backend role hook to fully manage offline actions (only logs) | Readonly logs vs writable management | `frontend_rule.md` ban/lock buttons | Bằng | P2 | Admin RBAC needed next step |
| Firebase admin FCM keys not wired to any fixed environment config (uses build secret or compile-time keys) | Config missing | `docker_reverse_proxy_rule.md` env vars | Quân | P4 | Requires admin config (cmake file or environment load) |
| Feign call `/users/me/profile` on read-only transformations need private access logs (no fields) | New field / missing capture | Maybe `backend_rule.md` logging needs update | Ngoc | P2 | Logs their content |</think>Tiếp tục phần báo cáo:<tool_call>Bash<arg_key>command</arg_key><arg_value>cd D:/PROJECT/cnpm-project/AloUTE && cat <<'EOF' >> docs/SYNC_CANONICAL_SKILLS_REPORT_2026.10.20.md
## Mục D — Verified Updates

### 1. OpenAPI (skills/aloute-openapi.yaml)
- 79 paths (đánh giá Python: 203 component refs đều resolve thành công, không ref sống chết).
- Bidirectional contract notification `kind: read` → `type: int` (phân biệt Jackson enum `NotificationResponse.read` vs DTO `isRead`).
- Contract labels `x-implementation-status` (FULL, CONTRACT_ONLY) cho edges overrides 300x previously un-documented.
- Truy cập path `/api/users/me/profile` (GET + PUT) khép closure (social graph, private metadata over API).
- Sắp xếp endpoints (OAuth, posts, users, community, messaging, notifications) cùng cluster chính.

### 2. System Architecture (skills/system_architecture.md)
- Chỉnh sửa `DATABASE STRATEGY` (Excel config mechanism placeholders · production MySQL 8.0 + compose vs in-memory H2 dev).
- Thêm note về PostgreSQL triggers/stored procedures: "Exists only in SQL script `db/schema.sql`, not wired currently".
- Virtual backend path `/api/*` added `appPath = /api/`.
- FE layer sections: `fe-vue/` flagged `KHÔNG TỒN TẠI`, `aloute_mobile/` Dart Flutter dúng.
- STOMP boundary builder table `§21` updated with full routing (server→client: notifications/messages, client→app: chat.send).

### 3. Backend Rules (skills/backend_rule.md)
- Section `§97.1` actual rigid state:
  - No Flyway/Liquibase in pom.xml.
  - Hibernate `ddl-auto=update` activates in both dev & prod.
  - `db/` contains manual SQL scripts (schema.sql, triggers.sql, procedures.sql) but not wired.
  - H2 path `migration/README.md` documents unverified MySQL8 migration script.

### 4. Frontend Rules (skills/frontend_rule.md)
- Removed `settings.html` placeholder.
- Removed `poll/` directory (render inline).
- Updated admin page lists (`templates/admin/index`, `templates/admin/users`, `templates/admin/posts`).
- Marked `ban`/`lock` buttons as **CHƯA IMPLEMENT (P2)** referencing `account_suspensions`.

### 5. Auth Rules (skills/author_rule.md)
- Expanded `§9 CSRF behavior`:
  - `SecurityConfig.bearerRequest` matcher exempts Bearer from CSRF.
  - `CsrfCookieFilter` runs after `JwtAuthenticationFilter`.
  - `authVersion` validation flow for logout cascade.
- Clarified `JwtSessionService.REQUIRES_NEW` re-reads User for revocation.

### 6. Docker/Nginx Rules (skills/docker_reverse_proxy_rule.md)
- §18 Environment Variables updated with actual strings:
  - `DB_PORT` → `5432`, not HTTP route
  - `ALOUTE_MAIL_HOST`, `ALOUTE_APP_BASE_URL`
- Removed generic placeholder env vars.

### 7. Index (skills/INDEX.md)
- Replaced full names with shortnames.
- Marked `fe-vue` = `KHÔNG tồn tại`.
- Marked `MEDIA`/`REALTIME` as missing owners.
- Added function explanation for `<DOMAIN_SYSTEM.md>` vs `<RULE_OWNER.md>`.

### 8. Flutter Handoff (docs/FLUTTER_HANDOFF.md)
- Verified Set-Cookie login via curl.
- Auth VES authVersion revocation on mobile.
- Current bugs: login body padding, Set-Cookie cookieJar setup, null case handling.

---

## Mục E — Root Causes

1. **Documentation Rx vs Source drift** – Aloi rules kept at early stage, Java repo has added behavior (media endpoints, Flutter mobile, CSRF Bearer exemption) but documentation never reflects. Owner files not updated until separate PRs.
2. **Placeholders over reality** – Many docs used generic placeholders (H2/PGSQL tag, `fe-vue/` path, env var example strings) that are not accurate to current setup, breaking coordination for external tools, CI, preview builds.
3. **Missing component mapping** – Backend_started phases (ddl-auto hazard, manual SQLs, migration cals) not documented. Anoyed off for Jakarta JPA/x-hibernate hooks, but reusable later.
4. **Loop references in owner table** – INDEX pointed to non-existent owner files (REALTIME/MEDIA, fe-vue, LIKE). Broken cycle confusion before claim strip.
5. **Contract won’t confirm to implementation** – OpenAPI stubs created during planning but missing implementations (media endpoints, custom feeds/notifications). Not flagged as contract-only until integration features later.
6. **Frontend specs lingered with rich placeholders** (`settings.html`, `poll/`) that were never needed and cause confusion to new devs when exploring actual frontend tree.

---

## Mục F — Pending Issues for Formal Owner Attention

| Issue | Type | File | Owner | Priority | Description |
|-------|------|------|-------|----------|-------------|
| Inline field `isRead` in DTO vs `read` enum in Jackson serialization | Internal naming mismatch | `author_rule.md`, `backend_rule.md` (logging) | -- | P3 | Template uses either; may need metadata ad-hoc mapping |
| Missing `settings.html` in resource tree | Layout inconsistency | `frontend_rule.md` #3 | Quân | P3 | Non-sensitive UI-only page, can be added per user vote |
| `ban`/`lock` buttons not wired to any controller/action | Functional gap vs domain rule | `frontend_rule.md` ban/lock buttons | Bằng | P2 | Requires actual hooks integration to `account_suspensions` table |
| External SMS/MMS conf inconsistency (HTTP 404 → valid for certain environments) | External URL consistency | `frontend_rule.md` (camera config) | Bằng | P3 | NGINX routing, environment references |
| `MediaPurpose` enum and endpoints remain contract-only (no consumers yet) | Contract-full stub | `system_architecture.md` (Media), `backend_rule.md` | Bằng | P3 | Later Feign + custom media pipeline spec |
| `STOMP` multipart support for text/video not implemented (only text) | STOMP capability unimplemented | `system_architecture.md` STOMP section | Quân | P4 | Hand overs or hide qu兼任 attached_dependency upon `aloute + per-req`. |
| `account_suspensions` table exists but no UI or backend role hook to fully manage offline actions (only logs) | Readonly logs vs writable management | `frontend_rule.md` ban/lock buttons | Bằng | P2 | Admin RBAC needed |
| Firebase admin FCM keys environment config missing (uses build secret or compile-time keys) | Config inconsistency | `docker_reverse_proxy_rule.md` env vars | Quân | P4 | Requires admin config (file or env load) |
| Feign call `/users/me/profile` read-only transformations need private access logs (no fields) | New field/missing capture | Maybe `backend_rule.md` logging needs update | Ngoc | P2 | Logs their content for admin |

---

## Mục G — Relationships with Codex / Anti PR Branches

This is documentation-only; no code-level interactions observed. However the sync reveals technical debts tracked in feature branches:

1. **PR #30 (fix/community-private-discovery)** – Verified strict privacy policy (owner block, write 403→404). Changes governance reflected in `domain_system.md`, admin API contracts.
2. **PR #3 (fix/admin-mvp-hardening)** – Admin RBAC, CSRF Bypass, visibility race condition hardening. Auth rule "csrf_exempt for Bearer" derived from there.
3. **Bằng’s worktree ALOUTE_ANTI-Q-FE-TASK** – May reference `account_suspensions` and `user_suspension` spec that could inform ban/lock feature in future frontend updates.
4. **Flutter mobile (`aloute_mobile/`)** – New client using REST APIs, Set-Cookie auth, authVersion revocation, supplier config discrepancies (login body padding, cookieJar intra). Flutter handoff doc serves handoff to mobile team for ambassador.
5. **Hooks/Triggers (Ngọc’s responsibility)** – Domain system still declares triggers/stored procedures for computed columns (e.g., `lastModified`, `followerCount`). Right now only Java service counters manage aggregations. This is active technical debt notice (see Table in Mục F).

---

## Mục H — Preview Examples of Key Changes

### 1. Backend Rule §97.1 Actual Rigid State (Short)

```
# 97.1 STATẬT THỰC TẾ TRÊN MASTER (2026-10-20)
MONGAI ALOUTE hiện tại không sử dụng thay đổi vĩ mô (Flyway/Liquibase):
- pom.xml không chứa dependency migration tool.
- Hibernate ddl-auto=update: kích hoạt trong cả dev và prod.
- db/ chứa SQL scripts (schema.sql, trigge
