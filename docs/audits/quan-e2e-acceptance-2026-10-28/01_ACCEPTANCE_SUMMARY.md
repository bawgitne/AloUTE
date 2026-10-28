# 01 — Acceptance Summary

**Ngày:** 2026-10-28 · **Branch QA:** `test/quan-e2e-acceptance` · **Base:** `74c8a6f`

## 1. Kết luận ngắn

Frontend AloUTE đã được kiểm thử end-to-end bằng browser thật trên 6 module chính. **155/165 case PASS (93,9%)**. Toàn bộ 4 case FAIL đều cùng một lỗi backend **BL-22** (page route trả JSON thay vì HTML/redirect), không phải lỗi UI. Sau khi sửa 12 lỗi frontend/accessibility, axe không còn vi phạm serious/critical trên 20 route; responsive và hiệu năng local đạt ngân sách; 3 engine browser PASS, Firefox bị chặn bởi môi trường.

**Trạng thái acceptance: PASS WITH LIMITATIONS cho phạm vi frontend; NOT READY cho production do BL-22 và các hạng mục chưa kiểm chứng.**

Đã chạy lại toàn bộ 165 case trên ZCode HEAD `e12b21b` với đúng 23 thay đổi frontend QA: kết quả **155/4/3/3, 0 khác biệt trạng thái** so với baseline; BL-22 vẫn mở. Chi tiết `12_FINAL_HANDOFF.md`.

## 2. Con số

| Chỉ số | Giá trị |
|---|---|
| Tổng case | **165** |
| PASS | **155** |
| FAIL | **4** (AUTH-15, AUTH-16, ADM-05, RES-02 — BL-22) |
| BLOCKED | **3** (SOC-P06 Firebase, COM-14b đặc tả, XB-03 Firefox) |
| NOT RUN | **3** (AUTH-26b 2FA, SOC-P47 poll hết hạn, SOC-P52 custom feed) |
| Kịch bản E2E | 7 (A–G), đã chạy thực tế |
| Route quét axe | 20, 0 vi phạm serious/critical |
| Viewport | 360×800, 768×1024, 1440×900 |
| Engine | Chrome 153, Edge 156, WebKit 27.2 PASS; Firefox 157 BLOCKED |
| Lỗi UI phát hiện | 14 (12 đã sửa, 1 backend ticket, 1 hạ tầng không sửa) |
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
| Responsive | Không tràn ngang 360/768/1440; touch target ≥24 px | RESP-01..06 |
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
| Tích hợp ZCode `e12b21b` | Không regression so với baseline | Đạt — 0 khác biệt trạng thái; BL-22 vẫn FAIL |

## 7. Rủi ro còn lại

1. **BL-22 (P1)** — người dùng gặp JSON thô khi chưa đăng nhập/không đủ quyền; ảnh hưởng trải nghiệm và kỳ vọng bảo mật.
2. **Firebase upload (P2)** — không thể demo avatar/cover/ảnh nếu môi trường thiếu service account.
3. **Private community (P2)** — chưa có đặc tả; không thể acceptance.
4. **Firefox (P3)** — chưa xác nhận tương thích.
5. **CDN + cache không ETag (P3)** — rủi ro giao diện khi mạng nội bộ chặn CDN; asset cũ tồn tại 12h.
6. **Dữ liệu/DB production (P2)** — toàn bộ hiệu năng và hành vi dựa trên H2 seed nhỏ.

## 8. Khuyến nghị

- ZCode xử lý BL-22 trước khi release; QA chạy lại 4 case AUTH-15/16, ADM-05, RES-02. Đã xác minh lại trên `e12b21b`: cả 4 case vẫn FAIL.
- Cấp Firebase service account cho môi trường demo; chốt đặc tả private community.
- Chạy lại cross-browser trên Firefox ESR/container Linux.
- Đo lại hiệu năng với PostgreSQL + dữ liệu lớn trước khi cam kết SLO.
- Dùng `10_DEMO_SCRIPT_VI.md` cho demo; tránh đường dẫn bảo vệ khi là guest và tránh upload nếu thiếu Firebase.
