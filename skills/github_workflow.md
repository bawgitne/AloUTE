# ALOUTE — GITHUB WORKFLOW & VERSION CONTROL CONVENTION

> **Scope**
>
> File này là source of truth cho cách team ALOUTE dùng:
>
> ```text
> Git
> GitHub
> Branch
> Commit
> Push
> Pull Request
> Merge
> Tag
> Release
> Version
> Hotfix
> Conflict Resolution
> ```
>
> File này không định nghĩa business rule, API, security, database schema hay coding convention.
>
> Các phần đó thuộc bộ tài liệu ALOUTE tương ứng.

---

# 1. MỤC TIÊU

Git workflow của ALOUTE phải đảm bảo:

```text
main luôn chạy được
commit dễ đọc
branch dễ hiểu
PR nhỏ
merge ít conflict
rollback được
release có version
team biết ai đang làm gì
```

Không dùng GitHub như nơi chỉ để backup code cuối kỳ.

---

# 2. REPOSITORY MODEL

ALOUTE dùng:

```text
1 repository
1 main branch
n feature/fix/refactor/docs branches
```

Không tạo mỗi thành viên một repository riêng để cuối kỳ copy code vào nhau.

Canonical:

```text
origin/main
→ source tích hợp chính của project
```

---

# 3. MAIN BRANCH

`main` phải luôn ở trạng thái:

```text
compile được
test quan trọng pass
application start được
không chứa code đang làm dở
không chứa secret
```

Không commit trực tiếp vào `main`.

Mọi thay đổi vào `main` qua Pull Request.

---

# 4. BRANCH STRATEGY

Không dùng Git Flow phức tạp với:

```text
develop
release/*
support/*
```

nếu project chưa cần.

ALOUTE dùng:

```text
main
+
short-lived branches
```

Các branch type:

```text
feature/
fix/
refactor/
docs/
test/
chore/
hotfix/
```

---

# 5. BRANCH NAMING

Format:

```text
<type>/<feature>-<short-description>
```

Ví dụ:

```text
feature/auth-login
feature/post-create
feature/community-join-request
feature/chat-send-message

fix/post-like-counter
fix/websocket-reconnect

refactor/post-query-service

docs/update-domain-follow-rule

test/community-ban-policy

chore/update-dependencies

hotfix/login-cookie
```

Không dùng:

```text
branch-thang
code-moi
test123
final
final2
abc
temp
```

---

# 6. MỖI BRANCH CHỈ NÊN CÓ MỘT MỤC TIÊU CHÍNH

Tốt:

```text
feature/post-like
```

Không tốt:

```text
feature/post-like-community-chat-ui-fix
```

Nếu task quá lớn:

```text
tách task
↓
tách branch
↓
tách PR
```

PR nhỏ giúp:

```text
review dễ
conflict ít
rollback dễ
history sạch
```

---

# 7. TẠO BRANCH

Luôn branch từ `main` mới nhất.

```bash
git checkout main
git pull origin main
git checkout -b feature/post-create
```

Không tạo branch từ một feature branch cũ trừ khi dependency rõ và team đã thống nhất.

---

# 8. TRƯỚC KHI CODE

Developer phải:

```text
1. pull main mới nhất
2. đọc RULE.md
3. đọc source-of-truth docs liên quan
4. xác định task scope
5. tạo branch đúng convention
```

Không code trước rồi mới tìm docs để hợp thức hóa implementation.

---

# 9. COMMIT PHẢI NHỎ VÀ CÓ Ý NGHĨA

Một commit nên đại diện cho một logical change.

Ví dụ tốt:

```text
feat(post): add create post service
feat(post): expose create post API
test(post): add create post integration tests
```

Không gom:

```text
30 file
5 feature
3 bug fix
1 migration
```

vào một commit `update code`.

---

# 10. CONVENTIONAL COMMIT FORMAT

ALOUTE dùng format:

```text
<type>(<scope>): <description>
```

Ví dụ:

```text
feat(auth): add login endpoint

fix(post): prevent duplicate likes

refactor(message): extract conversation policy

test(community): add ban permission tests

docs(domain): document private follow request

chore(build): update Maven dependency
```

---

# 11. COMMIT TYPES

Canonical types:

```text
feat
fix
refactor
test
docs
chore
perf
build
ci
revert
```

