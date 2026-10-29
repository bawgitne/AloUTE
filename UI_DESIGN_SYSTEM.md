# AloUTE UI Design System

> **Tài liệu đặc tả hệ thống giao diện hiện hành của AloUTE**<br>
> **Định hướng trực quan:** **Modern University Social Network — Light Campus Style**
> **Phạm vi:** design tokens, layout, fragment và component trong `src/main/resources/{templates,static/css}`
> **Nguyên tắc kiến trúc:** [README.md](README.md) §4 (phân lớp, Principal-driven security); hợp đồng API [docs/api/openapi.yaml](docs/api/openapi.yaml)

---

## 1. Design Concept & Visual Identity

AloUTE áp dụng định hướng trực quan chính thức: **Modern University Social Network — Light Campus Style** (Mạng xã hội học thuật hiện đại — Phong cách Giảng đường Sáng).

Giao diện đại diện cho cộng đồng học thuật số của sinh viên, giảng viên và các câu lạc bộ thuộc Trường Đại học Sư phạm Kỹ thuật TP.HCM (HCMUTE):
- **Không gian giảng đường ban ngày (Daylight Campus Ambiance):** Nền sáng nhẹ nhàng, thoáng đãng, kích thích trao đổi học thuật tích cực, giảm mỏi mắt khi đọc tài liệu, bài viết thảo luận dài, thông báo khoa và quy chế trường.
- **Bản sắc học thuật tinh tế (Academic Identity):** Sắc xanh dương chủ đạo của trường (`#2563EB`) kết hợp bề mặt thẻ trắng thanh lịch (`#FFFFFF`), đường phân cách xám nhạt (`#E2E8F0`), loại bỏ hiệu ứng trang trí màu mè hoặc dải màu rực rỡ không cần thiết.
- **Bố cục mạng xã hội thực thụ:** Áp dụng mô hình bố cục 3 cột tương tác chuẩn (tương tự Threads/Facebook nhưng tinh giản cho môi trường đại học), phân cấp thông tin rõ rệt và đặt người học làm trung tâm.
- **Tiếp cận Server-Side Rendering (Thymeleaf + Bootstrap 5):** Thiết kế mô-đun hóa tối đa thành Layouts và Reusable Fragments, phù hợp với kiến trúc Spring MVC SSR và Bootstrap 5 primitives theo README.md §4.

---

## 2. Light Color Tokens & CSS Variables

Mọi quy chuẩn màu sắc của AloUTE tuân thủ bảng mã màu sáng (Light Palette) và được khai báo tập trung trong `src/main/resources/static/css/tokens.css`:

### 2.1. Bề mặt và Nền (Surfaces & Backgrounds)

| CSS Variable | Giá trị Hex | Vai trò / Ngữ cảnh sử dụng |
|---|---|---|
| `--aloute-bg-body` | `#F8FAFC` | Nền canvas toàn bộ trang (Slate-50), tông xám dịu mắt |
| `--aloute-bg-surface` | `#FFFFFF` | Nền thẻ bài viết, ô soạn thảo, danh sách tin nhắn, card nội dung |
| `--aloute-bg-surface-elevated` | `#FFFFFF` | Nền cửa sổ Modal, Dropdown menu, Popover nổi |
| `--aloute-bg-surface-hover` | `#F1F5F9` | Nền khi hover vào thẻ, hàng danh sách hoặc mục điều hướng (Slate-100) |
| `--aloute-bg-surface-active` | `#E2E8F0` | Nền khi click chọn hoặc trạng thái kích hoạt (Slate-200) |

### 2.2. Đường viền và Phân cách (Borders & Dividers)

| CSS Variable | Giá trị Hex / RGBA | Vai trò / Ngữ cảnh sử dụng |
|---|---|---|
| `--aloute-border-color` | `#E2E8F0` | Đường viền mặc định cho thẻ, đường phân cách giữa các bài viết (Slate-200) |
| `--aloute-border-subtle` | `#F1F5F9` | Đường phân cách phụ, viền nhạt giữa các dòng bình luận (Slate-100) |
| `--aloute-border-strong` | `#CBD5E1` | Viền ô input form, viền phân vùng quan trọng (Slate-300) |
| `--aloute-border-focus` | `#2563EB` | Viền ô nhập liệu khi nhận focus (Academic Blue) |
| `--aloute-focus-ring` | `0 0 0 3px rgba(37, 99, 235, 0.25)` | Vòng hào quang hiển thị trạng thái focus theo chuẩn Accessibility |

