# Danh mục kiểm thử frontend — AloUTE

> **Ngày chạy:** 2026-10-27 (Asia/Ho_Chi_Minh)
> **Nhánh:** `fix/quan-frontend-hardening-20261027`
> **Baseline:** `origin/master` @ `99ab72ee450fd7cfb7bfae91af4f8e40d23d5ab6`
> **Trạng thái mã nguồn:** working tree có thay đổi chưa commit; không có SHA riêng cho bản hardening.
> **Nguyên tắc:** test tĩnh Node **không** được tính là bằng chứng browser E2E; mỗi loại được ghi riêng.

## 1. Môi trường

| Thành phần | Giá trị |
| :--- | :--- |
| OS | Windows 10.0.26200, x64 |
| Node.js | v24.19.0 |
| Maven | Apache Maven 3.9.16 |
| Java | 21.0.8 LTS |
| Ứng dụng local | Spring Boot, profile `dev`, H2 in-memory, `http://127.0.0.1:8081` |
| Dữ liệu local | `tools/demo-data.mjs` (loopback-only) + seed `DevTestDataInitializer` |
| Browser automation | Playwright 1.64.1, `chromium.launch({ channel: 'chrome', headless: true })` |
| Tài khoản browser test | Chỉ tài khoản seed local `demoan@aloute.test` (profile `dev`); **không** dùng trên production |
| Production | `https://justneed.food`, chỉ guest, ngày 2026-10-27; commit production không xác minh được |

## 2. Node static frontend suites

**Lệnh:**

```bash
node --test test/frontend/*.test.mjs
```

**Kết quả:** 396 test / 94 suite — **PASS 396, FAIL 0, SKIPPED 0**, exit code 0.

| Nhóm | Tệp |
| :--- | :--- |
| Guest/accessibility hardening (mới) | [`guest-ux-accessibility.test.mjs`](../../test/frontend/guest-ux-accessibility.test.mjs) — 26 test |
| Navbar responsive | [`navbar-responsive-overflow.test.mjs`](../../test/frontend/navbar-responsive-overflow.test.mjs) |
| Feed/composer/layout | [`batch1-ux-upgrade.test.mjs`](../../test/frontend/batch1-ux-upgrade.test.mjs), [`final-web-polish.test.mjs`](../../test/frontend/final-web-polish.test.mjs) |
| Post & interaction | [`post-interactions.test.mjs`](../../test/frontend/post-interactions.test.mjs), [`post-edit-delete.test.mjs`](../../test/frontend/post-edit-delete.test.mjs), [`quote-visibility-dropdown.test.mjs`](../../test/frontend/quote-visibility-dropdown.test.mjs) |
| Poll & media | [`poll-composer.test.mjs`](../../test/frontend/poll-composer.test.mjs), [`poll-media.test.mjs`](../../test/frontend/poll-media.test.mjs) |
| Messaging/notification | [`messaging-behavior.test.mjs`](../../test/frontend/messaging-behavior.test.mjs), [`notification-module.test.mjs`](../../test/frontend/notification-module.test.mjs) |
| Community/admin | [`community-manage.test.mjs`](../../test/frontend/community-manage.test.mjs), [`admin-dashboard.test.mjs`](../../test/frontend/admin-dashboard.test.mjs) |
| Settings | [`settings-init-lifecycle.test.mjs`](../../test/frontend/settings-init-lifecycle.test.mjs), [`settings-list-safe-render.test.mjs`](../../test/frontend/settings-list-safe-render.test.mjs) |
| Auth/OTP | [`auth-otp-behavior.test.mjs`](../../test/frontend/auth-otp-behavior.test.mjs) |
| Asset references | [`static-asset-references.test.mjs`](../../test/frontend/static-asset-references.test.mjs) |

Các test này đọc template/CSS/JS và assert contract tĩnh. Chúng **không** chứng minh giao diện render đúng trong browser.

## 3. Maven / Spring test suite

**Lệnh đầy đủ:**

```bash
mvn clean test
```

