package com.aloute.config;

import com.aloute.entity.ReportReason;
import com.aloute.entity.ReportStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Sample content for the local dev H2 database. Kept separate from the seeder
 * logic so the data can be reviewed and extended without touching persistence.
 */
final class DevSeedContent {

    private DevSeedContent() {
    }

    record DemoUser(String username, String displayName, String bio, String avatarUrl, String coverUrl,
                    boolean verified, boolean privateAccount) {
    }

    record DemoCommunity(String slug, String name, String description, String avatarUrl, String coverUrl,
                         boolean privateCommunity, String ownerUsername, String[] members) {
    }

    record DemoPost(String authorUsername, String communitySlug, String content) {
    }

    record DemoQuote(int quotedIndex, String authorUsername, String content) {
    }

    record DemoReport(String reporterUsername, String reportedUsername, int reportedPostIndex,
                      ReportReason reason, String description, ReportStatus status, String resolutionNote) {
    }

    private static String avatar(String key) {
        return "https://i.pravatar.cc/160?u=aloute-" + key;
    }

    private static String cover(String key) {
        return "https://picsum.photos/seed/aloute-cover-" + key + "/1200/400";
    }

    private static DemoUser user(String username, String displayName, String bio,
                                 boolean verified, boolean privateAccount, boolean withCover) {
        return new DemoUser(username, displayName, bio, avatar(username),
                withCover ? cover(username) : null, verified, privateAccount);
    }

    private static DemoCommunity community(String slug, String name, String description,
                                           String ownerUsername, boolean privateCommunity, String... members) {
        return new DemoCommunity(slug, name, description, avatar(slug), cover("community-" + slug),
                privateCommunity, ownerUsername, members);
    }

    private static DemoPost p(String authorUsername, String communitySlug, String content) {
        return new DemoPost(authorUsername, communitySlug, content);
    }

    static final List<DemoUser> USERS = List.of(
            user("khoinguyen", "Nguyễn Đoàn Minh Khôi",
                    "Sinh viên K21 Khoa CNTT HCMUTE. Đam mê Flutter, Spring Boot và Cloud Run.",
                    false, false, true),
            user("quangminh", "Phạm Minh Quang",
                    "Kỹ thuật Phần mềm K21 | Bóng rổ, guitar và cà phê code đêm.",
                    false, false, false),
            user("thuytien", "Nguyễn Thủy Tiên",
                    "Khoa Kinh tế | Trưởng ban Truyền thông CLB Tiếng Anh UTE.",
                    false, false, true),
            user("hoangnam", "Trần Hoàng Nam",
                    "Khoa Cơ khí Động lực | Robocon và xe tự hành.",
                    false, false, false),
            user("baongoc", "Lê Bảo Ngọc",
                    "Khoa Điện - Điện tử | Trợ giảng môn Vi xử lý và IoT.",
                    false, false, false),
            user("thanhdat", "Vũ Thành Đạt",
                    "Kỹ thuật Dữ liệu K22 | Machine Learning, Kaggle và Prompt Engineering.",
                    false, false, false),
            user("phuonganh", "Đặng Phương Anh",
                    "Khoa May và Thời trang | Thiết kế UI/UX freelancer.",
                    false, false, true),
            user("tuananh", "TS. Nguyễn Tuấn Anh",
                    "Giảng viên Bộ môn Mạng và An toàn thông tin - Khoa CNTT HCMUTE.",
                    true, false, false),
            user("thayvan", "Th.S Nguyễn Trần Thi Văn",
                    "Giảng viên hướng dẫn đồ án Công nghệ Phần mềm.",
                    true, false, false),
            user("linhchi", "Nguyễn Linh Chi",
                    "Khoa Ngoại ngữ | IELTS 8.0, yêu thích dịch thuật và viết lách.",
                    false, false, false),
            user("ducthanh", "Bùi Đức Thạnh",
                    "Sinh viên năm 4 Khoa CNTT, backend Java và hệ thống phân tán.",
                    false, false, false),
            user("minhtri", "Đỗ Minh Trí",
                    "Yêu thích an toàn thông tin, CTF và reverse engineering.",
                    false, false, false),
            user("hongnhung", "Trần Hồng Nhung",
                    "Khoa Mỹ thuật Công nghiệp | Vẽ minh họa và thiết kế poster.",
                    false, false, true),
            user("vietthong", "Nguyễn Việt Thông",
                    "Khoa Cơ khí Chế tạo máy, đam mê SolidWorks và CNC.",
                    false, false, false),
            user("khanhlinh", "Đặng Khánh Linh",
                    "Tài khoản riêng tư - chỉ chia sẻ với bạn bè thân thiết.",
                    false, true, false),
            user("quocbao", "Nguyễn Quốc Bảo",
                    "Sinh viên K22, đam mê mobile và các sản phẩm tối giản.",
                    false, false, false),
            user("ngocmai", "Phan Ngọc Mai",
                    "Khoa Hóa học | Thích làm bánh và thử nghiệm công thức mới.",
                    false, false, false),
            user("tranthao", "Trần Thảo",
                    "Khoa Kinh tế | Marketing và thương hiệu sinh viên.",
                    false, false, false),
            user("huunhan", "Lâm Hữu Nhân",
                    "Cựu sinh viên K18, backend engineer. Hay chia sẻ kinh nghiệm nghề nghiệp.",
                    true, false, true),
            user("giabao", "Trương Gia Bảo",
                    "CLB Robotics UTE, sinh viên K22.",
                    false, false, false),
            user("yenlinh", "Hoàng Yến Linh",
                    "Sinh viên K23, thích đọc sách và chụp ảnh.",
                    false, false, false),
            user("tuankiet", "Ngô Tuấn Kiệt",
                    "Khoa CNTT | Game development và đồ họa máy tính.",
                    false, false, false),
            user("maiphuong", "Đinh Mai Phương",
                    "Khoa Du lịch | Content creator và thích kể chuyện.",
                    false, false, false),
            user("longvu", "Trịnh Long Vũ",
                    "Khoa Cơ khí | Yêu thể thao và các hoạt động ngoài trời.",
                    false, false, false),
            user("thienthan", "Nguyễn Thiên Thanh",
                    "CLB Âm nhạc UTE | Vocalist và pianist.",
                    false, false, true),
            user("clb_guitar", "CLB Guitar UTE",
                    "CLB Âm nhạc sinh viên HCMUTE, sinh hoạt acoustic mỗi thứ Bảy.",
                    true, false, true)
    );

