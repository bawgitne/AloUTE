# Nhật ký sửa lỗi frontend — BL-17/20/21/22/23/25

> **Ngày:** 2026-10-27
> **Nhánh:** `fix/quan-frontend-hardening-20261027`
> **Baseline:** `origin/master` @ `99ab72ee450fd7cfb7bfae91af4f8e40d23d5ab6`
> **Trạng thái tại thời điểm đo (2026-10-27):** thay đổi nằm trong working tree trên nhánh `fix/quan-frontend-hardening-20261027`.
> **Trạng thái hiện tại (2026-10-28):** đã merge vào `master` qua PR #85 (merge commit `cd0d85c`); chưa deploy production.
> **Ràng buộc đã giữ:** không sửa backend security, không sửa schema/migration, không sửa deployment/production, không đổi authorization hoặc business rule.
> **Môi trường kiểm chứng:** local `http://127.0.0.1:8081`, profile `dev`, H2 in-memory; browser Chrome thật qua Playwright 1.64.1.

## 0. Tổng quan

| BL | Mức | Trạng thái trong nhánh | Phần frontend | Phần còn phụ thuộc backend |
| :--- | :--- | :--- | :--- | :--- |
| BL-17 | P2 | **PARTIAL** | Touch target 44px (navbar, interaction bar, CTA guest, sidebar); mép nút mobile 4→8px | Xác minh commit production; lỗi cắt không tái hiện local vì PR #59 đã có |
| BL-20 | P3 | **FIXED (local)** | `href` thật cho login/register, fallback profile | Chưa deploy production |
| BL-21 | P3 | **FIXED (local)** | Gate composer/reply/action/quote; CTA đăng nhập | Chưa deploy; backend vẫn là lớp bảo vệ cuối; `currentUser` best-effort ở controller |
| BL-22 | P3 | **BLOCKED (backend)** | Không có cách sửa frontend thuần túy | Auth redirect, content negotiation, whitelist/404, 403→404 |
| BL-23 | P3 | **FIXED (scope hẹp)** | Skip link ở `default` + `single`, `<h1>` search, touch target | WCAG đầy đủ chưa audit |
| BL-25 | P3 | **PARTIAL** | Token sai, SRI; cache: giữ mặc định 12h (365d đã hoàn nguyên) | Chưa self-host CDN; hex legacy còn trong CSS; cache dài hạn cho asset hash cần backend/config riêng |

Kiểm thử tổng: **Node 396/396 PASS**, **Maven 641 test / 0 fail / 0 error / 9 skipped**, **browser 27/27 PASS**. Chi tiết: [`FRONTEND_TEST_CASES.md`](FRONTEND_TEST_CASES.md).

---

## BL-17 — Navbar mobile và touch target

### Hiện trạng baseline

- Audit production ngày 2026-10-27: nút Đăng ký có `right = 356.8px` trên visual viewport `360.8px`, nhãn bị cắt; 10 phần tử tương tác `<40px` — [`03_LIVE_WEBSITE_AUDIT.md` §2.2](../audits/independent-full-audit-2026-10-27/03_LIVE_WEBSITE_AUDIT.md).
- Đo local tại `99ab72e`: không có overflow ngang; nút Đăng ký right = 356/371/386 ở 360/375/390 (mép 4px). **Không tái hiện cắt**; tái hiện được target nhỏ (7 target ở mobile, 31 ở desktop/tablet trên feed).

### Nguyên nhân