**Kết quả:** 641 test / 73 suite — **FAIL 0, ERROR 0, SKIPPED 9**, exit code 0. Tóm tắt: [`maven-clean-test-20261028-summary.txt`](../../test-results/quan-frontend-hardening/after/maven-clean-test-20261028-summary.txt); log đầy đủ 3.2MB chỉ lưu local (bị `*.log` ignore, không commit). Chạy `clean` để loại report Surefire cũ không còn source class (trước đó `target/` còn một XML của `ThrowawayPerfBaselineTest` từ 2026-10-24 làm tổng thành 642/74).

**Lệnh targeted cho phần render bị ảnh hưởng bởi guest gating:**

```bash
mvn -q -Dtest=ThymeleafViewRenderIntegrationTest test
```

**Kết quả targeted:** 43 test, 0 failure, 0 error, 0 skipped.

### 3.1. Thay đổi test đi kèm

[`ThymeleafViewRenderIntegrationTest.java`](../../src/test/java/com/aloute/controller/page/ThymeleafViewRenderIntegrationTest.java) được cập nhật để phản ánh contract mới: quote button chỉ render khi có `currentUser`, giữ nguyên các assert fail-closed khi `capabilities` null / `canQuote=false` / `allowQuote=false`, thêm assert guest không thấy quote trigger, và cập nhật class `aloute-auth-cta` cho `#btn-community-post` ở trạng thái `d-none`. Nếu không cập nhật, test cũ sẽ fail vì model render không có `currentUser` hoặc class CTA thay đổi.

### 3.2. 9 test bị skip

| Test class :: test | Lý do |
| :--- | :--- |
| `MediaControllerPathConfinementTest` (7 test) | Môi trường Windows không hỗ trợ kịch bản symlink/`DirectoryStream` như Linux |
| `MySql8RevocationVerificationTest.verifyRealMySql8LifecycleAndRevocation` | Thiếu system property `aloute.mysql8.enabled` / MySQL 8 thật |
| `ProductionSecretsSafetyTest.dockerComposeCliValidatesMissingAndEmptySecrets` | Không có Docker CLI trong môi trường test |

Các test này không thuộc phạm vi frontend và không được tính là PASS.

## 4. Browser verification (Playwright + Chrome thật)

**Lệnh đã chạy:**

```bash
cd test-results/quan-frontend-hardening
NODE_PATH="C:/Users/My Laptop/AppData/Local/npm-cache/_npx/e41f203b7505f1fb/node_modules" \
BASE_URL="http://127.0.0.1:8081" OUT_DIR="after" \
ALOUTE_SEED_EMAIL="<seed-email-local>" ALOUTE_SEED_PASSWORD="<seed-password-local>" \
node tools/verify.cjs
```

Hai biến `ALOUTE_SEED_*` là tài khoản seed local (xem [`DEMO_CHECKLIST_VI.md`](../DEMO_CHECKLIST_VI.md)); harness **không lưu credential trong repo**. Nếu bỏ trống, 16 check guest vẫn chạy và phần authenticated được skip có log.

**Kết quả:** 27/27 check PASS — [`verification.json`](../../test-results/quan-frontend-hardening/after/verification.json).

| # | Check | BL liên quan | Kết quả |
| :---: | :--- | :--- | :--- |
| 1 | Skip link là tab stop đầu tiên | BL-23 | PASS |
| 2 | Skip link hiển thị khi focus | BL-23 | PASS |
| 3 | Enter trên skip link đưa focus tới `#main-content` | BL-23 | PASS |
| 4 | Guest không thấy composer | BL-21 | PASS |
| 5 | Guest thấy CTA đăng nhập thay composer | BL-21 | PASS |
| 6 | Guest không thấy action menu/report | BL-21 | PASS |
| 7 | Guest không thấy quote trigger | BL-21 | PASS |
| 8 | Feed có đúng một `<h1>` | BL-23 | PASS |
| 9 | CTA đăng nhập của guest cao ≥44px | BL-17 | PASS |
| 10 | Không có console error trên feed guest | Regression | PASS |
| 11 | Guest không thấy nút “Tạo bài viết” trên `/posts/1` | BL-21 | PASS |
| 12 | Guest không có modal composer/textarea | BL-21 | PASS |
| 13 | Không có console error trên post detail guest | Regression | PASS |
| 14 | Guest không thấy action tạo bài viết trên `/topics/hoc-tap` | BL-21 | PASS |
| 15 | Trang topic công khai vẫn render cho guest (có `<h1>`) | Regression | PASS |
| 16 | Link đăng nhập hoạt động khi **tắt JavaScript** | BL-20 | PASS |
| 17 | Đăng nhập local bằng seed account thành công | Local auth | PASS |
| 18 | User đã đăng nhập thấy composer | BL-21 | PASS |
| 19 | User đã đăng nhập thấy action menu | BL-21 | PASS |
| 20 | User đã đăng nhập thấy form trả lời | BL-21 | PASS |
| 21 | Trang authenticated không hiện guest prompt | BL-21 | PASS |
| 22 | User đăng nhập thấy nút tạo bài viết + modal | BL-21 | PASS |
| 23 | User đăng nhập vẫn thấy action tạo bài viết trên topic page | BL-21 | PASS |
| 24 | Action tạo bài viết trên topic page cao ≥44px | BL-17 | PASS |
| 25 | `/settings` (layout `single`) có skip link | BL-23 | PASS |
| 26 | “Đã đọc tất cả” trong dropdown thông báo giữ chiều cao compact | BL-17 | PASS |
| 27 | Không có console error trên trang authenticated | Regression | PASS |

