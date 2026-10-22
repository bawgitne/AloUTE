import 'package:flutter/material.dart';
import '../core/theme/aloute_theme.dart';
import '../services/auth_service.dart';
import '../widgets/aloute_avatar.dart';
import 'admin/admin_dashboard_screen.dart';
import 'auth/login_screen.dart';
import 'community/community_list_screen.dart';
import 'feed/home_feed_screen.dart';
import 'messaging/conversation_list_screen.dart';
import 'notification/notification_screen.dart';
import 'post/create_post_screen.dart';
import 'post/saved_posts_screen.dart';
import 'profile/friends_screen.dart';
import 'profile/profile_screen.dart';
import 'search/search_screen.dart';
import 'settings/settings_screen.dart';

class MainNavigationScreen extends StatefulWidget {
  const MainNavigationScreen({super.key});

  @override
  State<MainNavigationScreen> createState() => _MainNavigationScreenState();
}

class _MainNavigationScreenState extends State<MainNavigationScreen> {
  int _currentIndex = 0;

  final List<Widget> _pages = const [
    HomeFeedScreen(),
    SearchScreen(),
    SizedBox.shrink(), // Center (+) button modal
    NotificationScreen(),
    ProfileScreen(),
  ];

  void _onTabSelected(int index) {
    if (index == 2) {
      Navigator.push(
        context,
        MaterialPageRoute(
          fullscreenDialog: true,
          builder: (_) => const CreatePostScreen(),
        ),
      );
      return;
    }
    setState(() => _currentIndex = index);
  }

