import 'package:flutter/foundation.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../models/post.dart';

class PostService extends ChangeNotifier {
  static final PostService _instance = PostService._internal();
  factory PostService() => _instance;
  PostService._internal();

  final ApiClient _api = ApiClient();
  final List<Post> _homePosts = [];
  bool _isLoading = false;

  List<Post> get homePosts => _homePosts;
  bool get isLoading => _isLoading;

  Future<void> fetchHomeFeed({bool refresh = false}) async {
    if (_isLoading) return;
    _isLoading = true;
    notifyListeners();

    if (refresh) {
      _homePosts.clear();
    }

    try {
      final response = await _api.get<dynamic>(ApiConstants.homeFeed);

      if (response.success && response.data != null) {
        final rawData = response.data;
        final List<dynamic> list = rawData is List
            ? rawData
            : (rawData is Map && rawData['content'] is List
                ? rawData['content'] as List<dynamic>
                : []);

        final parsed = list
            .map((item) => Post.fromJson(item as Map<String, dynamic>))
            .toList();
        _homePosts.clear();
        _homePosts.addAll(parsed);
        _isLoading = false;
        notifyListeners();
        return;
      }
    } catch (e) {
      debugPrint('[PostService] Fetch home feed failed: $e');
    }

    _isLoading = false;
    notifyListeners();
  }

  Future<bool> createPost(
    String content, {
    List<String> mediaUrls = const [],
    PostPoll? poll,
    String audience = 'PUBLIC',
  }) async {
    try {
      final validAudience = (audience == 'FOLLOWERS') ? 'FOLLOWERS' : 'PUBLIC';
      final body = {
        'content': content.trim(),
        'visibility': validAudience,
        'allowReply': true,
        'allowQuote': true,
      };
      final response = await _api.post<Map<String, dynamic>>(
        ApiConstants.posts,
        body: body,
      );

      if (response.success && response.data != null) {
        final newPost = Post.fromJson(response.data!);
        _homePosts.insert(0, newPost);
        notifyListeners();
        return true;
      }
    } catch (e) {
      debugPrint('[PostService] Create post failed: $e');
    }

    return false;
  }

  Future<List<Map<String, dynamic>>> getReplies(int postId) async {
    try {
      final response = await _api.get<dynamic>(ApiConstants.postReplies(postId));
      if (response.success && response.data != null) {
        final rawData = response.data;
        final List<dynamic> list = rawData is List
            ? rawData
            : (rawData is Map && rawData['content'] is List
                ? rawData['content'] as List<dynamic>
                : (rawData is Map && rawData['data'] is List
                    ? rawData['data'] as List<dynamic>
                    : []));

        return list.map((item) {
          final map = item is Map ? Map<String, dynamic>.from(item) : <String, dynamic>{};
          final author = map['author'] is Map
              ? Map<String, dynamic>.from(map['author'] as Map)
              : <String, dynamic>{};
          return {
            'id': map['id'],
            'authorName': author['displayName']?.toString() ??
                author['username']?.toString() ??
                'Người dùng',
            'username': author['username']?.toString() ?? 'user',
            'faculty': author['faculty']?.toString() ?? '',
            'avatarUrl': author['avatarUrl']?.toString(),
            'content': map['content']?.toString() ?? '',
            'createdAt': map['createdAt']?.toString() ?? 'Vừa xong',
          };
        }).toList();
      }
    } catch (e) {
      debugPrint('[PostService] Get replies failed: $e');
    }
    return [];
  }

