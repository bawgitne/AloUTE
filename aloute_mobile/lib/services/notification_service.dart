import 'package:flutter/foundation.dart';
import '../core/network/api_client.dart';
import '../models/notification_item.dart';

class NotificationService extends ChangeNotifier {
  static final NotificationService _instance = NotificationService._internal();
  factory NotificationService() => _instance;
  NotificationService._internal();

  final ApiClient _api = ApiClient();
  final List<NotificationItem> _notifications = [];
  bool _isLoading = false;

  List<NotificationItem> get notifications => List.unmodifiable(_notifications);
  bool get isLoading => _isLoading;

  Future<void> fetchNotifications({bool refresh = false}) async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await _api.get<dynamic>('/notifications');
      if (response.success && response.data != null) {
        _notifications.clear();
        final rawList = response.data is List
            ? response.data as List
            : (response.data['content'] as List? ?? []);
        for (final item in rawList) {
          _notifications.add(NotificationItem.fromJson(item as Map<String, dynamic>));
        }
      }
    } catch (e) {
      debugPrint('[NotificationService] fetchNotifications error: $e');
    }

    _isLoading = false;
    notifyListeners();
  }

  Future<void> markAsRead(int notificationId) async {
    final index = _notifications.indexWhere((n) => n.id == notificationId);
    if (index != -1) {
      _notifications[index].isRead = true;
      notifyListeners();
      try {
        await _api.patch('/notifications/$notificationId/read');
      } catch (_) {}
    }
  }

  Future<void> markAllAsRead() async {
    for (var n in _notifications) {
      n.isRead = true;
    }
    notifyListeners();

    try {
      await _api.patch('/notifications/read-all');
    } catch (_) {}
  }
}