  @override
  Widget build(BuildContext context) {
    final user = AuthService().currentUser;

    return Scaffold(
      drawer: Drawer(
        backgroundColor: AlouteColors.bgSurface,
        child: SafeArea(
          child: Column(
            children: [
              // Brand header matching web sidebar brand
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
                decoration: const BoxDecoration(
                  border: Border(bottom: BorderSide(color: AlouteColors.borderColor)),
                ),
                child: Row(
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: Image.asset(
                        'assets/images/logo.png',
                        width: 28,
                        height: 28,
                        fit: BoxFit.cover,
                        errorBuilder: (context, error, stackTrace) => Container(
                          width: 28,
                          height: 28,
                          decoration: BoxDecoration(
                            color: AlouteColors.accent,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Icon(Icons.school, color: Colors.white, size: 16),
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Image.asset(
                      'assets/images/logo_text.png',
                      height: 18,
                      fit: BoxFit.contain,
                      errorBuilder: (context, error, stackTrace) => const Text(
                        'ALOUTE',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                          color: AlouteColors.accent,
                          letterSpacing: 1.2,
                        ),
                      ),
                    ),
                    const Spacer(),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: AlouteColors.accentSubtle,
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: const Text(
                        'Campus',
                        style: TextStyle(
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                          color: AlouteColors.accent,
                        ),
                      ),
                    ),
                  ],
                ),
              ),

              // User header card matching web sidebar.html
              Container(
                padding: const EdgeInsets.all(20),
                decoration: const BoxDecoration(
                  color: AlouteColors.bgBody,
                  border: Border(bottom: BorderSide(color: AlouteColors.borderColor)),
                ),
                child: Row(
                  children: [
                    AlouteAvatar(
                      name: user?.displayName ?? 'Tôi',
                      imageUrl: user?.avatarUrl,
                      size: 52,
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            user?.displayName ?? 'Sinh viên HCMUTE',
                            style: const TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.bold,
                              color: AlouteColors.textPrimary,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          if (user?.username != null)
                            Text(
                              '@${user!.username}',
                              style: const TextStyle(fontSize: 12, color: AlouteColors.accent, fontWeight: FontWeight.w600),
                            ),
                          const SizedBox(height: 2),
                          Text(
                            user?.faculty ?? 'Sinh viên UTE',
                            style: const TextStyle(fontSize: 11, color: AlouteColors.textMuted),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),

              // Drawer Navigation Items matching web sidebar
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  children: [
                    _buildDrawerItem(
                      icon: Icons.home_outlined,
                      title: 'Bảng tin chính',
                      onTap: () {
                        Navigator.pop(context);
                        setState(() => _currentIndex = 0);
                      },
                    ),
                    _buildDrawerItem(
                      icon: Icons.people_alt_outlined,
                      title: 'Cộng đồng & Câu lạc bộ',
                      badge: 'CLB',
                      onTap: () {
                        Navigator.pop(context);
                        Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => const CommunityListScreen()),
                        );
                      },
                    ),
                    _buildDrawerItem(
                      icon: Icons.bookmark_border,
                      title: 'Bài viết đã lưu',
                      onTap: () {
                        Navigator.pop(context);
                        Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => const SavedPostsScreen()),
                        );
                      },
                    ),
                    _buildDrawerItem(
                      icon: Icons.people_outline,
                      title: 'Bạn bè & Kết nối',
                      onTap: () {
                        Navigator.pop(context);
                        Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => const FriendsScreen()),
                        );
                      },
                    ),
                    _buildDrawerItem(
                      icon: Icons.chat_bubble_outline,
                      title: 'Tin nhắn riêng',
                      badge: '2',
                      badgeColor: AlouteColors.accent,
                      onTap: () {
                        Navigator.pop(context);
                        Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => const ConversationListScreen()),
                        );
                      },
                    ),
                    _buildDrawerItem(
                      icon: Icons.notifications_none,
                      title: 'Thông báo',
                      onTap: () {
                        Navigator.pop(context);
                        setState(() => _currentIndex = 3);
                      },
                    ),
                    if (user?.isAdmin == true) ...[
                      const Divider(color: AlouteColors.borderSubtle, indent: 16, endIndent: 16),
                      _buildDrawerItem(
                        icon: Icons.admin_panel_settings_outlined,
                        title: 'Bảng quản trị (Admin)',
                        badge: 'Admin',
                        badgeColor: AlouteColors.warning,
                        onTap: () {
                          Navigator.pop(context);
                          Navigator.push(
                            context,
                            MaterialPageRoute(builder: (_) => const AdminDashboardScreen()),
                          );
                        },
                      ),
                    ],
                    _buildDrawerItem(
                      icon: Icons.settings_outlined,
                      title: 'Cài đặt tài khoản',
                      onTap: () {
                        Navigator.pop(context);
                        Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => const SettingsScreen()),
                        );
                      },
                    ),
                  ],
                ),
              ),

              // Drawer Footer with Logout
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                decoration: const BoxDecoration(
                  border: Border(top: BorderSide(color: AlouteColors.borderColor)),
                ),
                child: ListTile(
                  leading: const Icon(Icons.logout, color: AlouteColors.danger),
                  title: const Text(
                    'Đăng xuất',
                    style: TextStyle(color: AlouteColors.danger, fontWeight: FontWeight.bold, fontSize: 14),
                  ),
                  onTap: () async {
                    Navigator.pop(context);
                    await AuthService().logout();
                    if (context.mounted) {
                      Navigator.pushAndRemoveUntil(
                        context,
                        MaterialPageRoute(builder: (_) => const LoginScreen()),
                        (route) => false,
                      );
                    }
                  },
                ),
              ),
            ],
          ),
        ),
      ),
      body: IndexedStack(
        index: _currentIndex == 2 ? 0 : _currentIndex,
        children: _pages,
      ),
      bottomNavigationBar: Container(
        decoration: const BoxDecoration(
          color: AlouteColors.bgSurface,
          border: Border(top: BorderSide(color: AlouteColors.borderColor, width: 1)),
        ),
        child: BottomNavigationBar(
          currentIndex: _currentIndex,
          onTap: _onTabSelected,
          elevation: 0,
          backgroundColor: AlouteColors.bgSurface,
          selectedItemColor: AlouteColors.accent,
          unselectedItemColor: AlouteColors.textMuted,
          type: BottomNavigationBarType.fixed,
          selectedFontSize: 11,
          unselectedFontSize: 11,
          items: [
            const BottomNavigationBarItem(
              icon: Icon(Icons.home_outlined),
              activeIcon: Icon(Icons.home),
              label: 'Bảng tin',
            ),
            const BottomNavigationBarItem(
              icon: Icon(Icons.search_outlined),
              activeIcon: Icon(Icons.search),
              label: 'Khám phá',
            ),
            BottomNavigationBarItem(
              icon: Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
                decoration: BoxDecoration(
                  color: AlouteColors.accent,
                  borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
                ),
                child: const Icon(Icons.add, color: Colors.white, size: 20),
              ),
              label: '',
            ),
            const BottomNavigationBarItem(
              icon: Icon(Icons.notifications_outlined),
              activeIcon: Icon(Icons.notifications),
              label: 'Thông báo',
            ),
            const BottomNavigationBarItem(
              icon: Icon(Icons.person_outline),
              activeIcon: Icon(Icons.person),
              label: 'Hồ sơ',
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDrawerItem({
    required IconData icon,
    required String title,
    String? badge,
    Color? badgeColor,
    required VoidCallback onTap,
  }) {
    return ListTile(
      leading: Icon(icon, color: AlouteColors.textPrimary, size: 22),
      title: Text(
        title,
        style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AlouteColors.textPrimary),
      ),
      trailing: badge != null
          ? Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(
                color: (badgeColor ?? AlouteColors.accent).withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
                border: Border.all(color: (badgeColor ?? AlouteColors.accent).withValues(alpha: 0.3)),
              ),
              child: Text(
                badge,
                style: TextStyle(
                  color: badgeColor ?? AlouteColors.accent,
                  fontSize: 11,
                  fontWeight: FontWeight.bold,
                ),
              ),
            )
          : null,
      onTap: onTap,
    );
  }
}