Ảnh: [`verify-guest-feed-360.png`](../../test-results/quan-frontend-hardening/after/verify-guest-feed-360.png), [`verify-guest-topic-360.png`](../../test-results/quan-frontend-hardening/after/verify-guest-topic-360.png), [`verify-auth-feed-390.png`](../../test-results/quan-frontend-hardening/after/verify-auth-feed-390.png).

## 5. 26 test tĩnh mới (`guest-ux-accessibility.test.mjs`)

| ID | Test | Kỳ vọng | Kết quả |
| :--- | :--- | :--- | :--- |
| TC-GUEST-01 | Navbar guest actions point at `/login` và `/register` | href thật | PASS |
| TC-GUEST-02 | Sidebar welcome actions point at `/login` và `/register` | href thật | PASS |
| TC-GUEST-03 | Không còn auth action dùng `href="#"` | 0 fallback chết | PASS |
| TC-GUEST-04 | Guest sidebar profile shortcut fallback về `/login` | fallback đúng | PASS |
| TC-GUEST-05 | Layout có skip link là phần tử focusable đầu tiên | đúng thứ tự DOM | PASS |
| TC-GUEST-06 | Skip link target tồn tại và focusable | `#main-content`, `tabindex=-1` | PASS |
| TC-GUEST-07 | Skip link dùng token, hiện khi focus | không hex mới | PASS |
| TC-GUEST-08 | Layout `single` (settings) cũng có skip link + target | đúng | PASS |
| TC-GUEST-09 | Search có đúng một `<h1>` | 1 heading | PASS |
| TC-GUEST-10 | Feed composer chỉ render cho user đăng nhập | có `currentUser` | PASS |
| TC-GUEST-11 | Guest nhận CTA đăng nhập thay composer | CTA đúng | PASS |
| TC-GUEST-12 | Form trả lời chỉ render cho user đăng nhập | guest bị loại | PASS |
| TC-GUEST-13 | Action dropdown edit/delete/report ẩn với guest | guest bị loại | PASS |
| TC-GUEST-14 | Quote trigger chỉ hiện khi có quyền quote | guest/caps fail-closed | PASS |
| TC-GUEST-15 | Nút “Tạo bài viết” và `#createPostModal` chỉ dành cho user | guest bị loại | PASS |
| TC-GUEST-16 | Navbar controls giữ sàn 44px | CSS | PASS |
| TC-GUEST-17 | Interaction bar giữ sàn 44px | CSS | PASS |
| TC-GUEST-18 | Overflow menu, poll vote, reply submit đạt 44px | CSS | PASS |
| TC-GUEST-19 | Search filter tab đạt 44px trên mobile | CSS | PASS |
| TC-GUEST-20 | CTA guest ngoài navbar (feed 2, post detail 1, sidebar 2, topic 1) đều có `.aloute-auth-cta` | CSS + template | PASS |
| TC-GUEST-21 | Text action trong dropdown thông báo giữ compact | CSS override | PASS |
| TC-GUEST-22 | Không còn token `--al-text-primary` | token đúng | PASS |
| TC-GUEST-23 | Bootstrap Icons được pin bằng SRI ở mọi layout (public + admin) | `integrity` + `crossorigin` | PASS |
| TC-GUEST-24 | Cache asset giữ mặc định an toàn 12h, không có 365d | `period: 12h` + content strategy | PASS |
| TC-GUEST-25 | Section CSS mới không chứa mã hex thô | token-only | PASS |
| TC-GUEST-26 | Topic/community page công khai: entry tạo bài được gate; CTA join/leave/login/create giữ class 44px, kể cả khi JS đổi trạng thái | guest bị loại | PASS |

