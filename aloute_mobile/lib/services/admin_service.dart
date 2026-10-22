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
    } catch (e) {
      debugPrint('[AdminService] Fetch overview failed: $e');
    }

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
    } catch (e) {
      debugPrint('[AdminService] Fetch reports failed: $e');
    }

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
    } catch (e) {
      debugPrint('[AdminService] Fetch users failed: $e');
    }

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
    } catch (e) {
      debugPrint('[AdminService] Fetch posts failed: $e');
    }

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