    static final List<DemoCommunity> COMMUNITIES = List.of(
            community("fit-hcmute", "Khoa Công nghệ Thông tin HCMUTE",
                    "Kênh thông tin chính thức, tài liệu học tập, đồ án tốt nghiệp và cơ hội nghề nghiệp dành cho sinh viên Khoa CNTT.",
                    "tuananh", false,
                    "tuananh:ADMIN", "admin:MODERATOR", "khoinguyen:MEMBER", "quangminh:MEMBER",
                    "thanhdat:MEMBER", "minhtri:MEMBER", "ducthanh:MEMBER", "quocbao:MEMBER",
                    "tuankiet:MEMBER", "baongoc:MEMBER", "hongnhung:MEMBER", "thayvan:MEMBER"),
            community("sinh-vien-ute", "Cộng đồng Sinh viên UTE",
                    "Không gian mở dành cho sinh viên trường ĐH Sư phạm Kỹ thuật TP.HCM trao đổi học tập và đời sống.",
                    "admin", false,
                    "admin:ADMIN", "user:MODERATOR", "demoan:MEMBER", "demobinh:MEMBER",
                    "khoinguyen:MEMBER", "thuytien:MEMBER", "hoangnam:MEMBER", "baongoc:MEMBER",
                    "thanhdat:MEMBER", "phuonganh:MEMBER", "linhchi:MEMBER", "ngocmai:MEMBER",
                    "tranthao:MEMBER", "yenlinh:MEMBER", "maiphuong:MEMBER"),
            community("clb-lap-trinh-ai", "CLB Lập trình và AI HCMUTE",
                    "Cộng đồng đam mê lập trình, AI, dữ liệu và các dự án thực tế dành cho sinh viên.",
                    "khoinguyen", false,
                    "khoinguyen:ADMIN", "thanhdat:MODERATOR", "quangminh:MEMBER", "minhtri:MEMBER",
                    "ducthanh:MEMBER", "quocbao:MEMBER", "tuankiet:MEMBER", "baongoc:MEMBER",
                    "huunhan:MEMBER"),
            community("clb-tieng-anh-ute", "CLB Tiếng Anh UTE",
                    "Sân chơi học thuật và giao lưu tiếng Anh dành cho sinh viên toàn trường.",
                    "thuytien", false,
                    "thuytien:ADMIN", "linhchi:MODERATOR", "ngocmai:MEMBER", "tranthao:MEMBER",
                    "yenlinh:MEMBER", "phuonganh:MEMBER", "maiphuong:MEMBER", "demoan:MEMBER",
                    "demobinh:MEMBER"),
            community("hoc-bong-viec-lam", "Học bổng và Việc làm",
                    "Nơi tổng hợp học bổng, thực tập và cơ hội việc làm dành cho sinh viên HCMUTE.",
                    "huunhan", false,
                    "huunhan:ADMIN", "thayvan:MODERATOR", "admin:MEMBER", "user:MEMBER",
                    "khoinguyen:MEMBER", "thanhdat:MEMBER", "linhchi:MEMBER", "tranthao:MEMBER",
                    "quocbao:MEMBER", "maiphuong:MEMBER"),
            community("clb-am-nhac-ute", "CLB Âm nhạc UTE",
                    "Mái nhà chung của những sinh viên đam mê acoustic, guitar, piano và biểu diễn.",
                    "clb_guitar", false,
                    "clb_guitar:ADMIN", "thienthan:MODERATOR", "phuonganh:MEMBER", "thuytien:MEMBER",
                    "hoangnam:MEMBER", "longvu:MEMBER", "ngocmai:MEMBER", "demoan:MEMBER"),
            community("khoi-nghiep-sinh-vien", "Khởi nghiệp sinh viên UTE",
                    "Chia sẻ ý tưởng, cuộc thi và kinh nghiệm khởi nghiệp dành cho sinh viên.",
                    "thayvan", false,
                    "thayvan:ADMIN", "huunhan:MODERATOR", "admin:MEMBER", "thuytien:MEMBER",
                    "tranthao:MEMBER", "maiphuong:MEMBER", "quocbao:MEMBER", "thanhdat:MEMBER",
                    "khoinguyen:MEMBER"),
            community("nckh-ute", "Nhóm Nghiên cứu Khoa học trọng điểm",
                    "Cộng đồng riêng cho giảng viên và sinh viên tham gia đề tài nghiên cứu khoa học cấp trường.",
                    "tuananh", true,
                    "tuananh:ADMIN", "thayvan:MODERATOR", "admin:MEMBER", "khoinguyen:MEMBER",
                    "thanhdat:MEMBER", "baongoc:MEMBER")
    );

