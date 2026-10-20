import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../models/post.dart';
import '../../services/auth_service.dart';
import '../../services/post_service.dart';
import '../../widgets/aloute_avatar.dart';
import '../../widgets/aloute_button.dart';

class CreatePostScreen extends StatefulWidget {
  const CreatePostScreen({super.key});

  @override
  State<CreatePostScreen> createState() => _CreatePostScreenState();
}

class _CreatePostScreenState extends State<CreatePostScreen> {
  final TextEditingController _contentController = TextEditingController();
  final AuthService _authService = AuthService();
  final PostService _postService = PostService();

  String _selectedAudience = 'PUBLIC';
  bool _isSubmitting = false;
  PostPoll? _attachedPoll;

  @override
  void initState() {
    super.initState();
    _contentController.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _contentController.dispose();
    super.dispose();
  }

  void _handleSubmit() async {
    final text = _contentController.text.trim();
    if (text.isEmpty) return;

    setState(() => _isSubmitting = true);
    await _postService.createPost(
      text,
      poll: _attachedPoll,
      audience: _selectedAudience,
    );

    if (mounted) {
      setState(() => _isSubmitting = false);
      Navigator.pop(context);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Đã đăng bài viết thành công!'),
          backgroundColor: AlouteColors.success,
        ),
      );
    }
  }

  void _showAddPollDialog() {
    final questionCtrl = TextEditingController();
    final opt1Ctrl = TextEditingController();
    final opt2Ctrl = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Tạo cuộc bình chọn', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: questionCtrl,
              decoration: const InputDecoration(labelText: 'Câu hỏi bình chọn'),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: opt1Ctrl,
              decoration: const InputDecoration(labelText: 'Lựa chọn 1'),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: opt2Ctrl,
              decoration: const InputDecoration(labelText: 'Lựa chọn 2'),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Hủy'),
          ),
          ElevatedButton(
            onPressed: () {
              if (questionCtrl.text.isNotEmpty && opt1Ctrl.text.isNotEmpty && opt2Ctrl.text.isNotEmpty) {
                setState(() {
                  _attachedPoll = PostPoll(
                    id: DateTime.now().millisecondsSinceEpoch,
                    question: questionCtrl.text,
                    options: [
                      PollOption(id: 1, text: opt1Ctrl.text),
                      PollOption(id: 2, text: opt2Ctrl.text),
                    ],
                    totalVotes: 0,
                  );
                });
                Navigator.pop(ctx);
              }
            },
            child: const Text('Thêm'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final user = _authService.currentUser;
    final charCount = _contentController.text.length;
    final isValid = charCount > 0 && charCount <= 2000;

    return Scaffold(
      backgroundColor: AlouteColors.bgSurface,
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.close),
          onPressed: () => Navigator.pop(context),
        ),
        title: const Text('Tạo bài viết mới'),
        actions: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: AlouteButton(
              text: 'Đăng bài',
              isLoading: _isSubmitting,
              onPressed: isValid && !_isSubmitting ? _handleSubmit : null,
            ),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                AlouteAvatar(
                  name: user?.displayName ?? 'Tôi',
                  imageUrl: user?.avatarUrl,
                  size: 44,
                ),
                const SizedBox(width: 12),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      user?.displayName ?? 'Sinh viên AloUTE',
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        color: AlouteColors.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                      decoration: BoxDecoration(
                        color: AlouteColors.bgBody,
                        borderRadius: BorderRadius.circular(AlouteColors.radiusSm),
                        border: Border.all(color: AlouteColors.borderColor),
                      ),
                      child: DropdownButtonHideUnderline(
                        child: DropdownButton<String>(
                          value: _selectedAudience,
                          isDense: true,
                          style: const TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: AlouteColors.accent,
                          ),
                          items: const [
                            DropdownMenuItem(value: 'PUBLIC', child: Text('🌍 Công khai')),
                            DropdownMenuItem(value: 'FOLLOWERS', child: Text('👥 Người theo dõi')),
                            DropdownMenuItem(value: 'COMMUNITY', child: Text('🏛️ Cộng đồng')),
                          ],
                          onChanged: (val) {
                            if (val != null) setState(() => _selectedAudience = val);
                          },
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _contentController,
              maxLines: 8,
              maxLength: 2000,
              style: const TextStyle(fontSize: 16, height: 1.5),
              decoration: const InputDecoration(
                hintText: 'Bạn đang có thắc mắc hay chia sẻ gì về việc học hôm nay?',
                border: InputBorder.none,
                enabledBorder: InputBorder.none,
                focusedBorder: InputBorder.none,
                counterText: '',
              ),
            ),
            if (_attachedPoll != null) ...[
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AlouteColors.bgBody,
                  borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                  border: Border.all(color: AlouteColors.borderColor),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.poll_outlined, color: AlouteColors.accent),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Bình chọn: ${_attachedPoll!.question}',
                        style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close, size: 18),
                      onPressed: () => setState(() => _attachedPoll = null),
                    ),
                  ],
                ),
              ),
            ],
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    IconButton(
                      icon: const Icon(Icons.image_outlined, color: AlouteColors.accent),
                      onPressed: () {},
                      tooltip: 'Đính kèm ảnh',
                    ),
                    IconButton(
                      icon: const Icon(Icons.poll_outlined, color: AlouteColors.accent),
                      onPressed: _showAddPollDialog,
                      tooltip: 'Tạo bình chọn',
                    ),
                  ],
                ),
                Text(
                  '$charCount/2000',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                    color: charCount > 1900 ? AlouteColors.danger : AlouteColors.textMuted,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
