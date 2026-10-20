import 'package:flutter/foundation.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../models/topic.dart';
import '../models/post.dart';

class TopicService extends ChangeNotifier {
  static final TopicService _instance = TopicService._internal();
  factory TopicService() => _instance;
  TopicService._internal();

  final ApiClient _api = ApiClient();
  final List<Topic> _topics = [];
  bool _isLoading = false;

  List<Topic> get topics => List.unmodifiable(_topics);
  bool get isLoading => _isLoading;

  Future<void> fetchTopics() async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await _api.get<List<dynamic>>(ApiConstants.topics);
      if (response.success && response.data != null) {
        _topics.clear();
        for (final item in response.data!) {
          _topics.add(Topic.fromJson(item as Map<String, dynamic>));
        }
        _isLoading = false;
        notifyListeners();
        return;
      }
    } catch (_) {}

    // Mock Trending Topics
    if (_topics.isEmpty) {
      _topics.addAll([
        Topic(
          id: 1,
          name: 'DoAnTotNghiep2026',
          slug: 'do-an-tot-nghiep-2026',
          description: 'Thảo luận đề tài tốt nghiệp, chia sẻ kinh nghiệm bảo vệ đồ án trước hội đồng các khoa.',
          followerCount: 3420,
          postCount: 520,
          isFollowing: true,
        ),
        Topic(
          id: 2,
          name: 'CongNghePhanMem',
          slug: 'cong-nghe-phan-mem',
          description: 'Hỏi đáp các môn Kiến trúc phần mềm, Thiết kế hướng đối tượng, Kiểm thử và CI/CD.',
          followerCount: 2850,
          postCount: 412,
          isFollowing: false,
        ),
        Topic(
          id: 3,
          name: 'ThucTapDoanhNghiep',
          slug: 'thuc-tap-doanh-nghiep',
          description: 'Cơ hội thực tập IT, chia sẻ phỏng vấn CV và review môi trường làm việc các công ty công nghệ.',
          followerCount: 4190,
          postCount: 780,
          isFollowing: true,
        ),
        Topic(
          id: 4,
          name: 'HocBongKhuyenKhich',
          slug: 'hoc-bong-khuyen-khich',
          description: 'Quy chế xét học bổng khuyến khích học tập, điểm rèn luyện và chính sách sinh viên HCMUTE.',
          followerCount: 1980,
          postCount: 230,
          isFollowing: false,
        ),
      ]);
    }

    _isLoading = false;
    notifyListeners();
  }

  Future<void> toggleFollowTopic(int topicId) async {
    final index = _topics.indexWhere((t) => t.id == topicId);
    if (index == -1) return;

    final topic = _topics[index];
    final bool willFollow = !topic.isFollowing;
    topic.isFollowing = willFollow;
    topic.followerCount += willFollow ? 1 : -1;
    notifyListeners();

    try {
      if (willFollow) {
        await _api.post(ApiConstants.followTopic(topicId));
      } else {
        await _api.delete(ApiConstants.followTopic(topicId));
      }
    } catch (_) {}
  }

  Future<List<Post>> getTopicPosts(int topicId) async {
    try {
      final response = await _api.get<List<dynamic>>(ApiConstants.topicPosts(topicId));
      if (response.success && response.data != null) {
        return response.data!
            .map((e) => Post.fromJson(e as Map<String, dynamic>))
            .toList();
      }
    } catch (_) {}

    // Mock Topic Posts
    return [
      Post(
        id: 701,
        author: PostAuthor(
          id: 201,
          username: 'nguyenthithu',
          displayName: 'Nguyễn Thị Thu',
          avatarUrl: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150',
          faculty: 'Khoa CNTT',
        ),
        content: 'Tổng hợp checklist hồ sơ đăng ký đề tài Đồ án tốt nghiệp đợt 1 năm học 2026 cho các bạn khóa K22-K23 nhé! Mọi người chú ý hạn chót là 30/11.',
        createdAt: DateTime.now().subtract(const Duration(hours: 4)),
        likeCount: 68,
        replyCount: 18,
        repostCount: 22,
      ),
    ];
  }
}
