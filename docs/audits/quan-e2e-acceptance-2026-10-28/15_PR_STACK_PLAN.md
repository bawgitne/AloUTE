# 15 — PR Stack Plan trên PR #85

**Không thực hiện push/merge trong phiên QA này.** Tài liệu chỉ mô tả thao tác đề xuất.

## 1. Trạng thái tham chiếu

| Thành phần | Giá trị |
|---|---|
| Repo | `https://github.com/bawgitne/AloUTE` |
| PR nền | **#85** — `fix(ui): harden guest experience and accessibility (BL-17/20/21/23/25)` |
| Base của PR #85 | `master` |
| Head của PR #85 | `fix/quan-frontend-hardening-20261027` @ `74c8a6f` |
| Trạng thái PR #85 | OPEN, MERGEABLE, không draft |
| Branch QA | `test/quan-e2e-acceptance` |
| Base của branch QA | đúng `74c8a6f` (head PR #85) |
| Commit QA | `1cae6eb`, `0541064`, + commit handoff cuối |

Vì branch QA được tạo trực tiếp từ head PR #85, một PR mới với base `fix/quan-frontend-hardening-20261027` sẽ chỉ chứa commit QA — **không lặp lại** commit `74c8a6f` của PR #85.

## 2. Kiểm tra trước khi mở PR

```bash
git -C AloUTE-qoder-acceptance status --porcelain
git -C AloUTE-qoder-acceptance log --oneline 74c8a6f..HEAD
git -C AloUTE-qoder-acceptance diff --check 74c8a6f..HEAD
git -C AloUTE-qoder-acceptance diff --stat 74c8a6f..HEAD
```

Kỳ vọng: chỉ có commit QA; không có `target/`, `test-results/`, log, credential; `diff --check` sạch.

## 3. Thao tác đề xuất (stacked PR)

```bash
# 1) Push branch QA riêng — KHÔNG push vào branch của PR #85
git push -u origin test/quan-e2e-acceptance

# 2) Mở PR xếp tầng với base là branch của PR #85
gh pr create \
  --base fix/quan-frontend-hardening-20261027 \
  --head test/quan-e2e-acceptance \
  --title "test(qa): independent E2E acceptance suite and accessibility handoff" \
  --body-file docs/audits/quan-e2e-acceptance-2026-10-28/16_PR_BODY.md
```

Không dùng `--force`, không push `fix/quan-frontend-hardening-20261027`, không sửa commit `74c8a6f`.

## 4. Thứ tự merge

1. Merge PR #85 vào `master` theo quy trình hiện tại.
2. Review/merge PR xếp tầng (base tự retarget về `master` nếu GitHub xóa branch nền; kiểm tra lại trước khi merge).
3. Nếu PR #85 nhận thêm commit trước khi merge: rebase/cherry-pick **chỉ commit QA** lên head mới; giải quyết xung đột `app.css` bằng cách giữ cả rule của PR #85 và rule 44px của QA. Không rewrite branch PR #85.

## 5. Nội dung PR đề xuất

**Title:** `test(qa): independent E2E acceptance suite and accessibility handoff`

**Summary:**
- 165 Playwright E2E cases trên 6 suite; 155 PASS / 4 FAIL (BL-22) / 3 BLOCKED / 3 NOT RUN.
- 13 lỗi frontend/a11y đã sửa; axe 0 serious/critical trên 20 route; touch target 44×44 cả mobile và desktop.
- 4 case FAIL có ticket BL-22 kèm expected/actual/repro; không sửa backend.
- 13 báo cáo acceptance + demo 20 phút/8–10 phút + bằng chứng JSON/ảnh đã sanitize.

**Không có trong PR:** backend, security, DTO, migration, cấu hình production, credential, log, `test-results/`.

## 6. Rủi ro và cách tránh

| Rủi ro | Cách tránh |
|---|---|
| PR chứa lại commit `74c8a6f` | Base phải là `fix/quan-frontend-hardening-20261027`, không phải `master`; kiểm tra `git log base..HEAD` |
| Ghi đè branch PR #85 | Không push vào branch đó; chỉ push `test/quan-e2e-acceptance` |
| Xung đột `app.css` khi PR #85 tiến lên | Giữ cả hai rule; rebase chỉ commit QA |
| Lộ log/ảnh/JSON thô | `.gitignore` chặn `test-results/`; chỉ commit evidence đã curation |
| Tuyên bố quá mức | PR ghi rõ 4 FAIL, 3 BLOCKED, 3 NOT RUN; không nói production-ready |
