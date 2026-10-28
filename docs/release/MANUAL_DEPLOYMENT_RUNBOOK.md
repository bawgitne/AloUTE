# MANUAL DEPLOYMENT RUNBOOK — AloUTE

> **Trạng thái tài liệu:** soạn 2026-10-28, dựa trên repository `bawgitne/AloUTE`.
> **KHÔNG thực thi bất kỳ lệnh deploy nào từ tài liệu này khi chưa được owner phê duyệt.**
> **Hạ tầng thật chưa được xác minh trong phiên soạn tài liệu** → các bước dưới đây là **phương án có điều kiện**; người deploy phải xác nhận đúng nhánh hạ tầng trước khi làm theo.
> **Không ghi/in bất kỳ giá trị secret production nào.** Chỉ kiểm tra *sự hiện diện* của biến môi trường/secret.

---

## 1. Xác minh hạ tầng thật (bắt buộc trước mọi bước)

| Câu hỏi | Cách xác minh (chỉ đọc) | Ghi lại |
|---|---|---|
| Nền tảng | VPS+Docker Compose / Docker thuần / PaaS (Cloud Run/Render) / JAR+systemd | `________` |
| Reverse proxy | Nginx? Caddy? TLS do đâu? | `________` |
| DB | MySQL 8 ở đâu (host/port/db — **không** ghi mật khẩu) | `________` |
| Artifact | Container image / fat JAR / build tại chỗ | `________` |
| Ai deploy | Thủ công SSH? Panel? | `________` |

**Repo hiện chỉ có `.github/workflows/ci.yml` (node test + mvn test + mvn package) — KHÔNG có bước deploy/publish.** Không có bằng chứng auto-deploy từ GitHub; production deploy **thủ công**. Nếu phát hiện auto-deploy ngoài dự kiến → **dừng**, báo owner.

## 2. Xác minh release hiện tại
Ghi lại version/commit đang chạy production trước thay đổi; nếu có endpoint version/health (chỉ đọc) ghi lại; lưu ảnh chụp/log để so sánh rollback.

## 3. Xác minh commit sẽ deploy
`git fetch origin && git log -1 --oneline origin/master` → ghi **đúng SHA** `________`; xác nhận CI `test-and-build` SUCCESS; xác nhận không có thay đổi ngoài phạm vi được duyệt.

## 4. Sao lưu database (khi release có migration)
Nếu có DDL/DML: `mysqldump --single-transaction --routines --triggers <DB> > backup_<date>.sql` (điền DB thật; **không** đưa mật khẩu vào lịch sử shell). Kiểm tra `gzip -t`/đếm dòng. Batch 1 (#74–#77) **không có schema change** → mục này n/a nhưng vẫn xác nhận không có script mới.

## 5. Sao lưu runtime configuration
Sao lưu `.env`/secrets/compose env + cấu hình Nginx hiện tại (nơi an toàn, không in giá trị).

## 6. Kiểm tra secrets (chỉ kiểm tra *hiện diện*)
Bắt buộc: `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `ALOUTE_JWT_SECRET` (Base64 ≥32 bytes), `ALOUTE_JWT_EXPIRATION_SECONDS`, `ALOUTE_REGISTRATION_OTP_SECRET`; theo tính năng: `ALOUTE_FIREBASE_*`, `ALOUTE_MAIL_*`; và **khi deploy #84**: `ALOUTE_TWO_FACTOR_MASTER_KEY`.
Dùng `test -n "$VAR" && echo present || echo MISSING`; **tuyệt đối không** `echo "$SECRET"`.
**F-CFG-01 cảnh báo:** `application-prod.yml` có `ALOUTE_JWT_EXPIRATION_SECONDS: ${ALOUTE_JWT_EXPIRATION_SECONDS:86400}` tự tham chiếu → nếu env không đặt, prod lỗi circular placeholder. **Phải đặt rõ** biến này trước khi chạy prod.

## 7. Yêu cầu migration
Batch 1: **không có migration** — không chạy DDL. Release có #84 (tương lai): chạy 2 script MySQL sau backup + đặt master key (ngoài phạm vi Batch 1).

## 8. Build/deploy theo hạ tầng (§1)
- **A. VPS + Docker Compose:** checkout `<SHA>` → build → `docker compose up -d --build`; profile `prod` + `ALOUTE_PRODUCTION_MODE=true`.
- **B. Fat JAR + systemd:** `mvn -B -DskipTests package` → copy JAR (giữ bản cũ) → `systemctl restart <service>`; cờ `-Dspring.profiles.active=prod`.
- **C. PaaS:** push image tag=SHA → deploy revision mới (giữ revision cũ để rollback) → set env theo §6.

## 9. Health checks
`GET /login`→200 (HTML); `GET /feed` guest→200/302; log có `Started AlouteApplication`, không `APPLICATION FAILED TO START`, không lỗi DataSource/circular placeholder.

## 10. Login/authentication checks
Đăng nhập tài khoản test an toàn; cookie `aloute_access_token` (HttpOnly) được set; `/api/auth/me`→200; logout hoạt động.

## 11. API + frontend smoke (chỉ đọc, không mutate production)
`GET /api/posts?page=0&size=5`→200 envelope; `GET /api/admin/overview` khách→401 JSON (chưa #88) hoặc 302/403 HTML (đã #88); mở feed/profile/notifications/search không lỗi. **Không** tạo/sửa/xóa dữ liệu production trừ khi owner cho phép.

## 12. WebSocket checks
Kết nối STOMP `/ws`, subscribe kênh hợp lệ; sau #76 SEND client chỉ tới `/app/**`. Không thử tấn công trên production.

## 13. Post-deploy logs
Theo dõi 5–15 phút: 5xx, auth bất thường, lỗi DB/migration; ghi exception mới so với §2.

## 14. Rollback
Code: deploy lại SHA trước (§2) theo hạ tầng. DB: chỉ khi có migration (Batch 1 không). Config: khôi phục §5. Xác nhận bằng §9/§11. Batch 1 rollback = `git revert` merge commit tương ứng (a62d5f0 / 8da63c6 / 8cf1feb / 350d41f) + deploy lại bản trước; không schema impact.

## 15. Release evidence (điền khi thực hiện)
Người deploy `____` · Thời điểm `____` · Commit deploy `____` · Commit trước `____` · Backup DB `____` · Health check `____` · Smoke `____` · Logs bất thường `____` · Rollback? Có/Không + lý do `____`.

---
### Ghi chú an toàn
Không chứa secret, không thực thi gì. Không đọc/ghi/in secret production. Không chạy migration production ngoài quy trình được duyệt. Nếu hạ tầng thật khác → cập nhật §1 và §8 trước khi deploy.