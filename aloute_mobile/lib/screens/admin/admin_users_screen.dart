import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../services/admin_service.dart';

class AdminUsersScreen extends StatefulWidget {
  const AdminUsersScreen({super.key});

  @override
  State<AdminUsersScreen> createState() => _AdminUsersScreenState();
}

class _AdminUsersScreenState extends State<AdminUsersScreen> {
  final AdminService _adminService = AdminService();
  String _searchQuery = '';

  @override
  void initState() {
    super.initState();
    _adminService.fetchUsers();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      appBar: AppBar(
        title: const Text('Quản lý người dùng'),
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

          final allUsers = _adminService.users;
          final filtered = allUsers.where((u) {
            final q = _searchQuery.toLowerCase();
            return u.displayName.toLowerCase().contains(q) ||
                u.username.toLowerCase().contains(q) ||
                u.email.toLowerCase().contains(q);
          }).toList();

          return Column(
            children: [
              // Search Input
              Container(
                color: AlouteColors.bgSurface,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                child: Container(
                  decoration: BoxDecoration(
                    color: AlouteColors.bgBody,
                    borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
                    border: Border.all(color: AlouteColors.borderColor),
                  ),
                  child: TextField(
                    onChanged: (val) => setState(() => _searchQuery = val),
                    decoration: const InputDecoration(
                      hintText: 'Tìm theo tên, username hoặc email sinh viên...',
                      hintStyle: TextStyle(color: AlouteColors.textMuted, fontSize: 13),
                      prefixIcon: Icon(Icons.search, size: 20, color: AlouteColors.textMuted),
                      border: InputBorder.none,
                      contentPadding: EdgeInsets.symmetric(vertical: 10),
                    ),
                  ),
                ),
              ),
              const Divider(height: 1, color: AlouteColors.borderColor),

              // Users List
              Expanded(
                child: ListView.builder(
                  padding: const EdgeInsets.all(12),
                  itemCount: filtered.length,
                  itemBuilder: (context, index) {
                    final u = filtered[index];
                    final isActive = u.status == 'ACTIVE';

                    return Container(
                      margin: const EdgeInsets.only(bottom: 10),
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: AlouteColors.bgSurface,
                        borderRadius: BorderRadius.circular(AlouteColors.radiusLg),
                        border: Border.all(color: AlouteColors.borderColor),
                        boxShadow: AlouteColors.shadowSm,
                      ),
                      child: Row(
                        children: [
                          CircleAvatar(
                            radius: 22,
                            backgroundColor: AlouteColors.accentSubtle,
                            child: Text(
                              u.displayName.isNotEmpty ? u.displayName[0] : 'U',
                              style: const TextStyle(fontWeight: FontWeight.bold, color: AlouteColors.accent),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    Flexible(
                                      child: Text(
                                        u.displayName,
                                        style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AlouteColors.textPrimary),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                    const SizedBox(width: 6),
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                      decoration: BoxDecoration(
                                        color: isActive ? AlouteColors.successBg : AlouteColors.dangerBg,
                                        borderRadius: BorderRadius.circular(AlouteColors.radiusSm),
                                      ),
                                      child: Text(
                                        isActive ? 'Hoạt động' : 'Đã khóa',
                                        style: TextStyle(
                                          fontSize: 10,
                                          fontWeight: FontWeight.bold,
                                          color: isActive ? AlouteColors.success : AlouteColors.danger,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  '@${u.username} • ${u.email}',
                                  style: const TextStyle(fontSize: 12, color: AlouteColors.textMuted),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  u.faculty,
                                  style: const TextStyle(fontSize: 11, color: AlouteColors.textSecondary),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(width: 8),
                          ElevatedButton(
                            onPressed: () => _adminService.toggleUserStatus(u.id),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: isActive ? AlouteColors.dangerBg : AlouteColors.successBg,
                              foregroundColor: isActive ? AlouteColors.danger : AlouteColors.success,
                              elevation: 0,
                              side: BorderSide(color: isActive ? AlouteColors.dangerBorder : AlouteColors.successBorder),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AlouteColors.radiusFull)),
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                              minimumSize: Size.zero,
                              tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                            ),
                            child: Text(
                              isActive ? 'Khóa' : 'Kích hoạt',
                              style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold),
                            ),
                          ),
                        ],
                      ),
                    );
                  },
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
