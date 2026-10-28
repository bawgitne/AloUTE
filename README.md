# AloUTE

**AloUTE** là ứng dụng web mạng xã hội dành cho sinh viên Trường Đại học Sư phạm Kỹ thuật TP.HCM (HCMUTE): đăng bài, tương tác, tham gia cộng đồng, nhắn tin, nhận thông báo và quản trị nội dung. Repository này chứa **máy chủ web (server-rendered + REST API)** và một **client di động Flutter** riêng biệt trong [`aloute_mobile/`](aloute_mobile/).

> **Nguồn sự thật của dự án.** Mọi quy tắc nghiệp vụ, hợp đồng API và quy ước kỹ thuật được quản lý tập trung trong thư mục [`skills/`](skills/INDEX.md) theo nguyên tắc **ONE RULE → ONE OWNER → ONE SOURCE OF TRUTH**. Trước khi sửa code, đọc [`skills/INDEX.md`](skills/INDEX.md) và [`AGENTS.md`](AGENTS.md).

---

## 1. Mục tiêu dự án

Xây dựng một nền tảng mạng xã hội học đường đầy đủ chức năng, đúng chuẩn kỹ thuật phần mềm: kiến trúc phân lớp rõ ràng, phân quyền chặt chẽ, kiểm thử tự động, tài liệu hóa hợp đồng API, và sẵn sàng cho việc đánh giá đồ án.

## 2. Chức năng đã triển khai (trên nhánh `master`)

| Nhóm | Chức năng |
| :--- | :--- |
| **Tài khoản & Xác thực** | Đăng ký (xác thực OTP email), đăng nhập JWT (cookie HttpOnly), đăng xuất, quên/đặt lại mật khẩu, đổi mật khẩu, xác thực hai lớp (2FA/TOTP + mã dự phòng) |
| **Hồ sơ người dùng** | Trang cá nhân, cập nhật hồ sơ, avatar/ảnh bìa, thiết lập riêng tư tài khoản |
| **Nội dung** | Đăng/sửa/xóa bài viết (văn bản, ảnh, phương tiện), bài viết kèm bình chọn (poll), chủ đề (topic), bài viết nổi bật |
| **Tương tác** | Like, trả lời/bình luận, trích dẫn (quote), đăng lại (repost), lưu bài (bookmark), bỏ theo dõi bài viết (mute) |
| **Quan hệ xã hội** | Theo dõi/bỏ theo dõi, danh sách bạn bè, chặn (block) |
| **Cộng đồng** | Tạo/quản lý cộng đồng, thành viên, vai trò, duyệt bài, kiểm duyệt nội dung, dashboard quản trị cộng đồng |
| **Bảng tin & Khám phá** | Feed cá nhân, feed tùy chỉnh (custom feed), tìm kiếm (người dùng/bài viết/chủ đề) |
| **Nhắn tin & Thông báo** | Hội thoại trực tiếp (DIRECT), tin nhắn thời gian thực (STOMP/WebSocket), thông báo trong ứng dụng, đăng ký nhận thông báo đẩy (FCM) |
| **Quản trị hệ thống** | Bảng điều khiển admin: quản lý người dùng, bài viết, báo cáo vi phạm (report/moderation), vai trò |
| **Giao diện** | Thymeleaf SSR, responsive 360–1440px, hỗ trợ truy cập khách (guest), accessibility cơ bản |

> Các tính năng mô tả ở đây phản ánh trạng thái **đã hợp nhất vào `master`**. Các thay đổi đang trong Pull Request mở chưa được tính là đã có trên `master`.

## 3. Technology stack

| Tầng | Công nghệ |
| :--- | :--- |
| Ngôn ngữ | Java 17 (CI build trên JDK 21) |
| Framework | Spring Boot 3.2.5 — Spring MVC, Spring Security, Spring Data JPA, Spring WebSocket |
| Xác thực | JWT (HttpOnly cookie), Spring Security filter chain, TOTP 2FA |
| Cơ sở dữ liệu | MySQL 8 (production), H2 in-memory (local/test), hỗ trợ PostgreSQL |
| Giao diện | Thymeleaf (SSR), Bootstrap 5, Bootstrap Icons (SRI), Vanilla JavaScript |
| Thời gian thực | STOMP over WebSocket |
| Tích hợp | Firebase Cloud Messaging (thông báo đẩy), SMTP (email/OTP) |
| Build & test | Maven (`mvn`), Node.js built-in test runner cho test frontend |
| Triển khai | Docker / docker-compose, Nginx reverse proxy |
| Di động | Flutter (`aloute_mobile/`) |

