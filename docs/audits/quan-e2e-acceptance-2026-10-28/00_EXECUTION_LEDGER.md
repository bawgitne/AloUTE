# 00 — Execution Ledger

- **Mission:** ALOUTE — Independent Full E2E Acceptance Testing, UX Quality & Demo Readiness
- **Ngày:** 2026-10-28 (Asia/Ho_Chi_Minh)
- **QA owner:** Quân (independent QA worktree)
- **Kết luận điều hành:** xem `01_ACCEPTANCE_SUMMARY.md` và `11_ACCEPTANCE_READINESS.md`

## 1. Môi trường

| Thành phần | Giá trị |
|---|---|
| Worktree QA | `D:\PROJECT\cnpm-project\AloUTE-qoder-acceptance` |
| Branch | `test/quan-e2e-acceptance` |
| Base commit | `74c8a6f71be9e08d5fec0177fec2e86eb6daa6e9` (`fix(ui): harden guest experience and accessibility`) |
| Upstream tham chiếu | `AloUTE` (branch `fix/quan-frontend-hardening-20261027`) — cùng commit nền |
| ZCode worktree | `D:\PROJECT\cnpm-project\AloUTE-zc-release` (branch `fix/release-integration-20261028`) — **không sửa, không checkout**; kiểm thử tích hợp qua worktree tạm detached tại `e12b21b` |
| OS | Windows 10.0.26200 x64 |
| Java | 21.0.8 |
| Spring Boot | 3.2.5, profile `dev`, H2 in-memory `jdbc:h2:mem:aloutedb` |
| Node | v24.19.0 |
| Playwright | 1.64.1 |
| Chrome | 153.0.8010.53 |
| Edge | 156.0.4314.50 |
| WebKit | 27.2 |
| Firefox | 157.0 — BLOCKED do crash môi trường |
| Server | `http://127.0.0.1:8082`, log `test-results/quan-e2e-acceptance/server-8082.log` |
| Seed | `DevTestDataInitializer` (profile dev) + `tools/demo-data.mjs`; tài khoản local-only lấy từ `docs/DEMO_CHECKLIST_VI.md` |
| Worktree sign-off | `D:\PROJECT\cnpm-project\AloUTE-e2e-signoff-20261028` — **detached tại `25e7595`** (16 PR HEAD mới nhất), chỉ áp patch test poll fixture; không checkout/sửa worktree ZCode |
| Server sign-off | `http://127.0.0.1:8086`, H2 in-memory mới, profile `dev`, seed `tools/demo-data.mjs` 18 OK |

Không sử dụng tài khoản production, không seed lên website live, không in credential/token/OTP trong báo cáo hay ảnh.

## 2. Phạm vi thực hiện

- 6 suite browser thật: auth-guest, social, community, messaging-admin, cross-a11y, resilience.
- **165 case**, mapping tại `02_FULL_FUNCTIONAL_MATRIX.md`.
- 7 kịch bản E2E A–G tại `03_E2E_SCENARIOS.md`.
- 20 route quét axe; 3 viewport; 4 engine; 3 endpoint API đo p95; 3 lần đo feed.
- 17 tài liệu acceptance trong thư mục này (gồm demo 20 phút, demo 8–10 phút, BL-22 handoff, PR stack plan và PR body).

## 3. Dòng thời gian

