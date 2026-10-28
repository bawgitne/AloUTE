# 01 — Acceptance Summary

**Ngày:** 2026-10-28 · **Branch QA:** `test/quan-e2e-acceptance` · **Base:** `74c8a6f`

> **Cập nhật sign-off cuối (sau baseline dưới đây):** chạy lại toàn bộ 165 case trên worktree tích hợp cô lập `25e7595` (16 PR HEAD mới nhất, gồm BL-22 #88) + patch fixture poll — **165/165 ID, 159 PASS / 0 FAIL / 3 BLOCKED / 3 NOT RUN**, `RUN-INTEGRITY` không lệch. BL-22 (AUTH-15/16, ADM-05, RES-02) PASS. Bằng chứng: `evidence/final-signoff-integration-25e7595-results.json`. Các mục 1–3 dưới đây giữ nguyên số liệu lịch sử baseline `4d00b6a` (155/4/3/3) để truy vết; chi tiết sign-off ở mục 6.

## 1. Kết luận ngắn

Frontend AloUTE đã được kiểm thử end-to-end bằng browser thật trên 6 module chính. **155/165 case PASS (93,9%)**. Toàn bộ 4 case FAIL đều cùng một lỗi backend **BL-22** (page route trả JSON thay vì HTML/redirect), không phải lỗi UI. Sau khi sửa 13 lỗi frontend/accessibility, axe không còn vi phạm serious/critical trên 20 route; responsive và hiệu năng local đạt ngân sách; touch target 44px được kiểm tra cả mobile và desktop; 3 engine browser PASS, Firefox bị chặn bởi môi trường.

**Trạng thái acceptance (baseline `4d00b6a`): PASS WITH LIMITATIONS cho phạm vi frontend.** Trên integration `25e7595`, BL-22 đã PASS và kết quả là **159/0/3/3**; production vẫn **NOT READY** do Firefox/PostgreSQL/tải thật/CDN chưa xác nhận và còn 3 BLOCKED + 3 NOT RUN.

Đã chạy lại toàn bộ 165 case trên integration HEAD `e12b21b` với đúng 23 thay đổi frontend QA: kết quả **155/4/3/3, 0 khác biệt trạng thái** so với baseline; BL-22 khi đó vẫn mở (đã PASS trên `25e7595`). Chi tiết `12_FINAL_HANDOFF.md` và mục 9.

Run cuối `final-verification-20261028` trên worktree handoff: **165/165 ID duy nhất, 155 PASS / 4 FAIL (đúng BL-22) / 3 BLOCKED / 3 NOT RUN**, guard `RUN-INTEGRITY` PASS; bằng chứng `evidence/final-verification-results.json`.

## 2. Con số

| Chỉ số | Giá trị |
|---|---|
| Tổng case | **165** |
| PASS | **155** |
| FAIL | **4** (AUTH-15, AUTH-16, ADM-05, RES-02 — BL-22) |
| BLOCKED | **3** (SOC-P06 Firebase, COM-14b đặc tả, XB-03 Firefox) |
| NOT RUN | **3** (AUTH-26b 2FA, SOC-P47 poll hết hạn, SOC-P52 custom feed) |

Ghi chú: `full-run-1` thô có 5 FAIL (4 BL-22 + MSG-07 flake locator); MSG-07 đã được sửa test và rerun `full-run-4` PASS. Bảng trên là kết quả hợp nhất cuối, không phải nguyên trạng một lần chạy.
| Kịch bản E2E | 7 (A–G), đã chạy thực tế |
| Route quét axe | 20, 0 vi phạm serious/critical |
| Viewport | 360×800, 768×1024, 1440×900 |
| Engine | Chrome 153, Edge 156, WebKit 27.2 PASS; Firefox 157 BLOCKED |
| Lỗi UI phát hiện | 15 (13 đã sửa, 1 backend ticket, 1 hạ tầng không sửa) |
| Tệp thay đổi | 23 (3 CSS, 4 JS, 16 template) |

## 3. Kết quả theo module

| Module | Case | Kết quả | Ghi chú |
|---|---|---|---|
| Auth & tài khoản | 27 | 24 PASS / 2 FAIL / 1 NOT RUN | BL-22 ở route bảo vệ; 2FA setup ngoài contract |
| Social / bài viết | 36 | 33 PASS / 1 BLOCKED / 2 NOT RUN | Upload Firebase; poll hết hạn; custom feed stub |
| Community | 21 | 20 PASS / 1 BLOCKED | Private join chưa có đặc tả/API |
| Messaging + Notification | 22 | 22 PASS | Realtime, XSS, unread, filter đều đạt |
| Admin | 13 | 12 PASS / 1 FAIL | ADM-05 BL-22 |
| Resilience | 11 | 10 PASS / 1 FAIL | RES-02 BL-22 |
| Chất lượng (a11y/responsive/browser/perf) | 35 | 34 PASS / 1 BLOCKED | Firefox crash môi trường |

## 4. Phát hiện chính

### Đã sửa (frontend)
- **WCAG 2.5.3**: tên truy cập của nút like/comment/repost/quote/pagination/navbar lệch nội dung hiển thị.
- **Tương phản**: 7 nhóm màu dưới 4.5:1 (muted text, badge subtle, nav pill active, outline button, badge/nút admin).
- **ARIA**: icon thiếu `role="img"`; `role="feed"`/`role="tablist"` sai ngữ nghĩa; logout nằm trong tablist settings.
- **Lỗi chức năng**: messaging lộ lỗi tiếng Anh/`TypeError`, community không map mã lỗi slug trùng, form auth thiếu `method="post"`.
- **Layout/auth**: skip link + `main#main-content` cho trang auth.

### Chưa sửa (có lý do)
- **BL-22** — cần backend/security entry point: 4 case FAIL.
- **Firebase Storage** — thiếu service account môi trường: upload BLOCKED.
- **Private community join** — cần quyết định sản phẩm + endpoint.
- **CDN/ETag** — thay đổi hạ tầng, ngoài phạm vi frontend an toàn.

## 5. Chất lượng phi chức năng

| Hạng mục | Kết quả | Bằng chứng |
|---|---|---|
| Accessibility (axe WCAG 2.0/2.1/2.2 AA) | 0 vi phạm serious/critical; 20 route | `cross-run-2/axe-results.json` |
| Keyboard skip link | PASS | A11Y-22 |
| Responsive | Không tràn ngang 360/768/1440; touch target ≥44 px (navbar, interaction, guest CTA, sidebar) | RESP-01..06 |
| Cross-browser | Chrome/Edge/WebKit PASS; Firefox BLOCKED | XB-01/02/04/03 |
| Feed performance | DCL median 125 ms; LCP 140–168 ms | PERF-01 |
| API latency | p95 8–29 ms (local H2) | PERF-02 |
| Static CSS | 200, `text/css`, cache 12h, không ETag | PERF-03 |
| XSS | Bài viết, tin nhắn, notification preview đều escape | SOC-P21, MSG-07, NTF-09 |
| CSRF / session | Thiếu CSRF bị 403; JWT sửa/hết hạn bị từ chối | AUTH-12/13/24, RES-05 |

## 6. Gate acceptance

| Gate | Ngưỡng | Kết quả |
|---|---|---|
| Luồng critical chạy được end-to-end | 100% | Đạt, trừ route bảo vệ của guest/non-admin (BL-22) |
| Không lỗi console trên luồng chính | 0 | Đạt (MSG-13 + `assertClean`) |
| Axe serious/critical | 0 | Đạt |
| Responsive 3 breakpoint | Không tràn ngang | Đạt |
| Cross-browser | ≥3 engine | Đạt 3/4; Firefox chưa kết luận |
| Hiệu năng local | DCL < 3s, API p95 < 800ms | Đạt |
| Không rò rỉ secret/PII | 0 | Đạt trong phạm vi kiểm tra |
| Dọn dữ liệu test | Không sót bài/cộng đồng QA | Đạt (SOC-P90, COM-90, CLEAN-01) |
| Tích hợp integration `e12b21b` | Không regression so với baseline | Đạt — 0 khác biệt trạng thái; BL-22 vẫn FAIL |

## 7. Rủi ro còn lại

1. **BL-22 (P1)** — người dùng gặp JSON thô khi chưa đăng nhập/không đủ quyền; ảnh hưởng trải nghiệm và kỳ vọng bảo mật.
2. **Firebase upload (P2)** — không thể demo avatar/cover/ảnh nếu môi trường thiếu service account.
3. **Private community (P2)** — chưa có đặc tả; không thể acceptance.
4. **Firefox (P3)** — chưa xác nhận tương thích.
5. **CDN + cache không ETag (P3)** — rủi ro giao diện khi mạng nội bộ chặn CDN; asset cũ tồn tại 12h.
6. **Dữ liệu/DB production (P2)** — toàn bộ hiệu năng và hành vi dựa trên H2 seed nhỏ.

## 8. Khuyến nghị

- BL-22 đã được xác minh PASS trên integration `25e7595` (fix #88, đã merge vào `master`). PR #90 đã retarget về `master` nên base chứa #88; giữ gate rerun 4 case khi rebase trong tương lai.
- Cấp Firebase service account cho môi trường demo; chốt đặc tả private community.
- Chạy lại cross-browser trên Firefox ESR/container Linux.
- Đo lại hiệu năng với PostgreSQL + dữ liệu lớn trước khi cam kết SLO.
- Dùng `10_DEMO_SCRIPT_VI.md` cho demo; tránh đường dẫn bảo vệ khi là guest và tránh upload nếu thiếu Firebase.

## 9. Final sign-off run — integration `25e7595` (2026-10-28)

### 9.1 Bối cảnh

- Worktree cô lập **detached** `<sign-off worktree>` tại `25e7595` (merge 16 PR HEAD mới nhất, gồm #88 BL-22, #86 prod-seed guard, #84 auth follow-ups).
- Chỉ áp thay đổi test `test/e2e/quan-acceptance/suites/social.mjs` (+111 / −47); **không** sửa mã sản phẩm.
- Server `http://127.0.0.1:8086`, profile `dev`, H2 in-memory **mới**, seed `DevTestDataInitializer` + `tools/demo-data.mjs` (18 OK). Không dùng production DB/credential.

### 9.2 Root cause 3 FAIL poll và cách sửa

| Mục | Chi tiết |
|---|---|
| Triệu chứng | SOC-P40 FAIL "Bài seed 4 không có poll"; SOC-P42/P43 FAIL `Cannot read properties of null`; SOC-P41 BLOCKED do không có fixture |
| Nguyên nhân | Test giả định post ID 4 luôn là poll của mock data Aiven. H2 dev chỉ seed tài khoản/chủ đề; poll do `demo-data.mjs` tạo có ID phụ thuộc số bài đã tồn tại trước đó. Khi thứ tự/ID lệch, post 4 không còn là poll |
| Bản chất | **Lỗi fixture**, không phải lỗi sản phẩm: cùng DB lệch ID, SOC-P44/45/46 (tạo poll qua API) vẫn PASS |
| Sửa | Mỗi case SOC-P40/41/42/43 tự tạo poll qua API, xác minh lại bằng `GET /api/posts/{id}`, kiểm tra chưa có vote, cleanup bài trong `finally`; bỏ `globalThis.__qaPoll` và mọi phụ thuộc thứ tự suite. Cleanup được kiểm tra (204) và ghi vào `detail.fixtureCleanup`; lỗi cleanup được báo là `FIXTURE CLEANUP`, không lẫn với lỗi chức năng |
| Không thay đổi | Assertion nghiệp vụ giữ nguyên/được tăng cường (P41 xác minh thêm `totalVotes` không đổi; P42/P43 xác minh không ghi vote) |

### 9.3 Kết quả

| Run | Môi trường | Kết quả |
|---|---|---|
| `baseline-fixture-repro-20261028` | H2 mới + 1 bài tạo trước seed (poll id=5) | 29 PASS / **3 FAIL** (SOC-P40/42/43) / 2 BLOCKED (SOC-P06, SOC-P41) / 2 NOT RUN |
| `fixed-social-shifted-db-20261028` | Cùng DB lệch ID + test đã sửa | 33 PASS / **0 FAIL** / 1 BLOCKED (SOC-P06) / 2 NOT RUN |
| `final-signoff-20261028-r2` | H2 mới, seed chuẩn, full 6 suite | **159 PASS / 0 FAIL / 3 BLOCKED / 3 NOT RUN — 165/165 ID** |

Theo suite ở run sign-off: auth 26/0/0/1 · social 33/0/1/2 · community 20/0/1/0 · messaging-admin 35/0/0/0 · cross-a11y 34/0/1/0 · resilience 11/0/0/0.

BL-22 trên `25e7595`: AUTH-15 PASS (302 → `/login`), AUTH-16 PASS (302 → `/login`), ADM-05 PASS (`403 text/html`), RES-02 PASS (`404` HTML + heading). API `/api/**` vẫn trả JSON đúng contract (AUTH-07, ADM-04, RES-03 PASS).

### 9.4 Bằng chứng

- `evidence/final-signoff-integration-25e7595-results.json`
- `evidence/final-signoff-integration-25e7595-environment.json`
- `evidence/baseline-fixture-repro-old-suite-results.json`
- `evidence/fixed-poll-fixture-social-results.json`
- `evidence/screenshots/social-poll-result-final-signoff.png`, `evidence/screenshots/res-02-404-final-signoff.png`
- Node `node --test test/frontend/*.test.mjs`: **396/396 PASS**.
- Maven full suite: xem mục 9.5 (cập nhật sau khi chạy).

### 9.5 Maven

| Chỉ số | Giá trị |
|---|---|
| Lệnh | `mvn -q test` trên worktree `<sign-off worktree>` (25e7595 + patch test) |
| Kết quả | **723 tests, 0 failures, 0 errors, 9 skipped**; exit code 0 |
| Ghi chú | Khớp số liệu integration (723/9 skipped); không chạy IT/container/PostgreSQL |

### 9.6 CI

| Chỉ số | Giá trị |
|---|---|
| Workflow | `CI` / job `test-and-build` trên PR #90 |
| Commit | `25a804e1f47f1f985a1b511bc3ab793f411074e0` |
| Kết quả | **PASS** — run `40632713237`, 3m37s |
| Base PR | `master` (retargeted after PR #85 merged as `cd0d85c`) |