    static final List<DemoPost> POSTS = buildPosts();

    private static List<DemoPost> buildPosts() {
        List<DemoPost> posts = new ArrayList<>();
        posts.addAll(List.of(
                p("tuananh", "fit-hcmute", "Thông báo từ Khoa CNTT: Lịch nộp đề cương Đồ án Tốt nghiệp học kỳ này hạn chót là 17h00 thứ Sáu tuần sau. Các nhóm lưu ý nộp đúng quy cách và có chữ ký xác nhận của GVHD."),
                p("quocbao", "fit-hcmute", "Phòng máy A3 vừa được nâng cấp thêm 40 máy cấu hình mới, cài sẵn JDK 21, IntelliJ và MySQL 8. Anh em qua thực hành nhớ đăng nhập bằng tài khoản sinh viên nhé."),
                p("khoinguyen", "fit-hcmute", "Mình tổng hợp đề cương ôn thi môn Cấu trúc dữ liệu và giải thuật: mảng, danh sách liên kết, cây AVL, đồ thị và một số dạng quy hoạch động thường gặp. Bạn nào cần comment mình gửi link."),
                p("minhtri", "fit-hcmute", "Cho mình hỏi học kỳ này thầy Tuấn Anh có dạy lớp An toàn thông tin ca tối không ạ? Mình đang muốn đăng ký học cải thiện."),
                p("quangminh", "fit-hcmute", "Nhóm mình đang làm đồ án nhận diện khuôn mặt bằng OpenCV và YOLOv8, cần tuyển thêm một bạn biết React hoặc Flutter để làm giao diện demo. Deadline còn ba tuần, ai join được inbox mình nhé."),
                p("thayvan", "fit-hcmute", "Cập nhật lịch bảo vệ đồ án: các nhóm có tên trong danh sách sẽ bảo vệ vào sáng thứ Bảy tại phòng B2.05. Nhớ chuẩn bị slide 10 phút và demo trực tiếp."),
                p("ducthanh", "fit-hcmute", "Mình vừa hoàn thành bộ tài liệu Spring Boot 3 từ cơ bản đến bảo mật JWT, kèm source code mẫu. Share miễn phí cho anh em trong khoa, bạn nào cần gửi mail qua inbox nhé."),
                p("thanhdat", "fit-hcmute", "Ai có kinh nghiệm deploy project Java lên Cloud Run hoặc Railway cho mình hỏi cách cấu hình biến môi trường với ạ. Mình bị lỗi kết nối MySQL khi chạy production."),
                p("tuananh", "fit-hcmute", "Danh sách học bổng khuyến khích học tập học kỳ trước đã được dán tại bảng tin khoa. Sinh viên kiểm tra thông tin và phản hồi trước ngày 15 nếu có sai sót."),
                p("baongoc", "fit-hcmute", "Review nhanh môn Nhập môn Trí tuệ nhân tạo: giữa kỳ làm bài tập nhóm về tìm kiếm A*, cuối kỳ thi tự luận. Học kỹ phần mạng Bayes và học tăng cường là ổn."),
                p("tuankiet", "fit-hcmute", "Mình tìm bạn cùng học nhóm môn Lập trình hướng đối tượng vào tối thứ Ba và thứ Năm tại thư viện. Mục tiêu qua môn điểm A, ưu tiên bạn nghiêm túc và có laptop."),
                p("hongnhung", "fit-hcmute", "Sự kiện FIT Tech Day 2026 sẽ diễn ra vào cuối tháng này với các gian hàng tuyển dụng từ FPT, VNG và Bosch. Sinh viên năm 3, năm 4 nhớ mang CV nhé."),
                p("admin", "fit-hcmute", "Cảnh báo: hiện có một số tài khoản giả mạo thầy cô gửi link đóng học phí qua tin nhắn. Mọi giao dịch chính thức chỉ thực hiện trên cổng của trường."),
                p("quocbao", "fit-hcmute", "Mình đang tìm lại cuốn Giáo trình Cơ sở dữ liệu nâng cao để quên ở phòng B1.07 ca sáng thứ Hai. Bạn nào nhặt được cho mình xin lại, mình cảm ơn bằng trà sữa."),
                p("khoinguyen", "fit-hcmute", "Chia sẻ kinh nghiệm thi vấn đáp đồ án: nắm chắc kiến trúc tổng thể, biết vì sao chọn công nghệ đó và chuẩn bị phương án khi bị hỏi về điểm yếu hệ thống. Đừng học thuộc lòng slide."),
                p("thayvan", "fit-hcmute", "Khoa CNTT tuyển trợ giảng cho học kỳ tới, yêu cầu GPA từ 3.2 trở lên và đã hoàn thành môn học liên quan. Hồ sơ nộp về văn phòng khoa trước cuối tuần."),

                p("admin", "sinh-vien-ute", "Chào mừng tất cả các bạn sinh viên đã đến với AloUTE! Chúc mọi người một kỳ học mới nhiều năng lượng và thật nhiều trải nghiệm đáng nhớ."),
                p("hoangnam", "sinh-vien-ute", "Quán cơm tấm đối diện cổng số 2 dạo này mở lại chưa mọi người ơi? Đang chạy deadline mà đói bụng quá."),
                p("phuonganh", "sinh-vien-ute", "Góc tìm đồ thất lạc: mình để quên thẻ sinh viên tại bàn số 4 canteen khu B vào trưa nay. Bạn nào nhặt được cho mình xin lại nhé."),
                p("thanhdat", "sinh-vien-ute", "Có ai biết chỗ sửa laptop uy tín quanh trường không? Máy mình bị lỗi bàn phím, gõ cứ bị nhảy chữ."),
                p("thuytien", "sinh-vien-ute", "Sáng nay trường mình có chương trình hiến máu nhân đạo ở sảnh A, rất đông các bạn tham gia. Ai chưa đi thì tranh thủ qua trước 4 giờ chiều nhé."),
                p("baongoc", "sinh-vien-ute", "Ký túc xá vừa thông báo cắt nước vào tối nay để bảo trì, mọi người nhớ trữ nước trước nhé."),
                p("user", "sinh-vien-ute", "Mình muốn tìm phòng trọ gần trường giá dưới 2 triệu, ưu tiên có gác lửng và chỗ để xe. Bạn nào biết giới thiệu mình với."),
                p("ngocmai", "sinh-vien-ute", "Hôm nay ăn thử bún bò ở quán mới mở gần cổng 3, nước dùng ngon mà giá chỉ 30k. Rất đáng thử cho những ngày mưa."),
                p("hoangnam", "sinh-vien-ute", "Có bạn nào cùng tuyến xe buýt 19 từ Thủ Đức lên trường lúc 6h30 không? Mình muốn lập nhóm đi chung cho vui."),
                p("linhchi", "sinh-vien-ute", "Thư viện trường dạo này mở cửa đến 21h, có phòng tự học máy lạnh. Bạn nào ôn thi thì đây là địa điểm lý tưởng."),
                p("yenlinh", "sinh-vien-ute", "Chiều nay trời mưa lớn, nhiều bạn bị kẹt xe. Mọi người đi đường nhớ mang áo mưa và giữ an toàn nhé."),
                p("tranthao", "sinh-vien-ute", "Mình vừa đăng ký thẻ thư viện điện tử, giờ có thể mượn sách online và gia hạn qua app. Bạn nào chưa biết thì thao tác rất nhanh."),
                p("maiphuong", "sinh-vien-ute", "Sự kiện chào đón tân sinh viên K23 sẽ có mini game và nhiều quà tặng. Các bạn nhớ ghé gian hàng của CLB Truyền thông nhé."),
                p("phuonganh", "sinh-vien-ute", "Ai rành Photoshop cho mình hỏi cách làm ảnh thẻ nền xanh đúng chuẩn với. Mình chụp bằng điện thoại mà nền cứ bị lệch."),
                p("demoan", "sinh-vien-ute", "Trường mình vừa có thêm máy bán nước tự động ở khu C, thanh toán bằng ví điện tử rất tiện."),
                p("demobinh", "sinh-vien-ute", "Mình cần tìm một bạn ở ghép ký túc xá khu D, phòng 4 người sạch sẽ, giờ giấc thoải mái. Bạn nào quan tâm inbox nhé."),
                p("user", "sinh-vien-ute", "Cuối tuần này trường tổ chức ngày hội thể thao, có bóng đá, bóng rổ và kéo co. Ai muốn lập đội thì comment bên dưới."),
                p("khoinguyen", "sinh-vien-ute", "Mẹo nhỏ cho tân sinh viên: lưu lại lịch học, lịch thi và email giảng viên ngay từ tuần đầu. Đỡ phải hỏi lại mỗi khi cần."),

                p("khoinguyen", "clb-lap-trinh-ai", "CLB Lập trình và AI tổ chức buổi workshop Từ ý tưởng đến sản phẩm AI vào 18h thứ Năm tại phòng B2.05. Nội dung gồm cách chọn bài toán, thu thập dữ liệu và triển khai mô hình lên cloud."),
                p("thanhdat", "clb-lap-trinh-ai", "Mình vừa hoàn thành lộ trình tự học Machine Learning trong 6 tháng, tổng hợp lại thành checklist gồm Python, NumPy, Pandas, scikit-learn và PyTorch. Ai cần mình gửi link Notion nhé."),
                p("minhtri", "clb-lap-trinh-ai", "CLB tuyển thành viên ban kỹ thuật cho dự án chatbot tư vấn tuyển sinh. Yêu cầu biết Python hoặc Java, ưu tiên bạn có kinh nghiệm Spring Boot. Tham gia để được mentor trực tiếp."),
                p("quocbao", "clb-lap-trinh-ai", "Chia sẻ nhanh: vì sao nên dùng DTO thay vì trả Entity trực tiếp trong Spring Boot? Một ví dụ nhỏ cho thấy rò rỉ cấu trúc CSDL và dữ liệu nhạy cảm nếu làm sai."),
                p("ducthanh", "clb-lap-trinh-ai", "Mình đang làm side project về nhận diện biển số xe, dùng YOLOv8 để detect và VietOCR để đọc ký tự. Ai quan tâm có thể join cùng mình để học."),
                p("baongoc", "clb-lap-trinh-ai", "Anh em nào đã dùng Firebase FCM cho web push cho mình hỏi cách test thông báo khi tab đã đóng với ạ. Mình chạy localhost thì nhận được mà deploy thì không."),
                p("tuankiet", "clb-lap-trinh-ai", "Top 5 lỗi sinh viên CNTT hay gặp khi bắt đầu làm dự án thật: không tách cấu hình, không viết test, đặt logic trong controller, bỏ qua logging và không kiểm soát phiên bản CSDL. Bạn đã dính lỗi nào?"),
                p("huunhan", "clb-lap-trinh-ai", "CLB mở form đăng ký nhóm thi ICPC cấp trường. Đội 3 người, được phép dùng tài liệu. Hạn chót đăng ký là thứ Sáu tuần này."),
                p("khoinguyen", "clb-lap-trinh-ai", "Mình vừa đóng góp xong bộ tài liệu ôn tập SQL từ cơ bản đến nâng cao, gồm JOIN, window function và tối ưu index. Link nằm trong phần bình luận."),
                p("thanhdat", "clb-lap-trinh-ai", "Buổi sharing tuần này có chủ đề Bảo mật ứng dụng web sinh viên hay làm: SQL injection, XSS, CSRF và cách phòng tránh. Rất thiết thực cho các nhóm đồ án."),
                p("quangminh", "clb-lap-trinh-ai", "Mình đang tìm hai bạn cùng làm dự án nhỏ về gợi ý bài viết cho mạng xã hội. Công nghệ dự kiến Spring Boot, PostgreSQL và một model content-based đơn giản."),
                p("minhtri", "clb-lap-trinh-ai", "Nhắc nhở: bài tập lớn môn AI nộp trước 23h59 Chủ nhật. Các bạn nhớ nộp file PDF và mã nguồn đóng gói theo đúng tên nhóm."),

                p("thuytien", "clb-tieng-anh-ute", "CLB Tiếng Anh tổ chức buổi sinh hoạt chủ đề Job Interview Simulation vào tối thứ Sáu. Các bạn sẽ được thực hành phỏng vấn 1-1 với tình nguyện viên."),
                p("linhchi", "clb-tieng-anh-ute", "Mình để quên cuốn sách English Grammar in Use ở phòng học C1.05. Bạn nào thấy cho mình xin lại với ạ."),
                p("ngocmai", "clb-tieng-anh-ute", "Chia sẻ nguồn luyện nghe IELTS miễn phí: BBC 6 Minute English, TED-Ed và podcast Skills for IELTS. Mỗi ngày 20 phút là đủ duy trì phản xạ."),
                p("tranthao", "clb-tieng-anh-ute", "CLB tuyển thêm thành viên ban nội dung, ưu tiên các bạn có khả năng viết tiếng Anh tốt. Sinh hoạt mỗi thứ Năm lúc 17h30."),
                p("yenlinh", "clb-tieng-anh-ute", "Có bạn nào muốn lập nhóm nói tiếng Anh online mỗi tối 30 phút không? Mình đang muốn luyện nói đúng ngữ pháp trước khi thi."),
                p("maiphuong", "clb-tieng-anh-ute", "Workshop Academic Writing for Final Report sẽ hướng dẫn cấu trúc chương, cách trích dẫn APA và tránh đạo văn. Đặc biệt hữu ích cho các bạn làm đồ án."),
                p("linhchi", "clb-tieng-anh-ute", "Mình vừa thi thử IELTS trên máy, được 6.5 overall. Chia sẻ một ít kinh nghiệm là nên luyện listening theo dạng bài trước, đừng chỉ nghe thụ động."),
                p("thuytien", "clb-tieng-anh-ute", "CLB tổ chức cuộc thi hùng biện tiếng Anh dành cho sinh viên toàn trường. Giải nhất là học bổng khóa học 2 triệu đồng. Hạn đăng ký tuần sau."),

                p("huunhan", "hoc-bong-viec-lam", "Học bổng thực tập trước tốt nghiệp của một công ty công nghệ đang nhận hồ sơ, mức hỗ trợ 5 triệu đồng mỗi tháng và có cơ hội chuyển chính thức. Yêu cầu GPA 3.0 trở lên."),
                p("huunhan", "hoc-bong-viec-lam", "Tổng hợp 10 vị trí thực tập Java backend đang mở cho sinh viên năm 3, năm 4: có cả công ty product và outsourcing. Link trong bình luận, mọi người nhớ đọc kỹ yêu cầu."),
                p("thayvan", "hoc-bong-viec-lam", "Chia sẻ kinh nghiệm viết CV cho sinh viên chưa có nhiều kinh nghiệm: đặt tên dự án học thuật rõ ràng, mô tả công nghệ bạn dùng và kết quả đạt được bằng số liệu."),
                p("admin", "hoc-bong-viec-lam", "Công ty mình đang tuyển Fresher QA, yêu cầu biết viết test case và SQL cơ bản. Bạn nào muốn apply thì inbox mình gửi link, không qua trung gian."),
                p("khoinguyen", "hoc-bong-viec-lam", "Học bổng khuyến khích học tập dành cho sinh viên có hoàn cảnh khó khăn đã mở đơn. Hồ sơ gồm bảng điểm, giấy xác nhận hộ nghèo và bài luận ngắn."),
                p("thanhdat", "hoc-bong-viec-lam", "Mình vừa nhận offer thực tập backend sau 3 vòng phỏng vấn. Bí quyết của mình là làm một project nhỏ và biết giải thích từng quyết định thiết kế. Hỏi gì mình trả lời thêm."),
                p("linhchi", "hoc-bong-viec-lam", "Danh sách công ty đã xác nhận tham gia ngày hội việc làm: FPT Software, VNG, Bosch, Shopee và một số startup. Sinh viên nên in 5 bản CV."),
                p("tranthao", "hoc-bong-viec-lam", "Ai đang tìm hiểu chứng chỉ AWS Cloud Practitioner cho mình hỏi lộ trình ôn trong 3 tuần với. Mình có nền Java cơ bản."),
                p("quocbao", "hoc-bong-viec-lam", "Lưu ý khi thực tập: hỏi rõ phạm vi công việc, người hướng dẫn và chính sách làm thêm ngoài giờ trước khi ký. Đừng chỉ quan tâm mức lương."),
                p("maiphuong", "hoc-bong-viec-lam", "Học bổng trao đổi sinh viên châu Á đã mở đơn cho kỳ tới. Yêu cầu IELTS 6.0, GPA 3.0 và một thư giới thiệu từ giảng viên."),

                p("clb_guitar", "clb-am-nhac-ute", "Đêm nhạc Acoustic Thanh Xuân Tươi Đẹp sẽ diễn ra 18h30 thứ Sáu tại sảnh A. Vào cổng tự do, có trà sữa và bánh ngọt cho 100 bạn đến sớm nhất."),
                p("clb_guitar", "clb-am-nhac-ute", "CLB Âm nhạc đang tuyển vocalist cho ban nhạc sinh viên. Buổi thử giọng diễn ra chiều Chủ nhật tại phòng sinh hoạt CLB. Ai đam mê hát thì đừng bỏ lỡ."),
                p("thienthan", "clb-am-nhac-ute", "Mình vừa cover lại bài Nơi này có anh bằng guitar acoustic, mọi người nghe thử và góp ý cho mình nhé. Mong được chọn biểu diễn ở đêm nhạc sắp tới."),
                p("phuonganh", "clb-am-nhac-ute", "Ban tổ chức đêm nhạc cần thêm hai bạn hỗ trợ âm thanh và ánh sáng. Sinh viên nào rảnh tối thứ Sáu tuần này thì inbox mình."),
                p("thienthan", "clb-am-nhac-ute", "Mẹo tập guitar cho người mới: dành 15 phút mỗi ngày cho chuyển hợp âm cơ bản, đừng cố học bài khó ngay từ đầu. Kiên trì một tháng là thấy khác liền."),
                p("clb_guitar", "clb-am-nhac-ute", "Danh sách bài hát biểu diễn đêm nhạc đã chốt, có 12 tiết mục gồm acoustic, piano và một tiết mục beatbox. Cả nhà nhớ đến cổ vũ nhé."),

                p("thayvan", "khoi-nghiep-sinh-vien", "Cuộc thi khởi nghiệp sinh viên UTE 2026 đã chính thức mở đăng ký. Nhóm 3-5 người, nộp một trang ý tưởng và video pitch 2 phút trước cuối tháng."),
                p("huunhan", "khoi-nghiep-sinh-vien", "Mình đang ấp ủ dự án ứng dụng tìm trọ cho sinh viên, muốn tìm bạn đồng hành mảng marketing và vận hành. Ai quan tâm inbox mình trao đổi thêm nhé."),
                p("thuytien", "khoi-nghiep-sinh-vien", "Chia sẻ từ một bạn đã gọi vốn thành công: nhà đầu tư không quan tâm ý tưởng hay đến đâu nếu bạn chưa chứng minh được người dùng thật và chi phí vận hành."),
                p("tranthao", "khoi-nghiep-sinh-vien", "Buổi mentoring khởi nghiệp tháng này có anh cựu sinh viên K15 đang làm product manager. Chủ đề là Từ đồ án tốt nghiệp đến sản phẩm có người dùng."),
                p("maiphuong", "khoi-nghiep-sinh-vien", "Mình cần tìm hai bạn cùng làm dự án bán đồ ăn healthy trong trường. Đã có bếp và nguồn nguyên liệu, cần người làm content và giao hàng."),
                p("quocbao", "khoi-nghiep-sinh-vien", "Sai lầm lớn nhất của nhóm mình ở cuộc thi trước là làm quá nhiều tính năng mà không có người dùng thử. Lần này tụi mình chỉ tập trung vào một vấn đề duy nhất."),

                p("tuananh", "nckh-ute", "Nhóm nghiên cứu họp vào 14h thứ Tư tại phòng B2.10 để chốt hướng đề tài và phân công đọc paper. Các thành viên nhớ chuẩn bị tóm tắt hai bài liên quan."),
                p("khoinguyen", "nckh-ute", "Mình vừa cập nhật bộ dữ liệu thử nghiệm lên thư mục chung, gồm 12.000 mẫu đã gán nhãn. Mọi người kiểm tra lại phần tiền xử lý giúp mình nhé."),
                p("thanhdat", "nckh-ute", "Kết quả thử nghiệm mô hình baseline đạt F1 0.82, cao hơn kỳ vọng ban đầu. Tuần này mình sẽ thử thêm ablation study để so sánh các thành phần."),
                p("tuananh", "nckh-ute", "Thầy hướng dẫn nhắc nhóm nộp báo cáo tiến độ trước thứ Sáu. Phần của ai nấy hoàn thiện, nhớ ghi rõ số liệu và nguồn tham khảo."),

                p("user", "", "Hôm nay hoàn thành xong đồ án, cảm giác nhẹ cả người. Cảm ơn nhóm và thầy hướng dẫn đã đồng hành suốt ba tháng qua."),
                p("khoinguyen", "", "Một buổi chiều ở thư viện, cà phê và deadline. Sinh viên năm cuối có khác gì chạy marathon đâu."),
                p("yenlinh", "", "Mình vừa đọc xong cuốn Atomic Habits, thấy ý tưởng hệ thống thói quen nhỏ hiệu quả hơn động lực nhất thời. Ai đang muốn xây thói quen học tập thì nên thử."),
                p("maiphuong", "", "Cuối tuần này mình đi Vũng Tàu với nhóm bạn đại học. Sau bao ngày cày deadline mới thấy được thở."),
                p("huunhan", "", "Điểm mạnh lớn nhất của sinh viên mới ra trường không phải kiến thức mà là khả năng học nhanh và chịu học. Đừng quá lo nếu mình chưa giỏi."),
                p("ngocmai", "", "Mình vừa thử làm bánh mì chảo lần đầu, thành quả khá ổn. Bữa sáng tự làm vừa rẻ vừa vui."),
                p("ducthanh", "", "Một điều mình ước biết sớm hơn: học cách đọc tài liệu kỹ thuật hiệu quả trước khi lao vào code. Đọc hiểu vấn đề tiết kiệm rất nhiều thời gian."),
                p("longvu", "", "Hôm nay chạy bộ 5km quanh sân vận động, thấy tinh thần tỉnh táo hẳn. Mọi người nên thử vận động nhẹ giữa giờ học."),
                p("demoan", "", "Có ai đang xem lại bộ phim 3 Idiots không? Xem lại vẫn thấy câu All is well đúng trong mọi giai đoạn."),
                p("yenlinh", "", "Mình mới bắt đầu học tiếng Nhật, thấy bảng chữ cái Hiragana dễ thương ghê. Bạn nào có tài liệu hay giới thiệu mình với."),
                p("thienthan", "", "Đêm nay Sài Gòn mưa to, ngồi nghe nhạc và ôn bài. Một chút bình yên giữa tuần thi cử."),
                p("quocbao", "", "Mình vừa đổi giao diện điện thoại sang tông tối và thấy dễ chịu hơn khi dùng buổi tối. Chi tiết nhỏ nhưng thay đổi trải nghiệm."),
                p("tranthao", "", "Có ai từng chạy deadline mà quên ăn chưa? Nhắc mọi người đặt báo thức uống nước và ăn đúng bữa, sức khỏe quan trọng hơn điểm số."),
                p("hongnhung", "", "Chụp được tấm ảnh hoàng hôn ở sân trường đẹp quá, share cho mọi người cùng ngắm."),
                p("phuonganh", "", "Mình vừa hoàn thành khóa học online về UI/UX, phần được nhất là bài tập redesign một ứng dụng quen thuộc. Học đi đôi với làm mới nhớ lâu."),
                p("khanhlinh", "", "Mỗi lần mở portfolio của mình ra sửa lại thấy bản cũ vẫn chưa ưng. Có ai giống mình không?"),
                p("demobinh", "", "Sáng nay bắt được chuyến xe buýt cuối cùng, cảm giác như trúng số. Những điều nhỏ bé làm nên ngày vui."),
                p("thuytien", "", "Mình đang tìm một nhóm học tiếng Anh giao tiếp tối Chủ nhật hằng tuần, offline ở quán cà phê gần trường. Ai muốn tham gia thì comment nhé."),
                p("admin", "", "Điều mình thích ở trường mình là sân trường nhiều cây xanh, ngồi đọc sách dưới tán cây rất chill."),
                p("vietthong", "", "Kết thúc một tuần với ba bài kiểm tra và một buổi thuyết trình. Mệt nhưng vui vì đã cố gắng hết sức.")
        ));
        return List.copyOf(posts);
    }

