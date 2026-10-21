import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../models/user.dart';
import '../../services/auth_service.dart';
import '../auth/login_screen.dart';
import '../profile/edit_profile_screen.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  void _handleLogout(BuildContext context) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AlouteColors.bgSurface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusLg)),
        title: const Text('Đăng xuất tài khoản?'),
        content: const Text('Bạn có chắc chắn muốn đăng xuất khỏi ứng dụng AloUTE?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Hủy', style: TextStyle(color: AlouteColors.textSecondary)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AlouteColors.danger,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
            ),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Đăng xuất'),
          ),
        ],
      ),
    );

    if (confirmed == true && context.mounted) {
      await AuthService().logout();
      if (context.mounted) {
        Navigator.pushAndRemoveUntil(
          context,
          MaterialPageRoute(builder: (_) => const LoginScreen()),
          (route) => false,
        );
      }
    }
  }

  void _showTwoFactorInfoDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AlouteColors.bgSurface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusLg)),
        title: const Row(
          children: [
            Icon(Icons.shield_outlined, color: AlouteColors.accent),
            SizedBox(width: 8),
            Text('Bảo mật 2 lớp (2FA)', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          ],
        ),
        content: const Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Tính năng xác thực hai yếu tố (2FA) bảo vệ tài khoản bằng mã số động từ Google Authenticator hoặc Email sinh viên.',
              style: TextStyle(fontSize: 13, color: AlouteColors.textPrimary),
            ),
            SizedBox(height: 12),
            Text(
              '💡 Mẹo: Bạn có thể bật và quét mã QR thiết lập 2FA dễ dàng từ trang Cài đặt trên máy tính hoặc trình duyệt Web AloUTE.',
              style: TextStyle(fontSize: 12, color: AlouteColors.textSecondary),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Đã hiểu', style: TextStyle(color: AlouteColors.accent, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final user = AuthService().currentUser ??
        User(
          id: 24110166,
          username: 'nguyenvanan',
          displayName: 'Nguyễn Văn An',
          email: '24110166@student.hcmute.edu.vn',
          faculty: 'Khoa Công nghệ Thông tin',
        );

    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      appBar: AppBar(
        title: const Text('Cài đặt tài khoản'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Header Card matching settings/index.html
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
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: AlouteColors.accentSubtle,
                        borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                      ),
                      child: const Icon(Icons.settings, color: AlouteColors.accent, size: 24),
                    ),
                    const SizedBox(width: 12),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Thiết lập & Tùy chọn',
                            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: AlouteColors.textPrimary),
                          ),
                          SizedBox(height: 2),
                          Text(
                            'Quản lý hồ sơ, giao diện và bảo mật trên AloUTE',
                            style: TextStyle(fontSize: 12, color: AlouteColors.textSecondary),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),

          // Setting Options List
          _buildSettingCard(
            context,
            icon: Icons.person_outline,
            iconBg: AlouteColors.accentSubtle,
            iconColor: AlouteColors.accent,
            title: 'Hồ sơ cá nhân',
            subtitle: 'Cập nhật tên hiển thị, tiểu sử, ảnh đại diện và thông tin lớp',
            trailing: OutlinedButton(
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => EditProfileScreen(user: user)),
                );
              },
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: AlouteColors.accent),
                foregroundColor: AlouteColors.accent,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                minimumSize: Size.zero,
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
              ),
              child: const Text('Xem hồ sơ', style: TextStyle(fontSize: 12)),
            ),
          ),
          const SizedBox(height: 12),

          _buildSettingCard(
            context,
            icon: Icons.palette_outlined,
            iconBg: AlouteColors.infoBg,
            iconColor: AlouteColors.info,
            title: 'Giao diện sinh viên',
            subtitle: 'AloUTE Light Campus — Phong cách Giảng đường Sáng HCMUTE',
            trailing: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: AlouteColors.accentSubtle,
                borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
                border: Border.all(color: AlouteColors.borderColor),
              ),
              child: const Text(
                'Light Campus',
                style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AlouteColors.accent),
              ),
            ),
          ),
          const SizedBox(height: 12),

          _buildSettingCard(
            context,
            icon: Icons.notifications_none,
            iconBg: AlouteColors.warningBg,
            iconColor: AlouteColors.warning,
            title: 'Thông báo',
            subtitle: 'Tùy chỉnh thông báo tương tác bài viết, phản hồi và tin nhắn',
            trailing: Switch(
              value: true,
              activeTrackColor: AlouteColors.accent,
              onChanged: (val) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Đã cập nhật cài đặt thông báo')),
                );
              },
            ),
          ),
          const SizedBox(height: 12),

          _buildSettingCard(
            context,
            icon: Icons.notifications_off_outlined,
            iconBg: AlouteColors.warningBg,
            iconColor: AlouteColors.warning,
            title: 'Tài khoản đã tắt thông báo (Mute)',
            subtitle: 'Quản lý danh sách người dùng bạn không muốn nhận thông báo',
            trailing: OutlinedButton(
              onPressed: () {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Xem danh sách mute trên phiên bản Web')),
                );
              },
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: AlouteColors.warning),
                foregroundColor: AlouteColors.warning,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                minimumSize: Size.zero,
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
              ),
              child: const Text('Quản lý', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
            ),
          ),
          const SizedBox(height: 12),

          _buildSettingCard(
            context,
            icon: Icons.block_outlined,
            iconBg: AlouteColors.dangerBg,
            iconColor: AlouteColors.danger,
            title: 'Người dùng đã chặn',
            subtitle: 'Quản lý danh sách các tài khoản bạn đã chặn trên AloUTE',
            trailing: OutlinedButton(
              onPressed: () {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Xem danh sách chặn trên phiên bản Web')),
                );
              },
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: AlouteColors.danger),
                foregroundColor: AlouteColors.danger,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                minimumSize: Size.zero,
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
              ),
              child: const Text('Quản lý', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
            ),
          ),
          const SizedBox(height: 12),
          _buildSettingCard(
            context,
            icon: Icons.shield_rounded,
            iconBg: AlouteColors.accentSubtle,
            iconColor: AlouteColors.accent,
            title: 'Bảo mật 2 lớp (2FA)',
            subtitle: 'Bảo vệ tài khoản bằng Google Authenticator và mã dự phòng',
            trailing: OutlinedButton(
              onPressed: () => _showTwoFactorInfoDialog(context),
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: AlouteColors.accent),
                foregroundColor: AlouteColors.accent,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                minimumSize: Size.zero,
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
              ),
              child: const Text('Chi tiết', style: TextStyle(fontSize: 12)),
            ),
          ),
          const SizedBox(height: 12),

          _buildSettingCard(
            context,
            icon: Icons.shield_outlined,
            iconBg: AlouteColors.dangerBg,
            iconColor: AlouteColors.danger,
            title: 'Bảo mật & Phiên đăng nhập',
            subtitle: 'Quản lý mật khẩu, khóa phiên và đăng xuất khỏi thiết bị',
            trailing: ElevatedButton(
              onPressed: () => _handleLogout(context),
              style: ElevatedButton.styleFrom(
                backgroundColor: AlouteColors.dangerBg,
                foregroundColor: AlouteColors.danger,
                elevation: 0,
                side: const BorderSide(color: AlouteColors.dangerBorder),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                minimumSize: Size.zero,
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
              ),
              child: const Text('Đăng xuất', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
            ),
          ),
          const SizedBox(height: 24),

          // About Card
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AlouteColors.bgSurface,
              borderRadius: BorderRadius.circular(AlouteColors.radiusLg),
              border: Border.all(color: AlouteColors.borderColor),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Về ứng dụng AloUTE', style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AlouteColors.textPrimary)),
                const SizedBox(height: 6),
                const Text(
                  'AloUTE Mobile — Ứng dụng mạng xã hội học thuật dành riêng cho sinh viên Trường Đại học Sư phạm Kỹ thuật TP.HCM (HCMUTE).',
                  style: TextStyle(fontSize: 12, color: AlouteColors.textSecondary, height: 1.4),
                ),
                const SizedBox(height: 8),
                const Divider(color: AlouteColors.borderSubtle, height: 1),
                const SizedBox(height: 8),
                const Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Phiên bản', style: TextStyle(fontSize: 12, color: AlouteColors.textMuted)),
                    Text('1.0.0 (Release Candidate)', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AlouteColors.textPrimary)),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSettingCard(
    BuildContext context, {
    required IconData icon,
    required Color iconBg,
    required Color iconColor,
    required String title,
    required String subtitle,
    required Widget trailing,
  }) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AlouteColors.bgSurface,
        borderRadius: BorderRadius.circular(AlouteColors.radiusLg),
        border: Border.all(color: AlouteColors.borderColor),
        boxShadow: AlouteColors.shadowSm,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: iconBg,
              shape: BoxShape.circle,
            ),
            child: Icon(icon, color: iconColor, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AlouteColors.textPrimary)),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  style: const TextStyle(fontSize: 12, color: AlouteColors.textSecondary, height: 1.3),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          trailing,
        ],
      ),
    );
  }
}
