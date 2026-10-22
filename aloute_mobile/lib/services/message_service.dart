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
    } catch (e) {
      debugPrint('[MessageService] Fetch conversations failed: $e');
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
    } catch (e) {
      debugPrint('[MessageService] Fetch messages failed: $e');
    }

    return [];
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
