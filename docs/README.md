# Tài liệu AloUTE

Trang này là bản đồ tài liệu của repository: mỗi chủ đề có một tài liệu chính, tách rõ tài liệu hiện hành và snapshot lịch sử.

## 1. Bản đồ tài liệu kỹ thuật hiện hành

Tất cả các tài liệu kỹ thuật, quy chuẩn kiến trúc và kế hoạch thực thi được tổ chức trong thư mục `docs/` và thư mục gốc:

## 2. Tài liệu hiện hành

| Chủ đề | Tài liệu |
| :--- | :--- |
| Tổng quan dự án, chạy local, test | [`README.md`](../README.md) |
| Đóng góp, quy ước branch/commit/PR | [`CONTRIBUTING.md`](../CONTRIBUTING.md) |
| Kiến trúc tổng quan | [`ARCHITECTURE_TEMPLATE.md`](../ARCHITECTURE_TEMPLATE.md), [`SCREEN_FLOW.md`](../SCREEN_FLOW.md) |
| Thiết kế giao diện | [`UI_DESIGN_SYSTEM.md`](../UI_DESIGN_SYSTEM.md), [`docs/ui/wireframes/`](ui/wireframes/README.md) |
| Nghiệp vụ Authentication & Authorization | [`AUTHEN_AUTHOR_BUSINESS_PHASES.md`](AUTHEN_AUTHOR_BUSINESS_PHASES.md) |
| Thiết kế dữ liệu/xử lý | [`BAO_CAO_THIET_KE_DEN_XU_LY.md`](BAO_CAO_THIET_KE_DEN_XU_LY.md) |
| Đặc tả admin | [`ALOUTE_ADMIN_DASHBOARD_SPEC.md`](ALOUTE_ADMIN_DASHBOARD_SPEC.md) |
| Kế hoạch 2FA / Settings | [`TWO_FACTOR_AUTHENTICATION_PLAN.md`](TWO_FACTOR_AUTHENTICATION_PLAN.md), [`SETTINGS_IMPLEMENTATION_PLAN.md`](SETTINGS_IMPLEMENTATION_PLAN.md) |
| Kịch bản demo | [`DEMO_CHECKLIST_VI.md`](DEMO_CHECKLIST_VI.md) |
| Bàn giao Flutter | [`FLUTTER_HANDOFF.md`](FLUTTER_HANDOFF.md) |

## 3. Release và triển khai

| Chủ đề | Tài liệu |
| :--- | :--- |
| Deployment runbook (thủ công, có điều kiện) | [`release/MANUAL_DEPLOYMENT_RUNBOOK.md`](release/MANUAL_DEPLOYMENT_RUNBOOK.md) |
| Bằng chứng merge lô đầu tiên (PR #74–#77) | [`release/BATCH1_MERGE_EVIDENCE_2026-10-28.md`](release/BATCH1_MERGE_EVIDENCE_2026-10-28.md) |

## 4. Snapshot lịch sử (không phải trạng thái hiện tại)

Các tài liệu dưới đây giữ nguyên baseline, số liệu và kết quả tại thời điểm đo:

| Snapshot | Nội dung |
| :--- | :--- |
| [`audits/SYSTEM_TEST_REPORT_2026-10-23.md`](audits/SYSTEM_TEST_REPORT_2026-10-23.md) | Kiểm thử toàn hệ thống 2026-10-23; bằng chứng ảnh trong [`audits/evidence-2026-10-23/`](audits/evidence-2026-10-23/) |
| [`report-assets/runtime-ui/`](report-assets/runtime-ui/RUNTIME_UI_CAPTURE_REPORT_2026-10-20.md) | Bộ ảnh giao diện chạy thật (web) 2026-10-20/21 |
| [`report-assets/mobile-runtime-ui/`](report-assets/mobile-runtime-ui/SCREENSHOT_INDEX_MOBILE.md) | Bộ ảnh giao diện di động |
| [`quan/`](quan/README.md) | Hồ sơ frontend/UI-UX/E2E (baseline 2026-10-27, hardening đã merge qua PR #85) |

## 5. Hồ sơ đội ngũ

| Thư mục | Nội dung |
| :--- | :--- |
| [`khoi/`](khoi/WEEK2_WEEK3_IMPLEMENTATION.md) | Backend, tài khoản test dev |
| [`bang/`](bang/USE_CASE_MATRIX.md) | Use case, test case, sequence diagram |
| [`quan/`](quan/README.md) | Frontend, responsive, accessibility, E2E |
| [`ui/`](ui/wireframes/README.md) | Wireframe/mockup |

## 6. Tài liệu local-only (không có trong repository)

Một số hồ sơ chỉ tồn tại trên máy cục bộ và **không** được commit (báo cáo audit nội bộ, kết quả test thô, worktree tạm). Chúng không phải nguồn sự thật và không nên được dẫn chiếu như bằng chứng công khai. Khi cần dẫn chiếu, dùng bằng chứng đã commit trong `docs/audits/evidence-*`, `docs/report-assets/`, `test-results/` hoặc PR.

## 7. Quy ước tài liệu

- Tên file/tài liệu theo chức năng; không dùng tên công cụ soạn thảo làm tên người thực hiện hay loại báo cáo.
- Snapshot lịch sử phải ghi rõ ngày đo, baseline và bản chất "lịch sử", không phản ánh `master` hiện tại.
- Khi có khác biệt giữa tài liệu tóm tắt và file owner trong `skills/`, **owner file thắng**.