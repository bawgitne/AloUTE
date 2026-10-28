# Báo cáo responsive & accessibility — Frontend hardening

> **Ngày đo:** 2026-10-27
> **Nhánh:** `fix/quan-frontend-hardening-20261027` (working tree chưa commit), baseline `99ab72e`
> **Môi trường:** local `http://127.0.0.1:8081`, profile `dev`, H2 in-memory
> **Công cụ:** Playwright 1.64.1 + Chrome thật (`channel: 'chrome'`), headless; viewport height 900
> **Dữ liệu thô:** [`baseline/measurements.json`](../../test-results/quan-frontend-hardening/baseline/measurements.json), [`after/measurements.json`](../../test-results/quan-frontend-hardening/after/measurements.json)

## 1. Phạm vi đo

- Route: `/feed`, `/search?q=hoc-tap`, `/posts/1`.
- Viewport: **360, 375, 390, 768, 1440**.
- Mỗi lần đo ghi: overflow ngang, vị trí nút Đăng ký, `<h1>`, skip link, composer, reply form, action report, link auth `href="#"`, các target hiển thị có kích thước `<44px` (navbar, composer, interaction bar, `.aloute-auth-cta`, post card, dropdown, reply form).
- Đây là **local runtime**, không phải production. Production audit ngày 2026-10-27 chỉ đo được ~360.8px CSS và không có tài khoản đăng nhập.

## 2. Kết quả tổng hợp trước → sau

### 2.1. `/feed`

| Viewport | Overflow ngang | Target <44px | `<h1>` | Skip link | Nút Đăng ký (right) | Composer guest | Report actions | Auth `href="#"` |
| ---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| 360 | false → false | 7 → **0** | 1 → 1 | không → **có** | 356 → 352 | hiện → **ẩn** | 4 → **0** | 6 → **0** |
| 375 | false → false | 7 → **0** | 1 → 1 | không → **có** | 371 → 367 | hiện → **ẩn** | 4 → **0** | 6 → **0** |
| 390 | false → false | 7 → **0** | 1 → 1 | không → **có** | 386 → 382 | hiện → **ẩn** | 4 → **0** | 6 → **0** |
| 768 | false → false | 31 → **0** | 1 → 1 | không → **có** | 752 → 752 | hiện → **ẩn** | 4 → **0** | 6 → **0** |
| 1440 | false → false | 31 → **0** | 1 → 1 | không → **có** | 1424 → 1424 | hiện → **ẩn** | 4 → **0** | 6 → **0** |

### 2.2. `/search?q=hoc-tap`

| Viewport | Overflow ngang | Target <44px | `<h1>` | Skip link | Nút Đăng ký (right) | Report actions | Auth `href="#"` |
| ---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| 360 | false → false | 3 → **0** | **0 → 1** | không → **có** | 356 → 352 | 1 → **0** | 6 → **0** |
| 375 | false → false | 3 → **0** | **0 → 1** | không → **có** | 371 → 367 | 1 → **0** | 6 → **0** |
| 390 | false → false | 3 → **0** | **0 → 1** | không → **có** | 386 → 382 | 1 → **0** | 6 → **0** |
| 768 | false → false | 10 → **0** | **0 → 1** | không → **có** | 752 → 752 | 1 → **0** | 6 → **0** |
| 1440 | false → false | 10 → **0** | **0 → 1** | không → **có** | 1424 → 1424 | 1 → **0** | 6 → **0** |

### 2.3. `/posts/1`

| Viewport | Overflow ngang | Target <44px | `<h1>` | Skip link | Reply form guest | Report actions | Auth `href="#"` |
| ---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| 360 | false → false | 4 → **0** | 1 → 1 | không → **có** | hiện → **ẩn (prompt)** | 1 → **0** | 6 → **0** |
| 375 | false → false | 4 → **0** | 1 → 1 | không → **có** | hiện → **ẩn (prompt)** | 1 → **0** | 6 → **0** |
| 390 | false → false | 4 → **0** | 1 → 1 | không → **có** | hiện → **ẩn (prompt)** | 1 → **0** | 6 → **0** |
| 768 | false → false | 11 → **0** | 1 → 1 | không → **có** | hiện → **ẩn (prompt)** | 1 → **0** | 6 → **0** |
| 1440 | false → false | 11 → **0** | 1 → 1 | không → **có** | hiện → **ẩn (prompt)** | 1 → **0** | 6 → **0** |