### 2.3. Hệ thống Chữ (Typography / Text Colors)

| CSS Variable | Giá trị Hex | Vai trò / Ngữ cảnh sử dụng (Đạt chuẩn WCAG 2.1 AA) |
|---|---|---|
| `--aloute-text-primary` | `#0F172A` | Tiêu đề chính, nội dung bài viết, tên tác giả (Slate-900, độ tương phản 15.8:1) |
| `--aloute-text-secondary` | `#475569` | Phụ đề, mô tả khoa/lớp, nhãn phụ (Slate-600, độ tương phản 7.2:1) |
| `--aloute-text-muted` | `#5B6779` | Dấu thời gian, số ký tự đếm, hướng dẫn placeholder |
| `--aloute-text-inverse` | `#FFFFFF` | Chữ trắng trên nền nút xanh, badge trạng thái đậm |

### 2.4. Màu sắc Điểm nhấn Học thuật (University Blue Accent)

| CSS Variable | Giá trị Hex / RGBA | Vai trò / Ngữ cảnh sử dụng |
|---|---|---|
| `--aloute-accent` | `#2563EB` | Màu nhận diện chủ đạo HCMUTE, nút hành động chính (Primary CTA) |
| `--aloute-accent-hover` | `#1D4ED8` | Trạng thái hover của nút chính (Blue-700) |
| `--aloute-accent-active` | `#1E40AF` | Trạng thái nhấn giữ của nút chính (Blue-800) |
| `--aloute-accent-subtle` | `#EFF6FF` | Nền nhạt cho pill chủ đề, thông báo chưa đọc, tab đang active (Blue-50) |
| `--aloute-accent-contrast` | `#FFFFFF` | Màu chữ trên nền accent chính |

### 2.5. Màu Trạng thái Ngữ nghĩa (Semantic Feedback Colors)

| Trạng thái | Mã màu chính | Màu nền (`bg`) | Màu viền (`border`) | Ngữ cảnh ứng dụng |
|---|---|---|---|---|
| **Success** | `#16A34A` | `#F0FDF4` | `#BBF7D0` | Đăng ký thành công, cập nhật thông tin thành công |
| **Danger / Error** | `#DC2626` (nhấn mạnh: `#B91C1C`) | `#FEF2F2` | `#FECACA` | Báo lỗi đăng nhập, lỗi form, cảnh báo vi phạm bài viết |
| **Warning** | `#D97706` | `#FFFBEB` | `#FDE68A` | Cảnh báo nội dung nhạy cảm, nhắc nhở quy tắc ứng xử |
| **Info** | `#0284C7` | `#F0F9FF` | `#BAE6FD` | Thông tin hướng dẫn học vụ, quy chế sinh viên |

---

## 3. Typography & Text Hierarchy

Hệ thống phông chữ sử dụng font stack sans-serif hiện đại của hệ điều hành để đảm bảo tốc độ tải trang nhanh và độ sắc nét tối đa:
```css
font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
```

### Thang đo phân cấp (Type Scale)

| Phân cấp | Kích thước | Trọng lượng (Weight) | Chiều cao dòng (Line-height) | Ứng dụng cụ thể |
|---|---|---|---|---|
| **Display / H1** | `1.75rem` (28px) | 700 (Bold) | 1.3 | Tiêu đề màn hình Auth (Đăng nhập, Đăng ký), Tiêu đề Profile |
| **H2** | `1.375rem` (22px) | 600 (Semi-bold) | 1.35 | Tiêu đề khối Feed, Tiêu đề CLB/Community, Tiêu đề Tìm kiếm |
| **H3** | `1.125rem` (18px) | 600 (Semi-bold) | 1.4 | Tiêu đề thẻ con, Tên tác giả bài viết, Tiêu đề Modal |
| **H4** | `1.000rem` (16px) | 600 (Semi-bold) | 1.4 | Tiêu đề widget bên phải, Tiêu đề nhóm thông báo |
| **Body Regular** | `0.9375rem` (15px) | 400 (Regular) | 1.5 | Nội dung bài viết sinh viên, nội dung bình luận, bio |
| **Body Small** | `0.8125rem` (13px) | 400 (Regular) | 1.45 | Mô tả phụ, menu điều hướng phụ, nội dung tin nhắn phụ |
| **Caption / Meta** | `0.750rem` (12px) | 500 (Medium) | 1.4 | Dấu thời gian đăng bài, nhãn badge, số đếm ký tự (0/2000) |

