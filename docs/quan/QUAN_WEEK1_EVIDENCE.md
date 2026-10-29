# Tuần 1 — Bằng chứng thiết kế giao diện

> **Kế hoạch tuần:** 21/09/2026 – 26/09/2026
> **Nhiệm vụ theo phân công:** Thiết kế giao diện, wireframe/mockup; viết phần Thiết kế giao diện trong báo cáo.
> **Nguồn phân công:** kế hoạch 3 tuần của nhóm; vai trò thành viên theo [README.md §9](../../README.md#9-đội-ngũ-phát-triển-project-contributors).

## 1. Đối chiếu kế hoạch và thực tế

| Hạng mục kế hoạch | Sản phẩm thực tế | Bằng chứng | Trạng thái |
| :--- | :--- | :--- | :--- |
| 10 wireframe/mockup SVG | 10 file SVG standalone, canvas 1440×900 | Commit [`83e5729`](https://github.com/bawgitne/AloUTE/commit/83e5729) (2026-09-28, `Quan <qm33165@gmail.com>`) | Có artifact |
| Đặc tả thiết kế giao diện | `UI_DESIGN_SYSTEM.md` (tokens, layout, component rules, accessibility) | [`UI_DESIGN_SYSTEM.md`](../../UI_DESIGN_SYSTEM.md), cùng commit `83e5729` | Có artifact |
| Luồng màn hình | `SCREEN_FLOW.md` | [`SCREEN_FLOW.md`](../../SCREEN_FLOW.md), cùng commit `83e5729` | Có artifact |
| Danh mục & quy ước wireframe | `docs/ui/wireframes/README.md` | [`docs/ui/wireframes/README.md`](../ui/wireframes/README.md) | Có artifact |

Commit `83e5729` thay đổi 13 tệp, thêm 2.709 dòng:

```text
SCREEN_FLOW.md                         | 139 +
UI_DESIGN_SYSTEM.md                    | 124 +
docs/ui/wireframes/01-login.svg        | 144 +
...
docs/ui/wireframes/10-notification.svg | 271 +
docs/ui/wireframes/README.md           |  77 +
```

## 2. Danh mục 10 wireframe (design evidence)

| Mã | Màn hình | Artifact | Mã màn hình trong README |
| :--- | :--- | :--- | :--- |
| WF-01 | Đăng nhập | [`01-login.svg`](../ui/wireframes/01-login.svg) | `WF-01` |
| WF-02 | Đăng ký | [`02-register.svg`](../ui/wireframes/02-register.svg) | `WF-02` |
| WF-03 | Bảng tin | [`03-home-feed.svg`](../ui/wireframes/03-home-feed.svg) | `WF-03` |
| WF-04 | Hồ sơ | [`04-profile.svg`](../ui/wireframes/04-profile.svg) | `WF-04` |
| WF-05 | Tạo bài viết | [`05-create-post.svg`](../ui/wireframes/05-create-post.svg) | `WF-05` |
| WF-06 | Chi tiết bài viết | [`06-post-detail.svg`](../ui/wireframes/06-post-detail.svg) | `WF-06` |
| WF-07 | Tìm kiếm | [`07-search.svg`](../ui/wireframes/07-search.svg) | `WF-07` |
| WF-08 | Cộng đồng | [`08-community.svg`](../ui/wireframes/08-community.svg) | `WF-08` |
| WF-09 | Nhắn tin | [`09-messaging.svg`](../ui/wireframes/09-messaging.svg) | `WF-09` |
| WF-10 | Thông báo | [`10-notification.svg`](../ui/wireframes/10-notification.svg) | `WF-10` |

Đặc tả từng màn hình (mục tiêu, thành phần, trạng thái, ranh giới) nằm trong [`docs/ui/wireframes/README.md` §2](../ui/wireframes/README.md); hồ sơ này không sao chép lại.

## 3. Kiểm định artifact (chạy lại 2026-10-27)

Lệnh kiểm tra XML và thuộc tính canvas:

```bash
python - <<'PY'
import xml.etree.ElementTree as ET, glob, os
for f in sorted(glob.glob('docs/ui/wireframes/*.svg')):
    root = ET.parse(f).getroot()
    print(os.path.basename(f), root.get('viewBox'), root.get('width'), root.get('height'))
PY
```

Kết quả thực tế: **10/10 file parse XML thành công**, tất cả có `viewBox="0 0 1440 900"`, `width="1440"`, `height="900"`. Không có file lỗi.

Đây là **design evidence**, không phải bằng chứng giao diện chạy. Ảnh runtime tương ứng được liệt kê trong [`UI_TRACEABILITY_MATRIX.md`](UI_TRACEABILITY_MATRIX.md).

## 4. Khác biệt cần ghi nhận

1. **Thời điểm commit lệch kế hoạch tuần.** Không có commit nào của tác giả trong khoảng 21/09–26/09/2026. Commit thiết kế duy nhất `83e5729` có ngày tác giả **2026-09-28 11:40 +07**, tức đầu Tuần 2 theo lịch kế hoạch. Hồ sơ này không tuyên bố Tuần 1 hoàn thành đúng hạn; chỉ xác nhận artifact tồn tại và nội dung của nó.
2. **Bảng màu SVG Tuần 1 là lịch sử.** README wireframe ghi rõ các SVG dùng dark palette của Tuần 1; bảng màu triển khai là Light Campus trong [`UI_DESIGN_SYSTEM.md`](../../UI_DESIGN_SYSTEM.md). Hai nguồn không mâu thuẫn nếu đọc đúng vai trò: SVG là design evidence lịch sử, `UI_DESIGN_SYSTEM.md` là baseline triển khai.
3. **Định hướng Light Campus** được ghi nhận trong [`UI_DESIGN_SYSTEM.md`](../../UI_DESIGN_SYSTEM.md); hồ sơ này chỉ đối chiếu artifact và commit, không suy đoán quyết định ngoài dữ liệu Git.
4. **Không có ảnh chụp runtime trong Tuần 1.** Ảnh runtime đầu tiên được tạo ngày 2026-10-20/21 và thuộc bằng chứng Tuần 3 (xem [`QUAN_WEEK3_EVIDENCE.md`](QUAN_WEEK3_EVIDENCE.md)).

## 5. Giới hạn hồ sơ

- Bản kế hoạch Word của nhóm không được lưu trong repository; hồ sơ đối chiếu theo artifact và commit.
- Định hướng Light Campus được ghi nhận trong [`UI_DESIGN_SYSTEM.md`](../../UI_DESIGN_SYSTEM.md).
- Commit `83e5729` dùng tên `Quan`; lịch sử còn có tên `pminhquan` cùng email. Hồ sơ chỉ dùng dữ liệu Git, không suy đoán danh tính.
