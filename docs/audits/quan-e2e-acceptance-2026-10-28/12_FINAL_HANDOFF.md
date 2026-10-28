# 12 — Final Handoff

**Ngày bàn giao:** 2026-10-28 (Asia/Ho_Chi_Minh)
**QA owner:** Quân — independent QA worktree
**Trạng thái:** Hoàn tất phạm vi frontend acceptance; sign-off cuối trên integration `25e7595` đạt **159 PASS / 0 FAIL / 3 BLOCKED / 3 NOT RUN** (BL-22 PASS trên HEAD tích hợp).

## 1. Định danh bàn giao

| Thành phần | Giá trị |
|---|---|
| Worktree QA | `D:\PROJECT\cnpm-project\AloUTE-qoder-acceptance` |
| Branch QA | `test/quan-e2e-acceptance` |
| Base commit | `74c8a6f71be9e08d5fec0177fec2e86eb6daa6e9` |
| Commit bàn giao | `1cae6eb` — `test(qa): add independent E2E acceptance suite and UI hardening` |
| Commit tài liệu cuối | commit kế tiếp trên cùng branch (xem `git log`) — handoff, sửa test/a11y, demo ngắn, BL-22 handoff, PR plan và bằng chứng verification |
| ZCode tham chiếu | branch `fix/release-integration-20261028`, HEAD `e12b21be5b28822c9129436f15fc6604d93fa973`; worktree `AloUTE-zc-release` **không bị sửa** |
| Upstream tham chiếu | `AloUTE` — branch `fix/quan-frontend-hardening-20261027`, cùng base `74c8a6f` |

Không push, không merge, không deploy, không tạo tag.

## 2. Nội dung commit `1cae6eb`

75 tệp, **+8.903 / −48** dòng:

| Nhóm | Số tệp | Nội dung |
|---|---|---|
| `src/main/resources` | 23 | 3 CSS, 4 JS, 16 template — chỉ frontend/accessibility |
| `test/e2e/quan-acceptance` | 15 | harness, 6 suite (165 case), runner, package, 4 probe |
| `docs/audits/quan-e2e-acceptance-2026-10-28` | 37 | 12 báo cáo + 5 JSON + 20 ảnh chứng cứ |

Không thay đổi backend, security, DTO, repository, migration, route, API contract hay cấu hình production. Chi tiết từng lỗi và fix: `08_UI_BUGS_AND_FIXES.md`.

## 3. Kết quả acceptance baseline

| Chỉ số | Giá trị |
|---|---|
| Tổng case | **165** |
| PASS | **155** (93,9%) |
| FAIL | **4** — AUTH-15, AUTH-16, ADM-05, RES-02 (cùng BL-22) |
| BLOCKED | **3** — SOC-P06 Firebase, COM-14b private community, XB-03 Firefox |
| NOT RUN | **3** — AUTH-26b 2FA, SOC-P47 poll hết hạn, SOC-P52 custom feed |
| Axe | 20 route, 0 serious/critical |
| Responsive | 360/768/1440 không tràn ngang |
| Engine | Chrome, Edge, WebKit PASS; Firefox BLOCKED |
| Lỗi UI | 15 phát hiện, 13 đã sửa, 1 ticket backend (BL-22), 1 hạ tầng |
| Touch target | 44×44 px, kiểm tra 360/1440 |

Kết luận: **frontend PASS WITH LIMITATIONS**, demo **READY WITH LIMITATIONS**, release **CONDITIONAL**, production **NOT READY**. Không có tuyên bố production-ready.

### 3.1 Final verification (sau handoff)

| Chỉ số | Giá trị |
|---|---|
| Run | `final-verification-20261028` — chạy lại toàn bộ 6 suite trên worktree cuối |
| Tổng / ID duy nhất | **165 / 165**; guard `RUN-INTEGRITY` PASS |
| PASS / FAIL / BLOCKED / NOT RUN | **155 / 4 / 3 / 3** |
| FAIL | đúng AUTH-15, AUTH-16, ADM-05, RES-02 (BL-22) |
| Bằng chứng | `evidence/final-verification-results.json`, `evidence/final-verification-environment.json` |

Hai run trung gian `final-handoff-20261028` có flake test SOC-P11/SOC-P13 (chờ response like/bookmark chưa đủ); đã sửa test chờ response và rerun social, sau đó chạy lại full. `final-verification` là bằng chứng cuối, không dùng run flake.

## 4. Xác minh tích hợp với ZCode HEAD

### 4.1 Phương pháp (không đụng ZCode)