---

## 4. Spacing, Layout Boundaries & Elevation

### 4.1. Kích thước Ranh giới Bố cục (Layout Grid Boundaries)
- **Container Max Width:** `1280px` (`--aloute-container-max`).
- **Cột Trái (Sidebar Trái):** `260px` (`--aloute-sidebar-left-width`).
- **Cột Giữa (Feed Area):** Tối đa `660px` (`--aloute-feed-max-width`), vùng trọng tâm đọc và tương tác.
- **Cột Phải (Sidebar Phải):** `320px` (`--aloute-sidebar-right-width`).
- **Chiều cao Thanh Điều hướng (Navbar Height):** `64px` (`--aloute-navbar-height`).

### 4.2. Thang Khoảng cách (Spacing Scale)
- `xs: 0.25rem` (4px): Khoảng cách giữa icon và nhãn số đếm tương tác.
- `sm: 0.50rem` (8px): Khoảng cách trong nút bấm, khoảng cách giữa các badge tag.
- `md: 1.00rem` (16px): Padding chuẩn bên trong thẻ, khoảng cách giữa các phần tử form.
- `lg: 1.50rem` (24px): Khoảng cách giữa các bài viết trên Feed, khoảng cách phân đoạn lớn.
- `xl: 2.00rem` (32px): Khoảng cách giữa các khối cột lớn trên trang chủ.

### 4.3. Bo góc (Border Radii)
- `sm: 6px`: Badge phân loại, nhãn vai trò, tooltip.
- `md: 10px`: Nút bấm (`button`), ô nhập liệu (`input`, `textarea`), menu dropdown.
- `lg: 14px`: Bề mặt thẻ (`card`), hộp thoại Modal, khung chat.
- `full: 9999px`: Avatar tròn, pill điều hướng, thanh tìm kiếm bo tròn.

### 4.4. Đổ bóng Bề mặt Sáng (Light Elevation & Shadows)
Tránh bóng đen gắt của giao diện tối cũ; sử dụng bóng mờ tinh tế phân lớp ánh sáng tự nhiên:
- **Shadow Nhẹ (`--aloute-shadow-sm`):** `0 1px 3px 0 rgba(15, 23, 42, 0.06), 0 1px 2px -1px rgba(15, 23, 42, 0.04)` (Dùng cho thẻ bài viết tĩnh).
- **Shadow Vừa (`--aloute-shadow-md`):** `0 4px 6px -1px rgba(15, 23, 42, 0.07), 0 2px 4px -2px rgba(15, 23, 42, 0.05)` (Dùng khi hover bài viết hoặc thanh Navbar).
- **Shadow Nổi (`--aloute-shadow-lg`):** `0 10px 15px -3px rgba(15, 23, 42, 0.08), 0 4px 6px -4px rgba(15, 23, 42, 0.04)` (Dùng cho Modal Dialog và Popover).

---

## 5. Quy tắc Thiết kế Thành phần Chi tiết (Component Rules)

### 5.1. Thanh Điều Hướng (Navbar Rules)
- Vị trí cố định đỉnh trang (`position: sticky; top: 0; z-index: 1020`).
- Bề mặt trắng thanh nhã (`#FFFFFF`), viền dưới mỏng (`1px solid #E2E8F0`), bóng nhẹ `shadow-sm`.
- Logo AloUTE mang sắc xanh học thuật (`#2563EB`) đi kèm biểu tượng tri thức số.
- Ô tìm kiếm toàn cục dạng viên thuốc (Pill shape), nền xám sáng (`#F1F5F9`), viền trong suốt, tự động đổi viền sang `#2563EB` khi active.
- Nhóm icon thông báo/tin nhắn có huy hiệu đỏ số đếm chưa đọc dạng pill (`badge rounded-pill bg-danger`).
- Avatar tài khoản góc phải dạng dropdown cho phép truy cập Hồ sơ hoặc Đăng xuất.