Một số test kiểm tra cấu trúc file, không kiểm tra hành vi runtime; các hành vi tương ứng được kiểm ở mục 4.

## 6. Đo responsive (browser thật)

- Harness: [`tools/measure.cjs`](../../test-results/quan-frontend-hardening/tools/measure.cjs).
- Route: `/feed`, `/search?q=hoc-tap`, `/posts/1`.
- Viewport: 360, 375, 390, 768, 1440 (height 900).
- Phạm vi target đo gồm navbar, composer, interaction bar, `.aloute-auth-cta`, post card, dropdown, reply form.
- Trước: [`baseline/measurements.json`](../../test-results/quan-frontend-hardening/baseline/measurements.json); sau: [`after/measurements.json`](../../test-results/quan-frontend-hardening/after/measurements.json).
- Server local được restart trước lần đo cuối; dữ liệu demo seed lại bằng `node tools/demo-data.mjs http://127.0.0.1:8081` (local-only, fail-closed với host không loopback).
- Chi tiết số đo và ảnh: [`RESPONSIVE_ACCESSIBILITY_REPORT.md`](RESPONSIVE_ACCESSIBILITY_REPORT.md).

## 7. Ma trận trạng thái chức năng

| Hạng mục | Môi trường | Kết quả | Ghi chú |
| :--- | :--- | :--- | :--- |
| Guest login/register link không cần JS | Local browser | PASS | Check 16 |
| Guest không thấy composer/action ghi | Local browser | PASS | Check 4–7, 11–12, 14 |
| Guest không thấy action ghi trên trang public topic | Local browser | PASS | Check 14–15 |
| User đăng nhập thấy composer/action/reply | Local browser | PASS | Check 18–23 |
| Skip link + keyboard focus | Local browser | PASS | Check 1–3; layout `single` check 25 |
| Search có `<h1>` | Local browser | PASS | Check 8; production cũ vẫn thiếu |
| Touch target ≥44px | Local browser | PASS | Đo sau: 0 phần tử nhỏ; baseline 3–31 tùy trang; CTA guest 44px (check 9), topic CTA 44px (check 24) |
| Responsive 360–1440 | Local browser | PASS (không overflow) | Xem báo cáo responsive |
| BL-22 HTML error page + auth redirect | Local curl | **PARTIAL/BLOCKED** | Phụ thuộc backend; xem log |
| Guest/authenticated flows trên production | Production | **BLOCKED** | Không có tài khoản được cấp phép; không dùng seed account production |
| Messaging/notification trên production | Production | **BLOCKED** | Cần đăng nhập |

## 8. Giới hạn kiểm thử

- Chưa chạy axe-core, Lighthouse accessibility hoặc kiểm tra contrast tự động.
- Chưa test 320px trong harness (bắt đầu từ 360px); CSS media query có nhánh dưới 420px nhưng chưa đo 320px.
- Chưa test Firefox/Safari, thiết bị di động thật, screen reader thật.
- Community detail chưa có seed local nên CTA join/login/create mới chỉ được giữ bằng test tĩnh + class CSS, chưa đo browser.
- Chưa có E2E đầy đủ cho create/edit/delete/poll/messaging/notification trong một kịch bản browser duy nhất; hiện có test tĩnh + render + kiểm tra browser có mục tiêu.
- Không có production deployment evidence; mọi kết quả “PASS” là local.
- Test tĩnh Node không thay thế browser E2E; bảng mục 4 mới là bằng chứng browser.
- `currentUser` ở `/feed` và `/communities/{id}/manage/**` là best-effort; khi lookup profile lỗi, user đăng nhập có thể thấy UI guest. Chưa có test cho nhánh lỗi này vì cần thay đổi backend.