Ghi chú: “target <44px” dùng đúng tiêu chí của harness (`width < 44 || height < 44`) trên các nhóm navbar, composer, interaction bar, `.aloute-auth-cta`, post card, reply form. Con số 31 ở desktop chủ yếu là interaction button 38px và dropdown 25×30px trước khi sửa; con số này không tính CTA guest trong card/sidebar trước khi harness bổ sung selector, nên bảng được đo lại toàn bộ sau khi thêm `.aloute-auth-cta`.

## 3. BL-17 — Mobile navbar và touch target

### 3.1. Sự khác biệt giữa audit live và baseline local

- Audit production ngày 2026-10-27 ghi nhận nút Đăng ký `right = 356.8px` trên visual viewport `360.8px`, nhãn bị cắt, và 10 target `<40px` — xem [`03_LIVE_WEBSITE_AUDIT.md` §2.2](../audits/independent-full-audit-2026-10-27/03_LIVE_WEBSITE_AUDIT.md).
- Baseline local tại `99ab72e` **không tái hiện cắt**: không có overflow ngang; nút Đăng ký right = 356/371/386 ở 360/375/390, tức còn **4px** mép phải, không âm.
- Nguyên nhân khác biệt có bằng chứng: baseline local đã chứa PR [#59](https://github.com/bawgitne/AloUTE/pull/59) (commit [`c1d674b`](https://github.com/bawgitne/AloUTE/commit/c1d674b), 2026-10-22) “keep the mobile navbar inside the viewport” cùng regression test [`navbar-responsive-overflow.test.mjs`](../../test/frontend/navbar-responsive-overflow.test.mjs). Production không xác minh được commit, nên có khả năng live đang chạy bản trước PR #59.
- Vì vậy báo cáo **không tuyên bố đã tái hiện lỗi cắt local**; chỉ tuyên bố: (a) lỗi target nhỏ tái hiện được, (b) mép phải chỉ 4px nên rủi ro cắt khi scrollbar/zoom, (c) sau hardening mép tăng lên 8px.

### 3.2. Thay đổi trong nhánh

- [`app.css`](../../src/main/resources/static/css/app.css): `.aloute-navbar .btn` đặt `min-height: 44px`; `.aloute-navbar .dropdown-menu .btn` override `min-height: 0` để text action dày đặc không bị kéo cao; `.aloute-auth-cta` 44px cho CTA đăng nhập ngoài navbar; `.post-card-header .dropdown > .btn` 44×44; poll vote và reply submit 44px; search tab 44px **trên mobile** (dưới 576px); guest actions có margin phải nhỏ dưới 576px.
- `.interaction-btn` tăng `min-height` từ 38px lên 44px.
- Không thay layout navbar, không ẩn thêm control, không đổi breakpoint hiện có.

### 3.3. Kết quả

- Target `<44px`: **0** trên cả 3 route × 5 viewport sau sửa (trước: 3–31 tùy route/viewport).
- Không overflow ngang ở mọi viewport trước và sau.
- Nút Đăng ký mobile: 356→352 (360), 371→367 (375), 386→382 (390) — mép 4px→8px.
- Desktop không đổi vị trí (752/1424).

### 3.4. CTA đăng nhập của guest

Đo trực tiếp `getBoundingClientRect()` của mọi `.aloute-auth-cta` trong DOM (sidebar offcanvas vẫn có kích thước dù bị ẩn ở mobile):

| Viewport | `/feed` | `/posts/1` | Kết luận |
| ---: | :--- | :--- | :--- |
| 360 | Card: 86.9×**44**, 102.3×**44**; sidebar offcanvas ẩn: 293×**44** | Prompt: 276×**44**; sidebar offcanvas ẩn: 293×**44** | Đạt |
| 375 | Card: 86.9×**44**, 102.3×**44**; sidebar offcanvas ẩn: 308×**44** | Prompt: 291×**44**; sidebar offcanvas ẩn: 308×**44** | Đạt |
| 390 | Card: 86.9×**44**, 102.3×**44**; sidebar offcanvas ẩn: 323×**44** | Prompt: 306×**44**; sidebar offcanvas ẩn: 323×**44** | Đạt |
| 768 | Sidebar trái: 195.3×**44**; card: 86.9×**44**, 102.3×**44** | Sidebar trái: 195.3×**44**; prompt/reply CTA: 86.9×**44** | Đạt |
| 1440 | Sidebar trái: 260×**44**; card: 86.9×**44**, 102.3×**44** | Sidebar trái: 260×**44**; CTA: 86.9×**44** | Đạt |

Trước sửa các nút này dùng `btn-sm` (cao ~27.5px) và chỉ được đo gián tiếp; sau sửa tất cả đạt sàn 44px.

## 4. Accessibility

### 4.1. Skip link

- Vị trí: phần tử focusable đầu tiên trong [`layouts/default.html`](../../src/main/resources/templates/layouts/default.html), trước `<header>`; [`layouts/single.html`](../../src/main/resources/templates/layouts/single.html) (settings) cũng có skip link cùng target.
- Khi chưa focus: `top: -100px` (ẩn khỏi viewport).
- Khi focus: `top: 8px`, cao 39.25px, hiện đầy đủ; Enter đưa focus tới `#main-content` (`activeId = "main-content"`).
- CSS dùng token `--aloute-accent`, `--aloute-space-*`, `--aloute-radius-md`, `--aloute-shadow-lg`; không thêm hex.
- Bằng chứng browser: check 1–3 và check 25 trong [`verification.json`](../../test-results/quan-frontend-hardening/after/verification.json).

### 4.2. Heading

- `/feed`: 1 `<h1>` trước và sau.
- `/posts/1`: 1 `<h1>` trước và sau.
- `/search`: **0 → 1**; thêm `<h1 class="visually-hidden">Tìm kiếm trên AloUTE</h1>` trong [`search/index.html`](../../src/main/resources/templates/search/index.html).
- Chưa kiểm tra thứ tự heading `h1→h2→h3` trên toàn bộ trang; chỉ đếm `<h1>`.

### 4.3. Touch target

- Sàn 44px được áp cho navbar, interaction bar, CTA guest (`.aloute-auth-cta`), dropdown bài viết, poll vote, reply submit; search tab áp **trên mobile** (<576px), desktop giữ kích thước hiện có (31.3px) vì không phải touch target.
- Đo sau: 0 target `<44px` trong phạm vi harness; CTA guest đo riêng đạt 44px ở cả 5 viewport.
- Text action “Đã đọc tất cả” trong dropdown thông báo được giữ compact có chủ đích (không phải navigation target); được kiểm bằng browser check 26.
- Không đo target ngoài phạm vi harness (ví dụ trong modal, sidebar footer, admin table).

### 4.4. Keyboard

- Đã kiểm: Tab đầu tiên → skip link; Enter → main content.
- Chưa kiểm: toàn bộ tab order, focus trap trong modal, Escape đóng dropdown/modal, focus restore sau modal, keyboard cho poll/messaging.

### 4.5. Điều chưa đo

- Contrast ratio: không chạy axe/Lighthouse; không đo màu thực tế.
- Screen reader: không test NVDA/VoiceOver.
- Zoom 200%/400%: không test.
- `prefers-reduced-motion`: CSS có thể đã hỗ trợ nhưng chưa kiểm.
- Focus visible cho mọi control: chưa audit đầy đủ.

## 5. Ảnh bằng chứng

| Ảnh | Nội dung |
| :--- | :--- |
| [`baseline/feed-360.png`](../../test-results/quan-frontend-hardening/baseline/feed-360.png) | Feed guest trước sửa, 360px |
| [`after/feed-360.png`](../../test-results/quan-frontend-hardening/after/feed-360.png) | Feed guest sau sửa, 360px (CTA đăng nhập, skip link ẩn) |
| [`baseline/search-360-full.png`](../../test-results/quan-frontend-hardening/baseline/search-360-full.png) | Search trước sửa, không `<h1>` |
| [`after/search-360-full.png`](../../test-results/quan-frontend-hardening/after/search-360-full.png) | Search sau sửa |
| [`after/verify-guest-feed-360.png`](../../test-results/quan-frontend-hardening/after/verify-guest-feed-360.png) | Browser verification guest |
| [`after/verify-auth-feed-390.png`](../../test-results/quan-frontend-hardening/after/verify-auth-feed-390.png) | Browser verification user đăng nhập local |

Ảnh desktop/tablet: `feed-768.png`, `feed-1440.png`, `search-768.png`, `search-1440.png` trong cùng thư mục.

## 6. Kết luận

- **Đạt:** không overflow ngang 360–1440; touch target 44px trong phạm vi đo (gồm CTA guest); skip link hoạt động ở cả layout `default` và `single`; search có `<h1>`; guest surfaces được gate đúng.
- **Một phần:** BL-17 — phần target đã sửa và đo; phần “cắt nút” không tái hiện local vì PR #59 đã có trong baseline, production cần xác minh commit.
- **Chưa đạt/ chưa đo:** WCAG audit đầy đủ, contrast, screen reader, keyboard toàn trang, 320px, production authenticated.