### 5.2. Thanh Bên (Sidebar Rules)
- **Cột Trái (260px) — Điều hướng và Tóm tắt Hồ sơ:**
  - Thẻ tóm tắt: avatar, tên hiển thị, `@username` và badge `UTE Member`; guest thấy CTA Đăng nhập/Đăng ký.
  - Liên kết dọc: Bảng tin chính, Hồ sơ cá nhân, Cộng đồng, Bạn bè & Kết nối, Bài viết đã lưu. Trạng thái active dùng nền accent-subtle và chữ accent.
  - Nút tạo bài viết nằm trên navbar và menu mobile; sidebar không lặp lại CTA này.
- **Cột Phải (320px, hiển thị từ breakpoint `xl`) — Ngữ cảnh Học thuật:**
  - Khối “Văn hóa AloUTE”: quy tắc giao tiếp học thuật.
  - Khối “Chủ đề nổi bật”: các topic động từ `sidebarTopics`, liên kết tới `/topics/{slug}`.
  - Khối “Liên kết nhanh”: cổng thông tin, đào tạo và thư viện HCMUTE.
  - Footer bản quyền. Component tương ứng: `templates/fragments/info-card.html`.

### 5.3. Thẻ Bề Mặt & Thẻ Bài Viết (Card & Post Rules)
- Thẻ bài viết sử dụng nền trắng tinh khiết (`#FFFFFF`), viền `#E2E8F0`, bo góc `14px`, bóng nhẹ `shadow-sm`.
- **Phần Đầu bài viết (Header):** Avatar tròn 44px, Tên sinh viên (đậm), username mờ (`@nguyenvanan`), nhãn Khoa/K-class, thời gian đăng bài (tính tương đối), nút dropdown ba chấm tùy chọn bài viết (Sửa/Xóa cho tác giả, Báo cáo vi phạm cho người khác).
- **Phần Thân (Body):** Văn bản hiển thị tự nhiên với khoảng cách dòng thoáng; đính kèm ảnh có bo góc `10px` viền xám nhạt; ô bình chọn (Poll) hiển thị các thanh tỷ lệ phần trăm trực quan kèm số lượng vote.
- **Thanh Tương Tác (Interaction Bar):** Like, Bình luận, Repost, Quote (theo quyền), Bookmark và Copy link. Trạng thái đã Like dùng màu đỏ đậm `--aloute-danger-emphasis` trên nền `--aloute-danger-bg`; các trạng thái khác dùng accent/subtle token.

### 5.4. Hộp Soạn Thảo Bài Viết (Post Composer Rules)
- Nằm ở đầu Bảng tin (Home Feed), thiết kế dạng thẻ mở rộng.
- Hàng trên: Avatar sinh viên và ô textarea nhập liệu với placeholder thân thiện: *"Bạn đang có thắc mắc hay chia sẻ gì về việc học hôm nay?"*.
- Giới hạn 2000 ký tự với bộ đếm thời gian thực dạng caption (`0/2000`).
- Bộ chọn phạm vi bài viết (Audience selector dropdown): giữ đúng vùng chọn `Công khai`, `Người theo dõi`, `Cộng đồng` của wireframe; giá trị nghiệp vụ thực tế phải do Domain/OpenAPI cung cấp.
- Thanh công cụ phía dưới gồm icon đính kèm hình ảnh/tài liệu và nút mở tạo Bình chọn (Poll). Nút `[Đăng bài]` tự động vô hiệu hóa (`disabled`) khi nội dung rỗng.

