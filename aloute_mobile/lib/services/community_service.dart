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
    } catch (_) {}

    // Fallback Mock Communities for Campus Life
    if (_communities.isEmpty) {
      _communities.addAll([
        Community(
          id: 1,
          name: 'CLB Tin học & Lập trình (HITC)',
          slug: 'hitc-hcmute',
          description: 'Cộng đồng học thuật dành cho sinh viên đam mê lập trình, thuật toán và nghiên cứu phần mềm HCMUTE.',
          avatarUrl: 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=150',
          coverUrl: 'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800',
          memberCount: 1420,
          postCount: 350,
          isMember: true,
          currentUserRole: 'MEMBER',
        ),
        Community(
          id: 2,
          name: 'CLB Tiếng Anh Sư phạm Kỹ thuật (ESC)',
          slug: 'esc-ute',
          description: 'Môi trường rèn luyện giao tiếp tiếng Anh, IELTS, TOEIC và kết nối sinh viên quốc tế.',
          avatarUrl: 'https://images.unsplash.com/photo-1546410531-bb4caa6b424d?w=150',
          coverUrl: 'https://images.unsplash.com/photo-1434030216411-0b793f4b4173?w=800',
          memberCount: 2310,
          postCount: 512,
          isMember: false,
        ),
        Community(
          id: 3,
          name: 'Cộng đồng Sinh viên K24 HCMUTE',
          slug: 'k24-hcmute',
          description: 'Nơi trao đổi tài liệu tân sinh viên, thông báo học vụ, tìm bạn cùng phòng và thảo luận môn học chung.',
          avatarUrl: 'https://images.unsplash.com/photo-1523240795612-9a054b0db644?w=150',
          coverUrl: 'https://images.unsplash.com/photo-1541339907198-e08756dedf3f?w=800',
          memberCount: 4890,
          postCount: 1240,
          isMember: true,
          isOwner: true,
          currentUserRole: 'OWNER',
        ),
        Community(
          id: 4,
          name: 'CLB Robot & Trí tuệ Nhân tạo (RAI)',
          slug: 'rai-club',
          description: 'Nghiên cứu chế tạo Robot Robocon, IoT, thị giác máy tính và AI ứng dụng công nghiệp.',
          avatarUrl: 'https://images.unsplash.com/photo-1485827404703-89b55fcc595e?w=150',
          coverUrl: 'https://images.unsplash.com/photo-1507413245164-6160d8298b31?w=800',
          memberCount: 890,
          postCount: 180,
          isMember: false,
        ),
      ]);
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
        await _api.delete(ApiConstants.joinCommunity(communityId));
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
    } catch (_) {}

    // Mock community posts
    return [
      Post(
        id: 901,
        author: PostAuthor(
          id: 101,
          username: 'hitc_admin',
          displayName: 'Ban Chuyên Môn HITC',
          avatarUrl: 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=150',
          faculty: 'Khoa CNTT',
        ),
        content: 'Chào mừng các bạn đến với buổi Workshop: "Xây dựng ứng dụng Flutter & Spring Boot thực chiến 2026". Địa điểm: Hội trường A, 14:00 Thứ Bảy tuần này!',
        mediaUrls: ['https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?w=800'],
        communityName: 'CLB Tin học & Lập trình (HITC)',
        createdAt: DateTime.now().subtract(const Duration(hours: 3)),
        likeCount: 45,
        replyCount: 12,
        repostCount: 8,
      ),
      Post(
        id: 902,
        author: PostAuthor(
          id: 102,
          username: 'tranminhkhoi',
          displayName: 'Trần Minh Khôi',
          avatarUrl: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150',
          faculty: 'Khoa CNTT',
        ),
        content: 'Mọi người trong CLB cho mình hỏi tài liệu ôn thi Đồ án chuyên ngành Kỹ thuật phần mềm kỳ này ở đâu vậy ạ?',
        communityName: 'CLB Tin học & Lập trình (HITC)',
        createdAt: DateTime.now().subtract(const Duration(hours: 8)),
        likeCount: 15,
        replyCount: 5,
        repostCount: 1,
      ),
    ];
  }
}
