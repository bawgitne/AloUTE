import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../models/topic.dart';
import '../../models/user.dart';
import '../../services/auth_service.dart';
import '../../services/post_service.dart';
import '../../widgets/aloute_avatar.dart';
import '../../widgets/aloute_post_card.dart';
import '../messaging/conversation_list_screen.dart';
import '../post/create_post_screen.dart';
import '../post/post_detail_screen.dart';
import '../topic/topic_detail_screen.dart';

class HomeFeedScreen extends StatefulWidget {
  const HomeFeedScreen({super.key});

  @override
  State<HomeFeedScreen> createState() => _HomeFeedScreenState();
}

class _HomeFeedScreenState extends State<HomeFeedScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final PostService _postService = PostService();
  final AuthService _authService = AuthService();

  final List<Topic> _trendingTopics = [
    Topic(
      id: 1,
      name: 'DoAnTotNghiep2026',
      slug: 'do-an-tot-nghiep-2026',
      description: 'Thảo luận đề tài tốt nghiệp, chia sẻ kinh nghiệm bảo vệ đồ án.',
      followerCount: 3420,
      postCount: 520,
    ),
    Topic(
      id: 2,
      name: 'CongNghePhanMem',
      slug: 'cong-nghe-phan-mem',
      description: 'Hỏi đáp các môn Kiến trúc phần mềm và Kiểm thử.',
      followerCount: 2850,
      postCount: 412,
    ),
    Topic(
      id: 3,
      name: 'ThucTapDoanhNghiep',
      slug: 'thuc-tap-doanh-nghiep',
      description: 'Cơ hội thực tập IT và phỏng vấn doanh nghiệp.',
      followerCount: 4190,
      postCount: 780,
    ),
    Topic(
      id: 4,
      name: 'CLB_HITC',
      slug: 'clb-hitc',
      description: 'Hoạt động CLB Tin học HITC HCMUTE.',
      followerCount: 1540,
      postCount: 310,
    ),
  ];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    _postService.fetchHomeFeed();
    _postService.addListener(_onPostsUpdated);
  }

  void _onPostsUpdated() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _tabController.dispose();
    _postService.removeListener(_onPostsUpdated);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final currentUser = _authService.currentUser;

    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      appBar: AppBar(
        leading: Builder(
          builder: (context) => IconButton(
            icon: const Icon(Icons.menu, color: AlouteColors.textPrimary),
            tooltip: 'Menu hệ thống',
            onPressed: () {
              final rootScaffold = context.findRootAncestorStateOfType<ScaffoldState>();
              if (rootScaffold != null && rootScaffold.hasDrawer) {
                rootScaffold.openDrawer();
              } else {
                Scaffold.of(context).openDrawer();
              }
            },
          ),
        ),
        title: Row(
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Image.asset(
                'assets/images/logo.png',
                width: 30,
                height: 30,
                fit: BoxFit.cover,
              ),
            ),
            const SizedBox(width: 8),
            Image.asset(
              'assets/images/logo_text.png',
              height: 18,
              fit: BoxFit.contain,
            ),
            const SizedBox(width: 8),
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
        actions: [
          // Chat / Messaging shortcut button with unread count
          Stack(
            children: [
              IconButton(
                icon: const Icon(Icons.chat_bubble_outline, size: 22, color: AlouteColors.textPrimary),
                tooltip: 'Tin nhắn riêng',
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const ConversationListScreen()),
                  );
                },
              ),
              Positioned(
                right: 8,
                top: 8,
                child: Container(
                  width: 8,
                  height: 8,
                  decoration: const BoxDecoration(
                    color: AlouteColors.accent,
                    shape: BoxShape.circle,
                  ),
                ),
              ),
            ],
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          labelColor: AlouteColors.accent,
          unselectedLabelColor: AlouteColors.textSecondary,
          indicatorColor: AlouteColors.accent,
          indicatorWeight: 2.5,
          labelStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13),
          unselectedLabelStyle: const TextStyle(fontWeight: FontWeight.w500, fontSize: 13),
          tabs: const [
            Tab(text: 'Dành cho bạn'),
            Tab(text: 'Đang theo dõi'),
            Tab(text: 'Khám phá'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _buildFeedList(currentUser),
          _buildFeedList(currentUser, isFollowing: true),
          _buildFeedList(currentUser, isExplore: true),
        ],
      ),
    );
  }

  Widget _buildFeedList(User? user, {bool isFollowing = false, bool isExplore = false}) {
    final posts = _postService.homePosts;

    return RefreshIndicator(
      onRefresh: () => _postService.fetchHomeFeed(refresh: true),
      color: AlouteColors.accent,
      child: ListView.builder(
        padding: const EdgeInsets.only(top: 8, bottom: 80),
        itemCount: posts.length + 2, // 0: composer, 1: trending chips, 2+: posts
        itemBuilder: (context, index) {
          if (index == 0) {
            return _buildComposerCard(user);
          }
          if (index == 1) {
            return _buildTrendingChipsSection();
          }

          final post = posts[index - 2];
          return AloutePostCard(
            post: post,
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) => PostDetailScreen(post: post),
                ),
              );
            },
            onReplyTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) => PostDetailScreen(post: post, focusReply: true),
                ),
              );
            },
            onLike: () {
              _postService.toggleLike(post.id);
            },
            onBookmark: () {
              _postService.toggleBookmark(post.id);
            },
            onPollVote: (optId) {
              _postService.votePoll(post.id, optId);
            },
          );
        },
      ),
    );
  }

  Widget _buildComposerCard(User? user) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AlouteColors.bgSurface,
        borderRadius: BorderRadius.circular(AlouteColors.radiusLg),
        border: Border.all(color: AlouteColors.borderColor),
        boxShadow: AlouteColors.shadowSm,
      ),
      child: InkWell(
        onTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => const CreatePostScreen(),
            ),
          );
        },
        child: Row(
          children: [
            AlouteAvatar(
              name: user?.displayName ?? 'Tôi',
              imageUrl: user?.avatarUrl,
              size: 40,
            ),
            const SizedBox(width: 12),
            const Expanded(
              child: Text(
                'Bạn đang có thắc mắc hay chia sẻ gì về việc học hôm nay?',
                style: TextStyle(
                  color: AlouteColors.textMuted,
                  fontSize: 13,
                  height: 1.3,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            const SizedBox(width: 8),
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: AlouteColors.accentSubtle,
                borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
              ),
              child: const Icon(Icons.edit_note, color: AlouteColors.accent, size: 20),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTrendingChipsSection() {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      height: 38,
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        itemCount: _trendingTopics.length,
        itemBuilder: (context, index) {
          final t = _trendingTopics[index];
          return Container(
            margin: const EdgeInsets.only(right: 8),
            child: ActionChip(
              backgroundColor: AlouteColors.bgSurface,
              side: const BorderSide(color: AlouteColors.borderColor),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 2),
              avatar: const Icon(Icons.tag, size: 14, color: AlouteColors.accent),
              label: Text(
                t.name,
                style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AlouteColors.textPrimary),
              ),
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => TopicDetailScreen(topic: t)),
                );
              },
            ),
          );
        },
      ),
    );
  }
}