Meaning:

```text
feat
→ thêm capability mới

fix
→ sửa bug

refactor
→ đổi structure nhưng không đổi behavior mong muốn

test
→ thêm/sửa test

docs
→ tài liệu

chore
→ maintenance không ảnh hưởng behavior chính

perf
→ cải thiện performance

build
→ Maven/Docker/build config

ci
→ GitHub Actions/CI pipeline

revert
→ revert commit trước
```

---

# 12. COMMIT SCOPE

Scope nên là module/feature:

```text
auth
user
social
post
topic
poll
community
feed
message
notification
media
security
realtime
frontend
db
docker
build
docs
```

Không cần tạo scope quá chi tiết.

Tốt:

```text
fix(post): fix like counter race
```

Không cần:

```text
fix(post-like-counter-service-database): ...
```

---

# 13. COMMIT DESCRIPTION

Description:

- ngắn;
- imperative/action-oriented;
- nói thay đổi gì;
- không kết thúc bằng dấu chấm bắt buộc.

Tốt:

```text
feat(post): add quote post flow
```

Không tốt:

```text
feat: done
fix: fix bug
update code
final
work
```

---

# 14. COMMIT BODY

Commit body chỉ cần khi change cần giải thích.

Ví dụ:

```text
fix(poll): enforce single-choice voting

Move vote selection validation into PollService.
Keep UNIQUE(poll_id, user_id) as the aggregate constraint.
```

Không viết essay nếu title đã đủ rõ.

---

# 15. BREAKING CHANGE

Nếu change phá contract đang dùng:

```text
BREAKING CHANGE
```

phải được nêu rõ trong PR/commit.

Ví dụ:

```text
feat(api)!: replace allowReply with replyPermission
```

Hoặc body:

```text
BREAKING CHANGE: POST /api/posts no longer accepts allowReply.
```

Breaking change phải đồng bộ source-of-truth docs.

---

# 16. KHÔNG COMMIT CODE HỎNG CÓ CHỦ ĐÍCH LÊN MAIN

Feature branch có thể có commit trung gian khi cần.

Nhưng trước PR:

```text
build phải pass
test relevant phải pass
code dở phải hoàn chỉnh
TODO critical phải xử lý
```

Không merge commit chỉ chứa:

```text
temporary
debug
commented-out code
```

---

# 17. KHÔNG COMMIT SECRET

Không commit:

```text
.env
Firebase service account
JWT secret
DB password
private key
TLS key
personal token
GitHub token
SMTP password
```

Phải nằm:

```text
environment
GitHub Secrets
server secret file
local ignored file
```

Nếu secret đã commit:

```text
coi như secret đã lộ
→ rotate/revoke ngay
```

Chỉ xóa khỏi commit sau là chưa đủ.

---

# 18. .GITIGNORE

Repository phải ignore tối thiểu:

```text
.env
.env.*
target/
.idea/
.vscode/
*.log
local config
secret files
```

Nhưng phải giữ:

```text
.env.example
```

không chứa secret thật.

---

# 19. PUSH

Push branch:

```bash
git push -u origin feature/post-create
```

Sau lần đầu:

```bash
git push
```

Không force push `main`.

---

# 20. FORCE PUSH

Không dùng:

```bash
git push --force
```

trên shared branch hoặc `main`.

Nếu thật sự cần rewrite branch cá nhân:

```bash
git push --force-with-lease
```

ưu tiên hơn `--force`.

Chỉ làm khi hiểu hậu quả.

---

# 21. SYNC MAIN TRƯỚC KHI PR

Trước PR/merge:

```bash
git fetch origin
git checkout feature/post-create
git merge origin/main
```

Hoặc rebase nếu team thành thạo.

Cho ALOUTE mặc định:

```text
merge main into feature branch
```

dễ dùng và ít gây lỗi history cho sinh viên hơn.

---

# 22. REBASE

Rebase chỉ dùng khi developer hiểu Git history.

Không rebase branch shared đang có nhiều người cùng làm mà không thống nhất.

Nếu dùng rebase trên branch cá nhân:

```bash
git fetch origin
git rebase origin/main
```

sau đó:

```bash
git push --force-with-lease
```

Không rebase `main`.

---

# 23. CONFLICT RESOLUTION

Khi conflict:

