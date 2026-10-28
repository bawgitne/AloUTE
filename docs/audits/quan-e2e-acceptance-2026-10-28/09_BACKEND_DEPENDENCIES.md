# 09 — Backend dependencies & ticket bàn giao cho ZCode

Phạm vi QA này **không sửa backend**, không đổi security, DTO, repository, migration hay cấu hình production. Mọi lỗi dưới đây được ghi nhận kèm bằng chứng và hướng reproduce để ZCode xử lý trên nhánh tích hợp.

## Tổng hợp

| Ticket | Mức | Loại | Trạng thái QA | Case liên quan |
|---|---|---|---|---|
| QA-BE-01 | P1 | BL-22 — page route trả JSON 401 cho guest | FAIL (backend) | AUTH-15, AUTH-16 |
| QA-BE-02 | P1 | BL-22 — 404 page route trả JSON cho user | FAIL (backend) | RES-02 |
| QA-BE-03 | P1 | BL-22 — 403 page route trả JSON cho non-admin | FAIL (backend) | ADM-05 |
| QA-BE-04 | P2 | Cộng đồng riêng tư: không có luồng join/invite | BLOCKED (product + backend) | COM-14b |
| QA-BE-05 | P3 | 2FA setup không có trong OpenAPI canonical | NOT RUN | AUTH-26b |
| QA-ENV-01 | P2 | Firebase Storage thiếu service account | BLOCKED (môi trường) | SOC-P06 |
| QA-ENV-02 | P3 | Firefox crash trên máy QA | BLOCKED (môi trường) | XB-03 |
| QA-FEAT-01 | P3 | Poll hết hạn / custom feed chưa kiểm thử được | NOT RUN | SOC-P47, SOC-P52 |

---

## QA-BE-01 — Guest vào trang bảo vệ nhận JSON 401 thay vì chuyển đăng nhập

**Hành vi mong đợi:** điều hướng trình duyệt (Accept ưu tiên `text/html`) tới `/post/create` khi chưa đăng nhập phải trả `302 Location: /login`; `/admin` có thể trả `302 /login` hoặc trang `403 text/html`. Không trả JSON.

**Hành vi thực tế:** HTTP `401`, `Content-Type: application/json`, body envelope lỗi `AUTH_UNAUTHENTICATED`. Trình duyệt hiển thị JSON thô, người dùng không có đường quay lại.

**Reproduce:**
```bash
# Trình duyệt/Playwright với Accept đầy đủ của Chrome
GET http://127.0.0.1:8082/post/create   -> 401 application/json
GET http://127.0.0.1:8082/admin         -> 401 application/json
```
- Bằng chứng: `evidence/full-run-3-results.json`, `evidence/final-verification-results.json` (AUTH-15, AUTH-16)
- Ảnh: `evidence/screenshots/auth-15-guest-post-create.png`, `evidence/screenshots/auth-16-guest-admin.png`
- Test: `test/e2e/quan-acceptance/suites/auth-guest.mjs` (AUTH-15/16)

**Gợi ý hướng xử lý (không thực hiện trong QA):** cấu hình `AuthenticationEntryPoint` theo loại request — API giữ JSON 401; page route (`Accept` có `text/html` hoặc path không thuộc `/api/**`) redirect `/login?next=...`. Kiểm tra lại whitelist và luồng `/post/create` trong `PostPageController.create()` (gọi `requireUserId()` trực tiếp).

---

## QA-BE-02 — 404 cho page route trả JSON khi đã đăng nhập

**Mong đợi:** `/khong-ton-tai-*` trả trang lỗi HTML có tiêu đề 404 và liên kết về `/feed`.

**Thực tế:** HTTP 404 với `Content-Type: application/json`; trang HTML không render; test không tìm thấy `#error-content`.

**Reproduce:** đăng nhập user → mở `/khong-ton-tai-qa` → 404 JSON.
- Bằng chứng: `evidence/full-run-1-results.json`, `evidence/final-verification-results.json` (RES-02); response 404 `application/json` ghi trong `detail.error`
- Test: `suites/resilience.mjs`

