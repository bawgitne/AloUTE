import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../models/community.dart';
import '../../models/post.dart';
import '../../services/community_service.dart';
import '../../widgets/aloute_post_card.dart';
import '../post/create_post_screen.dart';

class CommunityDetailScreen extends StatefulWidget {
  final Community community;

  const CommunityDetailScreen({super.key, required this.community});

  @override
  State<CommunityDetailScreen> createState() => _CommunityDetailScreenState();
}

class _CommunityDetailScreenState extends State<CommunityDetailScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final CommunityService _communityService = CommunityService();
  List<Post> _posts = [];
  bool _isLoadingPosts = true;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    _loadPosts();
  }

  Future<void> _loadPosts() async {
    setState(() => _isLoadingPosts = true);
    final list = await _communityService.getCommunityPosts(widget.community.id);
    if (mounted) {
      setState(() {
        _posts = list;
        _isLoadingPosts = false;
      });
    }
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final comm = widget.community;

    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      body: NestedScrollView(
        headerSliverBuilder: (context, innerBoxIsScrolled) {
          return [
            SliverAppBar(
              expandedHeight: 180,
              pinned: true,
              backgroundColor: AlouteColors.accent,
              foregroundColor: Colors.white,
              title: innerBoxIsScrolled
                  ? Text(comm.name, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white))
                  : null,
              flexibleSpace: FlexibleSpaceBar(
                background: Stack(
                  fit: StackFit.expand,
                  children: [
                    Image.network(
                      comm.coverUrl ?? 'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800',
                      fit: BoxFit.cover,
                      errorBuilder: (context, error, stackTrace) => Container(
                        decoration: const BoxDecoration(
                          gradient: LinearGradient(
                            colors: [AlouteColors.accent, Color(0xFF0F172A)],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                        ),
                      ),
                    ),
                    Container(
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          colors: [Colors.black.withValues(alpha: 0.4), Colors.transparent],
                          begin: Alignment.topCenter,
                          end: Alignment.bottomCenter,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            SliverToBoxAdapter(
              child: Container(
                color: AlouteColors.bgSurface,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Avatar & Actions Row
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Transform.translate(
                          offset: const Offset(0, -32),
                          child: Container(
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                              border: Border.all(color: Colors.white, width: 3),
                              boxShadow: AlouteColors.shadowMd,
                            ),
                            child: ClipRRect(
                              borderRadius: BorderRadius.circular(AlouteColors.radiusMd - 2),
                              child: Image.network(
                                comm.avatarUrl ?? 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=150',
                                width: 68,
                                height: 68,
                                fit: BoxFit.cover,
                                errorBuilder: (context, error, stackTrace) => Container(
                                  width: 68,
                                  height: 68,
                                  color: AlouteColors.accentSubtle,
                                  child: const Icon(Icons.group, color: AlouteColors.accent, size: 32),
                                ),
                              ),
                            ),
                          ),
                        ),
                        Row(
                          children: [
                            if (comm.isOwner)
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                                decoration: BoxDecoration(
                                  color: AlouteColors.warningBg,
                                  borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
                                  border: Border.all(color: AlouteColors.warningBorder),
                                ),
                                child: const Row(
                                  children: [
                                    Icon(Icons.shield_outlined, size: 14, color: AlouteColors.warning),
                                    SizedBox(width: 4),
                                    Text('Trưởng nhóm', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AlouteColors.warning)),
                                  ],
                                ),
                              )
                            else
                              ElevatedButton(
                                onPressed: () {
                                  _communityService.toggleJoinCommunity(comm.id);
                                  setState(() {});
                                },
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: comm.isMember ? AlouteColors.bgBody : AlouteColors.accent,
                                  foregroundColor: comm.isMember ? AlouteColors.danger : Colors.white,
                                  side: BorderSide(color: comm.isMember ? AlouteColors.dangerBorder : Colors.transparent),
                                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
                                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                                  minimumSize: Size.zero,
                                ),
                                child: Text(comm.isMember ? 'Rời CLB' : 'Tham gia'),
                              ),
                            const SizedBox(width: 8),
                            IconButton(
                              onPressed: () {
                                Navigator.push(
                                  context,
                                  MaterialPageRoute(
                                    builder: (_) => const CreatePostScreen(),
                                  ),
                                );
                              },
                              icon: const Icon(Icons.edit_note, color: AlouteColors.accent),
                              tooltip: 'Đăng bài vào CLB',
                            ),
                          ],
                        ),
                      ],
                    ),

                    // Title & Slug
                    Text(
                      comm.name,
                      style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: AlouteColors.textPrimary),
                    ),
                    const SizedBox(height: 4),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: AlouteColors.accentSubtle,
                        borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
                        border: Border.all(color: AlouteColors.borderColor),
                      ),
                      child: Text(
                        'c/${comm.slug}',
                        style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AlouteColors.accent),
                      ),
                    ),
                    if (comm.description != null) ...[
                      const SizedBox(height: 10),
                      Text(
                        comm.description!,
                        style: const TextStyle(fontSize: 13, color: AlouteColors.textSecondary, height: 1.4),
                      ),
                    ],
                    const SizedBox(height: 12),

                    // Metrics
                    Row(
                      children: [
                        const Icon(Icons.people_alt_outlined, size: 16, color: AlouteColors.accent),
                        const SizedBox(width: 4),
                        Text('${comm.memberCount}', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AlouteColors.textPrimary)),
                        const Text(' thành viên', style: TextStyle(fontSize: 13, color: AlouteColors.textSecondary)),
                        const SizedBox(width: 16),
                        const Icon(Icons.article_outlined, size: 16, color: AlouteColors.accent),
                        const SizedBox(width: 4),
                        Text('${comm.postCount}', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: AlouteColors.textPrimary)),
                        const Text(' bài viết', style: TextStyle(fontSize: 13, color: AlouteColors.textSecondary)),
                      ],
                    ),
                    const SizedBox(height: 8),
                  ],
                ),
              ),
            ),
            SliverPersistentHeader(
              pinned: true,
              delegate: _TabBarDelegate(
                TabBar(
                  controller: _tabController,
                  labelColor: AlouteColors.accent,
                  unselectedLabelColor: AlouteColors.textSecondary,
                  indicatorColor: AlouteColors.accent,
                  indicatorWeight: 3,
                  labelStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13),
                  tabs: const [
                    Tab(text: 'Bài viết'),
                    Tab(text: 'Thành viên'),
                    Tab(text: 'Quy chế CLB'),
                  ],
                ),
              ),
            ),
          ];
        },
        body: TabBarView(
          controller: _tabController,
          children: [
            // Posts Tab
            _isLoadingPosts
                ? const Center(child: CircularProgressIndicator())
                : _posts.isEmpty
                    ? const Center(
                        child: Text(
                          'Chưa có bài viết nào trong cộng đồng này.',
                          style: TextStyle(color: AlouteColors.textSecondary),
                        ),
                      )
                    : ListView.builder(
                        padding: const EdgeInsets.only(top: 8, bottom: 24),
                        itemCount: _posts.length,
                        itemBuilder: (context, index) {
                          return AloutePostCard(post: _posts[index]);
                        },
                      ),

            // Members Tab
            Padding(
              padding: const EdgeInsets.all(24),
              child: Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.group_outlined, size: 48, color: AlouteColors.textMuted),
                    const SizedBox(height: 12),
                    Text(
                      'Cộng đồng hiện có ${comm.memberCount} thành viên',
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15, color: AlouteColors.textPrimary),
                    ),
                    const SizedBox(height: 6),
                    const Text(
                      'Danh sách thành viên tham gia đang được đồng bộ theo thời gian thực từ hệ thống.',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 13, color: AlouteColors.textSecondary),
                    ),
                  ],
                ),
              ),
            ),

            // Rules Tab
            ListView(
              padding: const EdgeInsets.all(16),
              children: [
                _buildRuleCard(
                  '1. Tôn trọng tinh thần học thuật & đồng môn',
                  'Luôn giao tiếp văn minh, hỗ trợ nhau trong các môn học và đề tài nghiên cứu. Không công kích cá nhân.',
                ),
                _buildRuleCard(
                  '2. Không mua bán tài liệu / dịch vụ thi hộ',
                  'Nghiêm cấm các bài đăng thương mại hóa tài liệu trái phép, rao bán dịch vụ làm đồ án hoặc vi phạm quy chế đào tạo.',
                ),
                _buildRuleCard(
                  '3. Đăng bài đúng chủ đề chuyên môn CLB',
                  'Chia sẻ tài liệu, bài giảng, thắc mắc bài tập hoặc sự kiện sinh hoạt của câu lạc bộ.',
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }



  Widget _buildRuleCard(String title, String desc) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AlouteColors.bgSurface,
        borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
        border: Border.all(color: AlouteColors.borderColor),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AlouteColors.textPrimary)),
          const SizedBox(height: 4),
          Text(desc, style: const TextStyle(fontSize: 13, color: AlouteColors.textSecondary, height: 1.3)),
        ],
      ),
    );
  }
}

class _TabBarDelegate extends SliverPersistentHeaderDelegate {
  final TabBar tabBar;
  _TabBarDelegate(this.tabBar);

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
  bool shouldRebuild(_TabBarDelegate oldDelegate) => false;
}
