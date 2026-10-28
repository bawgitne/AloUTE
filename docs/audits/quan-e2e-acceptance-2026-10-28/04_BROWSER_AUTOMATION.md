# 04 — Browser Automation (Playwright E2E)

## 1. Vị trí và công nghệ

| Thành phần | Giá trị |
|---|---|
| Thư mục | `test/e2e/quan-acceptance/` |
| Runtime | Node.js v24.x |
| Browser automation | Playwright 1.64.1 |
| Engine chính | Chrome 153.0.8010.53 (channel `chrome`, headless) |
| Engine phụ | Edge 156.0.4314.50, WebKit 27.2 |
| Accessibility | axe-core 4.10.2 (nhúng từ `node_modules`) |
| Base URL | `http://127.0.0.1:8082` (đổi bằng `BASE_URL`) |
| Báo cáo | `test-results/quan-e2e-acceptance/<run>/results.json` + screenshots |

Không dùng mock API, không dùng jsdom: mọi case chạy trên server Spring Boot thật với H2 in-memory và tài khoản seed local.

## 2. Cấu trúc

```
test/e2e/quan-acceptance/
├── run.mjs                 # runner: chọn suite, tạo OUT, tổng hợp counts
├── package.json            # playwright + axe-core, scripts test:*
├── lib/harness.mjs         # login, guest, open, api, diagnostics, assert, record, screenshots
├── suites/
│   ├── auth-guest.mjs      # 27 case: guest, auth, OTP, CSRF, 2FA, JS-off
│   ├── social.mjs          # 36 case: profile, post, like/bookmark/repost/quote, poll, feed
│   ├── community.mjs       # 21 case: tạo/quản lý/member/moderation/private
│   ├── messaging-admin.mjs # 35 case: messaging realtime, notification, admin
│   ├── cross-a11y.mjs      # 35 case: axe, keyboard, responsive, 4 engine, performance
│   └── resilience.mjs      # 11 case: 404/401/403, mất mạng, CSRF, validation
└── tools/                  # probe chẩn đoán (axe theo route, head, browser engine)
```

Tổng: **165 case** (27 + 36 + 21 + 35 + 35 + 11).

## 3. Cách chạy

Điều kiện:
1. Server chạy ở `127.0.0.1:8082` với profile `dev` và dữ liệu seed.
2. Biến môi trường tài khoản local (không ghi trong tài liệu này; lấy từ `docs/DEMO_CHECKLIST_VI.md`):
   `ALOUTE_ADMIN_EMAIL/PASSWORD`, `ALOUTE_USER_EMAIL/PASSWORD`, `ALOUTE_DEMOAN_EMAIL/PASSWORD`, `ALOUTE_DEMOBINH_EMAIL/PASSWORD`.
3. `ALOUTE_JWT_SECRET_B64` nếu chạy case JWT hết hạn (AUTH-13); thiếu biến này case bị BLOCKED, không FAIL.

Lệnh:

```bash
cd test/e2e/quan-acceptance

# toàn bộ
OUT_DIR=../../../test-results/quan-e2e-acceptance/run-local node run.mjs

# từng suite
node run.mjs --suite auth-guest
node run.mjs --suite social
node run.mjs --suite community
node run.mjs --suite messaging-admin
node run.mjs --suite cross-a11y
node run.mjs --suite resilience

# nhiều suite
node run.mjs --suite auth-guest,messaging-admin
```

Biến tùy chọn: `BASE_URL`, `BROWSER_CHANNEL` (mặc định `chrome`), `HEADED=1`, `OUT_DIR`.

## 4. Thiết kế harness

