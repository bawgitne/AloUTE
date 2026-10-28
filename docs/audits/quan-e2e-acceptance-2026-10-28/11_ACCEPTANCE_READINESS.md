# 11 — Acceptance Readiness

## 0. Cập nhật sign-off (integration `25e7595`, 2026-10-28)

| Chỉ số | Giá trị |
|---|---|
| Run | `final-signoff-20261028-r2` trên worktree cô lập `25e7595` + patch fixture poll (test-only) |
| Kết quả | **165/165 ID — 159 PASS / 0 FAIL / 3 BLOCKED / 3 NOT RUN** |
| BL-22 | AUTH-15, AUTH-16, ADM-05, RES-02 **PASS** (fix #88 có trong HEAD tích hợp) |
| Poll fixture | SOC-P40/41/42/43 PASS; đã sửa lỗi fixture hardcode ID, không phải lỗi sản phẩm |
| Bằng chứng | `evidence/final-signoff-integration-25e7595-results.json` |

Lưu ý stack: PR #90 vẫn dựa trên PR #85. Trạng thái PASS của BL-22 chỉ đúng trên HEAD tích hợp `25e7595` (đã có #88); trên branch PR #90 hiện tại 4 case này vẫn FAIL cho đến khi #88 merge và PR #90 được rebase.

## 1. Tuyên bố trạng thái

| Mức | Kết luận | Điều kiện |
|---|---|---|
| **Demo readiness** | **READY WITH LIMITATIONS** | Demo theo `10_DEMO_SCRIPT_VI.md`; tránh upload Firebase |
| **Frontend acceptance** | **PASS WITH LIMITATIONS** | Sign-off 159/165 case PASS; 13 lỗi UI đã sửa; axe 0 serious/critical |
| **Release acceptance** | **PASS WITH LIMITATIONS trên integration `25e7595`** | 0 FAIL; còn 3 BLOCKED + 3 NOT RUN; stack PR #90/#85 phải rebase #88 |
| **Production readiness** | **NOT READY** | Firefox chưa xác nhận + chưa đo PostgreSQL/tải thật + CDN/cache chưa chốt + 3 BLOCKED/3 NOT RUN |

Không có tuyên bố “production ready” trong báo cáo này.

## 2. Readiness theo module

| Module | Chức năng chính | Chất lượng | Trạng thái | Điều kiện còn lại |
|---|---|---|---|---|
| Auth | Đăng nhập/đăng ký/OTP/reset/session/logout | Axe sạch, CSRF/JWT đạt | **PASS-WL** | BL-22 ở route bảo vệ; 2FA setup chưa có contract |
| Feed & bài viết | Tạo/sửa/xóa, like, bookmark, repost, quote, comment, draft, XSS | Axe sạch, responsive | **PASS** | — |
| Poll | Tạo/vote/validation/quyền | Đạt | **PASS** | Poll hết hạn chưa test |
| Hồ sơ | Xem/sửa, follow, tab | Đạt | **PASS-WL** | Upload avatar/cover BLOCKED |
| Cộng đồng | Tạo/join/duyệt bài/role/xóa | Đạt | **PASS-WL** | Private join chưa có đặc tả/API |
| Tin nhắn | Danh sách, gửi, realtime, unread, lỗi mạng | Đạt | **PASS** | — |
| Thông báo | Sinh/lọc/đọc/xóa/XSS | Đạt | **PASS** | — |
| Admin | KPI, user/post, report, remove/restore, audit | Đạt | **PASS-WL** | ADM-05 BL-22 |
| Accessibility | 20 route + keyboard | 0 serious/critical | **PASS-WL** | Screen reader/zoom chưa test |
| Responsive | 360/768/1440 | Không tràn ngang | **PASS** | Mobile browser thật chưa test |
| Cross-browser | Chrome/Edge/WebKit | Đạt | **PASS-WL** | Firefox BLOCKED |
| Performance | Feed + API + CSS | Đạt ngân sách local | **PASS-WL** | Chưa đo production/DB thật |

## 3. Gate release

| Gate | Trạng thái | Bằng chứng |
|---|---|---|
| Critical paths E2E | PASS trừ route bảo vệ | `02_FULL_FUNCTIONAL_MATRIX.md` |
| Không lỗi console | PASS | MSG-13, `assertClean` |
| Accessibility serious/critical | PASS | `05_ACCESSIBILITY_REPORT.md` |
| Responsive | PASS | `06_RESPONSIVE_CROSS_BROWSER.md` |
| Cross-browser | CONDITIONAL | Firefox BLOCKED |
| Security cơ bản (XSS/CSRF/session/tenant) | PASS | AUTH-12/13/24, SOC-P21, MSG-07, MSG-12, RES-05 |
| Dọn dữ liệu test | PASS | SOC-P90, COM-90, CLEAN-01 |
| Backend error UX (BL-22) | **PASS trên `25e7595`** (FAIL trên baseline `4d00b6a`) | AUTH-15/16, ADM-05, RES-02 — `final-signoff-integration-25e7595-results.json` |
| Performance production | NOT VERIFIED | chỉ local H2 |
| Tích hợp integration `e12b21b` + frontend QA | PASS (không regression) | `12_FINAL_HANDOFF.md` mục 4; 0 khác biệt trạng thái |

## 4. Rủi ro và ưu tiên

| Ưu tiên | Rủi ro | Xác suất | Tác động | Hành động |
|---|---|---|---|---|
| P1 | BL-22: JSON thô khi guest/non-admin | Cao | Cao (UX + niềm tin bảo mật) | **Đã sửa qua #88 (merged)**; QA rerun 4 case PASS trên `25e7595` |
| P2 | Firebase thiếu service account | Cao ở môi trường mới | Trung bình (mất upload) | Cấp secret qua secret manager trước demo |
| P2 | Private community chưa có luồng join | Chắc chắn | Trung bình (thiếu tính năng) | Chốt đặc tả + endpoint |
| P2 | Chưa test PostgreSQL/tải thật | Trung bình | Cao nếu release | Load test + DB thật trước production |
| P3 | Firefox crash môi trường | Trung bình | Trung bình | Chạy Firefox ESR/container |
| P3 | CDN external 4 request, CSS không ETag | Trung bình | Thấp–trung bình | Cân nhắc self-host/SRI/ETag |
| P3 | 2FA setup ngoài OpenAPI | Thấp | Thấp | Bổ sung contract nếu release cần 2FA |

## 5. Điều kiện để chuyển sang READY

1. 4 case BL-22 chuyển PASS trên môi trường tích hợp. **Đã đạt trên `25e7595`** (fix #88) — cả 4 PASS. Trước đó trên `e12b21b` cả 4 FAIL; giữ gate rerun sau mỗi lần rebase stack.
2. Chạy lại smoke Firefox (hoặc chấp nhận danh sách trình duyệt hỗ trợ chính thức).
3. Cấu hình Firebase Storage cho môi trường demo và PASS upload.
4. Chốt đặc tả private community; bổ sung test nếu có.
5. Đo hiệu năng trên PostgreSQL + dữ liệu gần thực + mạng throttled; chốt SLO.
6. Quyết định CDN/SRI/ETag và cache asset.
7. Bổ sung kiểm tra screen reader/zoom 200% nếu acceptance bao gồm WCAG đầy đủ.

## 6. Phạm vi không được tuyên bố

- Không tuyên bố “WCAG AAA” hay “accessible đầy đủ”: mới kiểm tra axe + keyboard cơ bản.
- Không tuyên bố “ổn định dưới tải”: chưa load test.
- Không tuyên bố “an toàn tuyệt đối”: mới kiểm tra XSS/CSRF/session/tenant ở các luồng chính.
- Không tuyên bố “tương thích mọi trình duyệt”: Firefox chưa chạy được.
- Không tuyên bố “production ready”: xem mục 1.
