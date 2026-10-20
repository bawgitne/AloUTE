import 'package:flutter/foundation.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../models/message.dart';

class MessageService extends ChangeNotifier {
  static final MessageService _instance = MessageService._internal();
  factory MessageService() => _instance;
  MessageService._internal();

  final ApiClient _api = ApiClient();
  final List<ChatConversation> _conversations = [];
  final Map<int, List<ChatMessage>> _messagesByConv = {};
  bool _isLoading = false;

  List<ChatConversation> get conversations => List.unmodifiable(_conversations);
  bool get isLoading => _isLoading;

  Future<void> fetchConversations() async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await _api.get<List<dynamic>>(ApiConstants.conversations);
      if (response.success && response.data != null) {
        _conversations.clear();
        for (final item in response.data!) {
          _conversations.add(ChatConversation.fromJson(item as Map<String, dynamic>));
        }
        _isLoading = false;
        notifyListeners();
        return;
      }
    } catch (_) {}

    // Fallback Mock Conversations
    if (_conversations.isEmpty) {
      _conversations.addAll([
        ChatConversation(
          id: 1,
          title: 'Lê Hoàng Long',
          targetUsername: 'longle24',
          targetUserId: 24110300,
          avatarUrl: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150',
          lastMessage: 'Mai hẹn nhóm lúc 9h sáng tại Thư viện trung tâm nha An!',
          lastMessageAt: DateTime.now().subtract(const Duration(minutes: 15)),
          unreadCount: 2,
          isOnline: true,
        ),
        ChatConversation(
          id: 2,
          title: 'Nguyễn Thị Mai Chi',
          targetUsername: 'maichi_k24',
          targetUserId: 24110312,
          avatarUrl: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150',
          lastMessage: 'Cảm ơn cậu đã chia sẻ slide bài giảng môn Cơ sở dữ liệu nhé.',
          lastMessageAt: DateTime.now().subtract(const Duration(hours: 2)),
          unreadCount: 0,
          isOnline: false,
        ),
        ChatConversation(
          id: 3,
          title: 'Ban Cố Vấn Học Tập K24',
          targetUsername: 'covanhoctap',
          targetUserId: 24110001,
          avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150',
          lastMessage: 'Thông báo: Hạn nộp đơn phúc khảo học kỳ 1 đến hết ngày 25/10.',
          lastMessageAt: DateTime.now().subtract(const Duration(days: 1)),
          unreadCount: 0,
          isOnline: true,
        ),
      ]);
    }

    _isLoading = false;
    notifyListeners();
  }

  Future<List<ChatMessage>> fetchMessages(int conversationId, int currentUserId) async {
    if (_messagesByConv.containsKey(conversationId)) {
      return _messagesByConv[conversationId]!;
    }

    try {
      final response = await _api.get<List<dynamic>>(
        ApiConstants.conversationMessages(conversationId),
      );
      if (response.success && response.data != null) {
        final list = response.data!
            .map((e) => ChatMessage.fromJson(e as Map<String, dynamic>, currentUserId))
            .toList();
        _messagesByConv[conversationId] = list;
        return list;
      }
    } catch (_) {}

    // Fallback Mock Messages
    final mockList = [
      ChatMessage(
        id: 101,
        conversationId: conversationId,
        senderId: 9999,
        senderName: 'Lê Hoàng Long',
        content: 'Alo An ơi, đề tài môn Lập trình Web bạn làm tới đâu rồi?',
        createdAt: DateTime.now().subtract(const Duration(minutes: 45)),
        isMine: false,
      ),
      ChatMessage(
        id: 102,
        conversationId: conversationId,
        senderId: currentUserId,
        senderName: 'Nguyễn Văn An',
        content: 'Mình vừa xong phần giao diện Flutter với cả gọi API Spring Boot rồi nè, đang kết nối mượt lắm!',
        createdAt: DateTime.now().subtract(const Duration(minutes: 30)),
        isMine: true,
      ),
      ChatMessage(
        id: 103,
        conversationId: conversationId,
        senderId: 9999,
        senderName: 'Lê Hoàng Long',
        content: 'Đỉnh quá! Mai hẹn nhóm lúc 9h sáng tại Thư viện trung tâm nha An!',
        createdAt: DateTime.now().subtract(const Duration(minutes: 15)),
        isMine: false,
      ),
    ];
    _messagesByConv[conversationId] = mockList;
    return mockList;
  }

  Future<void> sendMessage({
    required int conversationId,
    required String content,
    required int currentUserId,
  }) async {
    if (content.trim().isEmpty) return;

    final newMessage = ChatMessage(
      id: DateTime.now().millisecondsSinceEpoch,
      conversationId: conversationId,
      senderId: currentUserId,
      senderName: 'Bạn',
      content: content.trim(),
      createdAt: DateTime.now(),
      isMine: true,
    );

    _messagesByConv.putIfAbsent(conversationId, () => []).add(newMessage);

    // Update conversation preview
    final convIndex = _conversations.indexWhere((c) => c.id == conversationId);
    if (convIndex != -1) {
      final old = _conversations[convIndex];
      _conversations[convIndex] = ChatConversation(
        id: old.id,
        title: old.title,
        avatarUrl: old.avatarUrl,
        lastMessage: content.trim(),
        lastMessageAt: DateTime.now(),
        unreadCount: 0,
        isOnline: old.isOnline,
        targetUsername: old.targetUsername,
        targetUserId: old.targetUserId,
      );
    }

    notifyListeners();

    try {
      await _api.post(
        ApiConstants.conversationMessages(conversationId),
        body: {'content': content.trim()},
      );
    } catch (_) {}
  }
}