```text
1. xác định file owner
2. đọc source-of-truth docs
3. hiểu cả hai change
4. resolve logic
5. compile/test lại
```

Không resolve bằng cách:

```text
accept ours
accept theirs
```

mù.

Đặc biệt với:

```text
Domain
OpenAPI
SecurityConfig
Entity
Migration
WebSocket config
```

phải review kỹ.

---

# 24. PULL REQUEST

Mọi change vào `main` qua Pull Request.

PR nên nhỏ.

Target:

```text
1 task
1 logical feature/fix
```

Không mở PR:

```text
"all features"
```

sau 2 tuần code.

---

# 25. PR TITLE

Format giống Conventional Commit:

```text
feat(post): add create post flow
fix(auth): revoke refresh session on logout
docs(domain): add notification preference rules
```

PR title phải nói rõ purpose.

---

# 26. PR DESCRIPTION TEMPLATE

Mỗi PR nên có:

```text
## What
Thay đổi gì?

## Why
Tại sao cần?

## Docs / Contract
Đã đọc hoặc thay file canonical nào?

## DB
Có migration không?

## Security
Có thay permission/auth không?

## Test
Đã test gì?

## Breaking Change
Có/Không
```

Ví dụ:

```text
## What
Add community ban flow.

## Docs / Contract
- ALOUTE_DOMAIN_SYSTEM.md
- aloute-openapi.yaml
- SPRING_SECURITY_CONVENTION.md

## DB
Uses existing community_bans table.

## Security
Checks scoped MODERATOR/ADMIN/OWNER permission.

## Test
- moderator bans member
- member cannot ban
- owner cannot be banned
```

---

# 27. PR REVIEW

Tối thiểu một người khác review trước merge nếu team size cho phép.

Reviewer check:

```text
build
tests
Domain
OpenAPI
Backend Convention
Security
DB schema/migration
Media
Realtime
Frontend
secret
```

Không chỉ nhìn "code chạy".

---

# 28. SHARED INFRASTRUCTURE CHANGE

Các file shared:

```text
SecurityConfig
GlobalExceptionHandler
ApiResponse
CurrentUserProvider
WebSocketConfig
Firebase adapter
common JS client
Docker Compose
Nginx
migration convention
```

nên yêu cầu owner/lead review.

Không sửa shared contract tùy tiện trong feature PR.

---

# 29. MERGE STRATEGY

ALOUTE mặc định dùng:

```text
Squash and Merge
```

Lý do:

```text
main history gọn
1 PR = 1 logical commit
rollback dễ
```

Feature branch có thể có nhiều commit nhỏ trong lúc làm.

Khi merge:

```text
squash thành 1 commit có nghĩa
```

---

# 30. KHI KHÔNG NÊN SQUASH

Có thể giữ multiple commits khi:

```text
migration history cần tách rõ
refactor lớn có nhiều atomic commit thật sự có giá trị
release/hotfix cần giữ history đặc biệt
```

Nhưng đây là exception.

Default:

```text
Squash and Merge.
```

---

# 31. MERGE COMMIT

Không dùng merge commit cho mọi PR nếu không cần.

Tránh history:

```text
Merge branch 'feature/a'
Merge branch 'feature/b'
Merge branch 'feature/c'
```

khó đọc.

---

# 32. DELETE BRANCH SAU MERGE

Sau PR merge:

```text
delete remote feature branch
```

Local branch không còn cần:

```bash
git branch -d feature/post-create
```

Giữ repository sạch.

---

# 33. VERSIONING

ALOUTE dùng Semantic Versioning đơn giản:

```text
MAJOR.MINOR.PATCH
```

Ví dụ:

```text
0.1.0
0.2.0
0.2.1
1.0.0
```

---

# 34. PRE-1.0 VERSION

Trong giai đoạn phát triển đồ án:

```text
0.x.y
```

Ví dụ:

```text
0.1.0
→ core auth/user

0.2.0
→ post/social

0.3.0
→ community

0.4.0
→ messaging/realtime
```

Khi project đủ feature để demo/final:

```text
1.0.0
```

---

# 35. VERSION INCREMENT

## PATCH

```text
x.y.Z
```

Tăng khi:

- bug fix;
- security fix không phá contract;
- small correction.

Ví dụ:

```text
1.0.0
→ 1.0.1
```

## MINOR

