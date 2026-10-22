-- ==============================================================================
-- AloUTE - COMPREHENSIVE MOCK DATA SEED SCRIPT (MYSQL 8 / AIVEN)
-- Bao quát toàn bộ 22 bảng trong hệ thống cơ sở dữ liệu AloUTE:
--   users, profiles, user_notification_settings, topics, user_topics,
--   communities, community_members, posts (root posts & nested replies),
--   post_topics, polls, poll_options, poll_votes, post_likes, bookmarks,
--   reposts, follows, conversations, conversation_members, messages,
--   notifications, reports, moderation_actions.
--
-- TÀI KHOẢN ĐĂNG NHẬP:
--   Admin: admin / admin@aloute.local        -> Mật khẩu: Admin@123
--   User:  khoinguyen, minhquang, thuytien,... -> Mật khẩu: User@123
-- ==============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------------------------
-- 1. NÂNG QUYỀN TÀI KHOẢN HIỆN CÓ THÀNH SYSTEM_ADMIN
-- ------------------------------------------------------------------------------
UPDATE users 
SET global_role = 'SYSTEM_ADMIN', is_verified = 1, is_active = 1, verification_required = 0 
WHERE username IN ('bangne', 'admin', 'Khôi Nguyễn');

-- ------------------------------------------------------------------------------
-- 2. TẠO 10 TÀI KHOẢN MẪU (USERS)
--    BCrypt Hashes:
--      Admin@123: $2a$10$.BQUaMPSW0o8ybEV4NlnbesfeML4Eb/R3p43w2eMPyQtOEKyuC9sS
--      User@123:  $2a$10$QVBbKeCIi4V/GBxA49Kl4e8sKZ0yrfjB9j1drx8w0BsgxTJPjiYvy
-- ------------------------------------------------------------------------------
INSERT INTO users (id, username, email, password_hash, is_verified, verification_required, is_private, is_active, global_role, auth_version, version, two_factor_enabled, created_at, updated_at)
VALUES 
(101, 'admin', 'admin@aloute.local', '$2a$10$.BQUaMPSW0o8ybEV4NlnbesfeML4Eb/R3p43w2eMPyQtOEKyuC9sS', 1, 0, 0, 1, 'SYSTEM_ADMIN', 0, 0, 0, NOW() - INTERVAL 30 DAY, NOW()),
(102, 'khoinguyen', 'khoinguyen@aloute.local', '$2a$10$QVBbKeCIi4V/GBxA49Kl4e8sKZ0yrfjB9j1drx8w0BsgxTJPjiYvy', 1, 0, 0, 1, 'USER', 0, 0, 0, NOW() - INTERVAL 25 DAY, NOW()),
(103, 'minhquang', 'minhquang@aloute.local', '$2a$10$QVBbKeCIi4V/GBxA49Kl4e8sKZ0yrfjB9j1drx8w0BsgxTJPjiYvy', 1, 0, 0, 1, 'USER', 0, 0, 0, NOW() - INTERVAL 20 DAY, NOW()),
(104, 'thuytien', 'thuytien@aloute.local', '$2a$10$QVBbKeCIi4V/GBxA49Kl4e8sKZ0yrfjB9j1drx8w0BsgxTJPjiYvy', 1, 0, 0, 1, 'USER', 0, 0, 0, NOW() - INTERVAL 18 DAY, NOW()),
(105, 'hoangnam', 'hoangnam@aloute.local', '$2a$10$QVBbKeCIi4V/GBxA49Kl4e8sKZ0yrfjB9j1drx8w0BsgxTJPjiYvy', 1, 0, 0, 1, 'USER', 0, 0, 0, NOW() - INTERVAL 15 DAY, NOW()),
(106, 'baongoc', 'baongoc@aloute.local', '$2a$10$QVBbKeCIi4V/GBxA49Kl4e8sKZ0yrfjB9j1drx8w0BsgxTJPjiYvy', 1, 0, 0, 1, 'USER', 0, 0, 0, NOW() - INTERVAL 12 DAY, NOW()),
(107, 'thanhdat', 'thanhdat@aloute.local', '$2a$10$QVBbKeCIi4V/GBxA49Kl4e8sKZ0yrfjB9j1drx8w0BsgxTJPjiYvy', 1, 0, 0, 1, 'USER', 0, 0, 0, NOW() - INTERVAL 10 DAY, NOW()),
(108, 'phuonganh', 'phuonganh@aloute.local', '$2a$10$QVBbKeCIi4V/GBxA49Kl4e8sKZ0yrfjB9j1drx8w0BsgxTJPjiYvy', 1, 0, 0, 1, 'USER', 0, 0, 0, NOW() - INTERVAL 8 DAY, NOW()),
(109, 'tuananh_fit', 'tuananh@aloute.local', '$2a$10$QVBbKeCIi4V/GBxA49Kl4e8sKZ0yrfjB9j1drx8w0BsgxTJPjiYvy', 1, 0, 0, 1, 'USER', 0, 0, 0, NOW() - INTERVAL 5 DAY, NOW()),
(110, 'clb_guitar', 'guitar.ute@aloute.local', '$2a$10$QVBbKeCIi4V/GBxA49Kl4e8sKZ0yrfjB9j1drx8w0BsgxTJPjiYvy', 1, 0, 0, 1, 'USER', 0, 0, 0, NOW() - INTERVAL 3 DAY, NOW())
ON DUPLICATE KEY UPDATE 
    global_role = VALUES(global_role),
    is_verified = VALUES(is_verified),
    is_active = VALUES(is_active);

