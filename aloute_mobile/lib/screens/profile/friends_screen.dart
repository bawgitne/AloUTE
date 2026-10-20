import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../models/user.dart';

class FriendsScreen extends StatefulWidget {
  final String initialTab; // 'following' or 'followers'

  const FriendsScreen({super.key, this.initialTab = 'following'});

  @override
  State<FriendsScreen> createState() => _FriendsScreenState();
}

class _FriendsScreenState extends State<FriendsScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;

  final List<User> _followingUsers = [
    User(
      id: 24110300,
      username: 'longle24',
      displayName: 'Lê Hoàng Long',
      faculty: 'Khoa Công nghệ Thông tin',
      bio: 'Lập trình viên Flutter & Cloud. Thích đá bóng và nghe nhạc indie.',
      avatarUrl: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150',
      followerCount: 210,
      followingCount: 180,
    ),
    User(
      id: 24110312,
      username: 'maichi_k24',
      displayName: 'Trần Thị Mai Chi',
      faculty: 'Khoa Điện - Điện tử',
      bio: 'Thành viên CLB Tiếng Anh ESC UTE. Đam mê IoT và Hệ thống thông minh.',
      avatarUrl: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150',
      followerCount: 350,
      followingCount: 120,
    ),
    User(
      id: 24110450,
      username: 'minhnhat_ute',
      displayName: 'Võ Minh Nhật',
      faculty: 'Khoa Cơ khí Chế tạo máy',
      bio: 'Chế tạo robot Robocon. Chuyên gia SolidWorks & AutoCAD.',
      avatarUrl: 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150',
      followerCount: 180,
      followingCount: 95,
    ),
  ];

  final List<User> _followerUsers = [
    User(
      id: 24110300,
      username: 'longle24',
      displayName: 'Lê Hoàng Long',
      faculty: 'Khoa Công nghệ Thông tin',
      bio: 'Lập trình viên Flutter & Cloud. Thích đá bóng và nghe nhạc indie.',
      avatarUrl: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150',
      followerCount: 210,
      followingCount: 180,
    ),
    User(
      id: 24110789,
      username: 'quochuy_k24',
      displayName: 'Đặng Quốc Huy',
      faculty: 'Khoa Kinh tế',
      bio: 'Quản trị kinh doanh & Khởi nghiệp đổi mới sáng tạo UTE.',
      avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150',
      followerCount: 92,
      followingCount: 140,
    ),
  ];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(
      length: 2,
      vsync: this,
      initialIndex: widget.initialTab == 'followers' ? 1 : 0,
    );
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      appBar: AppBar(
        title: const Text('Bạn bè & Kết nối'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: Column(
        children: [
          // Top Header Card matching user/friends.html
          Container(
            color: AlouteColors.bgSurface,
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Row(
                      children: [
                        Icon(Icons.people, color: AlouteColors.accent, size: 22),
                        SizedBox(width: 8),
                        Text(
                          'Mạng lưới bạn bè',
                          style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: AlouteColors.textPrimary),
                        ),
                      ],
                    ),
                    OutlinedButton.icon(
                      onPressed: () => Navigator.pop(context),
                      icon: const Icon(Icons.search, size: 16),
                      label: const Text('Tìm bạn mới'),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: AlouteColors.accent,
                        side: const BorderSide(color: AlouteColors.accent),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                        minimumSize: Size.zero,
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                const Text(
                  'Quản lý danh sách người bạn đang theo dõi và người theo dõi bạn.',
                  style: TextStyle(fontSize: 12, color: AlouteColors.textSecondary),
                ),
                const SizedBox(height: 12),
                TabBar(
                  controller: _tabController,
                  labelColor: AlouteColors.accent,
                  unselectedLabelColor: AlouteColors.textSecondary,
                  indicatorColor: AlouteColors.accent,
                  indicatorWeight: 3,
                  labelStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13),
                  tabs: [
                    Tab(
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(Icons.person_pin_circle_outlined, size: 16),
                          const SizedBox(width: 6),
                          Text('Đang theo dõi (${_followingUsers.length})'),
                        ],
                      ),
                    ),
                    Tab(
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(Icons.favorite_border, size: 16),
                          const SizedBox(width: 6),
                          Text('Người theo dõi (${_followerUsers.length})'),
                        ],
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const Divider(height: 1, color: AlouteColors.borderColor),

          // Tab Views
          Expanded(
            child: TabBarView(
              controller: _tabController,
              children: [
                // Following List
                _buildUserList(_followingUsers, isFollowingTab: true),

                // Followers List
                _buildUserList(_followerUsers, isFollowingTab: false),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildUserList(List<User> list, {required bool isFollowingTab}) {
    if (list.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.person_off_outlined, size: 48, color: AlouteColors.textMuted),
              const SizedBox(height: 12),
              Text(
                isFollowingTab ? 'Chưa theo dõi ai' : 'Chưa có người theo dõi',
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: AlouteColors.textPrimary),
              ),
              const SizedBox(height: 6),
              Text(
                isFollowingTab
                    ? 'Hãy khám phá các thành viên trong trường và theo dõi để cập nhật bài viết mới nhất.'
                    : 'Khi có người khác theo dõi bạn, họ sẽ xuất hiện tại đây.',
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 13, color: AlouteColors.textSecondary),
              ),
            ],
          ),
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.all(12),
      itemCount: list.length,
      itemBuilder: (context, index) {
        final u = list[index];

        return Container(
          margin: const EdgeInsets.only(bottom: 10),
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: AlouteColors.bgSurface,
            borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
            border: Border.all(color: AlouteColors.borderColor),
            boxShadow: AlouteColors.shadowSm,
          ),
          child: Row(
            children: [
              CircleAvatar(
                radius: 24,
                backgroundImage: NetworkImage(
                  u.avatarUrl ?? 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150',
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(
                            u.displayName,
                            style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: AlouteColors.textPrimary),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 4),
                        const Icon(Icons.verified, size: 14, color: AlouteColors.accent),
                      ],
                    ),
                    Text(
                      '@${u.username}',
                      style: const TextStyle(fontSize: 12, color: AlouteColors.textMuted),
                    ),
                    if (u.bio != null && u.bio!.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(
                        u.bio!,
                        style: const TextStyle(fontSize: 12, color: AlouteColors.textSecondary),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(width: 8),
              OutlinedButton(
                onPressed: () {
                  setState(() {
                    if (isFollowingTab) {
                      list.removeAt(index);
                    }
                  });
                },
                style: OutlinedButton.styleFrom(
                  side: const BorderSide(color: AlouteColors.borderColor),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  minimumSize: Size.zero,
                  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                ),
                child: Text(
                  isFollowingTab ? 'Đang theo dõi' : 'Theo dõi lại',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: isFollowingTab ? AlouteColors.textSecondary : AlouteColors.accent,
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
