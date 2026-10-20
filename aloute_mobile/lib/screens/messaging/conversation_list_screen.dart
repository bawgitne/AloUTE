import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../models/message.dart';
import '../../services/message_service.dart';
import 'chat_screen.dart';

class ConversationListScreen extends StatefulWidget {
  const ConversationListScreen({super.key});

  @override
  State<ConversationListScreen> createState() => _ConversationListScreenState();
}

class _ConversationListScreenState extends State<ConversationListScreen> {
  final MessageService _messageService = MessageService();
  String _filter = '';

  @override
  void initState() {
    super.initState();
    _messageService.fetchConversations();
  }

  void _showNewChatDialog() {
    final usernameCtrl = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AlouteColors.bgSurface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusLg)),
        title: const Row(
          children: [
            Icon(Icons.chat_bubble_outline, color: AlouteColors.accent),
            SizedBox(width: 8),
            Text('Nhắn tin mới', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Nhập username sinh viên bạn muốn trò chuyện:',
              style: TextStyle(fontSize: 13, color: AlouteColors.textSecondary),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: usernameCtrl,
              decoration: const InputDecoration(
                hintText: '@username',
                contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Hủy', style: TextStyle(color: AlouteColors.textSecondary)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AlouteColors.accent,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
            ),
            onPressed: () {
              final username = usernameCtrl.text.trim();
              Navigator.pop(ctx);
              if (username.isNotEmpty) {
                final newConv = ChatConversation(
                  id: DateTime.now().millisecondsSinceEpoch,
                  title: username,
                  targetUsername: username,
                  lastMessage: 'Bắt đầu cuộc trò chuyện...',
                  lastMessageAt: DateTime.now(),
                  isOnline: true,
                );
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => ChatScreen(conversation: newConv)),
                );
              }
            },
            child: const Text('Bắt đầu'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      appBar: AppBar(
        title: const Text('Tin nhắn'),
        actions: [
          IconButton(
            icon: const Icon(Icons.edit_square, color: AlouteColors.accent),
            tooltip: 'Tin nhắn mới',
            onPressed: _showNewChatDialog,
          ),
        ],
      ),
      body: ListenableBuilder(
        listenable: _messageService,
        builder: (context, _) {
          if (_messageService.isLoading) {
            return const Center(child: CircularProgressIndicator());
          }

          final allConvs = _messageService.conversations;
          final filtered = allConvs.where((c) {
            final q = _filter.toLowerCase();
            return c.title.toLowerCase().contains(q) || (c.lastMessage?.toLowerCase().contains(q) ?? false);
          }).toList();

          return RefreshIndicator(
            onRefresh: _messageService.fetchConversations,
            color: AlouteColors.accent,
            child: Column(
              children: [
                // Search Input matching web conversation search addon
                Container(
                  color: AlouteColors.bgSurface,
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                  child: Container(
                    decoration: BoxDecoration(
                      color: AlouteColors.bgBody,
                      borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
                      border: Border.all(color: AlouteColors.borderColor),
                    ),
                    child: TextField(
                      onChanged: (val) => setState(() => _filter = val),
                      decoration: const InputDecoration(
                        hintText: 'Lọc danh sách cuộc trò chuyện...',
                        hintStyle: TextStyle(color: AlouteColors.textMuted, fontSize: 13),
                        prefixIcon: Icon(Icons.search, size: 20, color: AlouteColors.textMuted),
                        border: InputBorder.none,
                        contentPadding: EdgeInsets.symmetric(vertical: 10),
                      ),
                    ),
                  ),
                ),
                const Divider(height: 1, color: AlouteColors.borderColor),

                // Conversations List
                Expanded(
                  child: filtered.isEmpty
                      ? Center(
                          child: Padding(
                            padding: const EdgeInsets.all(32),
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                const Icon(Icons.chat_bubble_outline, size: 48, color: AlouteColors.textMuted),
                                const SizedBox(height: 12),
                                const Text(
                                  'Chưa có cuộc trò chuyện nào',
                                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15, color: AlouteColors.textPrimary),
                                ),
                                const SizedBox(height: 4),
                                const Text(
                                  'Bắt đầu kết nối và trao đổi học tập với bạn bè trên AloUTE!',
                                  textAlign: TextAlign.center,
                                  style: TextStyle(fontSize: 13, color: AlouteColors.textSecondary),
                                ),
                                const SizedBox(height: 16),
                                ElevatedButton.icon(
                                  onPressed: _showNewChatDialog,
                                  icon: const Icon(Icons.add, size: 18),
                                  label: const Text('Nhắn tin mới'),
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: AlouteColors.accent,
                                    foregroundColor: Colors.white,
                                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        )
                      : ListView.separated(
                          itemCount: filtered.length,
                          separatorBuilder: (context, index) => const Divider(
                            height: 1,
                            color: AlouteColors.borderSubtle,
                            indent: 68,
                          ),
                          itemBuilder: (context, index) {
                            final conv = filtered[index];
                            final hasUnread = conv.unreadCount > 0;

                            return ListTile(
                              contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                              onTap: () {
                                Navigator.push(
                                  context,
                                  MaterialPageRoute(
                                    builder: (_) => ChatScreen(conversation: conv),
                                  ),
                                );
                              },
                              leading: Stack(
                                children: [
                                  CircleAvatar(
                                    radius: 24,
                                    backgroundImage: NetworkImage(
                                      conv.avatarUrl ?? 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150',
                                    ),
                                  ),
                                  if (conv.isOnline)
                                    Positioned(
                                      right: 0,
                                      bottom: 0,
                                      child: Container(
                                        width: 12,
                                        height: 12,
                                        decoration: BoxDecoration(
                                          color: AlouteColors.success,
                                          shape: BoxShape.circle,
                                          border: Border.all(color: Colors.white, width: 2),
                                        ),
                                      ),
                                    ),
                                ],
                              ),
                              title: Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Expanded(
                                    child: Text(
                                      conv.title,
                                      style: TextStyle(
                                        fontSize: 15,
                                        fontWeight: hasUnread ? FontWeight.w800 : FontWeight.w600,
                                        color: AlouteColors.textPrimary,
                                      ),
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                  if (conv.lastMessageAt != null)
                                    Text(
                                      '${conv.lastMessageAt!.hour.toString().padLeft(2, '0')}:${conv.lastMessageAt!.minute.toString().padLeft(2, '0')}',
                                      style: TextStyle(
                                        fontSize: 11,
                                        color: hasUnread ? AlouteColors.accent : AlouteColors.textMuted,
                                        fontWeight: hasUnread ? FontWeight.bold : FontWeight.normal,
                                      ),
                                    ),
                                ],
                              ),
                              subtitle: Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      conv.lastMessage ?? 'Chưa có tin nhắn',
                                      style: TextStyle(
                                        fontSize: 13,
                                        color: hasUnread ? AlouteColors.textPrimary : AlouteColors.textSecondary,
                                        fontWeight: hasUnread ? FontWeight.w600 : FontWeight.normal,
                                      ),
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                  if (hasUnread)
                                    Container(
                                      margin: const EdgeInsets.only(left: 6),
                                      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                                      decoration: BoxDecoration(
                                        color: AlouteColors.accent,
                                        borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
                                      ),
                                      child: Text(
                                        '${conv.unreadCount}',
                                        style: const TextStyle(
                                          color: Colors.white,
                                          fontSize: 11,
                                          fontWeight: FontWeight.bold,
                                        ),
                                      ),
                                    ),
                                ],
                              ),
                            );
                          },
                        ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}