### 5.5. Trang Hồ Sơ Cá Nhân (Profile Rules)
- Ảnh bìa (Cover Image) tỷ lệ 3:1 với khung viền bo nhẹ đỉnh thẻ.
- Avatar sinh viên kích thước lớn (110px), có viền trắng 4px nổi bật đè lên ảnh bìa.
- Thông tin học tập: Họ tên, Tên tài khoản, Bio giới thiệu bản thân, Khoa, Niên khóa, Số lượng Đang theo dõi / Người theo dõi.
- Nút hành động theo vai trò: Người xem thấy nút `[Theo dõi]` (xanh) và `[Nhắn tin]` (xám nhạt); Chủ tài khoản thấy nút `[Chỉnh sửa hồ sơ]` (outline).
- Thanh Tab nội dung: `Bài viết`, `Phản hồi`, `Ảnh/Media`, `Đã lưu` với gạch chân xanh định vị tab đang chọn.

### 5.6. Trang Cộng Đồng / Câu Lạc Bộ (Community Rules)
- Ảnh bìa CLB, Biểu trưng (Logo), Tên CLB học thuật, Thống kê thành viên, Nút `[Tham gia nhóm]` / `[Rời nhóm]`.
- Phù hiệu phân quyền rõ ràng: `Thành viên` (badge xám), `Kiểm duyệt viên` (badge xanh dương nhạt), `Quản trị viên` (badge xanh đậm).
- Luồng bài viết được lọc riêng theo CLB; cột phải hiển thị Ban chủ nhiệm và Nội quy sinh hoạt câu lạc bộ.

### 5.7. Màn Hình Nhắn Tin 1-1 (Messaging Rules)
- Bố cục 2 cột chuyên biệt chia tỉ lệ rõ ràng:
  - Cột danh sách hội thoại: Ô tìm kiếm tin nhắn, danh sách bạn bè đang chat, hiển thị tin nhắn xem trước và số thông báo chưa đọc dạng pill xanh.
  - Cột hội thoại đang mở: Thanh tiêu đề người nhận, lịch sử tin nhắn dạng bong bóng:
    - Tin nhắn đi (của mình): Nền xanh học thuật (`#2563EB`), chữ trắng (`#FFFFFF`), bo góc lệch phải.
    - Tin nhắn đến (của bạn): Nền xám sáng (`#F1F5F9`), chữ đen đậm (`#0F172A`), bo góc lệch trái.
  - Khung soạn tin nhắn cố định chân trang kèm nút gửi CTA và đính kèm tài liệu.

### 5.8. Trung Tâm Thông Báo (Notification Rules)
- Bộ lọc 2 tab: `Tất cả` và `Chưa đọc`, đi kèm hành động `[Đánh dấu tất cả đã đọc]`.
- 4 phân loại thông báo học thuật kèm icon ngữ nghĩa:
  - Thích bài viết (Icon Tim/Like)
  - Bình luận / Phản hồi (Icon Tin nhắn)
  - Theo dõi mới (Icon Người dùng)
  - Hoạt động CLB / Khoa (Icon Thông báo cộng đồng)
- Thông báo chưa đọc có nền xanh dương siêu nhạt (`#F0F7FF`) kèm chấm tròn xanh nhận diện; thông báo đã đọc có nền trắng trơn.

---

## 6. Quy Tắc Thích Ứng Giao Diện (Responsive Rules)

| Thiết bị / Độ rộng | Cấu trúc Cột | Hành vi Chi tiết |
|---|---|---|
| **Desktop ($\ge 1200px$)** | **3 cột chuẩn** | Trái (260px) + Giữa (660px) + Phải (320px). Hiển thị đầy đủ mọi widget và shortcut. |
| **Tablet ($768px - 1199px$)** | **2 cột** | Ẩn cột phải; cột trái `col-md-4`/`col-lg-3`, nội dung `col-md-8`/`col-lg-9`. |
| **Mobile ($< 768px$)** | **1 cột duy nhất** | Nội dung chiếm 100% (`col-12`); sidebar trái chuyển vào offcanvas qua nút hamburger; touch target tối thiểu 44px. |

---

## 7. Tiêu Chuẩn Tiếp Cận (Accessibility Rules - WCAG 2.1 AA)

