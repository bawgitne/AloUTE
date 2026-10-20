enum NotificationType {
  like,
  reply,
  follow,
  community,
}

class NotificationItem {
  final int id;
  final String actorName;
  final String? actorAvatar;
  final NotificationType type;
  final String message;
  final String? targetExcerpt;
  final DateTime createdAt;
  bool isRead;

  NotificationItem({
    required this.id,
    required this.actorName,
    this.actorAvatar,
    required this.type,
    required this.message,
    this.targetExcerpt,
    required this.createdAt,
    this.isRead = false,
  });

  factory NotificationItem.fromJson(Map<String, dynamic> json) {
    NotificationType parsedType = NotificationType.like;
    final typeStr = (json['type'] as String? ?? '').toLowerCase();
    if (typeStr.contains('reply') || typeStr.contains('comment')) {
      parsedType = NotificationType.reply;
    } else if (typeStr.contains('follow')) {
      parsedType = NotificationType.follow;
    } else if (typeStr.contains('community') || typeStr.contains('club')) {
      parsedType = NotificationType.community;
    }

    return NotificationItem(
      id: json['id'] is int ? json['id'] : int.tryParse(json['id'].toString()) ?? 0,
      actorName: json['actorName'] as String? ?? 'Người dùng AloUTE',
      actorAvatar: json['actorAvatar'] as String?,
      type: parsedType,
      message: json['message'] as String? ?? '',
      targetExcerpt: json['targetExcerpt'] as String?,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
      isRead: json['isRead'] as bool? ?? false,
    );
  }
}