1. Tạo worktree tạm **detached** tại `e12b21b` — không checkout, không sửa branch/worktree ZCode.
2. Áp đúng patch frontend QA: `git diff 74c8a6f 1cae6eb -- src/main/resources` → **23 tệp, áp sạch, 0 xung đột**. Hai bên không có tệp nào trùng.
3. Build `mvn -q -DskipTests -DskipITs package`; chạy jar profile `dev`, cổng `8083`, H2 in-memory.
4. Seed bằng `node tools/demo-data.mjs http://127.0.0.1:8083` (18 OK, 0 failed).
5. Chạy toàn bộ 6 suite với `BASE_URL=http://127.0.0.1:8083`; đối chiếu từng case với baseline hợp nhất.
6. Dọn worktree tạm, dừng server, xóa biến môi trường tạm.

Delta ZCode so với base: **63 tệp, +2.967 / −326**, gồm các commit `2a1331f`, `0ca3651`, `f0dd572`, merge `ee557f8` và `e12b21b`. Không tệp nào nằm trong 23 tệp QA sửa.

### 4.2 Kết quả

| Chỉ số | Baseline | Trên `e12b21b` + QA patch |
|---|---|---|
| Tổng case | 165 | 165 |
| PASS | 155 | **155** |
| FAIL | 4 | **4** (đúng 4 ID BL-22) |
| BLOCKED | 3 | **3** |
| NOT RUN | 3 | **3** |
| Khác biệt trạng thái từng case | — | **0** |

Theo suite trên `e12b21b`: auth 24/2/1 · social 33/1/2 · community 20/1/0 · messaging-admin 34/1/0 · cross-a11y 34/1/0 · resilience 10/1/0.

Kết luận tích hợp: **QA frontend tương thích ZCode HEAD, không phát sinh regression trong 165 case**. BL-22 vẫn mở; ZCode chưa sửa 4 case này.

Bằng chứng: `evidence/integration-e12b21b-results.json`, `evidence/integration-e12b21b-environment.json` (67 ảnh trong `test-results`, không commit).

### 4.3 Phạm vi tích hợp đã kiểm / chưa kiểm

**Đã kiểm:** toàn bộ 165 case E2E trên mã ZCode `e12b21b` + frontend QA, gồm auth/session/CSRF, feed/post/poll, profile/follow, community, messaging realtime, notification, admin moderation, 404/401/403, mất mạng, accessibility, responsive, 3 engine, hiệu năng local.

**Chưa kiểm (không kết luận):**
- 2FA enrollment/credential isolation của ZCode — AUTH-26b vẫn NOT RUN (ngoài OpenAPI canonical); mới smoke luồng login thường.
- FCM token ownership và push — thiếu Firebase.
- Community media ownership (BL-09) — upload BLOCKED.
- `CustomFeedController` — SOC-P52 NOT RUN (API stub).
- STOMP authorization ngoài happy path — realtime PASS nhưng chưa test destination trái phép.
- Login lockout, password-reset transaction — mới test hành vi UI/API cơ bản, chưa test độ bền transaction.
- Migration H2/MySQL8, ProductionProfileGuard, Dockerfile — không chạy trong môi trường dev/H2 này.
- Private community join, poll hết hạn — vẫn BLOCKED/NOT RUN như baseline.

## 5. Tái lập

```bash
# 1. Server QA (profile dev, H2)
mvn -q -DskipTests package
java -jar target/aloute-app-0.0.1-SNAPSHOT.jar --server.port=8082

# 2. Seed demo (local-only, loopback)
node tools/demo-data.mjs http://127.0.0.1:8082

# 3. Biến môi trường local (không ghi giá trị vào tài liệu)
#    ALOUTE_{ADMIN,USER,DEMOAN,DEMOBINH}_{EMAIL,PASSWORD}
#    ALOUTE_JWT_SECRET_B64 (chỉ cần cho AUTH-13)
cd test/e2e/quan-acceptance
BASE_URL=http://127.0.0.1:8082 OUT_DIR=../../../test-results/quan-e2e-acceptance/run-local node run.mjs
```

Chạy từng suite: `node run.mjs --suite auth-guest` (xem `04_BROWSER_AUTOMATION.md`).

## 6. Bản đồ bằng chứng