    static final List<DemoQuote> QUOTES = List.of(
            new DemoQuote(0, "khoinguyen", "Bài thông báo của khoa rất quan trọng, mình share lại cho các bạn khóa dưới cùng nắm nhé."),
            new DemoQuote(35, "baongoc", "Đọc xong checklist Machine Learning của bạn mà thấy có động lực học lại từ đầu. Cảm ơn bạn đã chia sẻ!"),
            new DemoQuote(17, "ngocmai", "Quán này mình cũng ăn thử rồi, xác nhận ngon và rẻ. Sinh viên nên thử!"),
            new DemoQuote(18, "demoan", "Góc tìm đồ của bạn làm mình nhớ lần mất ví hồi năm nhất, may có chú bảo vệ giữ lại."),
            new DemoQuote(64, "thuytien", "Đêm nhạc này nhất định phải đi, mình đã rủ cả phòng ký túc xá."),
            new DemoQuote(14, "ducthanh", "Kinh nghiệm thi vấn đáp rất chuẩn, mình bổ sung thêm là nên chuẩn bị câu hỏi phản biện từ phía hội đồng."),
            new DemoQuote(34, "quocbao", "Workshop này đúng thứ mình đang cần, đã đăng ký hai slot cho mình và bạn cùng nhóm."),
            new DemoQuote(82, "thanhdat", "Cuốn Atomic Habits mình cũng đang đọc, phần thói quen nhỏ đúng là thay đổi cách mình học mỗi ngày."),
            new DemoQuote(71, "thuytien", "Ý tưởng ứng dụng tìm trọ rất thiết thực với sinh viên, chúc nhóm sớm tìm được cộng sự."),
            new DemoQuote(75, "tranthao", "Bài học về việc làm ít tính năng nhưng có người dùng thật đáng để các nhóm khởi nghiệp ghi nhớ.")
    );

