import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../models/post.dart';
import '../../widgets/aloute_avatar.dart';
import '../../widgets/aloute_post_card.dart';

import '../../services/post_service.dart';

class PostDetailScreen extends StatefulWidget {
  final Post post;
  final bool focusReply;

  const PostDetailScreen({
    super.key,
    required this.post,
    this.focusReply = false,
  });

  @override
  State<PostDetailScreen> createState() => _PostDetailScreenState();
}

class _PostDetailScreenState extends State<PostDetailScreen> {
  final TextEditingController _replyController = TextEditingController();
  final FocusNode _focusNode = FocusNode();
  List<Map<String, dynamic>> _replies = [];
  bool _isLoadingReplies = false;

  @override
  void initState() {
    super.initState();
    _loadReplies();
    if (widget.focusReply) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _focusNode.requestFocus();
      });
    }
  }

  Future<void> _loadReplies() async {
    setState(() => _isLoadingReplies = true);
    final list = await PostService().getReplies(widget.post.id);
    if (mounted) {
      setState(() {
        _replies = list;
        _isLoadingReplies = false;
      });
    }
  }

  @override
  void dispose() {
    _replyController.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  Future<void> _sendReply() async {
    final text = _replyController.text.trim();
    if (text.isEmpty) return;

    final success = await PostService().createReply(widget.post.id, text);
    if (mounted) {
      if (success) {
        _replyController.clear();
        widget.post.replyCount++;
        await _loadReplies();
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Không thể gửi bình luận. Vui lòng thử lại!'),
            backgroundColor: AlouteColors.danger,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      appBar: AppBar(
        title: const Text('Chi tiết bài viết'),
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView(
              children: [
                AloutePostCard(post: widget.post),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
                  child: Text(
                    'Bình luận (${_replies.length})',
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      color: AlouteColors.textPrimary,
                    ),
                  ),
                ),
                if (_isLoadingReplies)
                  const Padding(
                    padding: EdgeInsets.all(24.0),
                    child: Center(child: CircularProgressIndicator()),
                  )
                else if (_replies.isEmpty)
                  const Padding(
                    padding: EdgeInsets.all(24.0),
                    child: Center(
                      child: Text(
                        'Chưa có bình luận nào.',
                        style: TextStyle(color: AlouteColors.textMuted),
                      ),
                    ),
                  )
                else
                  ..._replies.map((reply) => _buildReplyItem(reply)),
              ],
            ),
          ),
          _buildReplyInput(),
        ],
      ),
    );
  }

  Widget _buildReplyItem(Map<String, dynamic> reply) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AlouteColors.bgSurface,
        borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
        border: Border.all(color: AlouteColors.borderSubtle),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AlouteAvatar(name: reply['authorName'] as String, size: 36),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      reply['authorName'] as String,
                      style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13),
                    ),
                    const SizedBox(width: 6),
                    Text(
                      reply['createdAt'] as String,
                      style: const TextStyle(fontSize: 11, color: AlouteColors.textMuted),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  reply['content'] as String,
                  style: const TextStyle(fontSize: 13, height: 1.4, color: AlouteColors.textPrimary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildReplyInput() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      decoration: const BoxDecoration(
        color: AlouteColors.bgSurface,
        border: Border(top: BorderSide(color: AlouteColors.borderColor)),
      ),
      child: SafeArea(
        child: Row(
          children: [
            Expanded(
              child: TextField(
                controller: _replyController,
                focusNode: _focusNode,
                decoration: InputDecoration(
                  hintText: 'Viết bình luận của bạn...',
                  contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
                    borderSide: const BorderSide(color: AlouteColors.borderColor),
                  ),
                ),
              ),
            ),
            const SizedBox(width: 8),
            IconButton(
              icon: const Icon(Icons.send, color: AlouteColors.accent),
              onPressed: _sendReply,
            ),
          ],
        ),
      ),
    );
  }
}
