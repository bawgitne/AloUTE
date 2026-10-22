import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../models/user.dart';
import '../../services/auth_service.dart';
import '../../services/post_service.dart';
import '../../services/topic_service.dart';
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
  final TopicService _topicService = TopicService();
  final AuthService _authService = AuthService();

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    _postService.fetchHomeFeed();
    _topicService.fetchTopics();
    _postService.addListener(_onPostsUpdated);
    _topicService.addListener(_onPostsUpdated);
  }

  void _onPostsUpdated() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _tabController.dispose();
    _postService.removeListener(_onPostsUpdated);
    _topicService.removeListener(_onPostsUpdated);
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
          mainAxisSize: MainAxisSize.min,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Image.asset(
                'assets/images/logo.png',
                width: 30,
                height: 30,
                fit: BoxFit.cover,
                errorBuilder: (context, error, stackTrace) => Container(
                  width: 30,
                  height: 30,
                  decoration: BoxDecoration(
                    color: AlouteColors.accent,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(Icons.school, color: Colors.white, size: 18),
                ),
              ),
            ),
            const SizedBox(width: 8),
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
      child: _postService.isLoading && posts.isEmpty
          ? const Center(
              child: Padding(
                padding: EdgeInsets.all(40),
                child: CircularProgressIndicator(),
              ),
            )
          : ListView.builder(
              padding: const EdgeInsets.only(top: 8, bottom: 80),
              itemCount: posts.isEmpty ? 3 : posts.length + 2, // 0: composer, 1: trending chips, 2+: posts / empty card
              itemBuilder: (context, index) {
                if (index == 0) {
                  return _buildComposerCard(user);
                }
                if (index == 1) {
                  return _buildTrendingChipsSection();
                }
                if (posts.isEmpty) {
                  return Container(
                    margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
                    padding: const EdgeInsets.all(24),
                    decoration: BoxDecoration(
                      color: AlouteColors.bgSurface,
                      borderRadius: BorderRadius.circular(AlouteColors.radiusLg),
                      border: Border.all(color: AlouteColors.borderColor),
                      boxShadow: AlouteColors.shadowSm,
                    ),
                    child: const Column(
                      children: [
                        Icon(Icons.dynamic_feed_outlined, size: 48, color: AlouteColors.textMuted),
                        SizedBox(height: 12),
                        Text(
                          'Chưa có bài viết nào',
                          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15, color: AlouteColors.textPrimary),
                        ),
                        SizedBox(height: 6),
                        Text(
                          'Hãy kéo xuống để làm mới bảng tin hoặc bấm vào khung trên để đăng bài viết đầu tiên!',
                          textAlign: TextAlign.center,
                          style: TextStyle(fontSize: 13, color: AlouteColors.textSecondary),
                        ),
                      ],
                    ),
                  );
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
    final topics = _topicService.topics;
    if (topics.isEmpty) return const SizedBox.shrink();

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      height: 38,
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        itemCount: topics.length,
        itemBuilder: (context, index) {
          final t = topics[index];
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
