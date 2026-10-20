import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../services/admin_service.dart';

class AdminPostsScreen extends StatefulWidget {
  const AdminPostsScreen({super.key});

  @override
  State<AdminPostsScreen> createState() => _AdminPostsScreenState();
}

class _AdminPostsScreenState extends State<AdminPostsScreen> {
  final AdminService _adminService = AdminService();

  @override
  void initState() {
    super.initState();
    _adminService.fetchPosts();
  }

  void _confirmRemove(int postId) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AlouteColors.bgSurface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusLg)),
        title: const Text('Gỡ bài viết?'),
        content: Text('Bạn có chắc chắn muốn gỡ bài viết #$postId khỏi hệ thống AloUTE?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Hủy', style: TextStyle(color: AlouteColors.textSecondary)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AlouteColors.danger,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
            ),
            onPressed: () {
              Navigator.pop(ctx);
              _adminService.removePost(postId);
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text('Đã gỡ bài viết #$postId thành công'),
                  backgroundColor: AlouteColors.success,
                ),
              );
            },
            child: const Text('Xác nhận gỡ'),
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
        title: const Text('Kiểm duyệt bài viết'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListenableBuilder(
        listenable: _adminService,
        builder: (context, _) {
          if (_adminService.isLoading) {
            return const Center(child: CircularProgressIndicator());
          }

          final posts = _adminService.posts;

          return ListView.builder(
            padding: const EdgeInsets.all(12),
            itemCount: posts.length,
            itemBuilder: (context, index) {
              final p = posts[index];
              final isRemoved = p.status == 'REMOVED';

              return Container(
                margin: const EdgeInsets.only(bottom: 12),
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AlouteColors.bgSurface,
                  borderRadius: BorderRadius.circular(AlouteColors.radiusLg),
                  border: Border.all(color: AlouteColors.borderColor),
                  boxShadow: AlouteColors.shadowSm,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            Text(
                              p.authorName,
                              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: AlouteColors.textPrimary),
                            ),
                            const SizedBox(width: 6),
                            Text(
                              '@${p.authorUsername}',
                              style: const TextStyle(fontSize: 12, color: AlouteColors.textMuted),
                            ),
                          ],
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(
                            color: isRemoved ? AlouteColors.dangerBg : AlouteColors.accentSubtle,
                            borderRadius: BorderRadius.circular(AlouteColors.radiusSm),
                          ),
                          child: Text(
                            isRemoved ? 'Đã gỡ' : p.visibility,
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.bold,
                              color: isRemoved ? AlouteColors.danger : AlouteColors.accent,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      p.content,
                      style: const TextStyle(fontSize: 13, color: AlouteColors.textPrimary, height: 1.4),
                    ),
                    const SizedBox(height: 12),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Row(
                          children: [
                            const Icon(Icons.report_problem_outlined, size: 14, color: AlouteColors.warning),
                            const SizedBox(width: 4),
                            Text(
                              '${p.reportCount} lượt báo cáo',
                              style: const TextStyle(fontSize: 12, color: AlouteColors.textSecondary),
                            ),
                          ],
                        ),
                        if (!isRemoved)
                          ElevatedButton.icon(
                            onPressed: () => _confirmRemove(p.id),
                            icon: const Icon(Icons.delete_outline, size: 14),
                            label: const Text('Gỡ bài'),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: AlouteColors.dangerBg,
                              foregroundColor: AlouteColors.danger,
                              elevation: 0,
                              side: const BorderSide(color: AlouteColors.dangerBorder),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                              minimumSize: Size.zero,
                              tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                            ),
                          ),
                      ],
                    ),
                  ],
                ),
              );
            },
          );
        },
      ),
    );
  }
}