| Thời điểm (UTC) | Hoạt động | Kết quả / bằng chứng |
|---|---|---|
| 2026-10-28 00:36 | Baseline auth (`run-auth4`) | 24 PASS / 2 FAIL / 1 NOT RUN |
| 00:40 | Baseline social (`run-social3`) | 33 PASS / 1 BLOCKED / 2 NOT RUN |
| 01:03 | Baseline community (`run-community3`) | 20 PASS / 1 BLOCKED |
| 01:25 | Messaging + admin baseline (`run-messaging-admin-3`) | 34 PASS / 1 FAIL |
| 01:27 | Resilience baseline (`run-resilience-2`) | 10 PASS / 1 FAIL |
| 01:43 | Cross-a11y sau khi sửa UI (`cross-run-2`) | 34 PASS / 1 BLOCKED; axe 0 vi phạm |
| 01:44–01:47 | Full regression 6 suite (`full-run-1`) | 153 PASS / 5 FAIL / 4 BLOCKED / 3 NOT RUN |
| 01:49–01:50 | Rerun auth + messaging có JWT secret (`full-run-3`) | Auth 24 PASS / 2 FAIL / 1 NOT RUN; messaging còn flake MSG-07 |
| 01:51 | Rerun messaging sau khi sửa locator (`full-run-4`) | 34 PASS / 1 FAIL (ADM-05) |
| 09:03 | Integration run đầu trên ZCode `e12b21b` | Không hợp lệ: chưa seed demo-data → AUTH-01 FAIL; hủy, không dùng |
| 09:04–09:07 | Seed demo-data + full rerun trên `e12b21b` | 155 PASS / 4 FAIL / 3 BLOCKED / 3 NOT RUN; 0 khác biệt baseline (`integration-e12b21b-results.json`) |
| 09:47–09:52 | Final verification sau handoff (`final-verification-20261028`) | 165/165 ID, 155 PASS / 4 FAIL / 3 BLOCKED / 3 NOT RUN; guard PASS |
| 04:12 (UTC) | Tái hiện lỗi fixture poll trên H2 mới (`baseline-fixture-repro-20261028`) | 3 FAIL (SOC-P40/42/43) + SOC-P41 BLOCKED; 29 PASS trên cùng DB — lỗi test, không phải sản phẩm |
| 04:13 (UTC) | Chạy lại social với fixture tự tạo (`fixed-social-shifted-db-20261028`) | 33 PASS / 0 FAIL / 1 BLOCKED / 2 NOT RUN trên đúng DB lệch ID |
| 04:14–04:17 (UTC) | Sign-off run đầu trên worktree tích hợp `25e7595` + patch fixture (`final-signoff-20261028`) | 165/165 ID, 159 PASS / 0 FAIL / 3 BLOCKED / 3 NOT RUN; dùng để phát hiện 2 điểm cleanup cần siết |
| 04:25–04:29 (UTC) | **Final sign-off rerun** với code test cuối (`final-signoff-20261028-r2`) | **165/165 ID, 159 PASS / 0 FAIL / 3 BLOCKED / 3 NOT RUN**; fixture poll cleanup 204; BL-22 PASS; `RUN-INTEGRITY` không lệch |

`full-run-2` không hợp lệ: thiếu biến môi trường tài khoản seed, 12 case FAIL do không đăng nhập được. Không dùng làm bằng chứng sản phẩm.

## 4. Incident log

| Incident | Ảnh hưởng | Xử lý |
|---|---|---|
| `OUT_DIR` dạng Unix bị MSYS chuyển thành `D:\d\...` | Run `cross-run-1` ghi sai thư mục | Dùng đường dẫn tương đối; artifact ngoài worktree `D:\d` chỉ chứa run lỗi, không dùng làm bằng chứng |
| Firefox `browserContext.newPage: Target crashed` | XB-03 không chạy được | Ghi BLOCKED, không đánh FAIL sản phẩm; thử headed/headless/context tối thiểu đều crash |
| Thiếu `ALOUTE_JWT_SECRET_B64` | AUTH-13 BLOCKED ở `full-run-1` | Rerun với secret dev local (`full-run-3`) → PASS |
| Thiếu biến tài khoản seed | `full-run-2` invalid | Rerun với biến local từ demo checklist |
| MSG-07 flake locator | FAIL giả do bubble cũ cùng marker/tag | Sửa test chờ đúng payload đầy đủ; `full-run-4` PASS |
| `cp` sang `target/classes` lỗi đường dẫn | Thay đổi template không được server nhận | Dùng đường dẫn tuyệt đối; xác minh lại bằng browser sau mỗi lần sync |
| Integration run thiếu demo-data | Run `integration-...-0903` có AUTH-01 FAIL giả | Hủy run, chạy `tools/demo-data.mjs`, rerun `integration-...-0907` |
| Poll fixture hardcode `postId=4` | Trên H2 mới/thứ tự seed khác, post 4 không có poll → SOC-P40 FAIL, SOC-P42/P43 TypeError, SOC-P41 BLOCKED | Xác định là lỗi fixture, không phải sản phẩm. Sửa test: mỗi case tạo poll qua API, xác minh `GET /api/posts/{id}`, cleanup `finally`; bỏ `globalThis.__qaPoll` và phụ thuộc thứ tự. Baseline tái hiện 3 FAIL + 1 BLOCKED; sau sửa 0 FAIL (`evidence/baseline-fixture-repro-old-suite-results.json`, `evidence/fixed-poll-fixture-social-results.json`) |