    static final String[] REPLY_POOL = {
            "Cảm ơn bạn đã chia sẻ thông tin hữu ích!",
            "Cho mình xin link với ạ, mình đang cần đúng chủ đề này.",
            "Hay quá, mình lưu lại để đọc sau nhé.",
            "Mình cũng đang quan tâm, bạn check inbox giúp mình nhé.",
            "Thông tin rất bổ ích, cảm ơn bạn nhiều.",
            "Mình đã thử và xác nhận đúng như bạn nói.",
            "Bạn ơi còn nhận thêm người không ạ?",
            "Chúc nhóm sớm hoàn thành đúng hạn nhé!",
            "Đánh dấu lại, tối nay về đọc kỹ.",
            "Mình vừa gửi tin nhắn riêng cho bạn rồi nhé.",
            "Cảm ơn thầy cô đã thông báo ạ.",
            "Nhóm mình cũng đang gặp vấn đề tương tự, có thể trao đổi thêm không bạn?",
            "Thông tin này đến đúng lúc mình đang cần, cảm ơn bạn.",
            "Mình nghĩ nên bổ sung thêm phần kiểm thử trước khi nộp.",
            "Bạn có thể chia sẻ lại tài liệu cho mình với được không ạ?",
            "Mình đã áp dụng thử và thấy hiệu quả rõ rệt.",
            "Chỗ đó mình biết, để mình chỉ bạn nhé.",
            "Rất mong có thêm nhiều bài viết chất lượng như thế này.",
            "Mọi người nhớ kiểm tra kỹ trước khi nộp nhé, mình từng bị trừ điểm vì sai format.",
            "Mình đã lưu bài viết này rồi, cảm ơn bạn nhiều.",
            "Có ai cùng nhóm với mình không, mình cũng muốn tham gia.",
            "Bài viết rất chi tiết, đọc là hiểu ngay.",
            "Mình nghĩ bạn nên hỏi thêm cố vấn học tập cho chắc.",
            "Đã đăng ký, hẹn gặp mọi người ở buổi workshop.",
            "Mình vừa thử xong, kết quả khá ổn so với mong đợi.",
            "Cảm ơn bạn đã dành thời gian tổng hợp lại.",
            "Mình cần thêm thông tin về lịch nộp, bạn cập nhật giúp nhé.",
            "Chúc mừng bạn, thành quả xứng đáng sau nhiều ngày cố gắng.",
            "Mình cũng từng gặp lỗi này, cách xử lý là kiểm tra lại cấu hình môi trường.",
            "Hóng phần tiếp theo của bài viết!"
    };

