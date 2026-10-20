class ChatConversation {
  final int id;
  final String title;
  final String? avatarUrl;
  final String? lastMessage;
  final DateTime? lastMessageAt;
  final int unreadCount;
  final bool isOnline;
  final String? targetUsername;
  final int? targetUserId;

  ChatConversation({
    required this.id,
    required this.title,
    this.avatarUrl,
    this.lastMessage,
    this.lastMessageAt,
    this.unreadCount = 0,
    this.isOnline = false,
    this.targetUsername,
    this.targetUserId,
  });

  factory ChatConversation.fromJson(Map<String, dynamic> json) {
    return ChatConversation(
      id: json['id'] is int ? json['id'] as int : int.tryParse(json['id'].toString()) ?? 0,
      title: json['title'] as String? ?? json['name'] as String? ?? 'Người dùng',
      avatarUrl: json['avatarUrl'] as String?,
      lastMessage: json['lastMessage'] as String?,
      lastMessageAt: json['lastMessageAt'] != null
          ? DateTime.tryParse(json['lastMessageAt'].toString())
          : (json['updatedAt'] != null ? DateTime.tryParse(json['updatedAt'].toString()) : null),
      unreadCount: json['unreadCount'] as int? ?? 0,
      isOnline: json['isOnline'] as bool? ?? false,
      targetUsername: json['targetUsername'] as String?,
      targetUserId: json['targetUserId'] as int?,
    );
  }
}

class ChatMessage {
  final int id;
  final int conversationId;
  final int senderId;
  final String senderName;
  final String? senderAvatar;
  final String content;
  final String? mediaUrl;
  final DateTime createdAt;
  final bool isMine;

  ChatMessage({
    required this.id,
    required this.conversationId,
    required this.senderId,
    required this.senderName,
    this.senderAvatar,
    required this.content,
    this.mediaUrl,
    required this.createdAt,
    this.isMine = false,
  });

  factory ChatMessage.fromJson(Map<String, dynamic> json, int currentUserId) {
    final senderId = json['senderId'] is int
        ? json['senderId'] as int
        : int.tryParse(json['senderId'].toString()) ?? 0;

    return ChatMessage(
      id: json['id'] is int ? json['id'] as int : int.tryParse(json['id'].toString()) ?? 0,
      conversationId: json['conversationId'] is int
          ? json['conversationId'] as int
          : int.tryParse(json['conversationId'].toString()) ?? 0,
      senderId: senderId,
      senderName: json['senderName'] as String? ?? 'Sinh viên',
      senderAvatar: json['senderAvatar'] as String?,
      content: json['content'] as String? ?? '',
      mediaUrl: json['mediaUrl'] as String?,
      createdAt: json['createdAt'] != null
          ? (DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now())
          : DateTime.now(),
      isMine: senderId == currentUserId,
    );
  }
}
