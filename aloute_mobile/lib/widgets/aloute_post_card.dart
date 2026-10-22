import 'package:flutter/material.dart';
import '../core/theme/aloute_theme.dart';
import '../models/post.dart';
import '../services/post_service.dart';
import 'aloute_avatar.dart';

class AloutePostCard extends StatelessWidget {
  final Post post;
  final VoidCallback? onTap;
  final VoidCallback? onReplyTap;
  final VoidCallback? onLike;
  final VoidCallback? onBookmark;
  final void Function(int optionId)? onPollVote;

  const AloutePostCard({
    super.key,
    required this.post,
    this.onTap,
    this.onReplyTap,
    this.onLike,
    this.onBookmark,
    this.onPollVote,
  });

  String _formatTimeAgo(DateTime dateTime) {
    final diff = DateTime.now().difference(dateTime);
    if (diff.inMinutes < 1) return 'Vừa xong';
    if (diff.inMinutes < 60) return '${diff.inMinutes}p trước';
    if (diff.inHours < 24) return '${diff.inHours}h trước';
    return '${diff.inDays}d trước';
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      decoration: BoxDecoration(
        color: AlouteColors.bgSurface,
        borderRadius: BorderRadius.circular(AlouteColors.radiusLg),
        border: Border.all(color: AlouteColors.borderColor, width: 1),
        boxShadow: AlouteColors.shadowSm,
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(AlouteColors.radiusLg),
        child: InkWell(
          borderRadius: BorderRadius.circular(AlouteColors.radiusLg),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildHeader(context),
                const SizedBox(height: 12),
                _buildContent(),
                if (post.poll != null) ...[
                  const SizedBox(height: 12),
                  _buildPoll(context, post.poll!),
                ],
                if (post.mediaUrls.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  _buildMediaList(),
                ],
                const SizedBox(height: 16),
                const Divider(height: 1, color: AlouteColors.borderSubtle),
                const SizedBox(height: 10),
                _buildInteractionBar(context),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        AlouteAvatar(
          imageUrl: post.author.avatarUrl,
          name: post.author.displayName,
          size: 44,
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
                      post.author.displayName,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        color: AlouteColors.textPrimary,
                        letterSpacing: -0.2,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  if (post.author.faculty.isNotEmpty) ...[
                    const SizedBox(width: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: AlouteColors.accentSubtle,
                        borderRadius: BorderRadius.circular(AlouteColors.radiusSm),
                      ),
                      child: Text(
                        post.author.faculty,
                        style: const TextStyle(
                          fontSize: 10,
                          fontWeight: FontWeight.w600,
                          color: AlouteColors.accent,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
              const SizedBox(height: 2),
              Row(
                children: [
                  Text(
                    '@${post.author.username}',
                    style: const TextStyle(
                      fontSize: 13,
                      color: AlouteColors.textSecondary,
                    ),
                  ),
                  const Text(' • ', style: TextStyle(color: AlouteColors.textMuted)),
                  Text(
                    _formatTimeAgo(post.createdAt),
                    style: const TextStyle(
                      fontSize: 12,
                      color: AlouteColors.textMuted,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        IconButton(
          icon: const Icon(Icons.more_horiz, color: AlouteColors.textMuted, size: 20),
          onPressed: () {},
          padding: EdgeInsets.zero,
          constraints: const BoxConstraints(),
        ),
      ],
    );
  }

  Widget _buildContent() {
    return Text(
      post.content,
      style: const TextStyle(
        fontSize: 15,
        height: 1.5,
        color: AlouteColors.textPrimary,
        letterSpacing: -0.1,
      ),
    );
  }

  Widget _buildPoll(BuildContext context, PostPoll poll) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AlouteColors.bgBody,
        borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
        border: Border.all(color: AlouteColors.borderColor),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.poll_outlined, size: 18, color: AlouteColors.accent),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  poll.question,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: AlouteColors.textPrimary,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          ...poll.options.map((option) {
            final percentage = poll.totalVotes > 0
                ? (option.voteCount / poll.totalVotes)
                : 0.0;
            final isSelected = option.isVoted;

            return Padding(
              padding: const EdgeInsets.only(bottom: 8.0),
              child: InkWell(
                onTap: poll.hasVoted
                    ? null
                    : () {
                        if (onPollVote != null) {
                          onPollVote!(option.id);
                        } else {
                          PostService().votePoll(post.id, option.id);
                        }
                      },
                borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                child: Stack(
                  children: [
                    Container(
                      height: 40,
                      decoration: BoxDecoration(
                        color: AlouteColors.bgSurface,
                        borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                        border: Border.all(
                          color: isSelected ? AlouteColors.accent : AlouteColors.borderColor,
                          width: isSelected ? 1.5 : 1.0,
                        ),
                      ),
                    ),
                    if (poll.hasVoted)
                      FractionallySizedBox(
                        widthFactor: percentage,
                        child: Container(
                          height: 40,
                          decoration: BoxDecoration(
                            color: isSelected
                                ? AlouteColors.accent.withValues(alpha: 0.15)
                                : AlouteColors.bgSurfaceHover,
                            borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                          ),
                        ),
                      ),
                    Container(
                      height: 40,
                      padding: const EdgeInsets.symmetric(horizontal: 12),
                      alignment: Alignment.centerLeft,
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(
                            child: Text(
                              option.text,
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: isSelected ? FontWeight.w600 : FontWeight.w500,
                                color: isSelected ? AlouteColors.accent : AlouteColors.textPrimary,
                              ),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          if (poll.hasVoted)
                            Text(
                              '${(percentage * 100).toInt()}%',
                              style: TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                                color: isSelected ? AlouteColors.accent : AlouteColors.textSecondary,
                              ),
                            ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            );
          }),
          Text(
            '${poll.totalVotes} lượt bình chọn',
            style: const TextStyle(fontSize: 12, color: AlouteColors.textMuted),
          ),
        ],
      ),
    );
  }

  Widget _buildMediaList() {
    return ClipRRect(
      borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
      child: Container(
        constraints: const BoxConstraints(maxHeight: 250),
        decoration: BoxDecoration(
          border: Border.all(color: AlouteColors.borderColor),
          borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
        ),
        child: Image.network(
          post.mediaUrls.first,
          fit: BoxFit.cover,
          width: double.infinity,
          errorBuilder: (context, error, stackTrace) => const SizedBox.shrink(),
        ),
      ),
    );
  }

  Widget _buildInteractionBar(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        // Like
        _buildActionButton(
          icon: post.isLiked ? Icons.favorite : Icons.favorite_border,
          color: post.isLiked ? AlouteColors.danger : AlouteColors.textSecondary,
          label: post.likeCount > 0 ? '${post.likeCount}' : 'Thích',
          onTap: onLike ?? () => PostService().toggleLike(post.id),
        ),
        // Comment / Reply
        _buildActionButton(
          icon: Icons.chat_bubble_outline,
          color: AlouteColors.textSecondary,
          label: post.replyCount > 0 ? '${post.replyCount}' : 'Bình luận',
          onTap: onReplyTap ?? onTap ?? () {},
        ),
        // Repost
        _buildActionButton(
          icon: Icons.repeat,
          color: AlouteColors.textSecondary,
          label: post.repostCount > 0 ? '${post.repostCount}' : 'Chia sẻ',
          onTap: () {},
        ),
        // Bookmark
        _buildActionButton(
          icon: post.isBookmarked ? Icons.bookmark : Icons.bookmark_border,
          color: post.isBookmarked ? AlouteColors.accent : AlouteColors.textSecondary,
          label: '',
          onTap: onBookmark ?? () => PostService().toggleBookmark(post.id),
        ),
      ],
    );
  }

  Widget _buildActionButton({
    required IconData icon,
    required Color color,
    required String label,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(AlouteColors.radiusSm),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        child: Row(
          children: [
            Icon(icon, size: 18, color: color),
            if (label.isNotEmpty) ...[
              const SizedBox(width: 6),
              Text(
                label,
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w500,
                  color: color,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
