# Tuần 3 — Tích hợp UI, kiểm thử và bằng chứng runtime

> **Kế hoạch tuần:** 05/10/2026 – 11/10/2026
> **Nhiệm vụ theo phân công:** Hoàn thiện/tích hợp UI với backend cho chức năng chính; UI Messaging, Notification, Report/Moderation; responsive, validation, luồng thao tác; E2E theo checklist, sửa lỗi frontend; hình kết quả cài đặt trong báo cáo, UI demo.
> **Nguồn phân công:** [`skills/INDEX.md` §1.1](../../skills/INDEX.md); kế hoạch tuần tại [`PROJECT_EXECUTION_PLAN.md`](../../PROJECT_EXECUTION_PLAN.md) (local, chưa track).

## 1. Tổng quan lịch sử tác giả

Truy vấn `git shortlog -sne origin/master` cho thấy hai tên tác giả dùng **cùng email** `qm33165@gmail.com`:

```text
85  Quan <qm33165@gmail.com>
58  pminhquan <qm33165@gmail.com>
```

Tổng 143 commit trên `origin/master`. GitHub login `pminhquan` sở hữu **54 PR đã merge** (truy vấn `gh pr list --state merged`). Việc gộp hai tên author chỉ dựa trên email trùng khớp; hồ sơ không suy đoán danh tính ngoài dữ liệu git.

Phân bố commit theo khung kế hoạch (lệnh `git rev-list --count --author='qm33165@gmail.com' --since=<from> --until=<to> origin/master`):

| Khung | Số commit |
| :--- | ---: |
| 21/09 – 27/09 (Tuần 1) | 0 |
| 27/09 – 05/10 (Tuần 2) | 2 |
| 05/10 – 12/10 (Tuần 3) | 14 |
| 12/10 – 19/10 | 35 |
| 19/10 – 28/10 | 90 |

Kết luận trung thực: khối lượng tích hợp lớn nhất diễn ra **sau** khung Tuần 3 (19–27/10), không phải trong 05–11/10.

## 2. Trong khung Tuần 3 (05/10–11/10)

14 commit của tác giả, trong đó các mốc frontend chính:

| Commit | Ngày | Nội dung |
| :--- | :--- | :--- |
| [`8aed6fb`](https://github.com/bawgitne/AloUTE/commit/8aed6fb) | 06/10 | Accessibility + design system alignment |
| [`894bf4f`](https://github.com/bawgitne/AloUTE/commit/894bf4f) | 07/10 | Tích hợp auth/profile/follow |
| [`59f4bba`](https://github.com/bawgitne/AloUTE/commit/59f4bba) | 07/10 | Polish responsive auth/profile (PR #10) |
| [`932376a`](https://github.com/bawgitne/AloUTE/commit/932376a) | 08/10 | Messaging + Notification REST UI (PR #12) |
| [`e5477aa`](https://github.com/bawgitne/AloUTE/commit/e5477aa) | 10/10 | Visual foundations + local brand assets (PR #14) |
| [`a2bd6fa`](https://github.com/bawgitne/AloUTE/commit/a2bd6fa) | 10/10 | Mobile profile tabs + avatar fallback (PR #15) |
| [`48a0053`](https://github.com/bawgitne/AloUTE/commit/48a0053) | 10/10 | Post creation + replies (PR #17) |
| [`40863b4`](https://github.com/bawgitne/AloUTE/commit/40863b4) | 10/10 | Like/bookmark/repost interactions (PR #19) |
| [`ea316bd`](https://github.com/bawgitne/AloUTE/commit/ea316bd) | 11/10 | Post edit/delete (PR #20) |
| [`749ead0`](https://github.com/bawgitne/AloUTE/commit/749ead0) | 11/10 | Frontend stabilization + Thymeleaf render tests (PR #21) |
| [`2433414`](https://github.com/bawgitne/AloUTE/commit/2433414) | 12/10 | Frontend integration + QA fixes (PR #24) |

Ảnh runtime cho báo cáo **chưa** được tạo trong khung này; chúng được chụp ngày 20–21/10 (mục 3).

## 3. Bằng chứng runtime (local, không phải production)

Bộ ảnh giao diện chạy thật đầu tiên:

- Báo cáo: [`RUNTIME_UI_CAPTURE_REPORT_2026-10-20.md`](../report-assets/runtime-ui/RUNTIME_UI_CAPTURE_REPORT_2026-10-20.md).
- PR: [#42](https://github.com/bawgitne/AloUTE/pull/42) (merge 2026-10-21), commit [`e87d1d4`](https://github.com/bawgitne/AloUTE/commit/e87d1d4).
- Môi trường: local, profile `dev`, H2 in-memory, cổng 8098, Chromium thật 1440×900; 25 ảnh cho 15 màn hình.
- Giới hạn đã ghi trong báo cáo: `/friends` vỡ HTML do `u.bio` (đã sửa ở PR #47), `/admin/reports` rỗng, `/messages` tự chọn hội thoại đầu tiên (đã sửa ở PR #68).
- Chụp lại follow-up: PR [#49](https://github.com/bawgitne/AloUTE/pull/49) (merge 2026-10-21), commit [`bd7ff7c`](https://github.com/bawgitne/AloUTE/commit/bd7ff7c), bổ sung ảnh friends/settings/admin reports.

Đây là **local runtime evidence**. Không có ảnh nào được chụp từ `https://justneed.food` trong bộ này.

## 4. Các PR frontend/E2E sau khung Tuần 3

| PR | Merge | Phạm vi chính |
| :--- | :--- | :--- |
| [#47](https://github.com/bawgitne/AloUTE/pull/47) | 2026-10-21 | Sửa render `friends.html`, thêm render test |
| [#50](https://github.com/bawgitne/AloUTE/pull/50) | 2026-10-21 | Polish community detail, contextual post binding, empty state |
| [#52](https://github.com/bawgitne/AloUTE/pull/52) | 2026-10-22 | Feed loại tác giả bị mute trước pagination |
| [#54](https://github.com/bawgitne/AloUTE/pull/54) | 2026-10-22 | Tôn trọng query `page` của feed |
| [#55](https://github.com/bawgitne/AloUTE/pull/55), [#56](https://github.com/bawgitne/AloUTE/pull/56) | 2026-10-22 | Settings: init order, safe DOM render |
| [#58](https://github.com/bawgitne/AloUTE/pull/58) | 2026-10-22 | Empty state cho feed page ngoài phạm vi |
| [#59](https://github.com/bawgitne/AloUTE/pull/59) | 2026-10-22 | Navbar mobile nằm trong viewport + regression test |
| [#60](https://github.com/bawgitne/AloUTE/pull/60) | 2026-10-22 | Notification: chặn request trùng, ẩn lỗi transport thô |
| [#63](https://github.com/bawgitne/AloUTE/pull/63) | 2026-10-23 | Full-system audit fixes + 23 regression test |
| [#65](https://github.com/bawgitne/AloUTE/pull/65) | 2026-10-24 | Draft recovery, copy permalink, follow state, confirm dialogs |
| [#66](https://github.com/bawgitne/AloUTE/pull/66) | 2026-10-24 | Poll lifecycle end-to-end |
| [#67](https://github.com/bawgitne/AloUTE/pull/67) | 2026-10-24 | Quote visibility dropdown |
| [#68](https://github.com/bawgitne/AloUTE/pull/68) | 2026-10-24 | Web stabilization: messaging unread, topics, local media, feed N+1 |
| [#70](https://github.com/bawgitne/AloUTE/pull/70) | 2026-10-24 | Brand assets, HTTP compression, asset caching |
| [#71](https://github.com/bawgitne/AloUTE/pull/71) | 2026-10-24 | Benchmark tooling |
| [#72](https://github.com/bawgitne/AloUTE/pull/72) | 2026-10-24 | Web-push icon dùng asset tồn tại |
| [#73](https://github.com/bawgitne/AloUTE/pull/73) | 2026-10-27 | Community moderation, member management, admin dashboard |

Không phải mọi PR trên đều thuần frontend; một số PR sửa backend/contract và UI trong cùng phạm vi. Danh sách này chỉ dùng để truy xuất phần giao diện liên quan, không gán toàn bộ nội dung PR cho một cá nhân.

## 5. Kiểm thử và bằng chứng hiện tại

| Loại | Kết quả | Bằng chứng |
| :--- | :--- | :--- |
| Node frontend suites | 396/396 PASS, 94 suite | [`FRONTEND_TEST_CASES.md`](FRONTEND_TEST_CASES.md#2-node-static-frontend-suites) |
| Maven/Spring | 641 test, 0 fail, 0 error, 9 skipped, exit 0 | [`FRONTEND_TEST_CASES.md`](FRONTEND_TEST_CASES.md#3-maven--spring-test-suite) |
| Browser verification | 27/27 check PASS | [`verification.json`](../../test-results/quan-frontend-hardening/after/verification.json) |
| Responsive measurement | 3 route × 5 viewport, trước/sau | [`RESPONSIVE_ACCESSIBILITY_REPORT.md`](RESPONSIVE_ACCESSIBILITY_REPORT.md) |
| Regression mới | 26 test tĩnh cho BL-17/20/21/23/25 | [`guest-ux-accessibility.test.mjs`](../../test/frontend/guest-ux-accessibility.test.mjs) |

Các thay đổi hardening ngày 2026-10-27 nằm trong working tree trên nhánh `fix/quan-frontend-hardening-20261027`, baseline `99ab72e`; **chưa commit**, nên không có SHA/PR cho chính các thay đổi này.

## 6. Production và giới hạn xác minh

- Audit live ngày 2026-10-27 chỉ kiểm tra **guest** trên `https://justneed.food`; ghi nhận `origin/master` lúc audit là `6f257eb`, sau đó PR #73 merge tạo `99ab72e`. Không có bằng chứng production chạy commit nào trong hai commit này.
- Không có GitHub Deployment/environment record; không có endpoint version công khai (theo [`03_LIVE_WEBSITE_AUDIT.md`](../audits/independent-full-audit-2026-10-27/03_LIVE_WEBSITE_AUDIT.md)).
- Vì vậy mọi kết quả hardening trong hồ sơ này là **local**, không phải “đã sửa trên production”.
- Các luồng cần đăng nhập trên production: **BLOCKED** — không có tài khoản được cấp phép. Không dùng tài khoản seed/dev trên production.
- Các phát hiện P0/P1 của audit (seed account, STOMP, rate limit, poll visibility, private account, schema) nằm ngoài phạm vi frontend hardening; không sửa trong nhánh này.

## 7. Kết luận Tuần 3

**Đã có bằng chứng:** UI cho 10 màn hình chính, Messaging/Notification, tương tác post, edit/delete, community moderation, ảnh runtime local, test tự động và nhiều vòng sửa lỗi frontend.
**Chưa có:** kiểm thử E2E đầy đủ trên production, ảnh/màn hình xác nhận deploy, chạy trên thiết bị di động thật, axe/Lighthouse đầy đủ.
**Sai lệch thời gian:** phần lớn công việc tích hợp và QA hoàn tất trong 19–27/10, sau khung Tuần 3; báo cáo nhóm cần ghi đúng mốc này.
