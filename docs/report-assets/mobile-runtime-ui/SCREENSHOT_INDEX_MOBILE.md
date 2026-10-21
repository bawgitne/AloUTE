# Mục lục ảnh chụp giao diện thực tế Mobile (Flutter Client)

Ảnh chụp trực tiếp từ thiết bị di động thật kết nối qua ADB (`720×1600`), chạy ứng dụng **AloUTE Mobile** (`aloute_mobile`). Toàn bộ giao diện tuân thủ quy chuẩn thiết kế **Light Campus Style** (Trường ĐH Sư phạm Kỹ thuật TP.HCM).

| STT | Tệp ảnh | Màn hình tương ứng trên Web | Luồng / Chức năng chính | Ghi chú so sánh với Web |
| :-- | :--- | :--- | :--- | :--- |
| 1 | `00-forgot-password.png` | `/forgot-password` | Quên mật khẩu & gửi liên kết khôi phục | *Web chưa có ảnh trong doc* |
| 2 | `01-login.png` | `01-login.png` | Đăng nhập tài khoản sinh viên/admin | Form đăng nhập, nút demo sinh viên/admin |
| 3 | `02-register.png` | `02-register.png` | Đăng ký tài khoản sinh viên UTE | Nhập username, email trường, mật khẩu |
| 4 | `03-home-feed.png` | `03-home-feed-a.png` | Bảng tin chính Campus Feed | Khung nhìn chuẩn: thanh điều hướng, ô tạo bài, bài viết |
| 5 | `03-home-feed-b.png` | `03-home-feed-b.png` | Bảng tin cuộn & Tương tác | Có cuộc bình chọn Poll 3 lựa chọn, bài viết tương tác |
| 6 | `04-profile-a.png` | `04-profile-a.png` | Hồ sơ cá nhân sinh viên | Ảnh bìa, avatar, khoa CNTT, MSSV, chỉ số follower/following |
| 7 | `04-edit-profile.png` | `/settings` | Chỉnh sửa hồ sơ cá nhân | Sửa tên hiển thị, khoa, tiểu sử (bio), avatar |
| 8 | `05-create-post-a.png` | `05-create-post-a.png` | Hộp thoại tạo bài viết mới | Biểu mẫu trống, chọn phạm vi công khai/cộng đồng, đính kèm ảnh/poll |
| 9 | `05-create-post-b.png` | `05-create-post-b.png` | Tạo bài viết trạng thái nhập liệu | Minh họa soạn thảo nội dung bài viết |
| 10 | `06-post-detail.png` | `06-post-detail-b.png` | Chi tiết bài viết & Bình luận | Xem đầy đủ nội dung bài đăng, bình chọn poll và cây bình luận |
| 11 | `07-search-a.png` | `07-search-a.png` | Khám phá chủ đề thịnh hành | Danh mục hashtag học thuật: #CongNghePhanMem, #DoAnTotNghiep2026... |
| 12 | `07-search-b.png` | `07-search-a.png` | Tìm kiếm sinh viên & bạn bè | Tìm theo tên/MSSV và lối tắt xem kết nối |
| 13 | `08-community-a.png` | `08-community-a.png` | Danh sách CLB / Cộng đồng | CLB Tin Học HITC, CLB Tiếng Anh ESC, Đoàn - Hội FEEE |
| 14 | `08-community-detail.png` | `08-community-b.png` | Chi tiết Cộng đồng CLB HITC | Ảnh bìa, thông tin, số thành viên, bài viết workshop |
| 15 | `09-messaging-a.png` | `09-messaging-a.png` | Danh sách cuộc trò chuyện tin nhắn | Lịch sử chat, avatar, tin nhắn cuối, chỉ báo online, unread badge |
| 16 | `09-messaging-b.png` | `09-messaging-a.png` | Khung chat trò chuyện trực tiếp 1-1 | Bong bóng chat đến/đi thời gian thực, ô soạn tin nhắn |
| 17 | `10-notifications-a.png` | `10-notifications-a.png` | Trung tâm thông báo (Tất cả) | 4 thông báo tương tác (thích bài, reply, follow, CLB) |
| 18 | `10-notifications-b.png` | `10-notifications-b.png` | Thông báo chưa đọc | Lọc danh sách thông báo mới cần xử lý |
| 19 | `11-admin-dashboard.png` | `11-admin-dashboard.png` | Bảng điều khiển Quản trị (Admin) | Thẻ KPI người dùng, bài viết, báo cáo, CLB & 3 mô-đun quản lý |
| 20 | `12-admin-users.png` | `12-admin-users.png` | Quản lý người dùng sinh viên | Danh sách tài khoản kèm thao tác Khóa / Kích hoạt |
| 21 | `12-settings.png` | `/settings` | Cài đặt ứng dụng & tài khoản | Thiết lập hồ sơ, giao diện Light Campus, thông báo, bảo mật |
| 22 | `13-admin-reports.png` | `13-admin-reports.png` | Hàng đợi kiểm duyệt báo cáo vi phạm | **Có dữ liệu thật**: 2 báo cáo vi phạm kèm nút Bỏ qua / Gỡ bài |
| 23 | `14-friends.png` | `14-friends-broken.png` | Mạng lưới bạn bè (Đang theo dõi) | **Hoàn chỉnh 100%** (khắc phục lỗi gãy render HTML trên Web) |
| 24 | `14-friends-b.png` | `14-friends-broken.png` | Danh sách người theo dõi (Followers) | Danh sách tài khoản đang theo dõi sinh viên |
| 25 | `15-saved.png` | `15-saved.png` | Danh sách bài viết đã lưu | Bộ sưu tập tài liệu, bài viết CLB đã đánh dấu lưu |
| 26 | `16-navigation-drawer.png` | Sidebar Web | Thanh điều hướng trượt Drawer | Truy cập nhanh mọi phân hệ, thông tin sinh viên & đăng xuất |
| 27 | `17-admin-posts.png` | Quản trị bài viết | Kiểm duyệt nội dung bài viết Admin | Rà soát bài đăng vi phạm và gỡ bài |
