# 06 — Responsive & Cross-browser

- Ngày chạy: 2026-10-28
- Base URL: `http://127.0.0.1:8082`
- Bằng chứng: `test-results/quan-e2e-acceptance/cross-run-2/results.json`, `.../screenshots/`
- Kết quả suite: RESP-01..06 PASS, XB-01/02/04 PASS, XB-03 BLOCKED (lỗi môi trường)

## 1. Ma trận viewport

Mỗi viewport mở trang bằng phiên thật, đo `document.documentElement.scrollWidth` so với `clientWidth` (ngưỡng sai số 1 px), kiểm tra phần tử điều hướng đặc thù và chụp ảnh.

| ID | Viewport | Trang | Kỳ vọng | Đo được | Kết quả |
|---|---|---|---|---|---|
| RESP-01 | 360×800 | `/feed` | Không tràn ngang, menu offcanvas hiện, sidebar ẩn | scrollWidth 360 / clientWidth 360; toggler hiện; `aside.aloute-left-panel` ẩn | PASS |
| RESP-02 | 768×1024 | `/feed` | Sidebar hiện, không tràn ngang | 768 / 768 | PASS |
| RESP-03 | 1440×900 | `/feed` | Sidebar + ô tìm kiếm hiện, không tràn ngang | 1440 / 1440 | PASS |
| RESP-04 | 360×800 | `/messages` | Không tràn ngang, pane hội thoại dùng được | 360 / 360; pane conversation hiện, pane chat ẩn | PASS |
| RESP-05 | 360×800 | `/admin` | Không tràn ngang, bảng cuộn trong container | 360 / 360; 1 `.admin-table-container` | PASS |
| RESP-06 | 360×800 | `/feed` | Touch target ≥ 24×24 px | Tất cả nút navbar + interaction bar đạt | PASS |

Ảnh:
- `screenshots/resp-360-feed.png`
- `screenshots/resp-768-feed.png`
- `screenshots/resp-1440-feed.png`
- `screenshots/resp-360-messages.png`
- `screenshots/resp-360-admin.png`

**Giới hạn:** chỉ đo trang tĩnh sau khi tải; chưa đo bàn phím ảo, xoay ngang, text zoom 200%, hoặc nội dung dài bất thường (tên 200 ký tự, code block rộng).

## 2. Cross-browser smoke

Kịch bản: mở `/login`, kiểm tra HTTP 200, `html lang="vi"`, title, body background không trong suốt; đăng nhập API; mở `/feed` và đếm `.post-card`; thu console/page error.

| ID | Engine | Version | Kết quả | Bằng chứng |
|---|---|---|---|---|
| XB-01 | Chromium (Chrome) | 153.0.8010.53 | PASS | 4 bài trong feed, body `rgb(248, 250, 252)`, token `#0f172a` |
| XB-02 | Chromium (Edge) | 156.0.4314.50 | PASS | 4 bài, cùng token/màu nền |
| XB-03 | Firefox | 157.0 | **BLOCKED** | `browserContext.newPage: Target crashed` ngay khi tạo trang, cả headed lẫn headless, kể cả context tối thiểu |
| XB-04 | WebKit (Safari engine) | 27.2 | PASS | 4 bài, cùng token/màu nền |

Firefox không phải lỗi sản phẩm: cùng bản Playwright, cùng máy, Chromium/WebKit tạo trang bình thường; Firefox crash trước khi tải bất kỳ URL nào. Đã thử nhiều cấu hình context và chạy headed nhưng vẫn treo/crash. Cần môi trường khác (container Linux hoặc Firefox ESR) để xác nhận.

**Không được suy diễn:** PASS trên Chromium/WebKit không đảm bảo PASS trên Firefox/Safari thật. XB-03 giữ trạng thái BLOCKED.

## 3. Phụ thuộc CDN

Trang tải 4 request ngoài (Bootstrap CSS/JS, Bootstrap Icons, Google Fonts). Điều này ảnh hưởng:
- Hiển thị icon/layout nếu CDN bị chặn (mạng nội bộ, chính sách trường).
- Đo hiệu năng mạng thật (xem `07_PERFORMANCE_REPORT.md`).
- CSP/SRI nếu triển khai production.

Không có thay đổi nào được thực hiện với CDN trong phạm vi QA này (thay đổi hạ tầng ngoài phạm vi frontend an toàn).

## 4. Kết luận

Layout công khai, feed, tin nhắn và admin không tràn ngang ở 3 breakpoint chính; touch target đạt ngưỡng tối thiểu WCAG 2.2 (24 px). Cross-browser đạt trên Chromium (Chrome/Edge) và WebKit; Firefox bị chặn bởi môi trường, cần chạy lại trước khi tuyên bố hỗ trợ đầy đủ.
