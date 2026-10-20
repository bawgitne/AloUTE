import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../models/message.dart';
import '../../services/auth_service.dart';
import '../../services/message_service.dart';

class ChatScreen extends StatefulWidget {
  final ChatConversation conversation;

  const ChatScreen({super.key, required this.conversation});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final MessageService _messageService = MessageService();
  final TextEditingController _textController = TextEditingController();
  final ScrollController _scrollController = ScrollController();

  List<ChatMessage> _messages = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadMessages();
  }

  Future<void> _loadMessages() async {
    final currentUserId = AuthService().currentUser?.id ?? 24110166;
    final list = await _messageService.fetchMessages(widget.conversation.id, currentUserId);
    if (mounted) {
      setState(() {
        _messages = list;
        _isLoading = false;
      });
      _scrollToBottom();
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

  Future<void> _sendMessage() async {
    final text = _textController.text.trim();
    if (text.isEmpty) return;

    _textController.clear();
    final currentUserId = AuthService().currentUser?.id ?? 24110166;

    await _messageService.sendMessage(
      conversationId: widget.conversation.id,
      content: text,
      currentUserId: currentUserId,
    );

    setState(() {});
    _scrollToBottom();
  }

  @override
  void dispose() {
    _textController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      appBar: AppBar(
        titleSpacing: 0,
        title: Row(
          children: [
            Stack(
              children: [
                CircleAvatar(
                  radius: 18,
                  backgroundImage: NetworkImage(
                    widget.conversation.avatarUrl ?? 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150',
                  ),
                ),
                if (widget.conversation.isOnline)
                  Positioned(
                    right: 0,
                    bottom: 0,
                    child: Container(
                      width: 10,
                      height: 10,
                      decoration: BoxDecoration(
                        color: AlouteColors.success,
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.white, width: 2),
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    widget.conversation.title,
                    style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    widget.conversation.isOnline ? 'Đang hoạt động' : 'Offline',
                    style: TextStyle(
                      fontSize: 11,
                      color: widget.conversation.isOnline ? AlouteColors.success : AlouteColors.textMuted,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.info_outline, color: AlouteColors.textSecondary),
            onPressed: () {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text('Cuộc trò chuyện với ${widget.conversation.title}')),
              );
            },
          ),
        ],
      ),
      body: Column(
        children: [
          // Messages List
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : ListView.builder(
                    controller: _scrollController,
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                    itemCount: _messages.length,
                    itemBuilder: (context, index) {
                      final msg = _messages[index];
                      final isMine = msg.isMine;

                      return Align(
                        alignment: isMine ? Alignment.centerRight : Alignment.centerLeft,
                        child: Container(
                          margin: const EdgeInsets.only(bottom: 10),
                          constraints: BoxConstraints(
                            maxWidth: MediaQuery.of(context).size.width * 0.76,
                          ),
                          child: Column(
                            crossAxisAlignment: isMine ? CrossAxisAlignment.end : CrossAxisAlignment.start,
                            children: [
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                                decoration: BoxDecoration(
                                  color: isMine ? AlouteColors.accent : AlouteColors.bgSurface,
                                  borderRadius: BorderRadius.only(
                                    topLeft: const Radius.circular(AlouteColors.radiusLg),
                                    topRight: const Radius.circular(AlouteColors.radiusLg),
                                    bottomLeft: Radius.circular(isMine ? AlouteColors.radiusLg : 2),
                                    bottomRight: Radius.circular(isMine ? 2 : AlouteColors.radiusLg),
                                  ),
                                  border: isMine ? null : Border.all(color: AlouteColors.borderColor),
                                  boxShadow: AlouteColors.shadowSm,
                                ),
                                child: Text(
                                  msg.content,
                                  style: TextStyle(
                                    fontSize: 14,
                                    color: isMine ? Colors.white : AlouteColors.textPrimary,
                                    height: 1.35,
                                  ),
                                ),
                              ),
                              const SizedBox(height: 3),
                              Text(
                                '${msg.createdAt.hour.toString().padLeft(2, '0')}:${msg.createdAt.minute.toString().padLeft(2, '0')}',
                                style: const TextStyle(fontSize: 10, color: AlouteColors.textMuted),
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
          ),

          // Message Input Field
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: const BoxDecoration(
              color: AlouteColors.bgSurface,
              border: Border(top: BorderSide(color: AlouteColors.borderColor)),
            ),
            child: SafeArea(
              child: Row(
                children: [
                  IconButton(
                    icon: const Icon(Icons.photo_outlined, color: AlouteColors.textSecondary),
                    onPressed: () {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Đính kèm ảnh/tài liệu')),
                      );
                    },
                  ),
                  Expanded(
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14),
                      decoration: BoxDecoration(
                        color: AlouteColors.bgBody,
                        borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
                        border: Border.all(color: AlouteColors.borderColor),
                      ),
                      child: TextField(
                        controller: _textController,
                        onSubmitted: (_) => _sendMessage(),
                        decoration: const InputDecoration(
                          hintText: 'Nhập tin nhắn...',
                          hintStyle: TextStyle(color: AlouteColors.textMuted, fontSize: 14),
                          border: InputBorder.none,
                          contentPadding: EdgeInsets.symmetric(vertical: 10),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Container(
                    decoration: const BoxDecoration(
                      color: AlouteColors.accent,
                      shape: BoxShape.circle,
                    ),
                    child: IconButton(
                      icon: const Icon(Icons.send, color: Colors.white, size: 18),
                      onPressed: _sendMessage,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
