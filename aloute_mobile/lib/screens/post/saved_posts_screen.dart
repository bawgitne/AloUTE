import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../models/post.dart';
import '../../services/post_service.dart';
import '../../widgets/aloute_post_card.dart';

class SavedPostsScreen extends StatefulWidget {
  const SavedPostsScreen({super.key});

  @override
  State<SavedPostsScreen> createState() => _SavedPostsScreenState();
}

class _SavedPostsScreenState extends State<SavedPostsScreen> {
  final PostService _postService = PostService();
  List<Post> _savedPosts = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadSaved();
  }

  Future<void> _loadSaved() async {
    setState(() => _isLoading = true);
    final posts = await _postService.fetchSavedPosts();
    if (mounted) {
      setState(() {
        _savedPosts = posts;
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      appBar: AppBar(
        title: const Text('Bài viết đã lưu'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: RefreshIndicator(
        onRefresh: _loadSaved,
        color: AlouteColors.accent,
        child: _isLoading
            ? const Center(child: CircularProgressIndicator())
            : CustomScrollView(
                slivers: [
                  // Header Info Card matching saved.html
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
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Container(
                            padding: const EdgeInsets.all(10),
                            decoration: BoxDecoration(
                              color: AlouteColors.warningBg,
                              borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                              border: Border.all(color: AlouteColors.warningBorder),
                            ),
                            child: const Icon(
                              Icons.bookmark,
                              color: AlouteColors.warning,
                              size: 24,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    const Text(
                                      'Bộ sưu tập của bạn',
                                      style: TextStyle(
                                        fontSize: 16,
                                        fontWeight: FontWeight.w700,
                                        color: AlouteColors.textPrimary,
                                      ),
                                    ),
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                                      decoration: BoxDecoration(
                                        color: AlouteColors.bgBody,
                                        borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
                                        border: Border.all(color: AlouteColors.borderColor),
                                      ),
                                      child: Text(
                                        '${_savedPosts.length} bài viết',
                                        style: const TextStyle(
                                          fontSize: 12,
                                          fontWeight: FontWeight.w600,
                                          color: AlouteColors.textSecondary,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 4),
                                const Text(
                                  'Danh sách các bài viết và tài liệu học thuật bạn đã lưu lại để xem sau.',
                                  style: TextStyle(
                                    fontSize: 13,
                                    color: AlouteColors.textSecondary,
                                    height: 1.3,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),

                  // Empty State if no saved posts
                  if (_savedPosts.isEmpty)
                    SliverFillRemaining(
                      hasScrollBody: false,
                      child: Center(
                        child: Padding(
                          padding: const EdgeInsets.all(32),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Container(
                                width: 72,
                                height: 72,
                                decoration: BoxDecoration(
                                  color: AlouteColors.warningBg,
                                  shape: BoxShape.circle,
                                  border: Border.all(color: AlouteColors.warningBorder),
                                ),
                                child: const Icon(
                                  Icons.bookmark_border,
                                  size: 36,
                                  color: AlouteColors.warning,
                                ),
                              ),
                              const SizedBox(height: 16),
                              const Text(
                                'Chưa có bài viết nào được lưu',
                                style: TextStyle(
                                  fontSize: 16,
                                  fontWeight: FontWeight.w700,
                                  color: AlouteColors.textPrimary,
                                ),
                              ),
                              const SizedBox(height: 8),
                              const Text(
                                'Bấm vào biểu tượng bookmark trên bài viết bất kỳ trên Bảng tin để lưu lại tại đây!',
                                textAlign: TextAlign.center,
                                style: TextStyle(
                                  fontSize: 13,
                                  color: AlouteColors.textSecondary,
                                ),
                              ),
                              const SizedBox(height: 20),
                              ElevatedButton.icon(
                                onPressed: () => Navigator.pop(context),
                                icon: const Icon(Icons.home_outlined, size: 18),
                                label: const Text('Về Bảng tin'),
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: AlouteColors.accent,
                                  foregroundColor: Colors.white,
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
                                  ),
                                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    )
                  else
                    // Posts List
                    SliverList(
                      delegate: SliverChildBuilderDelegate(
                        (context, index) {
                          final post = _savedPosts[index];
                          return AloutePostCard(
                            post: post,
                            onLike: () {
                              _postService.toggleLike(post.id);
                              setState(() {});
                            },
                            onBookmark: () {
                              _postService.toggleBookmark(post.id);
                              setState(() {
                                _savedPosts.removeWhere((p) => p.id == post.id);
                              });
                            },
                            onPollVote: (optId) {
                              _postService.votePoll(post.id, optId);
                              setState(() {});
                            },
                          );
                        },
                        childCount: _savedPosts.length,
                      ),
                    ),
                ],
              ),
      ),
    );
  }
}