```text
x.Y.0
```

Tăng khi:

- thêm feature backward-compatible;
- thêm endpoint;
- thêm capability.

Ví dụ:

```text
0.3.0
→ 0.4.0
```

## MAJOR

```text
X.0.0
```

Tăng khi:

- breaking contract lớn;
- architecture/API không backward-compatible;
- major release.

---

# 36. VERSION KHÔNG TĂNG MỖI COMMIT

Không làm:

```text
commit 1 → 0.1.1
commit 2 → 0.1.2
commit 3 → 0.1.3
```

Version dành cho release milestone.

Commit có SHA riêng để track change.

---

# 37. GIT TAG

Mỗi release chính thức phải có Git tag:

```text
v0.1.0
v0.2.0
v1.0.0
```

Tạo annotated tag:

```bash
git tag -a v0.4.0 -m "ALOUTE v0.4.0"
git push origin v0.4.0
```

Không reuse/overwrite release tag đã public.

---

# 38. TAG IMMUTABILITY

Tag đã release:

```text
v1.0.0
```

không được trỏ sang commit khác.

Nếu release lỗi:

```text
fix
→ release v1.0.1
```

Không sửa lại `v1.0.0`.

---

# 39. GITHUB RELEASE

Cho milestone/demo quan trọng, tạo GitHub Release từ tag.

Release nên có:

```text
Version
Release date
Highlights
Features
Fixes
Known Issues
Migration note nếu có
```

Ví dụ:

```text
ALOUTE v0.4.0

Features
- Direct messaging
- Group conversation
- Realtime STOMP delivery

Fixes
- Follow counter race

Known Issues
- Presence not implemented
```

---

# 40. RELEASE BRANCH

ALOUTE không cần `release/*` branch mặc định.

Release từ:

```text
main
```

khi main đã stable.

Nếu cuối kỳ cần freeze demo:

```text
tag stable commit
```

thay vì tạo branch phức tạp.

---

# 41. HOTFIX

Bug nghiêm trọng sau release:

```text
main
↓
hotfix/<name>
↓
fix + test
↓
PR
↓
main
↓
new patch release
```

Ví dụ:

```text
v1.0.0
↓
hotfix/login-cookie
↓
v1.0.1
```

---

# 42. REVERT

Nếu merge gây lỗi lớn:

Ưu tiên:

```bash
git revert <commit>
```

hoặc GitHub Revert PR.

Không reset/force-push `main` để xóa history.

Main history phải giữ audit trail.

---

# 43. DATABASE MIGRATION + GIT

Migration file đã merge vào `main`:

```text
KHÔNG sửa nội dung migration cũ đã dùng.
```

Nếu cần sửa schema:

```text
tạo migration mới.
```

Ví dụ:

```text
V10__create_posts.sql
V11__add_post_quote_permission.sql
```

Không sửa lại:

```text
V10__create_posts.sql
```

sau khi đã merge/deploy.

---

# 44. MIGRATION VERSION CONFLICT

Nếu hai branch cùng tạo:

```text
V12__...
```

branch merge sau phải:

```text
sync main
↓
rename migration thành version mới
↓
test migration lại
```

Không merge duplicate Flyway version.

---

# 45. OPENAPI + GIT

Nếu PR thay HTTP contract:

```text
aloute-openapi.yaml
```

phải được update trong cùng PR.

Không merge:

```text
Controller mới
```

mà OpenAPI vẫn cũ.

---

# 46. DOMAIN + GIT

Nếu PR thay:

```text
business behavior
permission
entity relationship
physical schema
```

phải update:

```text
ALOUTE_DOMAIN_SYSTEM.md
```

trong cùng PR/change set.

---

# 47. SECURITY + GIT

Nếu PR thay:

```text
JWT
refresh
CSRF
global role
auth lifecycle
security strategy
```

phải update:

```text
SPRING_SECURITY_CONVENTION.md
```

nếu canonical rule thay đổi.

Không sửa SecurityConfig khác docs.

---

# 48. MEDIA + REALTIME + GIT

Nếu change:

```text
Firebase storage path
media state
upload flow
STOMP destination
event type
reconnect behavior
```

phải update owner convention tương ứng.

---

# 49. GITHUB ISSUE / TASK

Mỗi feature/fix lớn nên có Issue hoặc task ID.

