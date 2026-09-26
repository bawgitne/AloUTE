# CODEX.md — Guidelines for OpenAI Codex & Assistants in ALOUTE

> **CRITICAL**: Before generating, modifying, or reviewing any code, read the relevant specification files in `skills/`.
> Core Principle: **ONE RULE → ONE OWNER → ONE SOURCE OF TRUTH**.

## Step-by-Step Workflow

1. Consult `skills/INDEX.md` first to identify which skill document owns the requirement.
2. Read the designated owner document in `skills/`:
   - `skills/domain_system.md`: Domain model, entities, business logic, DDL, constraints, permissions.
   - `skills/system_architecture.md`: Response structure (`ApiResponse`), error codes, HTTP statuses, paging.
   - `skills/aloute-openapi.yaml`: Canonical OpenAPI specifications (routes, schemas, methods).
   - `skills/backend_rule.md`: Spring Boot backend standards, controllers, services, repositories, transactions, JPA.
   - `skills/author_rule.md`: Spring Security, JWT tokens, authentication, authorization.
   - `skills/frontend_rule.md`: Frontend UI, Thymeleaf templates, Bootstrap styling, vanilla JS.
   - `skills/docker_reverse_proxy_rule.md`: Docker containerization and Nginx reverse proxy configuration.
3. Never bypass OpenAPI or invent arbitrary REST responses or routes.
