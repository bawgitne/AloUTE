# AloUTE — Week 1 UI Wireframe & Mockup Specification

> **Phạm vi & Phân công**: Bằng chứng thiết kế giao diện Tuần 1 (21/09/2026 – 26/09/2026) theo kế hoạch đồ án môn học.
> **Owner**: **Phạm Minh Quân** (24110311) — Frontend + UI Design.
> **Nguồn thẩm quyền**: `Nhom13_KeHoachCodeTest.docx` (Word hiện hành).
> **Định dạng bàn giao**: Bộ 10 file vector SVG standalone (1440 × 900 Canvas), tuân thủ XML/SVG tiêu chuẩn.

---

## 1. Mục đích và Ranh giới Artifacts

1. **Bằng chứng thiết kế Tuần 1 (Design Evidence)**:
   - Các file SVG đóng vai trò là wireframe/mockup cấu trúc, thông tin, phân bố không gian, input/output và luồng tương tác cơ bản.
   - **Không phải mã nguồn ứng dụng**: Không thay đổi hoặc thay thế template Thymeleaf, CSS, JS runtime.
   - **Không sửa đổi backend/database**: Không can thiệp Spring Boot, Controller, Service, Repository, Entity hay JWT/Security.

2. **Định hướng thiết kế**:
   - **Phong cách**: *Modern University Social Network* — Mạng xã hội học thuật chuyên nghiệp cho sinh viên và giảng viên Trường Đại học Sư phạm Kỹ thuật TP.HCM (HCMUTE).
   - **Bố cục toàn cục (3 cột Desktop)**:
     - **Navbar**: Nhận diện AloUTE, tìm kiếm toàn cục, thanh điều hướng chính, profile chip.
     - **Cột Trái (260px)**: Tóm tắt hồ sơ sinh viên, menu điều hướng nhanh, phím tắt tạo bài viết.
     - **Cột Giữa (760px)**: Vùng nội dung / luồng hoạt động chính (Feed, Composer, Detail, Search, Community, Notification).
     - **Cột Phải (300px)**: Câu lạc bộ / nhóm học thuật đề xuất, chủ đề thịnh hành (#Trending), thông tin đào tạo & thư viện.
   - **Quy tắc Responsive**:
     - *Desktop*: 3 cột (Trái | Giữa | Phải).
     - *Tablet*: 2 cột (Trái + Giữa) hoặc cột trái dạng icon bar thu nhỏ.
     - *Mobile*: 1 cột duy nhất (Giữa), menu chuyển thành Bottom App Bar / Compact Drawer.

---

## 2. Danh mục 10 Wireframe SVGs

| STT | File Wireframe | Mã màn hình | Luồng / Chức năng chính | Ranh giới kiểm soát phạm vi |
|:---:|:---|:---:|:---|:---|
| 01 | [`01-login.svg`](01-login.svg) | `WF-01` | **Đăng nhập**: Logo AloUTE, form email/username, mật khẩu, Remember Me, Quên mật khẩu, nút Log In, Đăng ký, banner báo lỗi xác thực inline. | Single university account; không thêm Google/Facebook 2FA. |
| 02 | [`02-register.svg`](02-register.svg) | `WF-02` | **Đăng ký**: Họ và tên, Username, Email trường (@hcmute.edu.vn), Mật khẩu, Xác nhận mật khẩu, đồng ý Quy tắc ứng xử, nút Đăng ký, liên kết Đăng nhập. | Xác thực định dạng email trường; mật khẩu trùng khớp. |
| 03 | [`03-home-feed.svg`](03-home-feed.svg) | `WF-03` | **Trang chủ / Bảng tin**: Bố cục 3 cột chuẩn; Composer tạo bài viết nhanh (Ảnh, Poll, Topic, Public); Post card với avatar, thời gian, tag chủ đề, placeholder media; thanh tương tác (Like, Reply, Repost, Bookmark); thông tin CLB & trending bên phải; callout mô tả các trạng thái feed (Active, Empty, Loading, Error). | Không đưa vào thuật toán gợi ý AI thương mại. |
| 04 | [`04-profile.svg`](04-profile.svg) | `WF-04` | **Hồ sơ cá nhân**: Ảnh bìa (Cover), Avatar, Tên sinh viên, Username, Bio, Khoa CNTT, K22, thống kê Follower/Following/Bài viết; hiển thị rõ ràng các nút điều khiển thực tế gồm `[Follow]` và `[Message]` (cho bạn bè/peer) và `[Edit Profile]` (cho chủ tài khoản); các tab Bài viết, Phản hồi, Media, Bookmarks; thông tin hồ sơ học tập bên phải. | Không đưa vào công cụ phân tích tuyển dụng/LinkedIn. |
| 05 | [`05-create-post.svg`](05-create-post.svg) | `WF-05` | **Hộp thoại tạo bài viết**: Modal mở rộng trên nền Feed; chọn phạm vi (Public, Followers, Community); ô soạn thảo văn bản giới hạn 2000 ký tự; chọn chủ đề (#CongNghePhanMem); vùng tải đính kèm tài liệu/ảnh; tính năng tạo bình chọn (Poll) câu hỏi và các lựa chọn; nút Hủy & Đăng bài. | Trình soạn thảo văn bản đơn giản; không có AI writer, WYSIWYG phức tạp. |
| 06 | [`06-post-detail.svg`](06-post-detail.svg) | `WF-06` | **Chi tiết bài viết & Luồng thảo luận**: Breadcrumb quay lại feed; nội dung chi tiết bài viết, đính kèm media độ phân giải đầy đủ; nút Sửa/Xóa (cho tác giả) & Báo cáo vi phạm (Report); ô phản hồi nhanh; cây bình luận phân cấp (nested reply); thông tin ngữ cảnh bài viết bên phải. | Chỉ hiển thị khả năng UI hành động; không triển khai phân quyền backend. |
| 07 | [`07-search.svg`](07-search.svg) | `WF-07` | **Tìm kiếm**: Thanh tìm kiếm lớn có nút xóa; 4 tab phân loại rõ ràng: *People*, *Posts*, *Communities*, *Topics*; danh sách kết quả có đánh dấu từ khóa phù hợp; phân trang / nút Xem thêm; khung đặc tả trạng thái không có kết quả (Empty Result State). | Tìm kiếm trong phạm vi trường; không dùng toán tử Boolean, AI ranking. |
| 08 | [`08-community.svg`](08-community.svg) | `WF-08` | **Trang Cộng đồng (CLB)**: Ảnh bìa, biểu trưng CLB, tên CLB, mô tả, số lượng thành viên; nút Tham gia / Rời nhóm; nhãn phân quyền thành viên (*Member*, *Moderator*, *Admin*); ô đăng bài trong nhóm; danh sách bài viết đã lọc theo CLB; thông tin ban chủ nhiệm & quy định CLB bên phải. | Không tạo kênh chat voice, bot, kiến trúc server Discord. |
| 09 | [`09-messaging.svg`](09-messaging.svg) | `WF-09` | **Nhắn tin trực tiếp 1-1**: Bố cục 2 cột chuyên biệt; Cột trái: ô tìm kiếm hội thoại, danh sách cuộc trò chuyện, avatar, xem trước tin nhắn cuối, chỉ số chưa đọc, đặc tả trạng thái rỗng; Cột phải: tiêu đề người nhận, lịch sử tin nhắn bong bóng (đến/đi kèm thời gian), ô soạn tin nhắn và nút Gửi. | Chỉ nhắn tin 1-1; không có voice/video call, chat nhóm, bot. |
| 10 | [`10-notification.svg`](10-notification.svg) | `WF-10` | **Trung tâm thông báo**: Tiêu đề, tab lọc Tất cả / Chưa đọc, nút Đánh dấu đã đọc; 4 nhóm thông báo cốt lõi (*Thích bài viết*, *Trả lời bình luận*, *Theo dõi mới*, *Hoạt động câu lạc bộ*); chỉ thị trạng thái Đã đọc / Chưa đọc rõ ràng; khung đặc tả trạng thái không có thông báo mới (Empty State). | Thông báo tiêu chuẩn nội bộ trường; không tạo ma trận cài đặt phức tạp. |

---

## 3. Hệ thống trực quan của Wireframe (Dark Visual Direction)

- **Canvas Dimension**: `1440 × 900` px.
- **Màu sắc wireframe tối chuẩn (Đồng bộ với UI Design System & tokens.css)**:
  - Nền canvas tối chuyên nghiệp: `#0B0F19` (Dark body background).
  - Khối thẻ / Card surface: `#131B2A` viền `#233148` (Slate dark borders), bo góc `rx="8px"`.
  - Thanh trạng thái / Metadata: `#030712`, chữ sáng `#F8FAFC`, phụ `#64748B`.
  - Navbar: `#111827`, viền `#1F2937`, chữ thương hiệu `#3B82F6`.
  - Vùng đính kèm / Media placeholder: `#0F172A` viền đứt nét `#334155`, chữ placeholder `#94A3B8`.
  - Nút chính (Primary CTA): `#2563EB`, viền `#1D4ED8`, chữ trắng đậm.
  - Nút phụ (Secondary): `#1E293B`, viền `#334155`, chữ `#CBD5E1`.
  - Khối cảnh báo lỗi (Error Alert): Nền `#2D1214`, viền `#7F1D1D`, chữ `#FCA5A5`.
  - Khối thông báo (Info Alert): Nền `#0C1E3A`, viền `#1E40AF`, chữ `#93C5FD`.
  - Nhãn vai trò (Role Badge): Nền `#1E3A8A`, viền `#3B82F6`, chữ `#93C5FD`.
- **Typography & Độ tương phản**: Phông hệ thống sans-serif (`system-ui, -apple-system, sans-serif`), chữ tiêu đề `#F8FAFC`, nội dung `#CBD5E1`, tương phản cao, phân cấp trực quan rõ rệt.
- **Tiêu chuẩn kỹ thuật**:
  - 100% hợp lệ cú pháp XML/SVG (`ElementTree` validated).
  - Không nhúng ảnh ngoài (zero external image dependencies).
  - Có đầy đủ định danh màn hình (`WF-01` đến `WF-10`), ghi chú sở hữu của **Phạm Minh Quân**, và chỉ dẫn Responsive Layout ở thanh chân trang.

---

## 4. Bảng kiểm tra xác thực (Verification Checklist)

- [x] Đầy đủ 10 file SVG với tên chính xác tại `docs/ui/wireframes/`.
- [x] Mỗi file có thuộc tính `xmlns`, `viewBox="0 0 1440 900"`, `width="1440"`, `height="900"`.
- [x] Kiểm tra cú pháp XML thành công 10/10 file.
- [x] Nhãn giao diện nhất quán, đúng ngữ cảnh mạng xã hội sinh viên HCMUTE.
- [x] Thể hiện đầy đủ các thành phần, input, action, output và các trạng thái (error, empty, loading).
- [x] Không can thiệp bất kỳ file nào ngoài thư mục `docs/ui/wireframes/**`.
