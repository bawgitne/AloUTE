# Đóng góp cho AloUTE

Tài liệu này quy định cách đóng góp vào repository AloUTE. Đây là tài liệu trung lập dành cho mọi thành viên, người kiểm thử và người review, không phụ thuộc công cụ soạn thảo.

## 1. Đọc trước khi viết code

Trước khi thực hiện sửa đổi mã nguồn hoặc thêm tính năng mới, vui lòng đọc các tài liệu kỹ thuật chuẩn của dự án:

1. [`README.md`](README.md) — Tổng quan hệ thống, hướng dẫn chạy cục bộ và cấu hình môi trường.
2. [`ARCHITECTURE_TEMPLATE.md`](ARCHITECTURE_TEMPLATE.md) — Khung kiến trúc phân lớp, nguyên tắc Controller - Service - Repository.
3. [`UI_DESIGN_SYSTEM.md`](UI_DESIGN_SYSTEM.md) & [`SCREEN_FLOW.md`](SCREEN_FLOW.md) — Quy chuẩn thiết kế giao diện và luồng màn hình.
4. [`docs/README.md`](docs/README.md) — Bản đồ tài liệu kỹ thuật, báo cáo phân tích và kịch bản demo.

## 2. Branch và commit

- Luôn tạo branch từ `master` mới nhất.
- Tên branch theo loại thay đổi + mô tả ngắn (kebab-case), ví dụ: `feat/post-composer`, `fix/pagination-bound`, `docs/release-runbook`, `chore/deps`. Không dùng tên công cụ soạn thảo làm tiền tố branch.
- Mỗi branch chỉ nên có một mục tiêu chính; tách thay đổi không liên quan sang branch/PR khác.
- Commit message theo Conventional Commits: `type(scope): mô tả ngắn`.
- Không force-push lên `master` hoặc branch dùng chung.

## 3. Pull Request

Mô tả PR cần nêu rõ:

1. Vấn đề/động lực.
2. Thay đổi chính và file ảnh hưởng.
3. Cách kiểm chứng (lệnh đã chạy, kết quả).
4. Giới hạn chưa xác minh.
5. Phương án rollback.

Ưu tiên PR nhỏ, dễ review. Ghi rõ nếu có thay đổi backend/DB/production config.

## 4. Kiểm thử cục bộ

```bash
node --test test/frontend/*.test.mjs   # test tĩnh frontend
mvn -B test                            # test backend (H2)
git diff --check                       # kiểm tra whitespace/conflict markers
```

Test backend chạy trên H2, **không** tự chứng minh tương thích MySQL 8; xác minh MySQL riêng khi cần.

## 5. Quy ước kỹ thuật (tóm tắt)

- Controller chỉ xử lý HTTP và gọi Service; Repository chỉ được Service truy cập.
- `authorId`/`userId` luôn lấy từ Principal đã xác thực, không tin từ request body.
- Mọi phản hồi REST tuân theo envelope `ApiResponse<T>`; phân trang 0-indexed.
- Tuân thủ thiết kế DTO và response contract đã thống nhất.
- Không commit secret (JWT secret, khóa 2FA, mail, Firebase).

## 6. Tài liệu

- Tài liệu kỹ thuật hiện hành dùng tên theo chức năng; không dùng tên công cụ soạn thảo làm tên người thực hiện hay loại báo cáo.
- Báo cáo kiểm thử lịch sử giữ nguyên baseline, số liệu và kết quả tại thời điểm đo; ghi rõ đây là snapshot lịch sử, không phải trạng thái hiện tại.
- Không xóa bằng chứng (evidence) hoặc test case có giá trị truy xuất nguồn gốc.

## 7. Ngôn ngữ

- Tài liệu nghiệp vụ và hướng dẫn: tiếng Việt.
- Định danh code, tên biến, commit type/scope: tiếng Anh.