| Nội dung | Vị trí |
|---|---|
| Tổng hợp, ma trận, kịch bản | `01`, `02`, `03` |
| Automation, a11y, responsive, performance | `04`, `05`, `06`, `07` |
| Lỗi UI và fix | `08` |
| Dependency backend | `09` |
| Demo script | `10`, `13` (bản 8–10 phút) |
| Readiness | `11` |
| BL-22 backend handoff | `14` |
| PR stack plan / PR body | `15`, `16` |
| Kết quả baseline | `evidence/full-run-1/3/4-results.json`, `cross-run-2-results.json`, `axe-results.json` |
| Kết quả cuối | `evidence/final-verification-results.json`, `evidence/final-verification-environment.json` |
| Kết quả tích hợp | `evidence/integration-e12b21b-results.json`, `evidence/integration-e12b21b-environment.json` |
| Kết quả sign-off `25e7595` | `evidence/final-signoff-integration-25e7595-results.json`, `evidence/final-signoff-integration-25e7595-environment.json` |
| A/B fixture poll | `evidence/baseline-fixture-repro-old-suite-results.json`, `evidence/fixed-poll-fixture-social-results.json` |
| Ảnh | `evidence/screenshots/` (tuyển chọn) |

Không có credential, token, OTP hay tin nhắn riêng tư trong tài liệu/ảnh. Tài khoản seed chỉ là local (`@aloute.local`, `@aloute.test`).

## 7. Việc ZCode cần xử lý

1. **BL-22 (P1) — đã xử lý trên `25e7595`** (fix #88): 4 case AUTH-15/16, ADM-05, RES-02 PASS trong sign-off. Cần giữ #88 trong stack và rerun 4 case sau mỗi lần rebase; PR #90 (base #85) chưa chứa #88 nên CI E2E trên branch PR vẫn có thể thấy 4 FAIL này.
2. **Firebase Storage (P2)** — cấp service account cho môi trường demo để mở khóa upload.
3. **Private community (P2)** — chốt đặc tả + endpoint join.
4. **2FA/custom feed (P3)** — bổ sung contract và test nếu release cần.
5. **Hạ tầng (P3)** — Firefox ESR/container, PostgreSQL/tải thật, CDN/SRI/ETag.

## 8. Giới hạn bàn giao

- Toàn bộ kết quả chạy local H2 + seed nhỏ; không đại diện production.
- Không có CI, không load test, không mobile browser thật.
- PASS automation không thay thế đánh giá thủ công toàn bộ UX.
- Các mục BLOCKED/NOT RUN giữ nguyên trạng thái, không suy diễn PASS.

## 9. Xác nhận tuân thủ

- Không sửa backend, security, migration, DTO, repository, cấu hình production.
- Không đụng worktree/branch ZCode; xác minh ZCode vẫn `e12b21b`, working tree sạch sau kiểm thử.
- Không dùng tài khoản production, không seed lên website live.
- Không commit credential/session/token/artifact; `test-results/` để ngoài git.
- Thời điểm handoff ban đầu: chỉ local commit trên `test/quan-e2e-acceptance`, không push/merge/deploy.
- Sign-off ngày 2026-10-28: commit test fixture + tài liệu được push thường (không force-push) lên `origin/test/quan-e2e-acceptance` theo yêu cầu cập nhật PR #90; không merge/deploy.

## 10. Sign-off cuối — integration `25e7595`

### 10.1 Thay đổi

- `test/e2e/quan-acceptance/suites/social.mjs` (+111 / −47): fixture poll tự tạo qua API, xác minh bằng `GET /api/posts/{id}`, cleanup trong `finally`; bỏ `globalThis.__qaPoll`, `postId=4` và phụ thuộc thứ tự suite.
- Không đổi mã sản phẩm, backend, security, DTO, migration, route hay cấu hình.
- Tài liệu: `00`, `01`, `02`, `04`, `09`, `11`, `12`, `16` cập nhật số liệu sign-off; evidence lịch sử giữ nguyên.

### 10.2 Kết quả

| Hạng mục | Kết quả |
|---|---|
| E2E 165 case | **159 PASS / 0 FAIL / 3 BLOCKED / 3 NOT RUN**; 165 ID duy nhất |
| SOC-P40..P43 | 4/4 PASS; fixture do test tạo, không dùng ID seed |
| BL-22 | 4/4 PASS trên `25e7595` |
| Node | 396/396 PASS |
| Maven | **723 tests, 0 failures, 0 errors, 9 skipped** (`mvn -q test`, exit 0) |
| Run-integrity | Không có `RUN-INTEGRITY` FAIL; đủ số case theo ma trận (27/36/21/35/35/11) |

### 10.3 Kết luận

QA sẵn sàng cho bước review tiếp theo trên HEAD tích hợp `25e7595`. PR #90 giữ base PR #85 theo yêu cầu stack; cần rebase/merge #85 + #88 theo thứ tự trước khi coi là merge-ready vào `master`. Không tuyên bố production-ready; 3 BLOCKED và 3 NOT RUN giữ nguyên.
