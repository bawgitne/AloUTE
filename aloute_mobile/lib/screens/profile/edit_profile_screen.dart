import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../models/user.dart';
import '../../services/auth_service.dart';
import '../../widgets/aloute_button.dart';

class EditProfileScreen extends StatefulWidget {
  final User user;

  const EditProfileScreen({super.key, required this.user});

  @override
  State<EditProfileScreen> createState() => _EditProfileScreenState();
}

class _EditProfileScreenState extends State<EditProfileScreen> {
  late TextEditingController _displayNameController;
  late TextEditingController _bioController;
  late TextEditingController _facultyController;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _displayNameController = TextEditingController(text: widget.user.displayName);
    _bioController = TextEditingController(text: widget.user.bio);
    _facultyController = TextEditingController(text: widget.user.faculty);
  }

  @override
  void dispose() {
    _displayNameController.dispose();
    _bioController.dispose();
    _facultyController.dispose();
    super.dispose();
  }

  Future<void> _handleSave() async {
    setState(() => _isLoading = true);

    await AuthService().updateProfile(
      displayName: _displayNameController.text.trim(),
      bio: _bioController.text.trim(),
      faculty: _facultyController.text.trim(),
    );

    setState(() => _isLoading = false);

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Cập nhật hồ sơ thành công!'),
          backgroundColor: AlouteColors.success,
        ),
      );
      Navigator.pop(context, true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      appBar: AppBar(
        title: const Text('Chỉnh sửa hồ sơ'),
        leading: IconButton(
          icon: const Icon(Icons.close),
          onPressed: () => Navigator.pop(context),
        ),
        actions: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            child: ElevatedButton(
              onPressed: _isLoading ? null : _handleSave,
              style: ElevatedButton.styleFrom(
                backgroundColor: AlouteColors.accent,
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
                padding: const EdgeInsets.symmetric(horizontal: 16),
              ),
              child: _isLoading
                  ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                  : const Text('Lưu'),
            ),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            // Cover Photo & Avatar Edit Area
            Stack(
              clipBehavior: Clip.none,
              alignment: Alignment.bottomCenter,
              children: [
                // Cover photo preview
                GestureDetector(
                  onTap: () {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Chọn ảnh bìa mới từ thư viện ảnh...')),
                    );
                  },
                  child: Container(
                    height: 140,
                    width: double.infinity,
                    margin: const EdgeInsets.only(bottom: 44),
                    decoration: BoxDecoration(
                      color: AlouteColors.bgBody,
                      borderRadius: BorderRadius.circular(AlouteColors.radiusLg),
                      border: Border.all(color: AlouteColors.borderColor),
                      image: widget.user.coverUrl != null && widget.user.coverUrl!.isNotEmpty
                          ? DecorationImage(
                              image: NetworkImage(widget.user.coverUrl!),
                              fit: BoxFit.cover,
                            )
                          : null,
                    ),
                    child: Stack(
                      children: [
                        if (widget.user.coverUrl == null || widget.user.coverUrl!.isEmpty)
                          const Center(
                            child: Icon(Icons.panorama_outlined, size: 40, color: AlouteColors.textMuted),
                          ),
                        Positioned(
                          top: 10,
                          right: 10,
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                            decoration: BoxDecoration(
                              color: Colors.black.withValues(alpha: 0.65),
                              borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
                            ),
                            child: const Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.camera_alt, color: Colors.white, size: 14),
                                SizedBox(width: 4),
                                Text('Đổi ảnh bìa', style: TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold)),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                // Avatar preview
                GestureDetector(
                  onTap: () {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Chọn ảnh đại diện mới từ thư viện...')),
                    );
                  },
                  child: Stack(
                    children: [
                      Container(
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          border: Border.all(color: AlouteColors.bgSurface, width: 4),
                        ),
                        child: CircleAvatar(
                          radius: 44,
                          backgroundImage: NetworkImage(
                            widget.user.avatarUrl ?? 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150',
                          ),
                        ),
                      ),
                      Positioned(
                        bottom: 4,
                        right: 4,
                        child: Container(
                          padding: const EdgeInsets.all(6),
                          decoration: const BoxDecoration(
                            color: AlouteColors.accent,
                            shape: BoxShape.circle,
                          ),
                          child: const Icon(Icons.camera_alt, color: Colors.white, size: 15),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Form Fields
            Container(
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
                  const Text('Tên hiển thị', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AlouteColors.textPrimary)),
                  const SizedBox(height: 6),
                  TextField(
                    controller: _displayNameController,
                    decoration: const InputDecoration(
                      hintText: 'Nhập tên hiển thị...',
                      contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                    ),
                  ),
                  const SizedBox(height: 16),

                  const Text('Liên kết Website / Trang cá nhân', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AlouteColors.textPrimary)),
                  const SizedBox(height: 6),
                  TextField(
                    controller: _facultyController,
                    decoration: const InputDecoration(
                      hintText: 'https://...',
                      contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                    ),
                  ),
                  const SizedBox(height: 16),

                  const Text('Tiểu sử (Bio)', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AlouteColors.textPrimary)),
                  const SizedBox(height: 6),
                  TextField(
                    controller: _bioController,
                    maxLines: 4,
                    decoration: const InputDecoration(
                      hintText: 'Giới thiệu ngắn về bản thân, sở thích...',
                      contentPadding: EdgeInsets.all(12),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            AlouteButton(
              text: 'Lưu thay đổi',
              icon: Icons.check,
              isLoading: _isLoading,
              onPressed: _handleSave,
            ),
          ],
        ),
      ),
    );
  }
}