## 4. Kiến trúc tổng quan

Kiến trúc phân lớp chuẩn Spring Boot:

```
Controller (HTTP/REST/SSR)  →  Service (nghiệp vụ, @Transactional)  →  Repository (JPA)  →  Database
```

Các quy ước bắt buộc:

- Controller **chỉ** xử lý HTTP và gọi Service.
- Service chứa nghiệp vụ và ranh giới giao dịch; **chỉ** Service được truy cập Repository.
- `authorId`/`userId` **luôn** lấy từ Principal đã xác thực, **không bao giờ** tin từ request body.
- Mọi phản hồi REST tuân theo envelope thống nhất `ApiResponse<T>`; phân trang **0-indexed**.

Chi tiết: [`skills/system_architecture.md`](skills/system_architecture.md), [`skills/backend_rule.md`](skills/backend_rule.md), [`skills/domain_system.md`](skills/domain_system.md).

## 5. Cấu trúc thư mục

```
src/main/java/com/aloute/
├── common/           # ApiResponse, exception (ApiException, ErrorCode), util
├── config/           # Security, WebSocket/STOMP, CORS, seeder, profile guard
├── controller/       # API (REST), page (SSR), ws (WebSocket), advice
├── dto/              # request/response DTO
├── entity/           # JPA entities
├── repository/       # Spring Data JPA repositories
├── security/         # JWT, crypto (2FA), current-user, authorization, handlers
└── service/          # Interface + impl (nghiệp vụ)

src/main/resources/
├── templates/        # Thymeleaf (admin, auth, community, feed, messaging, ...)
├── static/           # css, js, images
├── db/migration/     # Script DDL thủ công (H2 + MySQL 8)
└── application*.yml  # Cấu hình theo profile (dev / prod)

test/frontend/        # Test tĩnh frontend (Node built-in runner, *.test.mjs)
src/test/java/        # Unit + integration test (JUnit, MockMvc, H2)
skills/               # Nguồn sự thật: quy tắc, hợp đồng OpenAPI, kiến trúc
docs/                 # Tài liệu thiết kế, báo cáo bằng chứng
scripts/              # run-local.ps1, test-run-local.ps1, ...
aloute_mobile/        # Client Flutter (ứng dụng riêng)
```

## 6. Phát triển cục bộ

**Yêu cầu môi trường:**

- JDK 17 trở lên (CI dùng 21).
- Maven 3.9+.
- Node.js 20+ (để chạy test frontend).
- Không bắt buộc cài MySQL cho môi trường dev/test — H2 in-memory được dùng mặc định.

**Chạy ứng dụng (khuyến nghị dùng script):**

```powershell
# Windows PowerShell — nạp .env an toàn và chạy Spring Boot từ mã nguồn (profile dev)
./scripts/run-local.ps1
```

Hoặc chạy trực tiếp bằng Maven:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Cấu hình:** các biến môi trường bí mật (JWT secret, khóa 2FA, cấu hình mail, Firebase) được nạp từ file `.env` ở gốc repository (không track trong git). Xem mẫu tên biến trong tài liệu cấu hình; **không commit** bất kỳ giá trị bí mật nào.

**Tài khoản seed dev:** profile `dev` khởi tạo dữ liệu mẫu qua `DevTestDataInitializer` để phục vụ phát triển local. Không sử dụng các tài khoản seed này trên production (xem §9).

## 7. Test

```bash
# Test tĩnh frontend (Node built-in runner)
node --test test/frontend/*.test.mjs

# Toàn bộ test backend (JUnit + integration, chạy trên H2)
mvn -B test

# Đóng gói (bỏ qua test) — tạo target/aloute-app-0.0.1-SNAPSHOT.jar
mvn -B -DskipTests package

# Kiểm tra whitespace/conflict markers
git diff --check
```

CI ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) chạy tuần tự: frontend test → `mvn test` → `mvn package` trên mỗi Pull Request và mỗi lần push vào `master`.

> **Lưu ý:** test backend chạy trên H2, **không** tự động chứng minh tương thích MySQL. Các script DDL MySQL 8 trong `src/main/resources/db/migration/` cần được xác minh riêng trên MySQL thật trước khi triển khai.

## 8. Tài liệu API và thiết kế