Ví dụ:

```text
POST-05 Like/Unlike Post
COMM-04 Approve Join Request
CHAT-02 Send Message
```

Branch có thể map theo task:

```text
feature/post-like
```

PR link issue:

```text
Closes #42
```

---

# 50. ASSIGNEE

Mỗi task nên có một primary owner.

Không để:

```text
4 người cùng sửa một feature
```

mà không rõ ai chịu trách nhiệm.

Nếu pair programming:

```text
vẫn có một branch owner.
```

---

# 51. CODE OWNER / MODULE OWNER

Nên xác định owner cho shared area:

```text
Security
Media/Firebase
Realtime/WebSocket
DB Migration
Infrastructure
```

Owner không có nghĩa người khác không được sửa.

Nó nghĩa:

```text
change quan trọng cần owner review.
```

---

# 52. PROTECTED MAIN

GitHub `main` nên bật branch protection nếu plan hỗ trợ.

Recommended:

```text
Require Pull Request
Require review
Block force push
Block deletion
Require status checks nếu CI có
```

Không cho mọi người push trực tiếp main.

---

# 53. CI STATUS CHECK

Nếu có GitHub Actions, PR nên chạy:

```text
compile
unit test
integration test quan trọng
OpenAPI validation nếu setup
```

PR fail CI:

```text
không merge.
```

---

# 54. GITHUB ACTION WORKFLOW

CI file là code/config thật:

```text
.github/workflows/ci.yml
```

Không cần định nghĩa CI command trùng ở nhiều docs.

CI phải dùng cùng build command team dùng local.

---

# 55. DEPENDENCY UPDATE

Dependency update không trộn vào unrelated feature PR.

Ví dụ:

```text
chore(build): update Spring dependency
```

PR riêng nếu update đáng kể.

Giảm rủi ro/debug difficulty.

---

# 56. FORMAT-ONLY CHANGE

Không trộn hàng trăm format-only lines với business change nếu có thể.

Ví dụ:

```text
PR A → reformat
PR B → behavior change
```

History/review dễ hơn.

---

# 57. GENERATED FILE

Không commit generated artifact nếu project không yêu cầu.

Ví dụ thường không commit:

```text
target/
compiled class
IDE cache
runtime logs
```

Commit:

```text
source
migration
OpenAPI
configuration template
docs
```

---

# 58. BINARY FILE

Không dùng Git repository làm media storage.

Không commit:

```text
user upload
large video
database dump lớn
random zip
```

Chỉ commit asset source thật sự thuộc project.

---

# 59. LARGE CHANGE

Nếu feature lớn:

```text
Epic
↓
Task nhỏ
↓
PR nhỏ
```

Ví dụ Messaging:

```text
CHAT-01 Create conversation
CHAT-02 Message persistence
CHAT-03 STOMP send
CHAT-04 STOMP subscribe
CHAT-05 Reaction
CHAT-06 Read marker
CHAT-07 UI integration
```

Không một branch `feature/chat` sống 3 tuần.

---

# 60. DAILY SYNC

Developer nên sync `main` thường xuyên.

Ít nhất:

```text
trước khi bắt đầu task
trước khi mở PR
khi branch sống nhiều ngày
```

Conflict xử lý sớm tốt hơn cuối sprint.

---

# 61. KHÔNG PUSH CODE DEBUG

Trước commit/PR remove:

```text
System.out.println debug
temporary password
fake bypass security
commented test
hardcoded localhost production value
```

Debug code phải rõ hoặc nằm đúng dev config.

---

# 62. TODO

TODO được phép nếu:

```text
không critical
có context rõ
có issue/task nếu cần
```

Tốt:

```java
// TODO CHAT-08: add message pagination cursor after current page-based MVP.
```

Không:

```java
// TODO fix later
```

---

# 63. VERSION SOURCE OF TRUTH

Git version gồm ba lớp:

```text
Commit SHA
→ exact code state

Git Tag
→ release version

GitHub Release
→ human-readable release notes
```

Nếu Docker image được build release:

```text
image tag nên map Git tag/SHA
```

Ví dụ:

```text
aloute:1.0.0
aloute:a1b2c3d
```

---

# 64. RELEASE CHECKLIST

Trước tag release:

```text
□ main up-to-date
□ build pass
□ critical tests pass
□ DB migrations valid
□ OpenAPI matches code
□ docs canonical synced
□ no secret
□ Docker build pass
□ app starts
□ login works
□ critical feature smoke test
□ WebSocket smoke test nếu relevant
```

Sau đó:

```text
tag
↓
push tag
↓
GitHub Release
↓
build/deploy
```

---

# 65. DEMO VERSION

Trước buổi demo:

```text
không demo từ random latest main commit.
```

Tạo stable tag:

```text
v0.9.0-demo
```

hoặc release version phù hợp.

Nếu muốn strictly SemVer:

```text
v0.9.0
```

rồi ghi GitHub Release là demo candidate.

---

# 66. FINAL COURSE RELEASE

Final submission nên có:

```text
v1.0.0
```

nếu project đạt scope final.

GitHub Release:

```text
ALOUTE 1.0.0 — Final Course Release
```

Giúp giảng viên/team checkout đúng code đã nộp.

---

# 67. KHÔNG SỬA HISTORY SAU KHI NỘP

Sau final tag:

```text
v1.0.0
```

không move tag.

Nếu sửa bug sau:

```text
v1.0.1
```

Lịch sử phải audit được.

---

# 68. TEAM WORKFLOW END-TO-END

Canonical:

```text
Task
↓
read RULE.md + owner docs
↓
pull main
↓
create branch
↓
code
↓
small meaningful commits
↓
sync main
↓
local build/test
↓
push
↓
Pull Request
↓
CI
↓
review
↓
Squash and Merge
↓
delete branch
↓
main
```

---

# 69. FEATURE FLOW EXAMPLE

Task:

```text
POST-05 Like Post
```

Commands:

```bash
git checkout main
git pull origin main

git checkout -b feature/post-like
```

Commits:

```text
feat(post): add post like service
test(post): add post like service tests
fix(post): handle duplicate like safely
```

Push:

```bash
git push -u origin feature/post-like
```

PR:

```text
feat(post): add like and unlike flow
```

Merge:

```text
Squash and Merge
```

Final main commit:

```text
feat(post): add like and unlike flow
```

---

# 70. BUG FIX FLOW EXAMPLE

```bash
git checkout main
git pull origin main
git checkout -b fix/post-like-counter
```

Commit:

```text
fix(post): prevent lost updates in like counter
```

PR → review → Squash and Merge.

Nếu release đang là:

```text
v1.0.0
```

và fix cần release:

```text
v1.0.1
```

---

# 71. FORBIDDEN PRACTICES

Không:

```text
push trực tiếp main
force-push main
commit secret
commit target/
commit random database dump
branch final/final2
commit "update code"
PR chứa nhiều feature không liên quan
merge code CI fail
sửa migration đã merge/deploy
move release tag
copy project thành nhiều repo rồi cuối kỳ ghép
resolve conflict mù
```

---

# 72. QUICK RULES

1. Một repository.
2. `main` luôn stable.
3. Không push trực tiếp `main`.
4. Branch ngắn.
5. Branch name semantic.
6. Một branch một mục tiêu.
7. Commit nhỏ.
8. Conventional Commits.
9. Không commit secret.
10. Sync main trước PR.
11. PR nhỏ.
12. PR title semantic.
13. Review trước merge.
14. Default Squash and Merge.
15. Delete branch sau merge.
16. Migration đã merge không sửa.
17. Contract đổi thì docs/OpenAPI đổi cùng PR.
18. Version theo release, không theo commit.
19. Git tag immutable.
20. Final submission có release tag rõ ràng.

---

# 73. FINAL RULE

Trước khi push hỏi:

```text
Branch đúng chưa?
Commit có nói rõ thay đổi không?
Có secret không?
Có code debug không?
Build/test pass chưa?
Docs/OpenAPI có cần update không?
```

Trước khi merge hỏi:

```text
PR scope nhỏ chưa?
Đã sync main chưa?
CI pass chưa?
Có reviewer chưa?
Canonical docs còn khớp code không?
```

Trước khi release hỏi:

```text
Commit nào chính xác?
Version nào?
Tag đã đúng chưa?
Migration/docs/OpenAPI sync chưa?
Rollback được không?
```

> **GitHub của ALOUTE phải phản ánh được lịch sử phát triển thật của project, không chỉ là nơi chứa source code.**
