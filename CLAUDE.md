# CLAUDE.md — AI Agent & Developer Instructions for ALOUTE

> **BẮT BUỘC:** Mọi tác vụ lập trình, thiết kế kiến trúc, sửa lỗi hoặc review code trong dự án ALOUTE **BẮT BUỘC PHẢI ĐỌC THƯ MỤC `skills/` TRƯỚC KHI THỰC HIỆN**.
> Nguyên tắc cốt lõi: **ONE RULE → ONE OWNER → ONE SOURCE OF TRUTH**.

---

## 1. QUY TRÌNH BẮT BUỘC CHO AI AGENTS (Claude / Codex / Antigravity)

Trước khi thực hiện bất kỳ câu hỏi hoặc task code nào:

1. **Bước 1 — Đọc chỉ mục định tuyến:**
   Đọc ngay [skills/INDEX.md](skills/INDEX.md) để biết chủ đề của task thuộc file nào sở hữu (Owner File).
2. **Bước 2 — Đọc file kỹ năng chuyên trách (Owner File):**
   Đọc nội dung file tương ứng trong `skills/` trước khi viết bất kỳ dòng code nào.
3. **Bước 3 — Đối chiếu API Contract (nếu đụng tới HTTP API):**
   Bắt buộc đọc [skills/aloute-openapi.yaml](skills/aloute-openapi.yaml) và [skills/system_architecture.md](skills/system_architecture.md). Tuyệt đối **không tự bịa (invent)** endpoint, request body hay response shape.
4. **Bước 4 — Tuân thủ quyền hạn & Invariant:**
   Bắt buộc kiểm tra [skills/domain_system.md](skills/domain_system.md) để đảm bảo không vi phạm business invariants và quy tắc "WHO CAN DO WHAT".

---

## 2. BẢN ĐỒ ĐỊNH TUYẾN SKILLS (ROUTING TABLE)

| Lĩnh vực / Vấn đề cần làm | Source of Truth File |
| :--- | :--- |
| **Quy tắc đọc, routing, thứ tự ưu tiên** | [`skills/INDEX.md`](skills/INDEX.md) |
| **Domain, Entity, Invariant, Business Flow, Who Can Do What, DB Schema / DDL** | [`skills/domain_system.md`](skills/domain_system.md) |
| **System Boundary, ApiResponse shape, ErrorCode, Pagination, HTTP Status** | [`skills/system_architecture.md`](skills/system_architecture.md) |
| **Exact HTTP Endpoints, Request/Response Schema, Path, Query Params** | [`skills/aloute-openapi.yaml`](skills/aloute-openapi.yaml) |
| **Spring Boot, Controller, Service, Repository, JPA, DTO, Mapper, Transaction** | [`skills/backend_rule.md`](skills/backend_rule.md) |
| **Spring Security, JWT, HttpOnly Cookie, Authentication, Authorization, Principal** | [`skills/author_rule.md`](skills/author_rule.md) |
| **Frontend, Thymeleaf, Bootstrap, Client JavaScript, DOM, Event Handling** | [`skills/frontend_rule.md`](skills/frontend_rule.md) |
| **Docker, Docker Compose, Nginx Reverse Proxy, SSL, VPS Deployment** | [`skills/docker_reverse_proxy_rule.md`](skills/docker_reverse_proxy_rule.md) |

---

## 3. NGUYÊN TẮC BẤT DI BẤT DỊCH (NON-NEGOTIABLE RULES)

- **Không tự chế rule mới:** Nếu rule đã có owner trong `skills/`, file khác chỉ được REFERENCE, CONSUME hoặc IMPLEMENT; không tự định nghĩa lại.
- **ApiResponse chuẩn:** Mọi response API phải tuân thủ format quy định trong `system_architecture.md` (bọc trong `ApiResponse<T>`, mã lỗi chuẩn, pagination zero-based).
- **Backend Layering:** Controller không bao giờ gọi Repository trực tiếp; `@Transactional` đặt ở Service layer; Entity không public setter bừa bãi; Request DTO không nhận `authorId` từ client mà phải lấy từ authenticated Principal.
- **Security:** Business permission (Ai được làm gì) thuộc về Domain (`domain_system.md`), Spring Security (`author_rule.md`) chỉ chịu trách nhiệm authenticate và enforce global authorization.
