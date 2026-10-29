# Hồ sơ Frontend, UI/UX & E2E

> **Thành viên:** Phạm Minh Quân (MSSV 24110311)<br>
> **Vai trò:** Frontend Lead, UI/UX và kiểm thử E2E<br>
> **Phạm vi:** `src/main/resources/templates/**`, `src/main/resources/static/{css,js,images}/**`, `docs/ui/wireframes/**`, `test/frontend/**`, `test/e2e/quan-acceptance/**`

## 1. Mục đích

Thư mục tập hợp tài liệu thiết kế, bằng chứng hiện thực hóa giao diện và hướng dẫn kiểm thử frontend của AloUTE. Các tài liệu tại đây phục vụ truy xuất nguồn gốc thiết kế và kiểm chứng giao diện; số liệu đo trong tài liệu lịch sử được giữ nguyên tại thời điểm đo.

## 2. Tài liệu

| Tài liệu | Nội dung |
| :--- | :--- |
| [`QUAN_WEEK1_EVIDENCE.md`](QUAN_WEEK1_EVIDENCE.md) | Thiết kế wireframe và đặc tả giao diện ban đầu |
| [`QUAN_WEEK2_EVIDENCE.md`](QUAN_WEEK2_EVIDENCE.md) | Hiện thực hóa layout, fragment và các màn hình Thymeleaf |
| [`QUAN_WEEK3_EVIDENCE.md`](QUAN_WEEK3_EVIDENCE.md) | Tích hợp UI, ảnh runtime và các vòng sửa lỗi frontend |
| [`UI_TRACEABILITY_MATRIX.md`](UI_TRACEABILITY_MATRIX.md) | Truy xuất wireframe → template → ảnh runtime → test |
| [`FRONTEND_TEST_CASES.md`](FRONTEND_TEST_CASES.md) | Danh mục test case và kết quả tại thời điểm đo |
| [`RESPONSIVE_ACCESSIBILITY_REPORT.md`](RESPONSIVE_ACCESSIBILITY_REPORT.md) | Đo responsive 360–1440px và kiểm tra accessibility |
| [`../ui/wireframes/README.md`](../ui/wireframes/README.md) | Đặc tả 10 wireframe SVG |

## 3. Mã nguồn và bằng chứng

| Nhóm | Vị trí |
| :--- | :--- |
| Layout & fragment | [`templates/layouts/`](../../src/main/resources/templates/layouts/), [`templates/fragments/`](../../src/main/resources/templates/fragments/) |
| Trang theo module | [`templates/`](../../src/main/resources/templates/) |
| CSS & design tokens | [`static/css/`](../../src/main/resources/static/css/) |
| JavaScript | [`static/js/`](../../src/main/resources/static/js/) |
| Ảnh runtime | [`docs/report-assets/runtime-ui/`](../report-assets/runtime-ui/RUNTIME_UI_CAPTURE_REPORT_2026-10-20.md) |
| Ảnh mobile | [`docs/report-assets/mobile-runtime-ui/`](../report-assets/mobile-runtime-ui/SCREENSHOT_INDEX_MOBILE.md) |
| Kết quả test local | [`test-results/quan-frontend-hardening/`](../../test-results/quan-frontend-hardening/) |

## 4. Kiểm thử frontend

```bash
node --test test/frontend/*.test.mjs     # test tĩnh template/CSS/JS
mvn -B test                              # test backend trên H2
```

Browser E2E nằm tại [`test/e2e/quan-acceptance/`](../../test/e2e/quan-acceptance/), dùng Playwright với tài khoản seed local của profile `dev`. Không sử dụng credential production.

- [`run.mjs`](../../test/e2e/quan-acceptance/run.mjs): suite acceptance theo module.
- [`admin-account-ui.mjs`](../../test/e2e/quan-acceptance/admin-account-ui.mjs): luồng quản trị tài khoản.
- [`lib/harness.mjs`](../../test/e2e/quan-acceptance/lib/harness.mjs): đăng nhập, mở trang, gọi API và ghi kết quả.

## 5. Giới hạn

- Kết quả test trong tài liệu lịch sử phản ánh baseline tại ngày đo, không phải trạng thái `master` hiện tại.
- Môi trường local dùng H2 và seed dev; không đại diện dữ liệu hoặc hiệu năng production.
- Các tích hợp Firebase Storage, FCM và SMTP phụ thuộc cấu hình bên ngoài; kiểm thử end-to-end cần credential môi trường đích.