- Target nhỏ: `.interaction-btn` cao 38px; dropdown “Thao tác bài viết” 25×30px; link auth/navbar chỉ cao 27–31px; thiếu sàn 44px cho nhiều control.
- Mép 4px: các control navbar sát biên; scrollbar/zoom có thể đẩy ra ngoài. Phần “navbar vượt viewport” đã được xử lý trước đó bởi PR [#59](https://github.com/bawgitne/AloUTE/pull/59) / commit [`c1d674b`](https://github.com/bawgitne/AloUTE/commit/c1d674b), có regression test [`navbar-responsive-overflow.test.mjs`](../../test/frontend/navbar-responsive-overflow.test.mjs). Vì baseline local đã chứa PR #59, lỗi cắt không còn tái hiện; production chưa xác minh commit nên vẫn có thể chạy bản cũ.

### Thay đổi

| File | Thay đổi |
| :--- | :--- |
| [`app.css`](../../src/main/resources/static/css/app.css#L518) | `.interaction-btn` `min-height: 44px` (dòng 530) |
| [`app.css`](../../src/main/resources/static/css/app.css#L307) | `.sidebar-nav-link` thêm `min-height: 44px` (trước ~43px) |
| [`app.css`](../../src/main/resources/static/css/app.css#L2316) | Section hardening: `.aloute-navbar .btn` 44px; `.aloute-navbar .dropdown-menu .btn` giữ compact; dropdown bài viết 44×44; poll/reply 44px; search tab 44px trên mobile; guest actions margin mobile |
| [`app.css`](../../src/main/resources/static/css/app.css#L2329) | `.aloute-auth-cta` 44px cho CTA chính ngoài navbar (guest auth, topic/community action) |
| [`feed/index.html`](../../src/main/resources/templates/feed/index.html#L42), [`post/detail.html`](../../src/main/resources/templates/post/detail.html#L77), [`sidebar.html`](../../src/main/resources/templates/fragments/sidebar.html#L32) | Gắn `aloute-auth-cta` cho 5 nút Đăng nhập/Đăng ký/Tạo tài khoản của guest |
| [`topic/detail.html`](../../src/main/resources/templates/topic/detail.html#L44), [`community/detail.html`](../../src/main/resources/templates/community/detail.html#L45) | Gắn `aloute-auth-cta` cho CTA tạo bài topic, join/leave/login/create bài community; [`page-actions.js`](../../src/main/resources/static/js/page-actions.js#L1217) giữ class khi JS đổi trạng thái join/leave (trước ~30px) |
| [`navbar.html`](../../src/main/resources/templates/fragments/navbar.html#L163) | Không đổi cấu trúc; chỉ giữ control hiện có |

### Kiểm chứng

- Đo sau: target `<44px` = **0** trên 3 route × 5 viewport (baseline: 3–31), selector đã bao gồm `.aloute-auth-cta` và `.sidebar-nav-link`.
- CTA guest (`.aloute-auth-cta`) cao đúng **44px** ở 360/375/390/768/1440 trên `/feed` và `/posts/1`, gồm cả sidebar ở desktop; trước sửa các nút `btn-sm` chỉ cao ~27.5px. CTA tạo bài trên `/topics/hoc-tap` (user đăng nhập) cũng đạt 44px (browser check 24).
- Community: không có community seed trong local nên CTA join/login/create được giữ bằng test tĩnh (`aloute-auth-cta`), **chưa đo browser**.
- Nút Đăng ký: 356→352 (360), 371→367 (375), 386→382 (390) — mép 8px.
- Không overflow ngang ở 360/375/390/768/1440 trước và sau.
- Browser check: không console error; guest/user navbar vẫn dùng được; “Đã đọc tất cả” trong dropdown thông báo vẫn cao compact (không bị ép 44px) — check 26.
- Ảnh: [`baseline/feed-360.png`](../../test-results/quan-frontend-hardening/baseline/feed-360.png) → [`after/feed-360.png`](../../test-results/quan-frontend-hardening/after/feed-360.png).

### Chưa xác minh

- Production có thực sự chạy commit trước PR #59 hay không.
- Viewport 320px, zoom 200%, scrollbar overlay trên Android thật.
- Không kết luận “đã sửa lỗi cắt trên production”; chỉ kết luận target 44px và mép mobile đã cải thiện trong local.

---

## BL-20 — Link Đăng nhập/Đăng ký dùng `href="#"`

### Hiện trạng baseline

`navbar.html:163-164` và `sidebar.html:32-33` dùng `href="#"`, chỉ hoạt động nhờ `data-action` JavaScript. Khi JS lỗi/bị chặn, guest không thể vào login/register. Audit: SRC-19, UX-09.

### Nguyên nhân

Link được dùng như button trigger JS thay vì anchor có đích thật.

### Thay đổi

| File | Thay đổi |
| :--- | :--- |
| [`navbar.html`](../../src/main/resources/templates/fragments/navbar.html#L163) | `th:href="@{/login}" href="/login"` và `@{/register}`; giữ `data-page`/`data-action` làm progressive enhancement |
| [`sidebar.html`](../../src/main/resources/templates/fragments/sidebar.html#L32) | Hai nút welcome dùng href thật |
| [`sidebar.html`](../../src/main/resources/templates/fragments/sidebar.html#L50) | Fallback profile shortcut đổi từ `'#'` sang `@{/login}` |

### Kiểm chứng

- TC-GUEST-01..04 PASS.
- Browser check 16: tắt JavaScript, click link Đăng nhập vẫn điều hướng thành công.
- Đo `hashAuthLinks`: **6 → 0** trên cả 3 route × 5 viewport.
- Không đổi route/controller; `/login`, `/register` là route có sẵn.

### Chưa xác minh

- Production vẫn có thể chạy bản cũ (audit live xác nhận `href="#"`); nhánh này chưa deploy.

---

## BL-21 — Guest thấy composer/comment/report/bookmark

### Hiện trạng baseline

Guest tại `/feed` thấy composer và action menu; `/posts/4` thấy form trả lời và report. Audit: UX-03, functional B15.

### Nguyên nhân

Template render theo cùng một nhánh cho guest và user; không kiểm tra `currentUser` trước khi render action ghi.

### Thay đổi

| File | Thay đổi |
| :--- | :--- |
| [`feed/index.html`](../../src/main/resources/templates/feed/index.html#L9) | Composer chỉ render khi `currentUser != null` |
| [`feed/index.html`](../../src/main/resources/templates/feed/index.html#L33) | Guest nhận card CTA “Chia sẻ với cộng đồng AloUTE” + nút Đăng nhập/Tạo tài khoản |
| [`post/detail.html`](../../src/main/resources/templates/post/detail.html#L40) | Reply form yêu cầu `currentUser != null` |
| [`post/detail.html`](../../src/main/resources/templates/post/detail.html#L71) | Guest nhận prompt “Đăng nhập để tham gia bình luận” + link login |
| [`post/_post-card.html`](../../src/main/resources/templates/post/_post-card.html#L69) | Dropdown edit/delete/report chỉ render khi có `post.id` và `currentUser` |
| [`post/_interaction-bar.html`](../../src/main/resources/templates/post/_interaction-bar.html#L49) | Quote trigger yêu cầu `currentUser`, `allowQuote`, `capabilities.canQuote` (fail-closed) |
| [`navbar.html`](../../src/main/resources/templates/fragments/navbar.html#L43) | Hai nút “Tạo bài viết” (desktop + mobile) yêu cầu `currentUser != null` |
| [`modal.html`](../../src/main/resources/templates/fragments/modal.html#L6) | `#createPostModal` chỉ render khi `currentUser != null`; guest không thể mở composer qua JS |
| [`topic/detail.html`](../../src/main/resources/templates/topic/detail.html#L44) | Hai entry “Đăng bài vào chủ đề”/“Tạo bài viết ngay” yêu cầu `currentUser != null`; guest nhận CTA đăng nhập trong empty state |
| [`app.css`](../../src/main/resources/static/css/app.css#L2296) | Style cho prompt icon, token-only |

Không thay đổi quyền backend: guest vẫn có thể gọi API nếu cố tình; server là lớp quyết định cuối. Thay đổi này chỉ ngăn UI mời gọi hành động không khả dụng.

### Kiểm chứng

- Browser guest: composer **true→false**, report actions **4→0** (feed), reply form **true→false** (post detail), quote 0, action menu 0.
- Browser guest `/posts/1` (360px): 0 action “Tạo bài viết” hiển thị, không có `#createPostModal`/textarea composer.
- Browser guest `/topics/hoc-tap` (public whitelist route): 0 action tạo bài viết, không có modal; trang vẫn render.
- Browser user local: composer/action/reply hiện đúng; không có guest prompt; nút tạo bài viết và modal composer hiện đúng trên `/posts/1` và `/topics/hoc-tap`.
- TC-GUEST-10..15, TC-GUEST-26 PASS; Maven quote fail-closed + create-modal guard tests PASS sau khi cập nhật model test.
- Ảnh: [`verify-guest-feed-360.png`](../../test-results/quan-frontend-hardening/after/verify-guest-feed-360.png), [`verify-guest-topic-360.png`](../../test-results/quan-frontend-hardening/after/verify-guest-topic-360.png), [`verify-auth-feed-390.png`](../../test-results/quan-frontend-hardening/after/verify-auth-feed-390.png).

### Chưa xác minh

- Bookmark/repost/like và form vote poll vẫn hiện cho guest; click sẽ nhận lỗi từ API. Chưa gate các nút này vì audit xếp chúng là read-only/không tạo nội dung và poll visibility thuộc phạm vi audit P0 khác; cần quyết định UX riêng nếu muốn ẩn hoàn toàn.
- [`FeedPageController`](../../src/main/java/com/aloute/controller/page/FeedPageController.java#L31) và [`CommunityManagePageController`](../../src/main/java/com/aloute/controller/page/CommunityManagePageController.java#L45) lấy `currentUser` best-effort; nếu lookup profile lỗi, user đã đăng nhập có thể thấy UI guest. Đây là hành vi controller hiện có; sửa cần thay đổi backend nên không thực hiện trong nhánh này.
- Chưa test trên production.

---

## BL-22 — HTML error page và redirect login

### Hiện trạng và bằng chứng local

Probe ngày 2026-10-27, `Accept: text/html`, local `:8081`, file thô: [`browser-route-probe-20261027.txt`](../../test-results/quan-frontend-hardening/bl22/browser-route-probe-20261027.txt).

| Route | HTTP | Content-Type | Quan sát | Kỳ vọng |
| :--- | ---: | :--- | :--- | :--- |
| `/explore` | 404 | `text/html` | Trang 404 HTML tiếng Việt | Đúng |
| `/khoi-test` | 404 | `text/html` | Trang 404 HTML | Đúng |
| `/admin` (guest) | 401 | `application/json` | `AUTH_UNAUTHENTICATED` JSON | Redirect `/login` cho browser route |
| `/khong-ton-tai-xyz` (guest) | 401 | `application/json` | JSON 401 thay vì 404 | 404 HTML |
| `/post/create` (guest) | **500** | `text/html` | Trang 500 HTML | Redirect `/login` |

### Nguyên nhân gốc (đã đọc source)

1. [`PostPageController.create()`](../../src/main/java/com/aloute/controller/page/PostPageController.java#L22) gọi `currentUserProvider.requireUserId()`; hàm này ném `ApiException(401)` — [`SecurityContextCurrentUserProvider.java`](../../src/main/java/com/aloute/security/currentuser/SecurityContextCurrentUserProvider.java#L16).
2. `GlobalExceptionHandler` render JSON cho API; browser gửi `Accept: text/html` nên không có representation phù hợp, dẫn tới `HttpMediaTypeNotAcceptableException` và trang 500.
3. `/admin` do security filter chain chặn trước khi vào page controller; [`AuthenticationFailureHandler`](../../src/main/java/com/aloute/security/handler/AuthenticationFailureHandler.java#L18) trả JSON 401 cho mọi request, kể cả browser navigation.
4. Route không tồn tại bị security filter chặn 401 trước khi `NoResourceFoundException`/404 được xử lý; whitelist không bao phủ unknown path.
5. `templates/error/404.html` và `500.html` đã tồn tại và render đúng trong Maven test; vấn đề là **content negotiation + entry point + whitelist**, không phải thiếu template.

### Vì sao không sửa trong nhánh này

Sửa BL-22 cần thay đổi backend security/exception handling (entry point theo `Accept`, redirect page route, whitelist/404, map 403→404). Task cấm thay đổi backend security để giải quyết lỗi UI, nên phần này được ghi là **BLOCKED**, không sửa.

### Phần frontend đã làm (không giải quyết BL-22)

- Guest login/register link hoạt động không cần JS (BL-20) — giảm phụ thuộc vào redirect khi JS lỗi.
- Guest CTA thay composer/reply form (BL-21) — không tạo thêm đường dẫn tới trang 500.
- Error template 404/500 vẫn giữ nguyên; Maven render tests cho `error/404` và `error/500` PASS.

### Việc backend cần làm (đề xuất, chưa thực hiện)

1. `AuthenticationEntryPoint` trả `302 /login` khi `Accept` ưu tiên HTML, giữ JSON 401 cho API.
2. `GlobalExceptionHandler`/page controller: guest page route redirect login thay vì ném `ApiException` dẫn tới 500.
3. Whitelist hoặc `NoResourceFoundException`: unknown browser route trả 404 HTML.
4. Post không được xem trả 404 thay vì 403 để không lộ tồn tại (audit SEC-14).

### Trạng thái

**BLOCKED** — không có bằng chứng sửa trên production; không có thay đổi backend trong nhánh.

---

## BL-23 — Search thiếu `<h1>` và skip link

### Hiện trạng baseline

- `/search` có 0 `<h1>`.
- Không có skip link trên layout.
- 10 target `<40px` ở mobile (audit live).

### Thay đổi

| File | Thay đổi |
| :--- | :--- |
| [`layouts/default.html`](../../src/main/resources/templates/layouts/default.html#L6) | Thêm `<a class="aloute-skip-link" href="#main-content">` là phần tử focusable đầu tiên |
| [`layouts/default.html`](../../src/main/resources/templates/layouts/default.html#L20) | `<main id="main-content" role="main" tabindex="-1">` (đã có sẵn target) |
| [`layouts/single.html`](../../src/main/resources/templates/layouts/single.html#L5) | Thêm skip link cho layout một cột (settings) — target `#main-content` đã có sẵn |
| [`search/index.html`](../../src/main/resources/templates/search/index.html#L6) | Thêm `<h1 class="visually-hidden">Tìm kiếm trên AloUTE</h1>` |
| [`app.css`](../../src/main/resources/static/css/app.css#L2274) | Skip link token-only, hiện khi focus; focus outline 2px |

### Kiểm chứng

- Browser check 1–3: skip link là tab stop đầu tiên, hiện khi focus (`top: 8px`), Enter đưa focus tới `#main-content`.
- Browser check 25: `/settings` (layout `single`) có skip link trỏ `#main-content`.
- Search `<h1>`: **0 → 1** ở cả 5 viewport.
- Feed/post detail giữ 1 `<h1>`.
- TC-GUEST-05..09 PASS.
- Không thay đổi layout thị giác khi skip link không focus (`top: -100px`).

### Chưa xác minh

- Thứ tự heading đầy đủ, contrast, screen reader, focus visible toàn bộ control, modal focus trap.
- Không chạy axe/Lighthouse.

---

## BL-25 — Design token, SRI và cache asset

### Hiện trạng baseline

- [`right-sidebar.html`](../../src/main/resources/templates/fragments/right-sidebar.html#L53) dùng `--al-text-primary` không được định nghĩa (2 chỗ: dòng 53, 63).
- Bootstrap Icons tải từ CDN nhưng không có `integrity`/`crossorigin`.
- Asset tĩnh cache 12 giờ trên production (audit live); content-hash chưa bật đầy đủ.

### Thay đổi

| File | Thay đổi |
| :--- | :--- |
| [`right-sidebar.html`](../../src/main/resources/templates/fragments/right-sidebar.html#L53) | Đổi sang `var(--aloute-text-primary)` (dòng 53, 63) |
| [`head.html`](../../src/main/resources/templates/fragments/head.html#L22), [`admin.html`](../../src/main/resources/templates/layouts/admin.html#L21) | Pin Bootstrap Icons 1.11.3 với SRI SHA-384 + `crossorigin="anonymous"` ở cả layout public và admin |
| [`app.css`](../../src/main/resources/static/css/app.css#L2269) | Section mới chỉ dùng token; test chặn hex thô trong section này |

### Kiểm chứng

- SRI: tải lại `bootstrap-icons.min.css` từ CDN và tính `openssl dgst -sha384`; kết quả khớp chính xác chuỗi trong `head.html` và `admin.html`: `sha384-XGjxtQfXaH2tnPFa9x+ruJTuLE3Aa6LhHSWRr1XeTyhezb4abCG4ccI5AkVDxqC+`. Test quét toàn bộ template, nên thiếu SRI ở layout admin sẽ fail.
- Local header asset có hash: `/css/app-2c8411760fc27ddda807fcfb04c70558.css` → `HTTP 200`, `Cache-Control: max-age=43200` (12h), `ETag` khớp MD5 nội dung file; log: [`cache-header-20261028.txt`](../../test-results/quan-frontend-hardening/after/cache-header-20261028.txt). **Chưa có directive `immutable`**; Spring chỉ trả `max-age`.
- Token: TC-GUEST-22 PASS; không còn `--al-text-primary` trong template.
- TC-GUEST-23..25 PASS.
- Browser: không console error; icon/CDN vẫn tải được.

### Vì sao không tăng cache lên 365 ngày

Đã thử `period: 365d` và phát hiện rủi ro hồi quy: content strategy chỉ rewrite URL đi qua Thymeleaf `@{...}`; nhiều URL `/images/...` được hardcode trong JS (`fcm-notification.js`, `messaging.js`, `notification.js`, `post-composer.js`, `settings.js`, `page-actions.js`) nên không có hash. Cache toàn cục 1 năm sẽ khiến các ảnh này bị stale sau release. Muốn cache dài hạn an toàn phải cấu hình cache theo path/loại resource ở tầng server — ngoài phạm vi frontend và bị ràng buộc “không sửa backend”. Vì vậy giữ mặc định 12h và ghi phần này là **DEFERRED**.

### Chưa xác minh / còn lại

- `app.css` vẫn còn nhiều mã hex và `!important` legacy ngoài section mới; chưa migrate toàn bộ (audit đếm 83 hex/63 `!important`).
- Bootstrap/Bootstrap Icons vẫn là CDN bên ngoài, chưa self-host; SRI giảm rủi ro nhưng vẫn là render-blocking cross-origin.
- Chưa đo Lighthouse/render-blocking sau thay đổi.
- Cache dài hạn cho asset content-hash (**DEFERRED**): cần thay đổi cấu hình server/backend để chỉ áp dụng cho URL có hash.
- Chưa xác minh header cache trên production; audit live ghi nhận production đang trả `max-age=43200`.

---

## 1. Thay đổi test và tương thích

- [`guest-ux-accessibility.test.mjs`](../../test/frontend/guest-ux-accessibility.test.mjs): 26 test mới, PASS.
- [`ThymeleafViewRenderIntegrationTest.java`](../../src/test/java/com/aloute/controller/page/ThymeleafViewRenderIntegrationTest.java#L557): cập nhật case A để có `currentUser`, thêm case A2 guest không thấy quote; các case B/C/D vẫn fail-closed. Test `testCreatePostModalActiveNavGuard` thêm `currentUser` cho case hợp lệ và case guest phải ẩn modal.
- Không thay đổi test backend/security khác. Full Maven suite PASS 641/0/0/9 (chạy `mvn clean test` để loại report cũ).

## 2. Tổng hợp kiểm chứng

| Lệnh | Kết quả | Ghi chú |
| :--- | :--- | :--- |
| `node --test test/frontend/*.test.mjs` | 396 PASS / 0 FAIL / 0 SKIP | Test tĩnh |
| `mvn clean test` | 641 test / 0 fail / 0 error / 9 skip | Có render test |
| Browser verification | 27/27 PASS | Chrome thật, local |
| Responsive measurement | 3 route × 5 viewport | Ảnh + JSON |

## 3. Việc chưa làm / cần quyết định

1. **BL-22** cần backend sửa; không có thay đổi backend trong nhánh.
2. **Production verification** cần commit SHA/deployment record và tài khoản được cấp phép; hiện BLOCKED.
3. **BL-25** còn hex/`!important` legacy và CDN external; cache dài hạn cho asset hash bị **DEFERRED** vì cần cấu hình server theo path, không thể sửa an toàn chỉ bằng `period` toàn cục.
4. **BL-17** cần xác minh production đã có PR #59 hay chưa.
5. `currentUser` best-effort ở `FeedPageController`/`CommunityManagePageController` có thể khiến user đăng nhập thấy UI guest khi lookup lỗi; cần backend xử lý nếu muốn loại bỏ hoàn toàn.
6. (Lịch sử) Không commit/push/merge cho tới khi được cho phép — ràng buộc này đã được gỡ khi owner phê duyệt merge qua PR #85.
