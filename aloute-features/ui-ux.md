# Giao diện responsive

Giao diện sử dụng Thymeleaf và Bootstrap 5, ưu tiên mobile-first và tính nhất quán.

## Màn hình Guest

- Landing/home với tối thiểu 10 bài công khai nổi bật.
- Search công khai.
- Profile công khai.
- Đăng ký, nhập OTP, đăng nhập và quên mật khẩu.

## Màn hình User

- Home feed với infinite scroll hoặc pagination 20 bài.
- Composer tạo bài có preview media và progress upload.
- Trang chi tiết bài và comment.
- Profile, bạn bè, saved posts và history.
- Search/explore.
- Chat layout hai cột trên desktop, một cột trên mobile.
- Notification center.
- Groups và events.
- Settings/security/privacy.

## Màn hình Manager/Admin

- Sidebar thu gọn trên mobile.
- Dashboard card + Chart.js.
- Data table có search, filter, sort, pagination.
- Report detail hiển thị evidence, snapshot và lịch sử xử lý.
- Modal xác nhận cho hành động khóa/ẩn/đổi role, bắt buộc nhập lý do.

## Design system

- Màu chính, success, warning, danger và neutral được định nghĩa bằng CSS variables.
- Component dùng chung: navbar, post card, avatar, badge, empty state, skeleton, toast, pagination.
- Có trạng thái loading, empty, validation error, permission denied và network disconnected.
- Contrast đạt mức dễ đọc; icon có label/tooltip; form có label thật.
- Không truyền đạt trạng thái chỉ bằng màu sắc.

## Responsive acceptance criteria

- Kiểm thử ở 360 px, 768 px, 1024 px và desktop lớn.
- Không có horizontal scroll ngoài vùng chủ đích.
- Nút chính có vùng bấm đủ lớn trên mobile.
- Ảnh/video giữ đúng tỷ lệ, có lazy loading và fallback.
- Chat và notification hiển thị trạng thái mất kết nối/reconnect rõ ràng.

