import 'package:flutter/foundation.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../models/community.dart';
import '../models/post.dart';

class CommunityService extends ChangeNotifier {
  static final CommunityService _instance = CommunityService._internal();
  factory CommunityService() => _instance;
  CommunityService._internal();

  final ApiClient _api = ApiClient();
  final List<Community> _communities = [];
  bool _isLoading = false;

  List<Community> get communities => List.unmodifiable(_communities);
  bool get isLoading => _isLoading;

  Future<void> fetchCommunities() async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await _api.get<List<dynamic>>(ApiConstants.communities);
      if (response.success && response.data != null) {
        _communities.clear();
        for (final item in response.data!) {
          _communities.add(Community.fromJson(item as Map<String, dynamic>));
        }
        _isLoading = false;
        notifyListeners();
        return;
      }
    } catch (e) {
      debugPrint('[CommunityService] Fetch communities failed: $e');
    }

    _isLoading = false;
    notifyListeners();
  }

  Future<bool> toggleJoinCommunity(int communityId) async {
    final index = _communities.indexWhere((c) => c.id == communityId);
    if (index == -1) return false;

    final comm = _communities[index];
    final bool willJoin = !comm.isMember;

    try {
      if (willJoin) {
        await _api.post(ApiConstants.joinCommunity(communityId));
      } else {
        await _api.delete(ApiConstants.leaveCommunity(communityId));
      }
    } catch (_) {}

    comm.isMember = willJoin;
    comm.memberCount += willJoin ? 1 : -1;
    notifyListeners();
    return true;
  }

  Future<List<Post>> getCommunityPosts(int communityId) async {
    try {
      final response = await _api.get<List<dynamic>>(ApiConstants.communityPosts(communityId));
      if (response.success && response.data != null) {
        return response.data!
            .map((e) => Post.fromJson(e as Map<String, dynamic>))
            .toList();
      }
    } catch (e) {
      debugPrint('[CommunityService] Get community posts failed: $e');
    }

    return [];
  }
}
