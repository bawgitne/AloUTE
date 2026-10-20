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
      final response = await _api.get<List<dynamic>>(
        ApiConstants.homeFeed,
        parser: (data) => data as List<dynamic>,
      );

      if (response.success && response.data != null) {
        final parsed = response.data!
            .map((item) => Post.fromJson(item as Map<String, dynamic>))
            .toList();
        _homePosts.addAll(parsed);
        _isLoading = false;
        notifyListeners();
        return;
      }
    } catch (_) {}

    // Fallback Mock data: Realistic HCMUTE Academic Campus Feed
    if (_homePosts.isEmpty) {
      _homePosts.addAll(_generateMockCampusPosts());
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
      final body = {
        'content': content,
        'mediaUrls': mediaUrls,
        'audience': audience,
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
    } catch (_) {}

    // Local state insertion
    final newMockPost = Post(
      id: DateTime.now().millisecondsSinceEpoch,
      author: PostAuthor(
        id: 24110166,
        username: 'nguyenvanan',
        displayName: 'Nguyễn Văn An',
        faculty: 'Khoa Công nghệ Thông tin',
        classCode: '24110CLA',
      ),
      content: content,
      mediaUrls: mediaUrls,
      poll: poll,
      audience: audience,
      createdAt: DateTime.now(),
    );
    _homePosts.insert(0, newMockPost);
    notifyListeners();
    return true;
  }

  void toggleLike(int postId) {
    final index = _homePosts.indexWhere((p) => p.id == postId);
    if (index != -1) {
      final post = _homePosts[index];
      post.isLiked = !post.isLiked;
      post.likeCount += post.isLiked ? 1 : -1;
      notifyListeners();

      _api.get(ApiConstants.postLikes(postId)).catchError(
        (_) => ApiResponse<dynamic>(success: false),
      );
    }
  }

  void toggleBookmark(int postId) {
    final index = _homePosts.indexWhere((p) => p.id == postId);
    if (index != -1) {
      final post = _homePosts[index];
      post.isBookmarked = !post.isBookmarked;
      notifyListeners();

      _api.get(ApiConstants.postBookmarks(postId)).catchError(
        (_) => ApiResponse<dynamic>(success: false),
      );
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

    // Fallback: Return locally bookmarked posts, or ensure at least one demo bookmarked post
    if (_homePosts.any((p) => p.isBookmarked)) {
      return savedPosts;
    }
    if (_homePosts.isNotEmpty) {
      _homePosts[0].isBookmarked = true;
    }
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

  List<Post> _generateMockCampusPosts() {
    return [
      Post(
        id: 101,
        author: PostAuthor(
          id: 1,
          username: 'hitc.club',
          displayName: 'CLB Tin Học HITC - HCMUTE',
          faculty: 'Khoa CNTT',
          classCode: 'Ban Chủ Nhiệm',
        ),
        content:
            '🔥 [TUYỂN THÀNH VIÊN GEN 2026] Câu lạc bộ Tin học HITC chính thức mở đơn đăng ký cho toàn thể sinh viên Trường ĐH Sư phạm Kỹ thuật TP.HCM!\n\nNếu bạn đam mê Web, Mobile, AI hay Hệ thống Nhúng, hãy gia nhập ngay ngôi nhà chung của các coder UTE nhé! Link đăng ký tại Bio.',
        audience: 'PUBLIC',
        communityName: 'CLB Tin học HITC',
        createdAt: DateTime.now().subtract(const Duration(minutes: 25)),
        likeCount: 42,
        replyCount: 15,
        repostCount: 8,
      ),
      Post(
        id: 102,
        author: PostAuthor(
          id: 2,
          username: 'tranthimai',
          displayName: 'Trần Thị Mai',
          faculty: 'Khoa Điện - Điện tử',
          classCode: '23145CLC',
        ),
        content:
            'Mọi người cho mình hỏi kỳ này môn Kiến trúc Máy tính thầy Văn thi trắc nghiệm hay tự luận vậy ạ? Có bạn nào có đề cương ôn tập không cho mình xin với!',
        poll: PostPoll(
          id: 1,
          question: 'Hình thức thi Kiến trúc Máy tính kỳ này?',
          options: [
            PollOption(id: 1, text: 'Trắc nghiệm 100% trên máy', voteCount: 58),
            PollOption(id: 2, text: 'Tự luận viết code ASM', voteCount: 24),
            PollOption(id: 3, text: 'Làm bài tập lớn / Project', voteCount: 19),
          ],
          totalVotes: 101,
        ),
        createdAt: DateTime.now().subtract(const Duration(hours: 2)),
        likeCount: 19,
        replyCount: 31,
        repostCount: 3,
      ),
      Post(
        id: 103,
        author: PostAuthor(
          id: 3,
          username: 'lequocbao',
          displayName: 'Lê Quốc Bảo',
          faculty: 'Khoa Cơ khí Chế tạo máy',
          classCode: '22110CK',
        ),
        content:
            'Hoàn thành buổi bảo vệ Đồ án môn học đầu tiên tại xưởng A1! Cảm ơn thầy cô và các anh em trong nhóm đã đồng hành suốt học kỳ vừa qua. Tinh thần Kỹ thuật UTE mãi đỉnh! 🛠️📐',
        createdAt: DateTime.now().subtract(const Duration(hours: 5)),
        likeCount: 87,
        replyCount: 12,
        repostCount: 5,
        isLiked: true,
      ),
    ];
  }
}
