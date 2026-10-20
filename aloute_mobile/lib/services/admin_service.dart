import 'package:flutter/foundation.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../models/admin_models.dart';

class AdminService extends ChangeNotifier {
  static final AdminService _instance = AdminService._internal();
  factory AdminService() => _instance;
  AdminService._internal();

  final ApiClient _api = ApiClient();
  AdminMetrics _metrics = AdminMetrics();
  final List<AdminReportItem> _reports = [];
  final List<AdminUserItem> _users = [];
  final List<AdminPostItem> _posts = [];
  bool _isLoading = false;

  AdminMetrics get metrics => _metrics;
  List<AdminReportItem> get reports => List.unmodifiable(_reports);
  List<AdminUserItem> get users => List.unmodifiable(_users);
  List<AdminPostItem> get posts => List.unmodifiable(_posts);
  bool get isLoading => _isLoading;

  Future<void> fetchOverview() async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await _api.get<Map<String, dynamic>>(ApiConstants.adminOverview);
      if (response.success && response.data != null) {
        final metricsData = response.data!['metrics'] as Map<String, dynamic>? ?? response.data!;
        _metrics = AdminMetrics.fromJson(metricsData);
        _isLoading = false;
        notifyListeners();
        return;
      }
    } catch (_) {}

    // Mock KPI Metrics
    _metrics = AdminMetrics(
      totalUsers: 12480,
      activeUsers: 8950,
      totalPosts: 45620,
      postsCreatedToday: 342,
      pendingReports: 7,
      reviewedReports: 18,
      totalCommunities: 64,
      activeCommunities: 52,
      systemHealth: 'HEALTHY',
    );
    _isLoading = false;
    notifyListeners();
  }

  Future<void> fetchReports({String status = 'PENDING'}) async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await _api.get<List<dynamic>>(
        '${ApiConstants.adminReports}?status=$status',
      );
      if (response.success && response.data != null) {
        _reports.clear();
        for (final item in response.data!) {
          _reports.add(AdminReportItem.fromJson(item as Map<String, dynamic>));
        }
        _isLoading = false;
        notifyListeners();
        return;
      }
    } catch (_) {}

    // Mock reports queue
    _reports.clear();
    _reports.addAll([
      AdminReportItem(
        id: 301,
        reason: 'Spam bán tài liệu trái phép và quảng cáo dịch vụ thi hộ',
        status: 'PENDING',
        reporterName: 'Võ Minh Nhật',
        reportedUserName: 'user_spam_99',
        targetType: 'POST',
        targetId: 442,
        contentPreview: 'Nhận làm đồ án tốt nghiệp trọn gói bao điểm A, inbox Zalo 090xxx...',
        createdAt: DateTime.now().subtract(const Duration(minutes: 30)),
      ),
      AdminReportItem(
        id: 302,
        reason: 'Ngôn từ xúc phạm giảng viên và vi phạm quy tắc ứng xử học đường',
        status: 'PENDING',
        reporterName: 'Đặng Quốc Huy',
        reportedUserName: 'anonymous_user',
        targetType: 'POST',
        targetId: 443,
        contentPreview: 'Bài đăng mang tính công kích cá nhân và xuyên tạc lịch học...',
        createdAt: DateTime.now().subtract(const Duration(hours: 2)),
      ),
    ]);

    _isLoading = false;
    notifyListeners();
  }

  Future<void> resolveReport(int reportId, String action) async {
    _reports.removeWhere((r) => r.id == reportId);
    notifyListeners();

    try {
      await _api.post(
        ApiConstants.adminReportAction(reportId),
        body: {'action': action},
      );
    } catch (_) {}
  }

  Future<void> fetchUsers() async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await _api.get<List<dynamic>>(ApiConstants.adminUsers);
      if (response.success && response.data != null) {
        _users.clear();
        for (final item in response.data!) {
          _users.add(AdminUserItem.fromJson(item as Map<String, dynamic>));
        }
        _isLoading = false;
        notifyListeners();
        return;
      }
    } catch (_) {}

    // Mock users list
    _users.clear();
    _users.addAll([
      AdminUserItem(
        id: 1,
        username: 'admin',
        displayName: 'Quản trị viên AloUTE',
        email: 'admin@aloute.local',
        role: 'ROLE_ADMIN',
        status: 'ACTIVE',
        faculty: 'Phòng Công nghệ Thông tin',
        createdAt: DateTime.now().subtract(const Duration(days: 365)),
      ),
      AdminUserItem(
        id: 24110166,
        username: 'nguyenvanan',
        displayName: 'Nguyễn Văn An',
        email: '24110166@student.hcmute.edu.vn',
        role: 'ROLE_USER',
        status: 'ACTIVE',
        faculty: 'Khoa Công nghệ Thông tin',
        createdAt: DateTime.now().subtract(const Duration(days: 60)),
      ),
      AdminUserItem(
        id: 24110999,
        username: 'spammer_acc',
        displayName: 'Tài khoản vi phạm',
        email: 'spam@student.hcmute.edu.vn',
        role: 'ROLE_USER',
        status: 'SUSPENDED',
        faculty: 'Khoa Cơ khí Chế tạo máy',
        createdAt: DateTime.now().subtract(const Duration(days: 5)),
      ),
    ]);

    _isLoading = false;
    notifyListeners();
  }

  Future<void> toggleUserStatus(int userId) async {
    final index = _users.indexWhere((u) => u.id == userId);
    if (index == -1) return;

    final user = _users[index];
    user.status = (user.status == 'ACTIVE') ? 'SUSPENDED' : 'ACTIVE';
    notifyListeners();

    try {
      await _api.patch(
        ApiConstants.adminUserById(userId),
        body: {'status': user.status},
      );
    } catch (_) {}
  }

  Future<void> fetchPosts() async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await _api.get<List<dynamic>>(ApiConstants.adminPosts);
      if (response.success && response.data != null) {
        _posts.clear();
        for (final item in response.data!) {
          _posts.add(AdminPostItem.fromJson(item as Map<String, dynamic>));
        }
        _isLoading = false;
        notifyListeners();
        return;
      }
    } catch (_) {}

    // Mock posts list
    _posts.clear();
    _posts.addAll([
      AdminPostItem(
        id: 501,
        authorName: 'Nguyễn Văn An',
        authorUsername: 'nguyenvanan',
        content: 'Chia sẻ kinh nghiệm làm Đồ án môn học Kiến trúc Phần mềm và CI/CD với Docker.',
        visibility: 'PUBLIC',
        status: 'ACTIVE',
        reportCount: 0,
        createdAt: DateTime.now().subtract(const Duration(hours: 3)),
      ),
      AdminPostItem(
        id: 502,
        authorName: 'Tài khoản vi phạm',
        authorUsername: 'spammer_acc',
        content: 'Bán đề thi cuối kỳ môn Toán cao cấp và Giải tích 1 kèm bài giải chi tiết...',
        visibility: 'PUBLIC',
        status: 'ACTIVE',
        reportCount: 5,
        createdAt: DateTime.now().subtract(const Duration(hours: 12)),
      ),
    ]);

    _isLoading = false;
    notifyListeners();
  }

  Future<void> removePost(int postId) async {
    final index = _posts.indexWhere((p) => p.id == postId);
    if (index != -1) {
      _posts[index].status = 'REMOVED';
      notifyListeners();
    }

    try {
      await _api.delete(ApiConstants.adminPostById(postId));
    } catch (_) {}
  }
}
