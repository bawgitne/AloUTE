import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../models/notification_item.dart';
import '../../services/notification_service.dart';
import '../../widgets/aloute_avatar.dart';

class NotificationScreen extends StatefulWidget {
  const NotificationScreen({super.key});

  @override
  State<NotificationScreen> createState() => _NotificationScreenState();
}

class _NotificationScreenState extends State<NotificationScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final NotificationService _notificationService = NotificationService();

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _notificationService.fetchNotifications();
    _notificationService.addListener(_onUpdated);
  }

  void _onUpdated() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _tabController.dispose();
    _notificationService.removeListener(_onUpdated);
    super.dispose();
  }

  void _markAllAsRead() {
    _notificationService.markAllAsRead();
  }

  @override
  Widget build(BuildContext context) {
    final notifications = _notificationService.notifications;
    final unreadList = notifications.where((n) => !n.isRead).toList();

    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      appBar: AppBar(
        title: const Text('Thông báo'),
        actions: [
          TextButton.icon(
            onPressed: _markAllAsRead,
            icon: const Icon(Icons.done_all, size: 18, color: AlouteColors.accent),
            label: const Text(
              'Đọc hết',
              style: TextStyle(color: AlouteColors.accent, fontSize: 13, fontWeight: FontWeight.w600),
            ),
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          labelColor: AlouteColors.accent,
          unselectedLabelColor: AlouteColors.textSecondary,
          indicatorColor: AlouteColors.accent,
          indicatorWeight: 2.5,
          tabs: [
            Tab(text: 'Tất cả (${notifications.length})'),
            Tab(text: 'Chưa đọc (${unreadList.length})'),
          ],
        ),
      ),
      body: RefreshIndicator(
        onRefresh: () => _notificationService.fetchNotifications(refresh: true),
        color: AlouteColors.accent,
        child: _notificationService.isLoading && notifications.isEmpty
            ? const Center(child: CircularProgressIndicator())
            : TabBarView(
                controller: _tabController,
                children: [
                  _buildNotificationList(notifications),
                  _buildNotificationList(unreadList),
                ],
              ),
      ),
    );
  }

  Widget _buildNotificationList(List<NotificationItem> items) {
    if (items.isEmpty) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.notifications_none, size: 48, color: AlouteColors.textMuted),
            SizedBox(height: 8),
            Text('Không có thông báo nào', style: TextStyle(color: AlouteColors.textSecondary)),
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.symmetric(vertical: 8),
      itemCount: items.length,
      itemBuilder: (context, index) {
        final item = items[index];
        return _buildNotificationCard(item);
      },
    );
  }

  Widget _buildNotificationCard(NotificationItem item) {
    IconData icon;
    Color iconColor;

    switch (item.type) {
      case NotificationType.like:
        icon = Icons.favorite;
        iconColor = AlouteColors.danger;
        break;
      case NotificationType.reply:
        icon = Icons.chat_bubble;
        iconColor = AlouteColors.accent;
        break;
      case NotificationType.follow:
        icon = Icons.person_add;
        iconColor = AlouteColors.success;
        break;
      case NotificationType.community:
        icon = Icons.campaign;
        iconColor = AlouteColors.warning;
        break;
    }

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: item.isRead ? AlouteColors.bgSurface : AlouteColors.accentSubtle,
        borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
        border: Border.all(
          color: item.isRead ? AlouteColors.borderColor : AlouteColors.accent.withValues(alpha: 0.3),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Stack(
            children: [
              AlouteAvatar(name: item.actorName, size: 44),
              Positioned(
                bottom: 0,
                right: 0,
                child: Container(
                  padding: const EdgeInsets.all(3),
                  decoration: const BoxDecoration(
                    color: Colors.white,
                    shape: BoxShape.circle,
                  ),
                  child: Icon(icon, size: 14, color: iconColor),
                ),
              ),
            ],
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                RichText(
                  text: TextSpan(
                    style: const TextStyle(fontSize: 14, color: AlouteColors.textPrimary, height: 1.3),
                    children: [
                      TextSpan(
                        text: item.actorName,
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                      const TextSpan(text: ' '),
                      TextSpan(text: item.message),
                    ],
                  ),
                ),
                if (item.targetExcerpt != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    '"${item.targetExcerpt!}"',
                    style: const TextStyle(
                      fontSize: 12,
                      fontStyle: FontStyle.italic,
                      color: AlouteColors.textSecondary,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
                const SizedBox(height: 6),
                Text(
                  _formatTime(item.createdAt),
                  style: const TextStyle(fontSize: 11, color: AlouteColors.textMuted),
                ),
              ],
            ),
          ),
          if (!item.isRead)
            Container(
              width: 8,
              height: 8,
              margin: const EdgeInsets.only(top: 6),
              decoration: const BoxDecoration(
                color: AlouteColors.accent,
                shape: BoxShape.circle,
              ),
            ),
        ],
      ),
    );
  }

  String _formatTime(DateTime time) {
    final diff = DateTime.now().difference(time);
    if (diff.inMinutes < 60) return '${diff.inMinutes} phút trước';
    if (diff.inHours < 24) return '${diff.inHours} giờ trước';
    return '${diff.inDays} ngày trước';
  }
}