**Gợi ý:** content negotiation cho error dispatch; API trả JSON, page trả `error/404` template.

---

## QA-BE-03 — Non-admin vào `/admin` nhận JSON 403

**Mong đợi:** trang 403 HTML (hoặc redirect) với thông báo tiếng Việt và lối về feed.

**Thực tế:** HTTP 403 `application/json`, body `AUTH_FORBIDDEN`; người dùng thường thấy JSON thô.
- Bằng chứng: `evidence/full-run-4-results.json`, `evidence/final-verification-results.json` (ADM-05); response 403 `application/json` ghi trong `detail.error`
- Test: `suites/messaging-admin.mjs`

Cùng gốc rễ với QA-BE-01/02 (entry point/exception handling toàn cục).

---

## QA-BE-04 — Cộng đồng riêng tư không có cơ chế tham gia

**Hiện trạng:** `POST /api/communities/{id}/join` trả 404 cho cộng đồng private; không có API lời mời/join-request trong canonical OpenAPI; UI không có luồng “yêu cầu tham gia”.

**Trạng thái:** **NEEDS PRODUCT DECISION** — chưa thể đánh PASS/FAIL vì chưa có đặc tả. QA đánh BLOCKED (COM-14b).
- Bằng chứng: `evidence/full-run-1-results.json` (COM-14b)
- Đề xuất: chốt mô hình (invite-only hay request/approve), bổ sung endpoint + trạng thái PENDING, sau đó QA bổ sung case.

---

## QA-BE-05 — 2FA setup thiếu trong canonical OpenAPI

**Hiện trạng:** có màn hình/endpoint nội bộ nhưng không nằm trong `aloute-openapi.yaml`; tài khoản seed không bật 2FA. Không thể thiết lập và xác thực đầy đủ.

**Trạng thái:** NOT RUN (AUTH-26b), không phải FAIL.
- Test: `suites/auth-guest.mjs` (AUTH-26b)
- Đề xuất: bổ sung contract 2FA setup/enable/verify + tài khoản seed bật 2FA cho QA.

---

## QA-ENV-01 — Upload avatar/cover bị chặn bởi Firebase Storage

**Hiện trạng:** `serviceAccountKey.json` không có trong môi trường QA local; upload trả lỗi cấu hình. Demo checklist đã ghi rõ đây là phụ thuộc deploy.
- Trạng thái: BLOCKED (SOC-P06), không phải lỗi UI.
- Đề xuất: cấp service account qua secret manager cho môi trường demo; QA bổ sung case upload khi có.

---

## QA-ENV-02 — Firefox crash khi tạo page

**Hiện trạng:** Firefox 157.0 (Playwright 1.64.1) crash `browserContext.newPage: Target crashed` trên máy QA, cả headed/headless; Chromium và WebKit bình thường.
- Trạng thái: BLOCKED (XB-03), không phải lỗi sản phẩm.
- Đề xuất: chạy lại trên container Linux/Firefox ESR trước khi kết luận hỗ trợ Firefox.

---

## QA-FEAT-01 — Hạng mục chưa kiểm thử được trong phạm vi

| Hạng mục | Lý do | Đề xuất |
|---|---|---|
| Poll hết hạn (SOC-P47) | Cần chờ 1–365 ngày hoặc sửa DB; QA không thao tác dữ liệu trực tiếp | Cho phép seed poll đã hết hạn hoặc endpoint test-only |
| Custom Feed đầy đủ (SOC-P52) | `GET /api/feeds` là stub/contract-only, chưa có UI hoàn chỉnh | Chốt phạm vi release; nếu có, bổ sung UI + test |

---

## Không thay đổi trong phạm vi này

- Không sửa `SecurityConfig`, entry point, controller, service, repository, DTO, migration.
- Không sửa `application-prod.yml`, không đổi JWT secret, không đổi CORS/CSRF.
- Các thay đổi frontend chỉ nằm trong template/CSS/JS và đã được kiểm tra lại bằng browser E2E.