| Tài liệu | Nội dung |
| :--- | :--- |
| [`skills/aloute-openapi.yaml`](skills/aloute-openapi.yaml) | Hợp đồng OpenAPI đầy đủ (nguồn sự thật cho endpoint) |
| [`skills/system_architecture.md`](skills/system_architecture.md) | Biên hệ thống, envelope, phân trang, mã lỗi |
| [`skills/domain_system.md`](skills/domain_system.md) | Thực thể, quy tắc nghiệp vụ, phân quyền, ERD/DDL |
| [`UI_DESIGN_SYSTEM.md`](UI_DESIGN_SYSTEM.md) | Hệ thống thiết kế giao diện |
| [`SCREEN_FLOW.md`](SCREEN_FLOW.md) | Luồng màn hình |
| [`docs/ui/wireframes/`](docs/ui/wireframes/README.md) | Wireframe |

## 9. Cấu hình an toàn & lưu ý vận hành

- **Bí mật:** không bao giờ commit `JWT secret`, `ALOUTE_TWO_FACTOR_MASTER_KEY`, thông tin mail hay Firebase vào repository.
- **Profile production:** production phải chạy với profile `prod` và cờ `aloute.production-mode=true` để bật các guard fail-closed (không seed dữ liệu dev, từ chối khởi động nếu thiếu khóa 2FA mạnh). Điều này được thực thi bởi `ProductionProfileGuard`.
- **Migration:** các script DDL được thực thi **thủ công** theo hướng dẫn trong `src/main/resources/db/migration/README.md`. Luôn có phương án rollback.

## 10. Thành viên và phân công

Phân công dựa trên bằng chứng commit/PR trong repository và [`skills/INDEX.md`](skills/INDEX.md). Hồ sơ bằng chứng cá nhân nằm trong [`docs/`](docs/):

| Vai trò | Phạm vi |
| :--- | :--- |
| **Backend / Domain / Security** | Spring Boot, JPA, authorization, messaging, notification, 2FA |
| **Frontend / UI-UX / E2E** | Thymeleaf templates, CSS/JS, responsive, accessibility (bằng chứng frontend nằm trong `docs/quan/`, được bổ sung cùng nhánh frontend hardening) |
| **Tester / QA** | Kịch bản kiểm thử và sequence diagrams (xem [`docs/bang/USE_CASE_MATRIX.md`](docs/bang/USE_CASE_MATRIX.md), [`docs/bang/sequence/`](docs/bang/sequence/)) |

Bằng chứng backend/thiết kế: [`docs/khoi/`](docs/khoi/WEEK2_WEEK3_IMPLEMENTATION.md), [`docs/BAO_CAO_THIET_KE_DEN_XU_LY.md`](docs/BAO_CAO_THIET_KE_DEN_XU_LY.md). Đặc tả admin: [`docs/ALOUTE_ADMIN_DASHBOARD_SPEC.md`](docs/ALOUTE_ADMIN_DASHBOARD_SPEC.md). Kế hoạch 2FA: [`docs/TWO_FACTOR_AUTHENTICATION_PLAN.md`](docs/TWO_FACTOR_AUTHENTICATION_PLAN.md).

## 11. Giới hạn đã biết

- Test backend chạy trên H2; tương thích MySQL 8 cần kiểm chứng riêng.
- Vài endpoint ít được gọi bởi frontend chỉ được kiểm chứng ở mức đơn vị/tích hợp.
- Thông báo đẩy (FCM) và một số tích hợp hạ tầng (Firebase, mail) phụ thuộc cấu hình môi trường bên ngoài.
- Danh sách đầy đủ các hạng mục tồn đọng được theo dõi trong các báo cáo chất lượng nội bộ (không phát hành công khai).

## 12. Triển khai

Dự án hỗ trợ đóng gói bằng Docker ([`Dockerfile`](Dockerfile), [`docker-compose.yml`](docker-compose.yml)) với Nginx làm reverse proxy (xem [`skills/docker_reverse_proxy_rule.md`](skills/docker_reverse_proxy_rule.md)).

> **Trạng thái triển khai:** quy trình tự động triển khai (CI/CD → môi trường chạy) **chưa được xác minh** trong repository này. Không suy diễn rằng một lần CI xanh đồng nghĩa với việc production đã được cập nhật.

---

## Giấy phép & bản quyền

Đồ án học phần — nhóm 13, HCMUTE. Xem thông tin tác giả trong lịch sử Git.
