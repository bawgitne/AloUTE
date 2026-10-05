# GEMINI.md — Instructions for Google Antigravity & Gemini in ALOUTE

> **BẮT BUỘC ĐỐI VỚI ANTIGRAVITY & GEMINI**:
> Luôn đọc và tuân thủ các quy tắc trong thư mục `skills/` trước khi trả lời câu hỏi, phân tích hoặc tạo/chỉnh sửa code.
> Nguyên tắc: **ONE RULE → ONE OWNER → ONE SOURCE OF TRUTH**.

---

## QUY TRÌNH THỰC HIỆN

1. Đọc [skills/INDEX.md](skills/INDEX.md) đầu tiên để xác định file owner cho tác vụ đang làm.
2. Đọc file owner tương ứng trong `skills/`:
   - Domain logic, permissions, DB Schema: [skills/domain_system.md](skills/domain_system.md)
   - API envelope, ErrorCode, Pagination, HTTP Status: [skills/system_architecture.md](skills/system_architecture.md)
   - HTTP Contract (Paths, DTOs, schemas): [skills/aloute-openapi.yaml](skills/aloute-openapi.yaml)
   - Spring Boot Backend conventions: [skills/backend_rule.md](skills/backend_rule.md)
   - Spring Security & JWT: [skills/author_rule.md](skills/author_rule.md)
   - Thymeleaf & Frontend JS: [skills/frontend_rule.md](skills/frontend_rule.md)
   - Docker & Nginx Reverse Proxy: [skills/docker_reverse_proxy_rule.md](skills/docker_reverse_proxy_rule.md)
3. Luôn tuân theo `skills/aloute-openapi.yaml` khi làm việc với API; không tự chế endpoint.