    static final DemoReport[] REPORTS = {
            new DemoReport("thuytien", "hoangnam", 17, ReportReason.SPAM,
                    "Bài viết lặp lại nhiều lần nội dung quảng cáo quán ăn trong cộng đồng.", ReportStatus.RESOLVED,
                    "Đã nhắc nhở tác giả và gỡ bài viết trùng lặp."),
            new DemoReport("minhtri", "user", 22, ReportReason.MISINFORMATION,
                    "Thông tin về lịch bảo trì ký túc xá không khớp với thông báo chính thức.", ReportStatus.REJECTED,
                    "Đã xác minh bài viết là thông tin cá nhân hợp lệ, không phải thông báo chính thức."),
            new DemoReport("ngocmai", null, 43, ReportReason.HARASSMENT,
                    "Bình luận có lời lẽ công kích cá nhân với thành viên khác.", ReportStatus.PENDING, null),
            new DemoReport("yenlinh", "demoan", -1, ReportReason.SPAM,
                    "Tài khoản gửi tin nhắn quảng cáo dịch vụ chạy điểm qua nhiều nhóm.", ReportStatus.REVIEWED, null),
            new DemoReport("linhchi", null, 55, ReportReason.HATE,
                    "Nội dung có ngôn từ phân biệt vùng miền, gây tranh cãi.", ReportStatus.PENDING, null),
            new DemoReport("tranthao", "thanhdat", -1, ReportReason.MISINFORMATION,
                    "Đăng thông tin tuyển dụng yêu cầu đóng phí hồ sơ trái quy định.", ReportStatus.REVIEWED, null),
            new DemoReport("quocbao", null, 60, ReportReason.SPAM,
                    "Bài viết chèn link bán hàng không liên quan chủ đề cộng đồng.", ReportStatus.RESOLVED,
                    "Đã xóa liên kết và nhắc nhở thành viên."),
            new DemoReport("hoangnam", "tranthao", -1, ReportReason.OTHER,
                    "Tài khoản mạo danh ban tổ chức sự kiện để thu tiền đăng ký.", ReportStatus.PENDING, null),
            new DemoReport("ngocmai", null, 74, ReportReason.VIOLENCE,
                    "Nội dung có ngôn từ gây hấn và đe dọa thành viên khác trong phần bình luận.", ReportStatus.PENDING, null),
            new DemoReport("maiphuong", null, 80, ReportReason.SPAM,
                    "Bài viết có dấu hiệu spam liên kết trong nhiều chủ đề.", ReportStatus.REJECTED,
                    "Không đủ căn cứ, bài viết được giữ nguyên."),
            new DemoReport("baongoc", "thanhdat", 7, ReportReason.MISINFORMATION,
                    "Hướng dẫn cấu hình sai có thể gây lỗi bảo mật cho người mới.", ReportStatus.RESOLVED,
                    "Chủ bài viết đã cập nhật lại nội dung chính xác."),
            new DemoReport("vietthong", null, 95, ReportReason.OTHER,
                    "Bài viết bị đăng nhầm cộng đồng, không đúng chủ đề.", ReportStatus.PENDING, null)
    };
}
