import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../services/community_service.dart';
import 'community_detail_screen.dart';

class CommunityListScreen extends StatefulWidget {
  const CommunityListScreen({super.key});

  @override
  State<CommunityListScreen> createState() => _CommunityListScreenState();
}

class _CommunityListScreenState extends State<CommunityListScreen> {
  final CommunityService _communityService = CommunityService();
  String _searchQuery = '';

  @override
  void initState() {
    super.initState();
    _communityService.fetchCommunities();
  }

  void _showCreateCommunityDialog() {
    final nameCtrl = TextEditingController();
    final slugCtrl = TextEditingController();
    final descCtrl = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AlouteColors.bgSurface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusLg)),
        title: const Row(
          children: [
            Icon(Icons.group_add, color: AlouteColors.accent),
            SizedBox(width: 8),
            Text('Tạo cộng đồng mới', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          ],
        ),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text('Tên cộng đồng / Câu lạc bộ', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
              const SizedBox(height: 4),
              TextField(
                controller: nameCtrl,
                decoration: const InputDecoration(
                  hintText: 'Ví dụ: CLB Sáng tạo Trẻ UTE',
                  contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                ),
              ),
              const SizedBox(height: 12),
              const Text('Đường dẫn ngắn (Slug)', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
              const SizedBox(height: 4),
              TextField(
                controller: slugCtrl,
                decoration: const InputDecoration(
                  hintText: 'sang-tao-tre',
                  prefixText: 'c/ ',
                  contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                ),
              ),
              const SizedBox(height: 12),
              const Text('Mô tả mục tiêu sinh hoạt', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
              const SizedBox(height: 4),
              TextField(
                controller: descCtrl,
                maxLines: 3,
                decoration: const InputDecoration(
                  hintText: 'Giới thiệu ngắn về cộng đồng...',
                  contentPadding: EdgeInsets.all(12),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Hủy', style: TextStyle(color: AlouteColors.textSecondary)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AlouteColors.accent,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
            ),
            onPressed: () {
              Navigator.pop(ctx);
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text('Yêu cầu tạo cộng đồng đã được gửi phê duyệt!'),
                  backgroundColor: AlouteColors.success,
                ),
              );
            },
            child: const Text('Tạo ngay'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      appBar: AppBar(
        title: const Text('Cộng đồng & Câu lạc bộ'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.pop(context),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.add_circle_outline, color: AlouteColors.accent),
            tooltip: 'Tạo cộng đồng',
            onPressed: _showCreateCommunityDialog,
          ),
        ],
      ),
      body: ListenableBuilder(
        listenable: _communityService,
        builder: (context, _) {
          if (_communityService.isLoading) {
            return const Center(child: CircularProgressIndicator());
          }

          final allCommunities = _communityService.communities;
          final filtered = allCommunities.where((c) {
            final q = _searchQuery.toLowerCase();
            return c.name.toLowerCase().contains(q) || c.slug.toLowerCase().contains(q);
          }).toList();

          return RefreshIndicator(
            onRefresh: _communityService.fetchCommunities,
            color: AlouteColors.accent,
            child: CustomScrollView(
              slivers: [
                // Top Header Card matching community/index.html
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
                          children: [
                            Container(
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                color: AlouteColors.accentSubtle,
                                borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                              ),
                              child: const Icon(Icons.people_alt, color: AlouteColors.accent, size: 24),
                            ),
                            const SizedBox(width: 12),
                            const Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Không gian học thuật & CLB',
                                    style: TextStyle(
                                      fontSize: 16,
                                      fontWeight: FontWeight.w700,
                                      color: AlouteColors.textPrimary,
                                    ),
                                  ),
                                  SizedBox(height: 2),
                                  Text(
                                    'Kết nối sinh hoạt chuyên đề sinh viên HCMUTE',
                                    style: TextStyle(fontSize: 12, color: AlouteColors.textSecondary),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 14),

                        // Search Bar
                        TextField(
                          onChanged: (val) => setState(() => _searchQuery = val),
                          decoration: InputDecoration(
                            hintText: 'Tìm kiếm câu lạc bộ, cộng đồng...',
                            prefixIcon: const Icon(Icons.search, size: 20, color: AlouteColors.textMuted),
                            filled: true,
                            fillColor: AlouteColors.bgBody,
                            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
                              borderSide: BorderSide.none,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),

                // Communities Grid / List
                if (filtered.isEmpty)
                  const SliverFillRemaining(
                    hasScrollBody: false,
                    child: Center(
                      child: Text(
                        'Không tìm thấy cộng đồng phù hợp.',
                        style: TextStyle(color: AlouteColors.textSecondary),
                      ),
                    ),
                  )
                else
                  SliverPadding(
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                    sliver: SliverList(
                      delegate: SliverChildBuilderDelegate(
                        (context, index) {
                          final comm = filtered[index];
                          return Container(
                            margin: const EdgeInsets.only(bottom: 12),
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
                                InkWell(
                                  onTap: () {
                                    Navigator.push(
                                      context,
                                      MaterialPageRoute(
                                        builder: (_) => CommunityDetailScreen(community: comm),
                                      ),
                                    );
                                  },
                                  child: Row(
                                    crossAxisAlignment: CrossAxisAlignment.center,
                                    children: [
                                      ClipRRect(
                                        borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                                        child: Image.network(
                                          comm.avatarUrl ?? 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=150',
                                          width: 48,
                                          height: 48,
                                          fit: BoxFit.cover,
                                          errorBuilder: (context, error, stackTrace) => Container(
                                            width: 48,
                                            height: 48,
                                            color: AlouteColors.accentSubtle,
                                            child: const Icon(Icons.group, color: AlouteColors.accent),
                                          ),
                                        ),
                                      ),
                                      const SizedBox(width: 12),
                                      Expanded(
                                        child: Column(
                                          crossAxisAlignment: CrossAxisAlignment.start,
                                          children: [
                                            Text(
                                              comm.name,
                                              style: const TextStyle(
                                                fontSize: 15,
                                                fontWeight: FontWeight.w700,
                                                color: AlouteColors.textPrimary,
                                              ),
                                              maxLines: 1,
                                              overflow: TextOverflow.ellipsis,
                                            ),
                                            const SizedBox(height: 2),
                                            Text(
                                              'c/${comm.slug}',
                                              style: const TextStyle(
                                                fontSize: 13,
                                                fontWeight: FontWeight.w600,
                                                color: AlouteColors.accent,
                                              ),
                                            ),
                                          ],
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                if (comm.description != null) ...[
                                  const SizedBox(height: 8),
                                  Text(
                                    comm.description!,
                                    style: const TextStyle(fontSize: 13, color: AlouteColors.textSecondary, height: 1.3),
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ],
                                const SizedBox(height: 12),
                                const Divider(color: AlouteColors.borderSubtle, height: 1),
                                const SizedBox(height: 10),
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    Row(
                                      children: [
                                        const Icon(Icons.people_outline, size: 16, color: AlouteColors.textMuted),
                                        const SizedBox(width: 4),
                                        Text(
                                          '${comm.memberCount} thành viên',
                                          style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AlouteColors.textPrimary),
                                        ),
                                      ],
                                    ),
                                    Row(
                                      children: [
                                        ElevatedButton(
                                          onPressed: () {
                                            _communityService.toggleJoinCommunity(comm.id);
                                          },
                                          style: ElevatedButton.styleFrom(
                                            backgroundColor: comm.isMember ? AlouteColors.bgBody : AlouteColors.accent,
                                            foregroundColor: comm.isMember ? AlouteColors.textPrimary : Colors.white,
                                            elevation: 0,
                                            side: comm.isMember ? const BorderSide(color: AlouteColors.borderStrong) : BorderSide.none,
                                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
                                            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                                            minimumSize: Size.zero,
                                            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                          ),
                                          child: Text(
                                            comm.isMember ? 'Đã tham gia' : 'Tham gia',
                                            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                                          ),
                                        ),
                                        const SizedBox(width: 8),
                                        OutlinedButton(
                                          onPressed: () {
                                            Navigator.push(
                                              context,
                                              MaterialPageRoute(
                                                builder: (_) => CommunityDetailScreen(community: comm),
                                              ),
                                            );
                                          },
                                          style: OutlinedButton.styleFrom(
                                            side: const BorderSide(color: AlouteColors.borderColor),
                                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
                                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                                            minimumSize: Size.zero,
                                            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                          ),
                                          child: const Text('Khám phá', style: TextStyle(fontSize: 12, color: AlouteColors.textSecondary)),
                                        ),
                                      ],
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          );
                        },
                        childCount: filtered.length,
                      ),
                    ),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }
}