- `login(browser, who)` — tạo context riêng, POST `/api/auth/login`, xác nhận `/api/auth/me`, trả `{ context, page, me }`. Mỗi case dùng phiên riêng, đóng trong `finally`.
- `guest()` — context không đăng nhập, dùng cho kiểm tra chặn truy cập.
- `api(session, method, route, data)` — gọi API bằng cookie của context, trả `{ status, body, headers, text }`; dùng để setup/verify/cleanup.
- `diagnostics(page)` — bắt `console.error`, `pageerror`, request failed; `assertClean()` chặn case nếu có lỗi JS.
- `test()` / `record()` — ghi PASS/FAIL/BLOCKED/NOT RUN kèm `detail`, `evidence`, timestamp; không dừng cả suite khi một case fail.
- `screenshot(page, name)` — lưu PNG vào `OUT/screenshots/`.
- `assert` / `assertEqual` — fail sớm với thông báo tiếng Việt.

Quy ước:
- Selector ưu tiên `id`, `data-action`, `aria-label`, lớp Bootstrap ổn định; tránh phụ thuộc text động.
- Mọi mutation qua UI đều được xác nhận lại qua API hoặc reload.
- Response API được kiểm tra envelope `{ success, data, error }`; không chấp nhận HTML thay JSON ở API.
- Cleanup: social/community tạo dữ liệu qua API rồi xóa trong `finally`; messaging/admin dọn bài QA.

## 5. Ma trận engine

| Engine | Case | Kết quả |
|---|---|---|
| Chrome 153 | Toàn bộ suite | PASS (trừ 4 FAIL backend BL-22) |
| Edge 156 | XB-02 smoke | PASS |
| WebKit 27.2 | XB-04 smoke | PASS |
| Firefox 157 | XB-03 smoke | BLOCKED — crash môi trường |

## 6. Kết quả các lần chạy chính

| Run | Suite | PASS | FAIL | BLOCKED | NOT RUN | Ghi chú |
|---|---|---|---|---|---|---|
| `full-run-1` | social, community, resilience, cross-a11y (+ auth/messaging trước khi rerun) | 153 | 5 | 4 | 3 | Chạy sau toàn bộ fix UI; lỗi auth-13 do thiếu env, MSG-07 flake locator |
| `full-run-3` | auth-guest, messaging-admin | 57 | 4 | 0 | 1 | Có JWT secret; auth sạch AUTH-13; MSG-07 còn flake |
| `full-run-4` | messaging-admin | 34 | 1 | 0 | 0 | Sau khi sửa locator MSG-07; chỉ còn ADM-05 (BL-22) |
| `cross-run-2` | cross-a11y | 34 | 0 | 1 | 0 | Axe 0 vi phạm, Firefox BLOCKED |
| `run-auth4` | auth-guest | 24 | 2 | 0 | 1 | Baseline trước regression |
| `run-social3` | social | 33 | 0 | 1 | 2 | Baseline |
| `run-community3` | community | 20 | 0 | 1 | 0 | Baseline |
| `run-resilience-2` | resilience | 10 | 1 | 0 | 0 | RES-02 BL-22 |

Kết quả hợp nhất cuối (best run mỗi suite, cùng code): **155 PASS / 4 FAIL / 3 BLOCKED / 3 NOT RUN**.

## 7. Flake đã xử lý

**MSG-07** ban đầu dùng `waitBubble(page, '__qaMsgXss')` và `.last()`. Marker này còn xuất hiện trong tin nhắn của lần chạy trước và trong case MSG-04 cùng suite, nên locator có thể trúng bubble cũ trước khi tin mới render → FAIL giả. Đã đổi sang chờ chính xác `payload` đầy đủ (chứa tag duy nhất của lần chạy), sau đó assert `textContent`. Rerun `full-run-4`: PASS.

## 8. Giới hạn

- Chỉ chạy local, chưa nối CI; kết quả phụ thuộc server/H2/seed.
- Không kiểm tra tải đồng thời, không có network throttling, không có mobile browser thật (chỉ viewport giả lập).
- Firefox bị chặn bởi môi trường.
- Upload media, poll hết hạn, custom feed, 2FA setup chưa kiểm thử được (xem `09_BACKEND_DEPENDENCIES.md`).
- Ảnh chụp chỉ là bằng chứng thị giác, không thay thế assertion; ngược lại, PASS của test không thay thế đánh giá thủ công UX.
