import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../services/admin_service.dart';
import 'admin_posts_screen.dart';
import 'admin_reports_screen.dart';
import 'admin_users_screen.dart';

class AdminDashboardScreen extends StatefulWidget {
  const AdminDashboardScreen({super.key});

  @override
  State<AdminDashboardScreen> createState() => _AdminDashboardScreenState();
}

class _AdminDashboardScreenState extends State<AdminDashboardScreen> {
  final AdminService _adminService = AdminService();

  @override
  void initState() {
    super.initState();
    _adminService.fetchOverview();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      appBar: AppBar(
        title: const Text('Bảng điều khiển Quản trị'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListenableBuilder(
        listenable: _adminService,
        builder: (context, _) {
          if (_adminService.isLoading) {
            return const Center(child: CircularProgressIndicator());
          }

          final m = _adminService.metrics;

          return RefreshIndicator(
            onRefresh: _adminService.fetchOverview,
            color: AlouteColors.accent,
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                // Top Header Card matching admin/index.html
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AlouteColors.bgSurface,
                    borderRadius: BorderRadius.circular(AlouteColors.radiusLg),
                    border: Border.all(color: AlouteColors.borderColor),
                    boxShadow: AlouteColors.shadowSm,
                  ),
                  child: Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: AlouteColors.accentSubtle,
                          borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                        ),
                        child: const Icon(Icons.speed, color: AlouteColors.accent, size: 28),
                      ),
                      const SizedBox(width: 14),
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Tổng quan hệ thống AloUTE',
                              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800, color: AlouteColors.textPrimary),
                            ),
                            SizedBox(height: 2),
                            Text(
                              'Thống kê vận hành và kiểm duyệt thời gian thực',
                              style: TextStyle(fontSize: 12, color: AlouteColors.textSecondary),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                // KPI Grid
                const Text(
                  'Chỉ số chính (KPI Metrics)',
                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AlouteColors.textPrimary),
                ),
                const SizedBox(height: 10),

                Row(
                  children: [
                    Expanded(
                      child: _buildKpiCard(
                        title: 'Người dùng',
                        value: '${m.totalUsers}',
                        subtext: '${m.activeUsers} đang hoạt động',
                        icon: Icons.people_outline,
                        color: AlouteColors.accent,
                        bg: AlouteColors.accentSubtle,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _buildKpiCard(
                        title: 'Bài viết',
                        value: '${m.totalPosts}',
                        subtext: '+${m.postsCreatedToday} hôm nay',
                        icon: Icons.article_outlined,
                        color: AlouteColors.success,
                        bg: AlouteColors.successBg,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),

                Row(
                  children: [
                    Expanded(
                      child: InkWell(
                        onTap: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(builder: (_) => const AdminReportsScreen()),
                          );
                        },
                        child: _buildKpiCard(
                          title: 'Báo cáo chờ xử lý',
                          value: '${m.pendingReports}',
                          subtext: 'Xem hàng đợi kiểm duyệt →',
                          icon: Icons.hourglass_top_outlined,
                          color: AlouteColors.warning,
                          bg: AlouteColors.warningBg,
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _buildKpiCard(
                        title: 'Cộng đồng / CLB',
                        value: '${m.totalCommunities}',
                        subtext: '${m.activeCommunities} hoạt động tích cực',
                        icon: Icons.hub_outlined,
                        color: AlouteColors.info,
                        bg: AlouteColors.infoBg,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 24),

                // Quick Navigation Actions
                const Text(
                  'Mô-đun quản trị',
                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AlouteColors.textPrimary),
                ),
                const SizedBox(height: 10),

                _buildActionTile(
                  icon: Icons.shield_outlined,
                  iconColor: AlouteColors.warning,
                  iconBg: AlouteColors.warningBg,
                  title: 'Hàng đợi duyệt báo cáo vi phạm',
                  subtitle: '${m.pendingReports} báo cáo đang chờ xử lý từ sinh viên',
                  onTap: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => const AdminReportsScreen()),
                    );
                  },
                ),
                const SizedBox(height: 10),

                _buildActionTile(
                  icon: Icons.manage_accounts_outlined,
                  iconColor: AlouteColors.accent,
                  iconBg: AlouteColors.accentSubtle,
                  title: 'Quản lý tài khoản sinh viên',
                  subtitle: 'Tìm kiếm, xem hồ sơ và khóa/mở tài khoản vi phạm',
                  onTap: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => const AdminUsersScreen()),
                    );
                  },
                ),
                const SizedBox(height: 10),

                _buildActionTile(
                  icon: Icons.rule_folder_outlined,
                  iconColor: AlouteColors.danger,
                  iconBg: AlouteColors.dangerBg,
                  title: 'Kiểm duyệt nội dung bài viết',
                  subtitle: 'Rà soát bài đăng, quyền riêng tư và gỡ bài viết vi phạm',
                  onTap: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => const AdminPostsScreen()),
                    );
                  },
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildKpiCard({
    required String title,
    required String value,
    required String subtext,
    required IconData icon,
    required Color color,
    required Color bg,
  }) {
    return Container(
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
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(title, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AlouteColors.textSecondary)),
              Container(
                padding: const EdgeInsets.all(6),
                decoration: BoxDecoration(color: bg, shape: BoxShape.circle),
                child: Icon(icon, color: color, size: 16),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            value,
            style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: AlouteColors.textPrimary),
          ),
          const SizedBox(height: 4),
          Text(
            subtext,
            style: TextStyle(fontSize: 11, fontWeight: FontWeight.w500, color: color),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }

  Widget _buildActionTile({
    required IconData icon,
    required Color iconColor,
    required Color iconBg,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(AlouteColors.radiusLg),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: AlouteColors.bgSurface,
          borderRadius: BorderRadius.circular(AlouteColors.radiusLg),
          border: Border.all(color: AlouteColors.borderColor),
          boxShadow: AlouteColors.shadowSm,
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(color: iconBg, shape: BoxShape.circle),
              child: Icon(icon, color: iconColor, size: 22),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AlouteColors.textPrimary)),
                  const SizedBox(height: 2),
                  Text(subtitle, style: const TextStyle(fontSize: 12, color: AlouteColors.textSecondary)),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: AlouteColors.textMuted),
          ],
        ),
      ),
    );
  }
}
