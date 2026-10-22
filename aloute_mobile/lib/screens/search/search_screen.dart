import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../widgets/aloute_avatar.dart';
import '../community/community_detail_screen.dart';
import '../profile/friends_screen.dart';
import '../topic/topic_detail_screen.dart';
import '../../services/topic_service.dart';
import '../../services/community_service.dart';

class SearchScreen extends StatefulWidget {
  const SearchScreen({super.key});

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final TextEditingController _searchController = TextEditingController();
  final TopicService _topicService = TopicService();
  final CommunityService _communityService = CommunityService();

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    _topicService.fetchTopics();
    _communityService.fetchCommunities();
  }

  @override
  void dispose() {
    _tabController.dispose();
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      appBar: AppBar(
        title: Container(
          height: 42,
          decoration: BoxDecoration(
            color: AlouteColors.bgSurfaceHover,
            borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
            border: Border.all(color: AlouteColors.borderColor),
          ),
          child: TextField(
            controller: _searchController,
            decoration: const InputDecoration(
              hintText: 'Tìm kiếm sinh viên, bài viết, CLB...',
              hintStyle: TextStyle(color: AlouteColors.textMuted, fontSize: 13),
              prefixIcon: Icon(Icons.search, size: 20, color: AlouteColors.textMuted),
              border: InputBorder.none,
              enabledBorder: InputBorder.none,
              focusedBorder: InputBorder.none,
              contentPadding: EdgeInsets.symmetric(vertical: 10),
            ),
          ),
        ),
        bottom: TabBar(
          controller: _tabController,
          labelColor: AlouteColors.accent,
          unselectedLabelColor: AlouteColors.textSecondary,
          indicatorColor: AlouteColors.accent,
          indicatorWeight: 2.5,
          labelStyle: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
          tabs: const [
            Tab(text: 'Chủ đề hot'),
            Tab(text: 'Cộng đồng / CLB'),
            Tab(text: 'Sinh viên'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _buildTrendingTab(),
          _buildCommunitiesTab(),
          _buildPeopleTab(),
        ],
      ),
    );
  }

  Widget _buildTrendingTab() {
    return AnimatedBuilder(
      animation: _topicService,
      builder: (context, _) {
        if (_topicService.isLoading && _topicService.topics.isEmpty) {
          return const Center(child: CircularProgressIndicator());
        }
        if (_topicService.topics.isEmpty) {
          return const Center(
            child: Text(
              'Chưa có chủ đề nào.',
              style: TextStyle(color: AlouteColors.textMuted),
            ),
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: _topicService.topics.length,
          separatorBuilder: (context, index) => const SizedBox(height: 8),
          itemBuilder: (context, index) {
            final item = _topicService.topics[index];
            return InkWell(
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => TopicDetailScreen(topic: item)),
                );
              },
              borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
              child: Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AlouteColors.bgSurface,
                  borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                  border: Border.all(color: AlouteColors.borderColor),
                  boxShadow: AlouteColors.shadowSm,
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '#${item.name}',
                          style: const TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.w700,
                            color: AlouteColors.accent,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          '${item.postCount} bài thảo luận • ${item.followerCount} người theo dõi',
                          style: const TextStyle(fontSize: 12, color: AlouteColors.textMuted),
                        ),
                      ],
                    ),
                    const Icon(Icons.arrow_forward_ios, size: 14, color: AlouteColors.textMuted),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  Widget _buildCommunitiesTab() {
    return AnimatedBuilder(
      animation: _communityService,
      builder: (context, _) {
        if (_communityService.isLoading && _communityService.communities.isEmpty) {
          return const Center(child: CircularProgressIndicator());
        }
        if (_communityService.communities.isEmpty) {
          return const Center(
            child: Text(
              'Chưa có cộng đồng nào.',
              style: TextStyle(color: AlouteColors.textMuted),
            ),
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: _communityService.communities.length,
          separatorBuilder: (context, index) => const SizedBox(height: 8),
          itemBuilder: (context, index) {
            final item = _communityService.communities[index];
            return InkWell(
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => CommunityDetailScreen(community: item)),
                );
              },
              borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
              child: Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: AlouteColors.bgSurface,
                  borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                  border: Border.all(color: AlouteColors.borderColor),
                  boxShadow: AlouteColors.shadowSm,
                ),
                child: Row(
                  children: [
                    AlouteAvatar(name: item.name, size: 44),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            item.name,
                            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: AlouteColors.textPrimary),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            'c/${item.slug} • ${item.memberCount} thành viên',
                            style: const TextStyle(fontSize: 12, color: AlouteColors.textSecondary),
                          ),
                        ],
                      ),
                    ),
                    ElevatedButton(
                      onPressed: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => CommunityDetailScreen(community: item)),
                        );
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AlouteColors.accentSubtle,
                        foregroundColor: AlouteColors.accent,
                        elevation: 0,
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                        minimumSize: const Size(0, 32),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
                      ),
                      child: const Text('Khám phá', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  Widget _buildPeopleTab() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.person_search, size: 48, color: AlouteColors.textMuted),
            const SizedBox(height: 8),
            const Text(
              'Nhập tên hoặc MSSV để tìm bạn bè UTE',
              style: TextStyle(color: AlouteColors.textSecondary, fontSize: 14),
            ),
            const SizedBox(height: 16),
            ElevatedButton.icon(
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const FriendsScreen(initialTab: 'following')),
                );
              },
              icon: const Icon(Icons.people_outline, size: 18),
              label: const Text('Xem danh sách kết nối'),
              style: ElevatedButton.styleFrom(
                backgroundColor: AlouteColors.accent,
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
