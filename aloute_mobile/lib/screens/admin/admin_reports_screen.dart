import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../models/admin_models.dart';
import '../../services/admin_service.dart';

class AdminReportsScreen extends StatefulWidget {
  const AdminReportsScreen({super.key});

  @override
  State<AdminReportsScreen> createState() => _AdminReportsScreenState();
}

class _AdminReportsScreenState extends State<AdminReportsScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final AdminService _adminService = AdminService();

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    _adminService.fetchReports(status: 'PENDING');
    _tabController.addListener(() {
      if (_tabController.indexIsChanging) {
        final statuses = ['PENDING', 'REVIEWED', 'RESOLVED'];
        _adminService.fetchReports(status: statuses[_tabController.index]);
      }
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  void _confirmAction(AdminReportItem report, String actionTitle, String actionCode) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AlouteColors.bgSurface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusLg)),
        title: Text(actionTitle),
        content: Text('Bạn có chắc chắn muốn thực hiện hành động này đối với báo cáo #${report.id}?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Hủy', style: TextStyle(color: AlouteColors.textSecondary)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: actionCode == 'DISMISS' ? AlouteColors.accent : AlouteColors.danger,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
            ),
            onPressed: () {
              Navigator.pop(ctx);
              _adminService.resolveReport(report.id, actionCode);
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text('Đã xử lý báo cáo #${report.id} ($actionTitle)'),
                  backgroundColor: AlouteColors.success,
                ),
              );
            },
            child: const Text('Xác nhận'),
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
        title: const Text('Kiểm duyệt báo cáo'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.pop(context),
        ),
        bottom: TabBar(
          controller: _tabController,
          labelColor: AlouteColors.accent,
          unselectedLabelColor: AlouteColors.textSecondary,
          indicatorColor: AlouteColors.accent,
          indicatorWeight: 3,
          labelStyle: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
          tabs: const [
            Tab(text: 'Chờ xử lý'),
            Tab(text: 'Đang xem xét'),
            Tab(text: 'Đã giải quyết'),
          ],
        ),
      ),
      body: ListenableBuilder(
        listenable: _adminService,
        builder: (context, _) {
          if (_adminService.isLoading) {
            return const Center(child: CircularProgressIndicator());
          }

          final reports = _adminService.reports;

          if (reports.isEmpty) {
            return const Center(
              child: Padding(
                padding: EdgeInsets.all(32),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.check_circle_outline, size: 48, color: AlouteColors.success),
                    SizedBox(height: 12),
                    Text(
                      'Hàng đợi trống!',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: AlouteColors.textPrimary),
                    ),
                    SizedBox(height: 4),
                    Text(
                      'Không có báo cáo vi phạm nào cần xử lý trong mục này.',
                      style: TextStyle(fontSize: 13, color: AlouteColors.textSecondary),
                    ),
                  ],
                ),
              ),
            );
          }

          return ListView.builder(
            padding: const EdgeInsets.all(12),
            itemCount: reports.length,
            itemBuilder: (context, index) {
              final r = reports[index];

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
                    // Header tag
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: AlouteColors.warningBg,
                            borderRadius: BorderRadius.circular(AlouteColors.radiusSm),
                            border: Border.all(color: AlouteColors.warningBorder),
                          ),
                          child: Text(
                            'Báo cáo #${r.id} • ${r.targetType}',
                            style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AlouteColors.warning),
                          ),
                        ),
                        Text(
                          '${r.createdAt.hour}:${r.createdAt.minute.toString().padLeft(2, '0')}',
                          style: const TextStyle(fontSize: 11, color: AlouteColors.textMuted),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),

                    // Reason
                    Text(
                      r.reason,
                      style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: AlouteColors.danger),
                    ),
                    const SizedBox(height: 6),

                    // Content preview
                    if (r.contentPreview != null)
                      Container(
                        padding: const EdgeInsets.all(10),
                        margin: const EdgeInsets.symmetric(vertical: 6),
                        decoration: BoxDecoration(
                          color: AlouteColors.bgBody,
                          borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                          border: Border.all(color: AlouteColors.borderColor),
                        ),
                        child: Text(
                          '"${r.contentPreview}"',
                          style: const TextStyle(fontSize: 12, fontStyle: FontStyle.italic, color: AlouteColors.textPrimary),
                        ),
                      ),

                    // Meta: reporter & reported user
                    Row(
                      children: [
                        const Icon(Icons.flag_outlined, size: 14, color: AlouteColors.textMuted),
                        const SizedBox(width: 4),
                        Text('Bởi: ${r.reporterName}', style: const TextStyle(fontSize: 12, color: AlouteColors.textSecondary)),
                        const SizedBox(width: 12),
                        const Icon(Icons.person_outline, size: 14, color: AlouteColors.textMuted),
                        const SizedBox(width: 4),
                        Text('Đối tượng: ${r.reportedUserName}', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AlouteColors.textPrimary)),
                      ],
                    ),
                    const SizedBox(height: 14),

                    // Action buttons
                    Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        OutlinedButton(
                          onPressed: () => _confirmAction(r, 'Bỏ qua báo cáo', 'DISMISS'),
                          style: OutlinedButton.styleFrom(
                            side: const BorderSide(color: AlouteColors.borderColor),
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                            minimumSize: Size.zero,
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
                          ),
                          child: const Text('Bỏ qua', style: TextStyle(fontSize: 12, color: AlouteColors.textSecondary)),
                        ),
                        const SizedBox(width: 8),
                        ElevatedButton(
                          onPressed: () => _confirmAction(r, 'Gỡ bài viết vi phạm', 'REMOVE_CONTENT'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: AlouteColors.danger,
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                            minimumSize: Size.zero,
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
                          ),
                          child: const Text('Gỡ bài', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                        ),
                      ],
                    ),
                  ],
                ),
              );
            },
          );
        },
      ),
    );
  }
}