-- ------------------------------------------------------------------------------
-- 3. HỒ SƠ CÁ NHÂN (PROFILES)
-- ------------------------------------------------------------------------------
INSERT INTO profiles (user_id, display_name, bio, avatar_url, cover_url, website, followers_count, following_count, posts_count)
VALUES 
(101, 'Quản Trị Viên AloUTE', 'Tài khoản điều hành và giám sát hệ thống mạng xã hội AloUTE HCMUTE.', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=256&q=80', 'https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?auto=format&fit=crop&w=1200&q=80', 'https://justneed.food', 245, 12, 14),
(102, 'Khôi Nguyễn', 'Sinh viên K21 Khoa Công nghệ Thông tin HCMUTE 🚀 Đam mê Flutter, Spring Boot & Cloud Run.', 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=256&q=80', 'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?auto=format&fit=crop&w=1200&q=80', NULL, 88, 42, 19),
(103, 'Minh Quang', 'Kỹ thuật Phần mềm K21 | Thích bóng rổ, guitar và cà phê code đêm ☕🏀', 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&w=256&q=80', NULL, NULL, 54, 38, 11),
(104, 'Thủy Tiên', 'Khoa Kinh tế & Quản trị kinh doanh HCMUTE 🌸 Trưởng ban Truyền thông CLB Tiếng Anh.', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=256&q=80', 'https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=1200&q=80', NULL, 162, 75, 16),
(105, 'Hoàng Nam', 'Khoa Cơ Khí Động Lực 🚗 Đam mê Robot & Xe tự hành Autonomous Racing.', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=256&q=80', NULL, NULL, 41, 35, 7),
(106, 'Bảo Ngọc', 'Khoa Điện - Điện tử HCMUTE ⚡ Trợ giảng môn Vi xử lý & IoT Systems.', 'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?auto=format&fit=crop&w=256&q=80', NULL, NULL, 73, 50, 9),
(107, 'Thành Đạt', 'Kỹ thuật Dữ liệu K22 | Thích nghiên cứu Machine Learning, Kaggle & Prompt Engineering 🤖', 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=256&q=80', NULL, NULL, 65, 48, 12),
(108, 'Phương Anh', 'Khoa May & Thời trang UTE 🎨 Thiết kế đồ họa UI/UX Freelancer.', 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=256&q=80', NULL, 'https://behance.net', 110, 80, 15),
(109, 'TS. Tuấn Anh', 'Giảng viên Bộ môn Mạng & An toàn thông tin - Khoa CNTT HCMUTE 👨‍🏫', 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=256&q=80', NULL, 'https://fit.hcmute.edu.vn', 350, 20, 25),
(110, 'CLB Guitar UTE', 'CLB Âm nhạc sinh viên trường ĐH Sư phạm Kỹ thuật TP.HCM 🎸 Giao lưu acoustic mỗi thứ 7!', 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=256&q=80', 'https://images.unsplash.com/photo-1465847899084-d164df4dedc6?auto=format&fit=crop&w=1200&q=80', 'https://facebook.com/guitar.ute', 520, 10, 34)
ON DUPLICATE KEY UPDATE 
    display_name = VALUES(display_name),
    bio = VALUES(bio),
    avatar_url = VALUES(avatar_url),
    cover_url = VALUES(cover_url),
    followers_count = VALUES(followers_count),
    following_count = VALUES(following_count);

-- ------------------------------------------------------------------------------
-- 4. CÀI ĐẶT THÔNG BÁO NGƯỜI DÙNG (USER_NOTIFICATION_SETTINGS)
-- ------------------------------------------------------------------------------
INSERT INTO user_notification_settings (user_id, likes_enabled, comments_enabled, follows_enabled, mentions_enabled, email_notifications_enabled, sound_enabled)
VALUES 
(101, 1, 1, 1, 1, 1, 1),
(102, 1, 1, 1, 1, 1, 1),
(103, 1, 1, 1, 1, 0, 1),
(104, 1, 1, 1, 1, 1, 1),
(105, 1, 1, 1, 1, 0, 1),
(106, 1, 1, 1, 1, 1, 1),
(107, 1, 1, 1, 1, 0, 1),
(108, 1, 1, 1, 1, 1, 1),
(109, 1, 1, 1, 1, 1, 1),
(110, 1, 1, 1, 1, 1, 1)
ON DUPLICATE KEY UPDATE likes_enabled = VALUES(likes_enabled);

-- ------------------------------------------------------------------------------
-- 5. CHỦ ĐỀ MẪU (TOPICS)
-- ------------------------------------------------------------------------------
INSERT INTO topics (id, name, slug, created_at)
VALUES 
(1, 'Học tập & Đồ án', 'hoc-tap', NOW() - INTERVAL 30 DAY),
(2, 'Công nghệ & Lập trình', 'cong-nghe', NOW() - INTERVAL 30 DAY),
(3, 'Đời sống sinh viên', 'doi-song', NOW() - INTERVAL 30 DAY),
(4, 'Hỏi đáp UTE', 'hoi-dap', NOW() - INTERVAL 30 DAY),
(5, 'Tuyển dụng & Thực tập', 'viec-lam', NOW() - INTERVAL 30 DAY),
(6, 'CLB & Sự kiện', 'clb-su-kien', NOW() - INTERVAL 25 DAY),
(7, 'Review môn học & Giảng viên', 'review-mon-hoc', NOW() - INTERVAL 20 DAY),
(8, 'Góc tìm đồ & Phòng trọ', 'nha-tro-tim-do', NOW() - INTERVAL 15 DAY)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ------------------------------------------------------------------------------
-- 6. NGƯỜI DÙNG THEO DÕI CHỦ ĐỀ (USER_TOPICS)
-- ------------------------------------------------------------------------------
INSERT INTO user_topics (user_id, topic_id, created_at)
VALUES 
(101, 1, NOW()), (101, 2, NOW()), (101, 4, NOW()),
(102, 1, NOW()), (102, 2, NOW()), (102, 5, NOW()),
(103, 2, NOW()), (103, 3, NOW()), (103, 6, NOW()),
(104, 3, NOW()), (104, 6, NOW()), (104, 7, NOW()),
(105, 1, NOW()), (105, 4, NOW()),
(106, 1, NOW()), (106, 2, NOW()),
(107, 2, NOW()), (107, 5, NOW()),
(108, 3, NOW()), (108, 8, NOW())
ON DUPLICATE KEY UPDATE created_at = VALUES(created_at);

-- ------------------------------------------------------------------------------
-- 7. CỘNG ĐỒNG MẪU (COMMUNITIES)
-- ------------------------------------------------------------------------------
INSERT INTO communities (id, owner_id, name, slug, description, avatar_url, cover_url, is_private, member_count, post_count, created_at, updated_at)
VALUES 
(1, 109, 'Khoa Công Nghệ Thông Tin - HCMUTE', 'fit-hcmute', 'Kênh thông tin chính thức, tài liệu học tập, đồ án tốt nghiệp và cơ hội nghề nghiệp dành cho sinh viên Khoa CNTT.', 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?auto=format&fit=crop&w=256&q=80', 'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?auto=format&fit=crop&w=1200&q=80', 0, 420, 45, NOW() - INTERVAL 28 DAY, NOW()),
(2, 101, 'Cộng Đồng Sinh Viên UTE (AloUTE)', 'sinh-vien-ute', 'Không gian mở dành cho sinh viên trường ĐH Sư phạm Kỹ thuật TP.HCM trao đổi, chia sẻ kinh nghiệm học tập và đời sống.', 'https://images.unsplash.com/photo-1523240795612-9a054b0db644?auto=format&fit=crop&w=256&q=80', 'https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?auto=format&fit=crop&w=1200&q=80', 0, 680, 80, NOW() - INTERVAL 28 DAY, NOW()),
(3, 102, 'CLB Lập Trình & AI HCMUTE', 'clb-ai-lap-trinh', 'Cộng đồng đam mê nghiên cứu Trí tuệ nhân tạo, Data Science, Flutter, Web3 và Cloud Computing.', 'https://images.unsplash.com/photo-1555066931-4365d14bab8c?auto=format&fit=crop&w=256&q=80', 'https://images.unsplash.com/photo-1504384308090-c894fdcc538d?auto=format&fit=crop&w=1200&q=80', 0, 185, 22, NOW() - INTERVAL 20 DAY, NOW()),
(4, 105, 'Khoa Cơ Khí Chế Tạo Máy & Động Lực', 'fme-hcmute', 'Diễn đàn kỹ thuật, tài liệu AutoDesk, SolidWorks, đồ án chi tiết máy và nghiên cứu chế tạo cơ khí.', 'https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=256&q=80', NULL, 0, 210, 18, NOW() - INTERVAL 15 DAY, NOW()),
(5, 110, 'CLB Âm Nhạc & Nghệ Thuật HCMUTE', 'am-nhac-ute', 'Mái nhà chung của những tâm hồn đam mê Acoustic, Guitar, Piano và biểu diễn các sự kiện lớn nhỏ trong trường.', 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=256&q=80', 'https://images.unsplash.com/photo-1465847899084-d164df4dedc6?auto=format&fit=crop&w=1200&q=80', 0, 310, 35, NOW() - INTERVAL 12 DAY, NOW()),
(6, 101, 'Nhóm Nghiên Cứu Khoa Học Trọng Điểm', 'nckh-ute-private', 'Cộng đồng bảo mật dành riêng cho các giảng viên và sinh viên đề tài Nghiên cứu Khoa học cấp Trường.', 'https://images.unsplash.com/photo-1532094349884-543bc11b234d?auto=format&fit=crop&w=256&q=80', NULL, 1, 35, 8, NOW() - INTERVAL 10 DAY, NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- ------------------------------------------------------------------------------
-- 8. THÀNH VIÊN CỘNG ĐỒNG (COMMUNITY_MEMBERS)
-- ------------------------------------------------------------------------------
INSERT INTO community_members (community_id, user_id, role, joined_at)
VALUES 
(1, 109, 'ADMIN', NOW()), (1, 101, 'MODERATOR', NOW()), (1, 102, 'MEMBER', NOW()), (1, 103, 'MEMBER', NOW()), (1, 107, 'MEMBER', NOW()),
(2, 101, 'ADMIN', NOW()), (2, 104, 'MODERATOR', NOW()), (2, 102, 'MEMBER', NOW()), (2, 103, 'MEMBER', NOW()), (2, 105, 'MEMBER', NOW()), (2, 108, 'MEMBER', NOW()), (2, 110, 'MEMBER', NOW()),
(3, 102, 'ADMIN', NOW()), (3, 107, 'MODERATOR', NOW()), (3, 103, 'MEMBER', NOW()), (3, 101, 'MEMBER', NOW()),
(4, 105, 'ADMIN', NOW()), (4, 101, 'MEMBER', NOW()), (4, 103, 'MEMBER', NOW()),
(5, 110, 'ADMIN', NOW()), (5, 104, 'MODERATOR', NOW()), (5, 103, 'MEMBER', NOW()), (5, 108, 'MEMBER', NOW()),
(6, 101, 'ADMIN', NOW()), (6, 109, 'MODERATOR', NOW()), (6, 102, 'MEMBER', NOW())
ON DUPLICATE KEY UPDATE role = VALUES(role);

-- ------------------------------------------------------------------------------
-- 9. BÀI VIẾT GỐC (ROOT POSTS - BẢNG POSTS)
-- ------------------------------------------------------------------------------
INSERT INTO posts (id, user_id, content, parent_post_id, quoted_post_id, community_id, visibility, allow_reply, allow_quote, like_count, reply_count, repost_count, quote_count, view_count, created_at, updated_at)
VALUES 
(1001, 101, 'Chào mừng tất cả các bạn sinh viên đã đến với mạng xã hội AloUTE! 🎓 Chúc các bạn có một kỳ học mới tràn đầy năng lượng và đạt kết quả thật cao!', NULL, NULL, 2, 'PUBLIC', 1, 1, 48, 5, 8, 0, 420, NOW() - INTERVAL 24 HOUR, NOW()),
(1002, 109, 'Thông báo từ Khoa CNTT: Lịch nộp đề cương Đồ án Tốt nghiệp học kỳ 2 hạn chót là 17h00 ngày 30/11/2026. Các nhóm sinh viên lưu ý nộp đúng quy cách và có chữ ký xác nhận của GVHD.', NULL, NULL, 1, 'PUBLIC', 1, 1, 65, 4, 15, 0, 580, NOW() - INTERVAL 20 HOUR, NOW()),
(1003, 102, 'Cho mình hỏi các bạn K21 ngành Kỹ thuật Phần mềm kỳ này có nhóm làm Đồ án Tốt nghiệp chưa ạ? Nhóm mình đang cần tuyển thêm 1 bạn làm Frontend Flutter & Flutter Web nhé! Liên hệ mình nha 🚀', NULL, NULL, 1, 'PUBLIC', 1, 1, 28, 4, 3, 0, 210, NOW() - INTERVAL 16 HOUR, NOW()),
(1004, 103, 'Bộ tài liệu tóm tắt môn Cấu trúc Dữ liệu và Giải thuật (DSA) + đề thi trắc nghiệm các kỳ trước mình đã upload lên Google Drive. Bạn nào cần comment bên dưới mình gửi link nha ☕📚', NULL, NULL, 1, 'PUBLIC', 1, 1, 82, 6, 24, 0, 690, NOW() - INTERVAL 12 HOUR, NOW()),
(1005, 104, 'Quán cơm tấm đêm đối diện cổng số 2 trường mình dạo này có mở lại chưa mọi người ơi? Đang đói bụng chạy deadline đồ án quá 😭🍲', NULL, NULL, 2, 'PUBLIC', 1, 1, 19, 3, 1, 0, 140, NOW() - INTERVAL 10 HOUR, NOW()),
(1006, 107, 'Bài viết tổng hợp: Lộ trình tự học Machine Learning & Deep Learning từ con số 0 đến làm dự án thực tế cho sinh viên UTE. Mình đã đính kèm roadmap và github repo mã nguồn mẫu nhé!', NULL, NULL, 3, 'PUBLIC', 1, 1, 54, 3, 12, 0, 390, NOW() - INTERVAL 8 HOUR, NOW()),
(1007, 102, 'Vừa deploy thành công phiên bản AloUTE Mobile với tính năng xác thực 2FA bằng mã TOTP Authenticator cực mượt! Các bạn vào mục Cài đặt -> Bảo mật 2 lớp test thử nhé!', NULL, NULL, 3, 'PUBLIC', 1, 1, 41, 4, 6, 0, 290, NOW() - INTERVAL 6 HOUR, NOW()),
(1008, 110, '🎸 ĐÊM NHẠC ACOUSTIC HÈ 2026: CLB Guitar UTE sẽ tổ chức đêm nhạc tại sảnh A vào 18h30 thứ 6 tuần này. Vào cổng tự do, có trà sữa và âm nhạc chill hết nấc! Tag ngay đứa bạn thân đi cùng nào 🎶', NULL, NULL, 5, 'PUBLIC', 1, 1, 95, 4, 32, 0, 810, NOW() - INTERVAL 5 HOUR, NOW()),
(1009, 105, 'Bác nào có kinh nghiệm gia công chi tiết CNC trên máy phay 5 trục ở xưởng C cho em xin ít review về dung sai và dao cắt với ạ. Em xin cảm ơn và hậu tạ cà phê!', NULL, NULL, 4, 'PUBLIC', 1, 1, 15, 2, 0, 0, 115, NOW() - INTERVAL 4 HOUR, NOW()),
(1010, 108, 'Góc tìm đồ thất lạc: Trưa nay tầm 11h30 mình có để quên 1 chiếc thẻ sinh viên tên Đặng Phương Anh tại bàn số 4 canteen khu B. Bạn nào nhặt được cho mình xin chuộc lại ạ 🙏', NULL, NULL, 2, 'PUBLIC', 1, 1, 32, 3, 14, 0, 310, NOW() - INTERVAL 3 HOUR, NOW()),
(1011, 101, 'Khảo sát ý kiến sinh viên: Kỳ này mọi người đăng ký bao nhiêu tín chỉ để cân bằng việc học và làm thêm?', NULL, NULL, 2, 'PUBLIC', 1, 1, 37, 2, 2, 0, 280, NOW() - INTERVAL 2 HOUR, NOW())
ON DUPLICATE KEY UPDATE 
    content = VALUES(content),
    like_count = VALUES(like_count),
    reply_count = VALUES(reply_count),
    repost_count = VALUES(repost_count);

-- ------------------------------------------------------------------------------
-- 10. GẮN CHỦ ĐỀ CHO BÀI VIẾT (POST_TOPICS)
-- ------------------------------------------------------------------------------
INSERT INTO post_topics (post_id, topic_id)
VALUES 
(1001, 3), (1001, 6),
(1002, 1), (1002, 2),
(1003, 1), (1003, 2),
(1004, 1), (1004, 7),
(1005, 3), (1005, 4),
(1006, 2), (1006, 5),
(1007, 2),
(1008, 6), (1008, 3),
(1009, 1), (1009, 4),
(1010, 8), (1010, 4),
(1011, 1), (1011, 3)
ON DUPLICATE KEY UPDATE topic_id = VALUES(topic_id);

-- ------------------------------------------------------------------------------
-- 11. BÌNH CHỌN MẪU (POLLS, POLL_OPTIONS, POLL_VOTES)
-- ------------------------------------------------------------------------------
INSERT INTO polls (id, post_id, question, allow_multiple, closed_at, created_at)
VALUES 
(1, 1011, 'Kỳ này bạn đăng ký bao nhiêu tín chỉ?', 0, NOW() + INTERVAL 7 DAY, NOW() - INTERVAL 2 HOUR)
ON DUPLICATE KEY UPDATE question = VALUES(question);

INSERT INTO poll_options (id, poll_id, option_text, vote_count, position)
VALUES 
(1, 1, 'Dưới 14 tín chỉ (Nhẹ nhàng)', 8, 1),
(2, 1, '14 - 18 tín chỉ (Chuẩn tiến độ)', 24, 2),
(3, 1, '19 - 24 tín chỉ (Cày cuốc tốt nghiệp sớm)', 15, 3),
(4, 1, 'Trên 24 tín chỉ (Bất tử)', 3, 4)
ON DUPLICATE KEY UPDATE vote_count = VALUES(vote_count);

INSERT INTO poll_votes (id, poll_id, option_id, user_id, created_at)
VALUES 
(1, 1, 2, 102, NOW() - INTERVAL 90 MINUTE),
(2, 1, 2, 103, NOW() - INTERVAL 80 MINUTE),
(3, 1, 3, 104, NOW() - INTERVAL 70 MINUTE),
(4, 1, 1, 105, NOW() - INTERVAL 60 MINUTE),
(5, 1, 2, 106, NOW() - INTERVAL 50 MINUTE),
(6, 1, 3, 107, NOW() - INTERVAL 40 MINUTE),
(7, 1, 4, 108, NOW() - INTERVAL 30 MINUTE)
ON DUPLICATE KEY UPDATE option_id = VALUES(option_id);

-- ------------------------------------------------------------------------------
-- 12. BÌNH LUẬN & THẢO LUẬN (REPLIES / COMMENTS - BẢNG POSTS)
-- ------------------------------------------------------------------------------
INSERT INTO posts (id, user_id, content, parent_post_id, quoted_post_id, community_id, visibility, allow_reply, allow_quote, like_count, reply_count, repost_count, quote_count, view_count, created_at, updated_at)
VALUES 
-- Replies cho Post 1001 (Chào mừng)
(2001, 102, 'Giao diện app AloUTE phiên bản mới nhìn hiện đại và mượt mà quá admin ơi! 🔥', 1001, NULL, 2, 'PUBLIC', 1, 1, 6, 0, 0, 0, 45, NOW() - INTERVAL 23 HOUR, NOW()),
(2002, 104, 'Hy vọng sắp tới có thêm nhiều tính năng hay cho các CLB trong trường nữa ạ ❤️', 1001, NULL, 2, 'PUBLIC', 1, 1, 4, 0, 0, 0, 38, NOW() - INTERVAL 22 HOUR, NOW()),
(2003, 101, 'Cảm ơn các bạn nhé, đội ngũ phát triển đang tiếp tục hoàn thiện thêm nhiều tính năng mới!', 1001, NULL, 2, 'PUBLIC', 1, 1, 5, 0, 0, 0, 32, NOW() - INTERVAL 21 HOUR, NOW()),
(2004, 103, 'Ủng hộ dự án mạng xã hội nội bộ của trường mình 100%!', 1001, NULL, 2, 'PUBLIC', 1, 1, 3, 0, 0, 0, 28, NOW() - INTERVAL 20 HOUR, NOW()),
(2005, 108, 'UI màu xanh navy dịu mắt và xịn xò ghê, điểm 10 trải nghiệm người dùng!', 1001, NULL, 2, 'PUBLIC', 1, 1, 2, 0, 0, 0, 22, NOW() - INTERVAL 19 HOUR, NOW()),

-- Replies cho Post 1002 (Thông báo ĐATN)
(2006, 102, 'Thầy cho em hỏi phần nộp bản in qua văn phòng khoa hay chỉ cần nộp file PDF trên hệ thống thôi ạ?', 1002, NULL, 1, 'PUBLIC', 1, 1, 3, 0, 0, 0, 50, NOW() - INTERVAL 19 HOUR, NOW()),
(2007, 109, '@khoinguyen Cả 2 nhé em: nộp file PDF trên cổng môn học và nộp 1 cuốn in bìa mềm tại VPK.', 1002, NULL, 1, 'PUBLIC', 1, 1, 8, 0, 0, 0, 62, NOW() - INTERVAL 18 HOUR, NOW()),
(2008, 103, 'Cảm ơn Thầy đã nhắc nhở, nhóm em đang gấp rút hoàn thiện chương 3.', 1002, NULL, 1, 'PUBLIC', 1, 1, 1, 0, 0, 0, 25, NOW() - INTERVAL 17 HOUR, NOW()),
(2009, 107, 'Cố lên các anh chị K21!', 1002, NULL, 1, 'PUBLIC', 1, 1, 2, 0, 0, 0, 18, NOW() - INTERVAL 16 HOUR, NOW()),

-- Replies cho Post 1003 (Tuyển nhóm đồ án)
(2010, 103, 'Mình thạo Flutter với REST API đây bạn ơi, cho mình xin 1 slot vào nhóm với nhé!', 1003, NULL, 1, 'PUBLIC', 1, 1, 4, 0, 0, 0, 29, NOW() - INTERVAL 15 HOUR, NOW()),
(2011, 102, '@minhquang Tuyệt vời, bạn check inbox mình vừa gửi chi tiết đề tài nhé!', 1003, NULL, 1, 'PUBLIC', 1, 1, 2, 0, 0, 0, 24, NOW() - INTERVAL 14 HOUR, NOW()),
(2012, 104, 'Chúc nhóm hai bạn hoàn thành xuất sắc đồ án nhé!', 1003, NULL, 1, 'PUBLIC', 1, 1, 1, 0, 0, 0, 19, NOW() - INTERVAL 13 HOUR, NOW()),
(2013, 107, 'Nhóm còn tuyển thêm backend Spring Boot không anh Khôi ơi?', 1003, NULL, 1, 'PUBLIC', 1, 1, 0, 0, 0, 0, 14, NOW() - INTERVAL 12 HOUR, NOW()),

-- Replies cho Post 1004 (Tài liệu DSA)
(2014, 104, 'Cho mình xin link Google Drive với ạ, cảm ơn bạn nhiều lắm!', 1004, NULL, 1, 'PUBLIC', 1, 1, 5, 0, 0, 0, 40, NOW() - INTERVAL 11 HOUR, NOW()),
(2015, 102, 'Tài liệu xịn xò quá bạn ơi, đúng phần cây nhị phân cân bằng AVL mình đang cần ôn!', 1004, NULL, 1, 'PUBLIC', 1, 1, 4, 0, 0, 0, 36, NOW() - INTERVAL 10 HOUR, NOW()),
(2016, 103, 'Mình đã gửi link cho tất cả các bạn comment rồi nhé, chúc mọi người qua môn điểm A!', 1004, NULL, 1, 'PUBLIC', 1, 1, 7, 0, 0, 0, 52, NOW() - INTERVAL 9 HOUR, NOW()),
(2017, 106, 'Tài liệu biên soạn rất chi tiết và dễ hiểu, cảm ơn em đã chia sẻ cho các khóa sau.', 1004, NULL, 1, 'PUBLIC', 1, 1, 6, 0, 0, 0, 48, NOW() - INTERVAL 8 HOUR, NOW()),

-- Replies cho Post 1005 (Cơm tấm)
(2018, 103, 'Quán mở lại rồi nha bạn, bán từ 18h tới 2h sáng lận, sườn bì chả vẫn ngon đỉnh 🤤', 1005, NULL, 2, 'PUBLIC', 1, 1, 5, 0, 0, 0, 35, NOW() - INTERVAL 9 HOUR, NOW()),
(2019, 104, 'Cảm ơn bạn nhé, mình vừa ra ăn về, no nê có sức cày tiếp deadline haha.', 1005, NULL, 2, 'PUBLIC', 1, 1, 2, 0, 0, 0, 28, NOW() - INTERVAL 8 HOUR, NOW()),
(2020, 105, 'Quán này có canh chua miễn phí ngon xuất sắc nè!', 1005, NULL, 2, 'PUBLIC', 1, 1, 3, 0, 0, 0, 20, NOW() - INTERVAL 7 HOUR, NOW()),

-- Replies cho Post 1007 (2FA Mobile)
(2021, 101, 'Bảo mật 2FA hoạt động rất chuẩn, có cơ chế mã dự phòng khẩn cấp chống mất điện thoại rất an tâm.', 1007, NULL, 3, 'PUBLIC', 1, 1, 4, 0, 0, 0, 35, NOW() - INTERVAL 5 HOUR, NOW()),
(2022, 104, 'Mình vừa quét QR bằng Google Authenticator phát kích hoạt ngay, mượt mà lắm!', 1007, NULL, 3, 'PUBLIC', 1, 1, 3, 0, 0, 0, 29, NOW() - INTERVAL 4 HOUR, NOW()),
(2023, 106, 'Chuẩn kiến trúc Spring Security + TOTP RFC 6238, làm bài bản ghê Khôi.', 1007, NULL, 3, 'PUBLIC', 1, 1, 5, 0, 0, 0, 31, NOW() - INTERVAL 3 HOUR, NOW()),

-- Replies cho Post 1008 (CLB Guitar)
(2024, 104, '@thuytien @minhquang Thứ 6 tan học qua sảnh A nghe nhạc nhé mọi người ơi!', 1008, NULL, 5, 'PUBLIC', 1, 1, 4, 0, 0, 0, 40, NOW() - INTERVAL 4 HOUR, NOW()),
(2025, 103, 'Đã lên lịch, nhất định sẽ có mặt cổ vũ anh em CLB Guitar 🎸', 1008, NULL, 5, 'PUBLIC', 1, 1, 3, 0, 0, 0, 35, NOW() - INTERVAL 3 HOUR, NOW()),

-- Replies cho Post 1010 (Tìm thẻ SV)
(2026, 102, 'Mình có thấy chị lao công gửi thẻ SV này tại bàn bảo vệ cổng 1 đó bạn, bạn ghé nhận lại nha!', 1010, NULL, 2, 'PUBLIC', 1, 1, 7, 0, 0, 0, 55, NOW() - INTERVAL 2 HOUR, NOW()),
(2027, 108, 'Ôi may quá, mình vừa qua cổng 1 xin lại được thẻ rồi. Cảm ơn bạn Khôi và chú bảo vệ nhiều lắm ạ ❤️', 1010, NULL, 2, 'PUBLIC', 1, 1, 5, 0, 0, 0, 48, NOW() - INTERVAL 1 HOUR, NOW())
ON DUPLICATE KEY UPDATE content = VALUES(content);

-- ------------------------------------------------------------------------------
-- 13. THẢ TIM BÀI VIẾT & BÌNH LUẬN (POST_LIKES)
-- ------------------------------------------------------------------------------
INSERT INTO post_likes (user_id, post_id, created_at)
VALUES 
(101, 1002, NOW()), (101, 1003, NOW()), (101, 1004, NOW()), (101, 1006, NOW()), (101, 1007, NOW()), (101, 1008, NOW()),
(102, 1001, NOW()), (102, 1002, NOW()), (102, 1004, NOW()), (102, 1006, NOW()), (102, 1008, NOW()), (102, 1010, NOW()),
(103, 1001, NOW()), (103, 1002, NOW()), (103, 1003, NOW()), (103, 1004, NOW()), (103, 1007, NOW()), (103, 1008, NOW()),
(104, 1001, NOW()), (104, 1003, NOW()), (104, 1004, NOW()), (104, 1005, NOW()), (104, 1007, NOW()), (104, 1008, NOW()), (104, 1010, NOW()),
(105, 1001, NOW()), (105, 1004, NOW()), (105, 1008, NOW()), (105, 1009, NOW()),
(106, 1001, NOW()), (106, 1002, NOW()), (106, 1004, NOW()), (106, 1006, NOW()), (106, 1007, NOW()),
(107, 1001, NOW()), (107, 1002, NOW()), (107, 1003, NOW()), (107, 1004, NOW()), (107, 1006, NOW()), (107, 1007, NOW()),
(108, 1001, NOW()), (108, 1004, NOW()), (108, 1005, NOW()), (108, 1008, NOW()), (108, 1010, NOW()),
(109, 1001, NOW()), (109, 1003, NOW()), (109, 1006, NOW()),
(110, 1001, NOW()), (110, 1004, NOW()), (110, 1008, NOW()),
-- Likes cho bình luận
(101, 2001, NOW()), (101, 2007, NOW()), (102, 2007, NOW()), (103, 2011, NOW()), (104, 2018, NOW()), (108, 2026, NOW())
ON DUPLICATE KEY UPDATE created_at = VALUES(created_at);

-- ------------------------------------------------------------------------------
-- 14. LƯU BÀI VIẾT (BOOKMARKS)
-- ------------------------------------------------------------------------------
INSERT INTO bookmarks (user_id, post_id, created_at)
VALUES 
(101, 1002, NOW()), (101, 1006, NOW()),
(102, 1004, NOW()), (102, 1006, NOW()), (102, 1008, NOW()),
(103, 1004, NOW()), (103, 1008, NOW()),
(104, 1004, NOW()), (104, 1008, NOW()), (104, 1005, NOW()),
(107, 1004, NOW()), (107, 1006, NOW()),
(108, 1008, NOW()), (108, 1010, NOW())
ON DUPLICATE KEY UPDATE created_at = VALUES(created_at);

-- ------------------------------------------------------------------------------
-- 15. CHIA SẺ BÀI VIẾT (REPOSTS)
-- ------------------------------------------------------------------------------
INSERT INTO reposts (user_id, post_id, created_at)
VALUES 
(102, 1002, NOW()), (102, 1004, NOW()), (102, 1008, NOW()),
(103, 1004, NOW()), (103, 1008, NOW()),
(104, 1001, NOW()), (104, 1008, NOW()), (104, 1010, NOW()),
(107, 1006, NOW()), (108, 1008, NOW()), (108, 1010, NOW())
ON DUPLICATE KEY UPDATE created_at = VALUES(created_at);

-- ------------------------------------------------------------------------------
-- 16. THEO DÕI NHAU (FOLLOWS)
-- ------------------------------------------------------------------------------
INSERT INTO follows (follower_id, following_id)
VALUES 
(101, 102), (101, 104), (101, 109), (101, 110),
(102, 101), (102, 103), (102, 104), (102, 107), (102, 109), (102, 110),
(103, 101), (103, 102), (103, 104), (103, 110),
(104, 101), (104, 102), (104, 103), (104, 108), (104, 110),
(105, 101), (105, 102), (105, 103),
(106, 101), (106, 102), (106, 107), (106, 109),
(107, 101), (107, 102), (107, 103), (107, 106), (107, 109),
(108, 101), (108, 104), (108, 110),
(109, 101), (109, 102), (109, 106),
(110, 101), (110, 103), (110, 104)
ON DUPLICATE KEY UPDATE follower_id = VALUES(follower_id);

-- ------------------------------------------------------------------------------
-- 17. ĐOẠN CHAT & THÀNH VIÊN (CONVERSATIONS & CONVERSATION_MEMBERS)
-- ------------------------------------------------------------------------------
INSERT INTO conversations (id, type, name, avatar_url, created_at, updated_at)
VALUES 
(1, 'DIRECT', NULL, NULL, NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 1 HOUR),
(2, 'DIRECT', NULL, NULL, NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 2 HOUR),
(3, 'GROUP', 'Nhóm Đồ Án Kỹ Thuật Phần Mềm', 'https://images.unsplash.com/photo-1522071820081-009f0129c71c?auto=format&fit=crop&w=256&q=80', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 30 MINUTE),
(4, 'GROUP', 'Ban Tổ Chức Đêm Nhạc UTE', 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=256&q=80', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 15 MINUTE)
ON DUPLICATE KEY UPDATE type = VALUES(type);

INSERT INTO conversation_members (id, conversation_id, user_id, joined_at, last_read_message_id, last_read_at)
VALUES 
-- Chat Direct giữa 102 (Khôi) và 103 (Quang)
(1, 1, 102, NOW() - INTERVAL 10 DAY, 3, NOW()),
(2, 1, 103, NOW() - INTERVAL 10 DAY, 3, NOW()),
-- Chat Direct giữa 101 (Admin) và 102 (Khôi)
(3, 2, 101, NOW() - INTERVAL 8 DAY, 5, NOW()),
(4, 2, 102, NOW() - INTERVAL 8 DAY, 5, NOW()),
-- Nhóm Đồ Án: 102, 103, 107
(5, 3, 102, NOW() - INTERVAL 5 DAY, 8, NOW()),
(6, 3, 103, NOW() - INTERVAL 5 DAY, 8, NOW()),
(7, 3, 107, NOW() - INTERVAL 5 DAY, 8, NOW()),
-- Nhóm Đêm Nhạc: 104, 108, 110
(8, 4, 104, NOW() - INTERVAL 3 DAY, 11, NOW()),
(9, 4, 108, NOW() - INTERVAL 3 DAY, 11, NOW()),
(10, 4, 110, NOW() - INTERVAL 3 DAY, 11, NOW())
ON DUPLICATE KEY UPDATE joined_at = VALUES(joined_at);

-- ------------------------------------------------------------------------------
-- 18. TIN NHẮN CHAT MẪU (MESSAGES)
-- ------------------------------------------------------------------------------
INSERT INTO messages (id, conversation_id, sender_id, reply_to_message_id, content, created_at)
VALUES 
-- Chat Khôi & Quang
(1, 1, 102, NULL, 'Chào Quang, tối nay bạn rảnh tầm 20h không anh em mình call trao đổi về đồ án nhé?', NOW() - INTERVAL 3 HOUR),
(2, 1, 103, 1, 'Oke Khôi ơi, 20h mình online Google Meet nhé, mình đã đọc xong phần đặc tả yêu cầu rồi.', NOW() - INTERVAL 2 HOUR),
(3, 1, 102, NULL, 'Tuyệt vời, tí gặp nhau nha!', NOW() - INTERVAL 1 HOUR),

-- Chat Admin & Khôi
(4, 2, 101, NULL, 'Chào bạn Khôi, hệ thống ghi nhận phiên bản AloUTE Mobile chạy rất ổn định trên thiết bị Android.', NOW() - INTERVAL 5 HOUR),
(5, 2, 102, 4, 'Dạ cảm ơn Admin ạ, nhóm em đang tiếp tục tối ưu hóa phần tải bình luận tức thì!', NOW() - INTERVAL 2 HOUR),

-- Chat Nhóm Đồ Án
(6, 3, 102, NULL, 'Chào cả nhóm, mình vừa cập nhật tài liệu API và sơ đồ CSDL trên Google Drive rồi nhé.', NOW() - INTERVAL 4 HOUR),
(7, 3, 103, 6, 'Mình đã nhận được, phần giao diện Flutter mình đang kết nối với endpoint /posts.', NOW() - INTERVAL 2 HOUR),
(8, 3, 107, NULL, 'Phần mô hình AI phân loại bài viết mình cũng train xong rồi, độ chính xác đạt 94.5% nhé cả nhà 🚀', NOW() - INTERVAL 30 MINUTE),

-- Chat Nhóm Đêm Nhạc
(9, 4, 110, NULL, 'Ban truyền thông đã chuẩn bị xong poster cho đêm nhạc thứ 6 chưa ạ?', NOW() - INTERVAL 2 HOUR),
(10, 4, 108, 9, 'Poster và banner đăng bài mình vừa hoàn thiện xong, gửi mọi người duyệt nha!', NOW() - INTERVAL 1 HOUR),
(11, 4, 104, NULL, 'Poster quá đẹp luôn Phương Anh ơi, mình sẽ lên bài trên cộng đồng AloUTE ngay bây giờ!', NOW() - INTERVAL 15 MINUTE)
ON DUPLICATE KEY UPDATE content = VALUES(content);

-- ------------------------------------------------------------------------------
-- 19. THÔNG BÁO MẪU (NOTIFICATIONS)
-- ------------------------------------------------------------------------------
INSERT INTO notifications (id, recipient_id, actor_id, type, target_type, target_id, content_preview, is_read, created_at)
VALUES 
(1, 101, 102, 'POST_LIKE', 'POST', 1001, 'Khôi Nguyễn đã thích bài viết của bạn', 1, NOW() - INTERVAL 20 HOUR),
(2, 101, 104, 'POST_REPLY', 'POST', 1001, 'Thủy Tiên đã bình luận: "Hy vọng sắp tới có thêm nhiều tính năng hay..."', 1, NOW() - INTERVAL 18 HOUR),
(3, 102, 103, 'POST_REPLY', 'POST', 1003, 'Minh Quang đã bình luận: "Mình thạo Flutter với REST API đây bạn ơi..."', 1, NOW() - INTERVAL 14 HOUR),
(4, 102, 101, 'FOLLOW', 'USER', 101, 'Quản Trị Viên AloUTE đã bắt đầu theo dõi bạn', 1, NOW() - INTERVAL 12 HOUR),
(5, 103, 104, 'POST_LIKE', 'POST', 1004, 'Thủy Tiên đã thích bài viết của bạn', 1, NOW() - INTERVAL 10 HOUR),
(6, 103, 102, 'POST_REPLY', 'POST', 1004, 'Khôi Nguyễn đã bình luận: "Tài liệu xịn xò quá bạn ơi..."', 0, NOW() - INTERVAL 8 HOUR),
(7, 104, 103, 'POST_REPLY', 'POST', 1005, 'Minh Quang đã trả lời câu hỏi của bạn', 0, NOW() - INTERVAL 6 HOUR),
(8, 108, 102, 'POST_REPLY', 'POST', 1010, 'Khôi Nguyễn đã tìm thấy thẻ sinh viên của bạn tại cổng 1', 0, NOW() - INTERVAL 2 HOUR),
(9, 102, 106, 'POST_LIKE', 'POST', 1007, 'Bảo Ngọc đã thích bài viết về 2FA của bạn', 0, NOW() - INTERVAL 1 HOUR)
ON DUPLICATE KEY UPDATE type = VALUES(type);

-- ------------------------------------------------------------------------------
-- 20. BÁO CÁO VI PHẠM MẪU DÀNH CHO ADMIN DASHBOARD (REPORTS)
-- ------------------------------------------------------------------------------
INSERT INTO reports (id, reporter_id, reported_user_id, reported_post_id, reason, description, status, reviewed_by_id, reviewed_at, closed_at, resolution_note, version, created_at)
VALUES 
(1, 104, 105, NULL, 'SPAM', 'Tài khoản đăng nhiều bài quảng cáo link mua bán xe không rõ nguồn gốc trong cộng đồng.', 'RESOLVED', 101, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY, 'Đã nhắc nhở người dùng và gỡ bài viết vi phạm quảng cáo.', 1, NOW() - INTERVAL 3 DAY),
(2, 103, 107, NULL, 'OTHER', 'Nghi vấn tài khoản đăng bài tuyển dụng có dấu hiệu thu phí sinh viên trái quy định.', 'PENDING', NULL, NULL, NULL, NULL, 0, NOW() - INTERVAL 12 HOUR),
(3, 108, NULL, 1005, 'SPAM', 'Nội dung bình luận chào mời dịch vụ chạy điểm thi vi phạm chính sách của trường.', 'PENDING', NULL, NULL, NULL, NULL, 0, NOW() - INTERVAL 4 HOUR),
(4, 102, 108, NULL, 'MISINFORMATION', 'Thông báo giả mạo thông tin dời lịch thi của phòng Đào tạo.', 'DISMISSED', 101, NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 2 HOUR, 'Đã xác minh thông tin là thông báo nội bộ hợp lệ của khoa.', 1, NOW() - INTERVAL 6 HOUR)
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- ------------------------------------------------------------------------------
-- 21. NHẬT KÝ KIỂM DUYỆT (MODERATION_ACTIONS)
-- ------------------------------------------------------------------------------
INSERT INTO moderation_actions (id, actor_id, target_type, target_id, action, reason, note, created_at)
VALUES 
(1, 101, 'USER', 105, 'WARN', 'SPAM', 'Nhắc nhở lần 1 về việc đăng link thương mại không phù hợp', NOW() - INTERVAL 1 DAY),
(2, 101, 'POST', 1005, 'DISMISS_REPORT', 'MISINFORMATION', 'Báo cáo sai, bài viết trao đổi quán ăn bình thường', NOW() - INTERVAL 2 HOUR)
ON DUPLICATE KEY UPDATE action = VALUES(action);

SET FOREIGN_KEY_CHECKS = 1;

-- ==============================================================================
-- HOÀN TẤT SEED FULL DỮ LIỆU TOÀN DIỆN CHO ALOUTE!
-- ==============================================================================