## 5. Thay đổi mã nguồn trong worktree

- 23 tệp: 3 CSS, 4 JS, 16 template. Tổng +106 / −48 dòng.
- Không thay đổi backend, security, DTO, repository, migration, cấu hình production, route hay API contract.
- Chi tiết từng fix: `08_UI_BUGS_AND_FIXES.md`.
- Sign-off: chỉ sửa `test/e2e/quan-acceptance/suites/social.mjs` (+111 / −47) để tạo poll fixture xác định; không đổi assertion nghiệp vụ, không đổi mã sản phẩm.
- Test bổ sung trong sign-off: Node `node --test test/frontend/*.test.mjs` — **396/396 PASS**; Maven `mvn -q test` — **723 tests, 0 failures, 0 errors, 9 skipped** (exit 0).
- Trạng thái git cuối: xem `12_FINAL_HANDOFF.md`.

## 6. Bằng chứng và tính toàn vẹn

| Loại | Vị trí |
|---|---|
| Kết quả từng case (raw, local) | `test-results/quan-e2e-acceptance/<run>/results.json` — không commit |
| Kết quả case đã curation | `evidence/*-results.json`, `evidence/integration-e12b21b-results.json` |
| Ảnh chụp | `.../<run>/screenshots/*.png` (full-run-1: 66 ảnh) |
| Axe | `cross-run-2/axe-results.json` |
| Fixture tạo/dọn | `social-fixtures.json`, `community-fixtures.json`, `admin-report-fixture.json` |
| Server log (raw, local) | `test-results/quan-e2e-acceptance/server-8082.log` — không commit |
| Seed marker (raw, local) | `test-results/quan-e2e-acceptance/seed-20261028.txt` — không commit |
| Kết quả tích hợp ZCode | `evidence/integration-e12b21b-results.json`, `evidence/integration-e12b21b-environment.json` |
| Kết quả sign-off `25e7595` | `evidence/final-signoff-integration-25e7595-results.json`, `evidence/final-signoff-integration-25e7595-environment.json` |
| A/B fixture poll | `evidence/baseline-fixture-repro-old-suite-results.json`, `evidence/fixed-poll-fixture-social-results.json` |

Không có credential, token, OTP hay nội dung tin nhắn riêng tư trong tài liệu. Tên tài khoản seed chỉ là local (`@aloute.local`, `@aloute.test`).

## 7. Giới hạn của bằng chứng

- Chạy local với H2 in-memory, dữ liệu seed nhỏ; không đại diện production.
- Không có CI, không chạy song song nhiều người dùng.
- Firefox, Firebase, private-community join, poll hết hạn, custom feed, 2FA setup chưa kiểm thử được.
- PASS của automation không thay thế đánh giá thủ công toàn bộ UX; các khoảng trống được liệt kê trong `02` mục 13 và `05` mục 5.
