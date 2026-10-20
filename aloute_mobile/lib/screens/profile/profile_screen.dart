import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../models/post.dart';
import '../../models/user.dart';
import '../../services/auth_service.dart';
import '../../services/post_service.dart';
import '../../widgets/aloute_avatar.dart';
import '../../widgets/aloute_button.dart';
import '../../widgets/aloute_post_card.dart';
import '../auth/login_screen.dart';
import '../settings/settings_screen.dart';
import 'edit_profile_screen.dart';
import 'friends_screen.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final AuthService _authService = AuthService();
  final PostService _postService = PostService();

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  void _handleLogout() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Đăng xuất'),
        content: const Text('Bạn có chắc chắn muốn đăng xuất khỏi tài khoản AloUTE?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Hủy'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: ElevatedButton.styleFrom(backgroundColor: AlouteColors.danger),
            child: const Text('Đăng xuất', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );

    if (confirm == true) {
      await _authService.logout();
      if (mounted) {
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(builder: (_) => const LoginScreen()),
          (route) => false,
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final user = _authService.currentUser;
    final myPosts = _postService.homePosts
        .where((p) => p.author.username == user?.username || p.author.id == user?.id)
        .toList();
    final bookmarkedPosts = _postService.homePosts.where((p) => p.isBookmarked).toList();

    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      appBar: AppBar(
        title: Text(user?.username != null ? '@${user!.username}' : 'Hồ sơ cá nhân'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout, color: AlouteColors.danger),
            onPressed: _handleLogout,
            tooltip: 'Đăng xuất',
          ),
        ],
      ),
      body: NestedScrollView(
        headerSliverBuilder: (context, innerBoxIsScrolled) {
          return [
            SliverToBoxAdapter(
              child: _buildProfileHeader(user),
            ),
            SliverPersistentHeader(
              pinned: true,
              delegate: _SliverTabBarDelegate(
                TabBar(
                  controller: _tabController,
                  labelColor: AlouteColors.accent,
                  unselectedLabelColor: AlouteColors.textSecondary,
                  indicatorColor: AlouteColors.accent,
                  indicatorWeight: 2.5,
                  labelStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14),
                  tabs: [
                    Tab(text: 'Bài viết (${myPosts.length})'),
                    const Tab(text: 'Phản hồi'),
                    Tab(text: 'Đã lưu (${bookmarkedPosts.length})'),
                  ],
                ),
              ),
            ),
          ];
        },
        body: TabBarView(
          controller: _tabController,
          children: [
            _buildPostList(myPosts, 'Bạn chưa đăng bài viết nào'),
            const Center(child: Text('Chưa có phản hồi nào', style: TextStyle(color: AlouteColors.textMuted))),
            _buildPostList(bookmarkedPosts, 'Chưa có bài viết nào được lưu'),
          ],
        ),
      ),
    );
  }

  Widget _buildProfileHeader(User? user) {
    return Column(
      children: [
        Stack(
          clipBehavior: Clip.none,
          children: [
            // Cover Photo
            Container(
              height: 120,
              width: double.infinity,
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  colors: [AlouteColors.accent, Color(0xFF1E40AF)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
              ),
              child: Stack(
                children: [
                  Positioned.fill(
                    child: Opacity(
                      opacity: 0.1,
                      child: Image.network(
                        'https://images.unsplash.com/photo-1523050854058-8df90110c9f1',
                        fit: BoxFit.cover,
                        errorBuilder: (context, error, stackTrace) => const SizedBox(),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            // Avatar with 4px border overlapping cover
            Positioned(
              left: 20,
              bottom: -40,
              child: AlouteAvatar(
                name: user?.displayName ?? 'Tôi',
                imageUrl: user?.avatarUrl,
                size: 88,
                borderWidth: 4,
                borderColor: Colors.white,
              ),
            ),
          ],
        ),
        const SizedBox(height: 48),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          user?.displayName ?? 'Sinh viên HCMUTE',
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w800,
                            color: AlouteColors.textPrimary,
                            letterSpacing: -0.3,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          '@${user?.username ?? "user"}',
                          style: const TextStyle(fontSize: 13, color: AlouteColors.textSecondary),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.settings_outlined, color: AlouteColors.textPrimary),
                    onPressed: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const SettingsScreen()),
                      );
                    },
                    tooltip: 'Cài đặt',
                  ),
                  IconButton(
                    icon: const Icon(Icons.logout, color: AlouteColors.danger),
                    onPressed: _handleLogout,
                    tooltip: 'Đăng xuất',
                  ),
                ],
              ),
              const SizedBox(height: 12),
              // Sửa hồ sơ button
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Text(
                      user?.bio ?? 'Sinh viên Trường Đại học Sư phạm Kỹ thuật TP.HCM (HCMUTE).',
                      style: const TextStyle(fontSize: 14, height: 1.4, color: AlouteColors.textPrimary),
                    ),
                  ),
                  const SizedBox(width: 12),
                  AlouteButton(
                    text: 'Sửa hồ sơ',
                    variant: AlouteButtonVariant.outline,
                    onPressed: () {
                      if (user != null) {
                        Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => EditProfileScreen(user: user)),
                        ).then((updated) {
                          if (updated == true) setState(() {});
                        });
                      }
                    },
                  ),
                ],
              ),
              const SizedBox(height: 12),
              // Academic Faculty Badge & Student Code
              Row(
                children: [
                  const Icon(Icons.school_outlined, size: 16, color: AlouteColors.accent),
                  const SizedBox(width: 6),
                  Text(
                    user?.faculty ?? 'Khoa Công nghệ Thông tin',
                    style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AlouteColors.accent),
                  ),
                  const SizedBox(width: 12),
                  const Icon(Icons.badge_outlined, size: 16, color: AlouteColors.textMuted),
                  const SizedBox(width: 4),
                  Text(
                    'MSSV: ${user?.studentCode ?? "24110xxx"}',
                    style: const TextStyle(fontSize: 13, color: AlouteColors.textSecondary),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              // Stats with clickable navigation to FriendsScreen
              Row(
                children: [
                  GestureDetector(
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const FriendsScreen(initialTab: 'following')),
                      );
                    },
                    child: _buildStatItem('Đang theo dõi', '${user?.followingCount ?? 95}'),
                  ),
                  const SizedBox(width: 20),
                  GestureDetector(
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const FriendsScreen(initialTab: 'followers')),
                      );
                    },
                    child: _buildStatItem('Người theo dõi', '${user?.followerCount ?? 128}'),
                  ),
                  const SizedBox(width: 20),
                  _buildStatItem('Bài viết', '${user?.postCount ?? 14}'),
                ],
              ),
              const SizedBox(height: 14),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildStatItem(String label, String count) {
    return Row(
      children: [
        Text(
          count,
          style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14, color: AlouteColors.textPrimary),
        ),
        const SizedBox(width: 4),
        Text(
          label,
          style: const TextStyle(fontSize: 13, color: AlouteColors.textSecondary),
        ),
      ],
    );
  }

  Widget _buildPostList(List<Post> posts, String emptyMessage) {
    if (posts.isEmpty) {
      return Center(
        child: Text(emptyMessage, style: const TextStyle(color: AlouteColors.textMuted)),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.only(top: 8, bottom: 40),
      itemCount: posts.length,
      itemBuilder: (context, index) {
        return AloutePostCard(post: posts[index]);
      },
    );
  }
}

class _SliverTabBarDelegate extends SliverPersistentHeaderDelegate {
  final TabBar tabBar;
  _SliverTabBarDelegate(this.tabBar);

  @override
  double get minExtent => tabBar.preferredSize.height;
  @override
  double get maxExtent => tabBar.preferredSize.height;

  @override
  Widget build(BuildContext context, double shrinkOffset, bool overlapsContent) {
    return Container(
      color: AlouteColors.bgSurface,
      child: tabBar,
    );
  }

  @override
  bool shouldRebuild(_SliverTabBarDelegate oldDelegate) => false;
}