  Future<({String? error, Map<String, dynamic>? reply})> createReply(
    int postId,
    String content,
  ) async {
    try {
      final response = await _api.post<dynamic>(
        ApiConstants.postReplies(postId),
        body: {
          'content': content.trim(),
          'visibility': 'PUBLIC',
          'allowReply': true,
          'allowQuote': true,
        },
      );
      if (response.success) {
        Map<String, dynamic>? replyMap;
        if (response.data != null && response.data is Map) {
          final map = Map<String, dynamic>.from(response.data as Map);
          final author = map['author'] is Map
              ? Map<String, dynamic>.from(map['author'] as Map)
              : <String, dynamic>{};
          replyMap = {
            'id': map['id'],
            'authorName': author['displayName']?.toString() ??
                author['username']?.toString() ??
                'Tôi',
            'username': author['username']?.toString() ?? 'user',
            'faculty': author['faculty']?.toString() ?? '',
            'avatarUrl': author['avatarUrl']?.toString(),
            'content': map['content']?.toString() ?? content.trim(),
            'createdAt': 'Vừa xong',
          };
        } else {
          replyMap = {
            'id': DateTime.now().millisecondsSinceEpoch,
            'authorName': 'Tôi',
            'username': 'me',
            'faculty': '',
            'avatarUrl': null,
            'content': content.trim(),
            'createdAt': 'Vừa xong',
          };
        }
        return (error: null, reply: replyMap);
      }
      return (
        error: response.error?.message ?? 'Không thể gửi bình luận. Vui lòng thử lại!',
        reply: null
      );
    } catch (e) {
      debugPrint('[PostService] Create reply failed: $e');
      return (error: 'Lỗi gửi bình luận: $e', reply: null);
    }
  }

  void toggleLike(int postId) {
    final index = _homePosts.indexWhere((p) => p.id == postId);
    if (index != -1) {
      final post = _homePosts[index];
      post.isLiked = !post.isLiked;
      post.likeCount += post.isLiked ? 1 : -1;
      notifyListeners();

      if (post.isLiked) {
        _api.put(ApiConstants.postLikes(postId)).catchError(
          (_) => ApiResponse<dynamic>(success: false),
        );
      } else {
        _api.delete(ApiConstants.postLikes(postId)).catchError(
          (_) => ApiResponse<dynamic>(success: false),
        );
      }
    }
  }

  void toggleBookmark(int postId) {
    final index = _homePosts.indexWhere((p) => p.id == postId);
    if (index != -1) {
      final post = _homePosts[index];
      post.isBookmarked = !post.isBookmarked;
      notifyListeners();

      if (post.isBookmarked) {
        _api.put(ApiConstants.postBookmarks(postId)).catchError(
          (_) => ApiResponse<dynamic>(success: false),
        );
      } else {
        _api.delete(ApiConstants.postBookmarks(postId)).catchError(
          (_) => ApiResponse<dynamic>(success: false),
        );
      }
    }
  }

  List<Post> get savedPosts => _homePosts.where((p) => p.isBookmarked).toList();

  Future<List<Post>> fetchSavedPosts() async {
    try {
      final response = await _api.get<List<dynamic>>(ApiConstants.savedPosts);
      if (response.success && response.data != null) {
        return response.data!
            .map((item) => Post.fromJson(item as Map<String, dynamic>))
            .toList();
      }
    } catch (_) {}

    return savedPosts;
  }

  void votePoll(int postId, int optionId) {
    final postIndex = _homePosts.indexWhere((p) => p.id == postId);
    if (postIndex != -1) {
      final post = _homePosts[postIndex];
      if (post.poll != null && !post.poll!.hasVoted) {
        for (var opt in post.poll!.options) {
          if (opt.id == optionId) {
            opt.voteCount++;
            opt.isVoted = true;
          }
        }
        _homePosts[postIndex] = Post(
          id: post.id,
          author: post.author,
          content: post.content,
          mediaUrls: post.mediaUrls,
          audience: post.audience,
          communityName: post.communityName,
          createdAt: post.createdAt,
          likeCount: post.likeCount,
          replyCount: post.replyCount,
          repostCount: post.repostCount,
          isLiked: post.isLiked,
          isBookmarked: post.isBookmarked,
          poll: PostPoll(
            id: post.poll!.id,
            question: post.poll!.question,
            options: post.poll!.options,
            totalVotes: post.poll!.totalVotes + 1,
            hasVoted: true,
          ),
        );
        notifyListeners();
      }
    }
  }
}
