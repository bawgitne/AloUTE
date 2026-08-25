# Các stage triển khai và quality gate

Nguyên tắc: hoàn thành một stage theo chiều dọc (database → backend → UI → security → test → docs → demo) rồi mới sang stage tiếp theo. Test đỏ hoặc lỗi security mức cao nghĩa là stage chưa hoàn thành.

## Quality gate chung

- Acceptance criteria của feature đã pass.
- Migration chạy được trên database sạch và database có dữ liệu stage trước.
- Test mới pass; toàn bộ regression test cũ vẫn xanh.
- Không có endpoint thiếu authentication, authorization hoặc ownership.
- UI có loading, empty, success, error; responsive 360 px và desktop.
- Pull Request được review; demo script và tài liệu được cập nhật.
- Không có secret hoặc credential trong Git history.

## Stage 0 — Nền tảng

**Build:** Spring Boot skeleton, package ba tầng, PostgreSQL, Flyway, Thymeleaf layout, Bootstrap, global error, logging, CI và profile config.

**Test gate:** application context, Testcontainers PostgreSQL, migration smoke test, error page và CI.

**Demo gate:** clone repo, cấu hình environment mẫu, chạy một lệnh và mở health/home page.

## Stage 1 — Identity và access

**Build:** register, OTP email, rate limit, login password, Google OIDC, account linking, JWT/refresh rotation, logout, forgot/reset password và role.

**Test gate:** OTP đúng/sai/hết hạn/dùng lại; account enumeration; Google state/nonce/callback; safe linking; 401/403; refresh reuse; suspended account.

**Demo gate:** hai cách đăng ký/login hoạt động; token ở HttpOnly cookie; User bị chặn khỏi Admin route.

## Stage 2 — Profile và bảo mật cơ bản

**Build:** profile, Firebase avatar/cover, privacy cơ bản, đổi password, session list/revoke, login events và login alert.

**Test gate:** ownership/IDOR, MIME/magic bytes, session revoke, private fields và block privacy.

**Demo gate:** upload media, mở hai session rồi revoke một session trên Security Center.

## Stage 3 — Core social content

**Build:** post/media/hashtag/mention, visibility, reaction, comment/reply, share, save, view history, friendship/follow/block/restrict/mute.

**Test gate:** transaction upload, visibility matrix, block propagation, unique relation/reaction, XSS, pagination và N+1 query.

**Demo gate:** hai User kết bạn, đăng bài friends-only và chứng minh người thứ ba không xem được.

## Stage 4 — Feed, search và recommendation

**Build:** Guest explore ≥10 bài, User feed 20 bài/page, search/filter, candidate generation, scoring, reranking, reason code và negative feedback.

**Test gate:** private/blocked/quarantined không lọt feed; score với clock cố định; cold start; stable pagination; search permission.

**Demo gate:** mở score breakdown, bấm “không quan tâm” và cho thấy feed thay đổi.

## Stage 5 — Realtime communication

**Build:** conversation, message request, WebSocket auth, online/last seen, multi-tab presence, typing throttle/timeout, delivered/read, notification và reconnect sync.

**Test gate:** unauthorized subscribe, blocked chat, transaction-before-publish, multi-tab disconnect, stale heartbeat, typing privacy và reconnect.

**Demo gate:** hai browser show online, typing, read receipt, mất mạng/reconnect và message request.

## Stage 6 — Group và community

**Build:** group public/private/hidden, form nhiều bước, onboarding question, member approval, role/permission, announcement mode, invite link và group chat/post.

**Test gate:** privacy matrix, hidden search, invite expiry/limit, form draft, ownership invariant, permission escalation và onboarding-answer privacy.

**Demo gate:** tạo private hidden group, xin tham gia bằng account khác, duyệt rồi nhận đúng quyền.

## Stage 7 — AutoMod và manual moderation

**Build:** keyword/phrase/wildcard/safe-regex, Unicode normalization, spam/link/mention rule, risk score, warn/quarantine/block, moderator queue, report, appeal và audit.

**Test gate:** Vietnamese evasion corpus, false positive, ReDoS timeout, rule versioning, group/global permission, appeal restore và audit transaction.

**Demo gate:** một nội dung warn, một nội dung quarantine; Manager review và User appeal.

## Stage 8 — Security showcase và release

**Build:** TOTP 2FA, backup code, recent authentication, full Privacy Checkup, export/deactivate/delete, dashboard, performance, accessibility và seed data.

**Test gate:** 2FA replay/rate-limit/recovery, sensitive-action re-auth, export authorization, full regression, responsive matrix và dependency scan.

**Demo gate:** chạy end-to-end, test suite xanh, GitHub Issues/PR/commit rõ, backup demo video và release tag.

## Stop rule

- Stage chưa pass thì sửa/test trong chính stage đó, không “để sau”.
- Bonus feature chỉ bắt đầu sau khi core acceptance criteria của stage hiện tại xanh.
- Nếu thiếu thời gian, cắt bonus Stage 8 trước; không cắt authentication, authorization, privacy hoặc moderation audit.

