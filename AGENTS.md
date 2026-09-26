# AGENTS.md — Universal AI Agent Guidelines for ALOUTE

> **CRITICAL INSTRUCTION FOR ALL AI CODING AGENTS (Antigravity, Codex, Claude, Cursor, Copilot)**:
> You MUST read the relevant files in the `skills/` directory before proposing, reviewing, or writing any code.
> Core architectural principle: **ONE RULE → ONE OWNER → ONE SOURCE OF TRUTH**.

---

## 1. MANDATORY AGENT WORKFLOW

Whenever tasked with any query, feature implementation, refactoring, or bugfix in this repository:

1. **Step 1 — Read the Master Routing Index**:
   Open and inspect [skills/INDEX.md](skills/INDEX.md). Identify the canonical owner file for the topic.
2. **Step 2 — Read the Owning Skill File**:
   Read the specific rule document in `skills/` before touching code.
3. **Step 3 — Inspect API Contract (for HTTP/REST endpoints)**:
   Always check [skills/aloute-openapi.yaml](skills/aloute-openapi.yaml) and [skills/system_architecture.md](skills/system_architecture.md). Do NOT invent endpoints, parameters, or response formats.
4. **Step 4 — Enforce Business Invariants & Permissions**:
   Verify [skills/domain_system.md](skills/domain_system.md) for "WHO CAN DO WHAT" rules, entity boundaries, and database schema constraints.

---

## 2. SKILL ROUTING TABLE

| Area / Task Type | Canonical Owner File |
| :--- | :--- |
| **Routing, Priorities, Reading Order, Ownership** | [`skills/INDEX.md`](skills/INDEX.md) |
| **Domain Entities, Business Rules, Invariants, Roles, ERD / DDL** | [`skills/domain_system.md`](skills/domain_system.md) |
| **System Boundary, API Response wrapper, Pagination, Status Codes, Errors** | [`skills/system_architecture.md`](skills/system_architecture.md) |
| **Exact HTTP Endpoints, Request/Response Payloads, OpenAPI Contract** | [`skills/aloute-openapi.yaml`](skills/aloute-openapi.yaml) |
| **Spring Boot, Controller, Service, Repository, JPA, DTO, Transactions** | [`skills/backend_rule.md`](skills/backend_rule.md) |
| **Spring Security, JWT, Cookie, Auth Filters, SecurityContext, Principal** | [`skills/author_rule.md`](skills/author_rule.md) |
| **Frontend, Thymeleaf templates, Bootstrap, JavaScript DOM & Events** | [`skills/frontend_rule.md`](skills/frontend_rule.md) |
| **Docker, Docker Compose, Nginx Reverse Proxy, VPS Deployment** | [`skills/docker_reverse_proxy_rule.md`](skills/docker_reverse_proxy_rule.md) |

---

## 3. STRICT CONVENTIONS

- **No Custom Invention**: Never invent new rules, endpoint paths, or custom JSON envelopes when one is defined in `skills/`.
- **API Envelope**: All REST responses must conform to the unified `ApiResponse<T>` structure specified in `system_architecture.md`. Pagination is 0-indexed.
- **Backend Layers**:
  - Controllers only handle HTTP concerns and call Service methods.
  - Services contain business logic and transactional boundaries (`@Transactional`).
  - Repositories are only accessed by Services, never Controllers.
  - `authorId` / `userId` must come from the authenticated Principal, NEVER trusted from request bodies.
- **Security Separation**: Domain rules define permissions ("Who can do what"). Spring Security enforces authentication and global authorization filters.
