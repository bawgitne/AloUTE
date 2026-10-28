# 14 — BL-22 Backend Handoff (cho backend reviewer)

**Ticket:** BL-22 — page route trả JSON envelope thay vì HTML/redirect cho guest, non-admin và 404.
**Mức:** P1 · **Trạng thái QA:** FAIL (4 case) · **Ngày xác minh:** 2026-10-28
**Phạm vi:** chỉ báo cáo; QA **không sửa backend/security**. Đã xác minh trên QA branch (`74c8a6f` + frontend QA) và ZCode HEAD `e12b21b`; cả hai đều FAIL.

## 1. Tóm tắt

| Case | Vai trò | Route | Mong đợi | Thực tế | Bằng chứng |
|---|---|---|---|---|---|
| AUTH-15 | Guest | `GET /post/create` | `302 Location: /login` (hoặc 303) | `401 application/json`, `AUTH_UNAUTHENTICATED` | ảnh `evidence/screenshots/auth-15-guest-post-create.png`, `evidence/full-run-3-results.json` |
| AUTH-16 | Guest | `GET /admin` | `302 /login` **hoặc** `403 text/html` | `401 application/json`, `AUTH_UNAUTHENTICATED` | ảnh `evidence/screenshots/auth-16-guest-admin.png`, `evidence/full-run-3-results.json` |
| ADM-05 | User thường | `GET /admin` | redirect (feed/login) **hoặc** `403 text/html` | `403 application/json`, `AUTH_FORBIDDEN` | `evidence/full-run-4-results.json` |
| RES-02 | User đã đăng nhập | `GET /khong-ton-tai-*` | `404 text/html` với `#error-content` và link `/feed` | `404 application/json`, `RESOURCE_NOT_FOUND` | `evidence/full-run-1-results.json` |

API routes (`/api/**`) **không thuộc phạm vi lỗi**: chúng phải tiếp tục trả JSON envelope. Chỉ page route (`text/html`) bị sai content negotiation.

## 2. Tái hiện chính xác

Chuẩn bị: server local profile `dev`, H2, seed `tools/demo-data.mjs`, tài khoản seed local (không ghi credential vào ticket).

```bash
BASE=http://127.0.0.1:8082
HTML_ACCEPT='Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8'

# AUTH-15 / AUTH-16 — guest, không cookie
curl -si -H "$HTML_ACCEPT" "$BASE/post/create"
curl -si -H "$HTML_ACCEPT" "$BASE/admin"

# ADM-05 / RES-02 — đăng nhập user thường rồi dùng cookie jar
curl -s -c /tmp/aloute-cookies.txt -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d "{\"email\":\"$ALOUTE_USER_EMAIL\",\"password\":\"$ALOUTE_USER_PASSWORD\"}" \
  "$BASE/api/auth/login"
curl -si -b /tmp/aloute-cookies.txt -H "$HTML_ACCEPT" "$BASE/admin"
curl -si -b /tmp/aloute-cookies.txt -H "$HTML_ACCEPT" "$BASE/khong-ton-tai-bl22"
```

Quan sát ngày 2026-10-28:

| Route | Status | Content-Type | Body (rút gọn) |
|---|---|---|---|
| `/post/create` (guest) | 401 | `application/json` | `{"success":false,"error":{"code":"AUTH_UNAUTHENTICATED","message":"Authentication required"}}` |
| `/admin` (guest) | 401 | `application/json` | `{"success":false,"error":{"code":"AUTH_UNAUTHENTICATED","message":"Authentication required or credential invalid"}}` |
| `/admin` (user) | 403 | `application/json` | `{"success":false,"error":{"code":"AUTH_FORBIDDEN","message":"Operation is not permitted"}}` |
| `/khong-ton-tai-bl22` (user) | 404 | `application/json` | `{"success":false,"error":{"code":"RESOURCE_NOT_FOUND","message":"Resource not found"}}` |

Browser/Playwright gửi đầy đủ `Accept` của Chrome và vẫn nhận JSON; đây không phải lỗi do curl thiếu header.

## 3. Manh mối root cause (chỉ đọc, không sửa)

- `src/main/java/com/aloute/security/handler/AuthenticationFailureHandler.java` — luôn set `APPLICATION_JSON` và ghi `ErrorResponse`, không phân biệt page/API.
- `src/main/java/com/aloute/security/handler/AccessDeniedHandler.java` — luôn set `APPLICATION_JSON` cho 403.
- `src/main/java/com/aloute/controller/page/PostPageController.java` (`create`) gọi `currentUserProvider.requireUserId()` trực tiếp; `SecurityContextCurrentUserProvider.requireUserId()` ném `ApiException(401)` → `GlobalExceptionHandler` trả JSON.
- `src/main/java/com/aloute/common/exception/GlobalExceptionHandler.java` (`handleNoResourceFound`) bắt `NoResourceFoundException` và trả `ResponseEntity<ErrorResponse>` JSON cho mọi request, kể cả page.
- `SecurityConfig` gắn cùng entry point/access denied handler cho toàn bộ filter chain; không có nhánh page vs `/api/**`, không đọc `Accept` hay `DispatcherType.ERROR`.

## 4. Tiêu chí acceptance cho fix backend

1. Guest mở page bảo vệ (`/post/create`, `/admin`) nhận `302/303` tới `/login` (hoặc `403 text/html`), không thấy JSON thô.
2. User thường mở `/admin` nhận trang 403 HTML hoặc redirect; không lộ envelope JSON.
3. Route không tồn tại khi đã đăng nhập nhận `404 text/html` có `#error-content`, tiêu đề 404 và link về `/feed`.
4. `/api/**` giữ nguyên JSON 401/403/404 và mã lỗi hiện tại.
5. Không nới quyền, không bỏ CSRF, không đổi JWT/session; API contract không đổi.
6. Bốn case AUTH-15, AUTH-16, ADM-05, RES-02 PASS khi QA rerun; các case API 401/403/404 (AUTH-07, ADM-04, RES-03) vẫn PASS.

## 5. Test & bằng chứng

```bash
cd test/e2e/quan-acceptance
BASE_URL=http://127.0.0.1:8082 OUT_DIR=<run> node run.mjs --suite auth-guest,messaging-admin,resilience
```

- `test/e2e/quan-acceptance/suites/auth-guest.mjs` — AUTH-15/16 (AUTH-16 chấp nhận redirect login **hoặc** 403 HTML).
- `test/e2e/quan-acceptance/suites/messaging-admin.mjs` — ADM-05 (chấp nhận redirect **hoặc** 200/403 HTML; FAIL nếu JSON).
- `test/e2e/quan-acceptance/suites/resilience.mjs` — RES-02 (404 HTML + link feed).
- JSON: `evidence/full-run-1-results.json`, `full-run-3-results.json`, `full-run-4-results.json`, `final-verification-results.json`, `integration-e12b21b-results.json`.
- Ảnh: `evidence/screenshots/auth-15-guest-post-create.png`, `auth-16-guest-admin.png`.

**Không sửa** các file trên trong QA branch; backend reviewer sở hữu fix và cần giữ nguyên test contract.
