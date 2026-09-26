# Workspace Rule: Read Skills Before Implementing

Every agent must follow the canonical skills structure defined in the `skills/` directory:

1. Always inspect `skills/INDEX.md` first to identify the owner file.
2. Read the designated owner file before implementing code:
   - `skills/domain_system.md` for business rules, entities, and database schema.
   - `skills/system_architecture.md` for API envelope, pagination, error handling.
   - `skills/aloute-openapi.yaml` for exact HTTP endpoints and schemas.
   - `skills/backend_rule.md` for Spring Boot architecture, service layer, repositories, JPA.
   - `skills/author_rule.md` for Spring Security, JWT, authentication, authorization.
   - `skills/frontend_rule.md` for Thymeleaf, Bootstrap, client JavaScript.
   - `skills/docker_reverse_proxy_rule.md` for Docker and Nginx reverse proxy.
3. Obey the principle: ONE RULE → ONE OWNER → ONE SOURCE OF TRUTH.
