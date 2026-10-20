import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../models/post.dart';
import '../../models/topic.dart';
import '../../services/topic_service.dart';
import '../../widgets/aloute_post_card.dart';

class TopicDetailScreen extends StatefulWidget {
  final Topic topic;

  const TopicDetailScreen({super.key, required this.topic});

  @override
  State<TopicDetailScreen> createState() => _TopicDetailScreenState();
}

class _TopicDetailScreenState extends State<TopicDetailScreen> {
  final TopicService _topicService = TopicService();
  List<Post> _posts = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadPosts();
  }

  Future<void> _loadPosts() async {
    setState(() => _isLoading = true);
    final list = await _topicService.getTopicPosts(widget.topic.id);
    if (mounted) {
      setState(() {
        _posts = list;
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final topic = widget.topic;

    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      appBar: AppBar(
        title: Text('#${topic.name}'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: RefreshIndicator(
        onRefresh: _loadPosts,
        color: AlouteColors.accent,
        child: CustomScrollView(
          slivers: [
            // Header Info Card matching topic/detail.html
            SliverToBoxAdapter(
              child: Container(
                margin: const EdgeInsets.all(12),
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
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                            color: AlouteColors.accentSubtle,
                            borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                          ),
                          child: const Icon(Icons.tag, color: AlouteColors.accent, size: 28),
                        ),
                        ElevatedButton.icon(
                          onPressed: () {
                            _topicService.toggleFollowTopic(topic.id);
                            setState(() {});
                          },
                          icon: Icon(
                            topic.isFollowing ? Icons.check : Icons.add,
                            size: 16,
                          ),
                          label: Text(topic.isFollowing ? 'Đang theo dõi' : 'Theo dõi chủ đề'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: topic.isFollowing ? AlouteColors.bgBody : AlouteColors.accent,
                            foregroundColor: topic.isFollowing ? AlouteColors.textPrimary : Colors.white,
                            side: BorderSide(
                              color: topic.isFollowing ? AlouteColors.borderStrong : Colors.transparent,
                            ),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
                            ),
                            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                            minimumSize: Size.zero,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Text(
                      '#${topic.name}',
                      style: const TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w800,
                        color: AlouteColors.textPrimary,
                      ),
                    ),
                    if (topic.description != null) ...[
                      const SizedBox(height: 6),
                      Text(
                        topic.description!,
                        style: const TextStyle(
                          fontSize: 13,
                          color: AlouteColors.textSecondary,
                          height: 1.4,
                        ),
                      ),
                    ],
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        const Icon(Icons.people_outline, size: 16, color: AlouteColors.textMuted),
                        const SizedBox(width: 4),
                        Text(
                          '${topic.followerCount}',
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.bold,
                            color: AlouteColors.textPrimary,
                          ),
                        ),
                        const Text(' người theo dõi', style: TextStyle(fontSize: 13, color: AlouteColors.textSecondary)),
                        const SizedBox(width: 16),
                        const Icon(Icons.article_outlined, size: 16, color: AlouteColors.textMuted),
                        const SizedBox(width: 4),
                        Text(
                          '${topic.postCount}',
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.bold,
                            color: AlouteColors.textPrimary,
                          ),
                        ),
                        const Text(' bài viết', style: TextStyle(fontSize: 13, color: AlouteColors.textSecondary)),
                      ],
                    ),
                  ],
                ),
              ),
            ),

            // Posts Stream
            if (_isLoading)
              const SliverFillRemaining(
                child: Center(child: CircularProgressIndicator()),
              )
            else if (_posts.isEmpty)
              const SliverFillRemaining(
                hasScrollBody: false,
                child: Center(
                  child: Text(
                    'Chưa có bài viết nào với chủ đề này.',
                    style: TextStyle(color: AlouteColors.textSecondary),
                  ),
                ),
              )
            else
              SliverList(
                delegate: SliverChildBuilderDelegate(
                  (context, index) {
                    return AloutePostCard(post: _posts[index]);
                  },
                  childCount: _posts.length,
                ),
              ),
          ],
        ),
      ),
    );
  }
}