- **Tỷ lệ tương phản màu sắc (Color Contrast):**
  - Chữ nội dung chính (`#0F172A`) trên nền trắng (`#FFFFFF`): Độ tương phản khoảng **17.85:1** (vượt xa chuẩn 4.5:1).
  - Chữ thứ cấp (`#475569`) trên nền trắng: Độ tương phản khoảng **7.58:1** (chuẩn AA cho văn bản nhỏ).
  - Nút chính (`#2563EB`) với chữ trắng (`#FFFFFF`): Độ tương phản khoảng **5.17:1**.
- **Điều hướng bằng bàn phím (Keyboard Accessibility):** Mọi nút tương tác, đường link và trường nhập liệu đều hiển thị viền focus rõ ràng (`--aloute-focus-ring`).
- **Thẻ ngữ nghĩa (Semantic HTML):** Sử dụng chuẩn xác `<header>`, `<nav>`, `<main>`, `<aside>`, `<article>`, `<section>`, `<footer>` trong cấu trúc layout Thymeleaf.
- **Không truyền đạt trạng thái chỉ bằng màu sắc:** Cảnh báo lỗi, trạng thái chưa đọc hoặc huy hiệu vai trò luôn đi kèm biểu tượng hoặc văn bản diễn giải rõ ràng.

---

## 8. Đối chiếu wireframe với template hiện hành

10 file SVG tại `docs/ui/wireframes/` là design evidence; giao diện triển khai theo Light Campus Style. Bảng dưới đối chiếu từng màn hình với template Thymeleaf hiện hành:

| STT | Màn hình | Wireframe | Template |
|:---:|---|---|---|
| 01 | Đăng nhập | [`01-login.svg`](docs/ui/wireframes/01-login.svg) | [`auth/login.html`](src/main/resources/templates/auth/login.html) |
| 02 | Đăng ký | [`02-register.svg`](docs/ui/wireframes/02-register.svg) | [`auth/register.html`](src/main/resources/templates/auth/register.html) |
| 03 | Bảng tin | [`03-home-feed.svg`](docs/ui/wireframes/03-home-feed.svg) | [`feed/index.html`](src/main/resources/templates/feed/index.html) |
| 04 | Hồ sơ cá nhân | [`04-profile.svg`](docs/ui/wireframes/04-profile.svg) | [`user/profile.html`](src/main/resources/templates/user/profile.html) |
| 05 | Tạo bài viết | [`05-create-post.svg`](docs/ui/wireframes/05-create-post.svg) | [`post/create.html`](src/main/resources/templates/post/create.html), [`post/_composer-form.html`](src/main/resources/templates/post/_composer-form.html) |
| 06 | Chi tiết bài viết | [`06-post-detail.svg`](docs/ui/wireframes/06-post-detail.svg) | [`post/detail.html`](src/main/resources/templates/post/detail.html), [`post/_post-card.html`](src/main/resources/templates/post/_post-card.html) |
| 07 | Tìm kiếm | [`07-search.svg`](docs/ui/wireframes/07-search.svg) | [`search/index.html`](src/main/resources/templates/search/index.html) |
| 08 | Cộng đồng | [`08-community.svg`](docs/ui/wireframes/08-community.svg) | [`community/index.html`](src/main/resources/templates/community/index.html), [`community/detail.html`](src/main/resources/templates/community/detail.html) |
| 09 | Nhắn tin | [`09-messaging.svg`](docs/ui/wireframes/09-messaging.svg) | [`messaging/index.html`](src/main/resources/templates/messaging/index.html) |
| 10 | Thông báo | [`10-notification.svg`](docs/ui/wireframes/10-notification.svg) | [`notification/index.html`](src/main/resources/templates/notification/index.html) |

---

## 9. Phạm vi tài liệu

- **Design tokens & component:** [`tokens.css`](src/main/resources/static/css/tokens.css), [`app.css`](src/main/resources/static/css/app.css).
- **Layout & fragment:** [`templates/layouts/`](src/main/resources/templates/layouts/), [`templates/fragments/`](src/main/resources/templates/fragments/).
- **Kiểm thử giao diện:** [`test/frontend/`](test/frontend/), [`test/e2e/quan-acceptance/`](test/e2e/quan-acceptance/).
- **Accessibility:** WCAG 2.1 AA, keyboard focus, skip link, touch target tối thiểu 44px và axe audit trong browser E2E.
