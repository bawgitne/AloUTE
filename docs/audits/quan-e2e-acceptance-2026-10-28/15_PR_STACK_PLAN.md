# 15 — PR Stack & Release Plan (PR #90)

> Tài liệu này mô tả kế hoạch xếp tầng (stack), retarget và merge của PR acceptance #90 đã thực hiện trong đợt release. Không phải hướng dẫn phát triển.

## 1. Trạng thái tham chiếu (sau release)

| Thành phần | Giá trị |
|---|---|
| Repo | `https://github.com/bawgitne/AloUTE` |
| PR nền | **#85** — `fix(ui): harden guest experience and accessibility (BL-17/20/21/23/25)` |
| Base của PR #85 | `master` |
| Head của PR #85 | `fix/quan-frontend-hardening-20261027` @ `74c8a6f` |
| Trạng thái PR #85 | **MERGED** (merge commit `cd0d85c`) |
| PR acceptance | **#90** — `test/quan-e2e-acceptance` |
| Base PR #90 ban đầu | `fix/quan-frontend-hardening-20261027` |
| Base PR #90 sau retarget | **`master`** |
| Merge-base `master` ↔ branch QA | `74c8a6f` (đã thuộc `master` qua PR #85) |
| Trạng thái PR #90 | MERGED sau khi CI xanh tại head cuối |

## 2. Vì sao retarget an toàn

Branch QA được tạo từ head PR #85 (`74c8a6f`). Sau khi #85 merge bằng merge commit `cd0d85c`, `74c8a6f` đã thuộc `master`. Vì vậy khi đổi base PR #90 sang `master`:

- diff của PR chỉ còn các commit QA (không lặp lại thay đổi của #85);
- không cần rebase, không squash, không rewrite commit đã publish;
- không force-push.

## 3. Kiểm tra trước khi merge

```bash
git fetch origin
git merge-base origin/master origin/test/quan-e2e-acceptance   # kỳ vọng 74c8a6f
git log --oneline origin/master..origin/test/quan-e2e-acceptance
git diff --check origin/master..origin/test/quan-e2e-acceptance
```

Kỳ vọng: chỉ commit QA; không có `target/`, `test-results/` thô, log, credential; `diff --check` sạch.

## 4. Thao tác đã thực hiện

1. Đổi base PR #90: `gh pr edit 90 --base master`.
2. Push các commit chuẩn hóa tài liệu QA lên `test/quan-e2e-acceptance` (fast-forward, không force).
3. Chờ CI `test-and-build` xanh tại head mới.
4. Mark ready for review và merge bằng merge commit.
5. Không xóa branch.

## 5. Rủi ro và cách tránh

| Rủi ro | Cách tránh |
|---|---|
| PR chứa lại thay đổi của #85 | Kiểm tra merge-base là `74c8a6f`; diff chỉ gồm commit QA |
| Ghi đè branch PR #85 | Không push vào branch đó; chỉ push `test/quan-e2e-acceptance` |
| Lộ log/ảnh/JSON thô | `.gitignore` chặn `test-results/`; chỉ commit evidence đã curation |
| Tuyên bố quá mức | Ghi rõ 0 FAIL chỉ trên integration `25e7595`; còn 3 BLOCKED, 3 NOT RUN; không nói production-ready |

## 6. Nội dung PR

Xem `16_PR_BODY.md`.