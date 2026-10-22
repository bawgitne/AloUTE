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
    } catch (e) {
      debugPrint('[TopicService] Fetch topics failed: $e');
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
    } catch (e) {
      debugPrint('[TopicService] Get topic posts failed: $e');
    }

    return [];
  }
}
