class AdminMetrics {
  final int totalUsers;
  final int activeUsers;
  final int totalPosts;
  final int postsCreatedToday;
  final int pendingReports;
  final int reviewedReports;
  final int totalCommunities;
  final int activeCommunities;
  final String systemHealth;

  AdminMetrics({
    this.totalUsers = 0,
    this.activeUsers = 0,
    this.totalPosts = 0,
    this.postsCreatedToday = 0,
    this.pendingReports = 0,
    this.reviewedReports = 0,
    this.totalCommunities = 0,
    this.activeCommunities = 0,
    this.systemHealth = 'HEALTHY',
  });

  factory AdminMetrics.fromJson(Map<String, dynamic> json) {
    return AdminMetrics(
      totalUsers: json['totalUsers'] as int? ?? 0,
      activeUsers: json['activeUsers'] as int? ?? 0,
      totalPosts: json['totalPosts'] as int? ?? 0,
      postsCreatedToday: json['postsCreatedToday'] as int? ?? 0,
      pendingReports: json['pendingReports'] as int? ?? 0,
      reviewedReports: json['reviewedReports'] as int? ?? 0,
      totalCommunities: json['totalCommunities'] as int? ?? 0,
      activeCommunities: json['activeCommunities'] as int? ?? 0,
      systemHealth: json['systemHealth'] as String? ?? 'HEALTHY',
    );
  }
}

class AdminReportItem {
  final int id;
  final String reason;
  final String status; // PENDING, REVIEWED, RESOLVED, DISMISSED
  final String reporterName;
  final String reportedUserName;
  final String targetType; // POST, USER, COMMENT
  final int targetId;
  final String? contentPreview;
  final DateTime createdAt;

  AdminReportItem({
    required this.id,
    required this.reason,
    required this.status,
    required this.reporterName,
    required this.reportedUserName,
    required this.targetType,
    required this.targetId,
    this.contentPreview,
    required this.createdAt,
  });

  factory AdminReportItem.fromJson(Map<String, dynamic> json) {
    return AdminReportItem(
      id: json['id'] is int ? json['id'] as int : int.tryParse(json['id'].toString()) ?? 0,
      reason: json['reason'] as String? ?? 'Nội dung vi phạm tiêu chuẩn cộng đồng',
      status: json['status'] as String? ?? 'PENDING',
      reporterName: json['reporterName'] as String? ?? 'Sinh viên ẩn danh',
      reportedUserName: json['reportedUserName'] as String? ?? 'Người dùng',
      targetType: json['targetType'] as String? ?? 'POST',
      targetId: json['targetId'] is int ? json['targetId'] as int : int.tryParse(json['targetId'].toString()) ?? 0,
      contentPreview: json['contentPreview'] as String? ?? json['description'] as String?,
      createdAt: json['createdAt'] != null
          ? (DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now())
          : DateTime.now(),
    );
  }
}

class AdminUserItem {
  final int id;
  final String username;
  final String displayName;
  final String email;
  final String role; // ROLE_USER, ROLE_ADMIN, ROLE_MODERATOR
  String status; // ACTIVE, SUSPENDED, BANNED
  final String faculty;
  final DateTime createdAt;

  AdminUserItem({
    required this.id,
    required this.username,
    required this.displayName,
    required this.email,
    required this.role,
    this.status = 'ACTIVE',
    this.faculty = 'Khoa CNTT',
    required this.createdAt,
  });

  factory AdminUserItem.fromJson(Map<String, dynamic> json) {
    return AdminUserItem(
      id: json['id'] is int ? json['id'] as int : int.tryParse(json['id'].toString()) ?? 0,
      username: json['username'] as String? ?? 'user',
      displayName: json['displayName'] as String? ?? json['username'] as String? ?? 'Sinh viên',
      email: json['email'] as String? ?? 'user@student.hcmute.edu.vn',
      role: json['role'] as String? ?? 'ROLE_USER',
      status: json['status'] as String? ?? 'ACTIVE',
      faculty: json['faculty'] as String? ?? 'Khoa CNTT',
      createdAt: json['createdAt'] != null
          ? (DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now())
          : DateTime.now(),
    );
  }
}

class AdminPostItem {
  final int id;
  final String authorName;
  final String authorUsername;
  final String content;
  final String visibility;
  String status; // ACTIVE, HIDDEN, REMOVED
  final int reportCount;
  final DateTime createdAt;

  AdminPostItem({
    required this.id,
    required this.authorName,
    required this.authorUsername,
    required this.content,
    this.visibility = 'PUBLIC',
    this.status = 'ACTIVE',
    this.reportCount = 0,
    required this.createdAt,
  });

  factory AdminPostItem.fromJson(Map<String, dynamic> json) {
    return AdminPostItem(
      id: json['id'] is int ? json['id'] as int : int.tryParse(json['id'].toString()) ?? 0,
      authorName: json['authorName'] as String? ?? 'Sinh viên',
      authorUsername: json['authorUsername'] as String? ?? 'sinhvien',
      content: json['content'] as String? ?? '',
      visibility: json['visibility'] as String? ?? 'PUBLIC',
      status: json['status'] as String? ?? 'ACTIVE',
      reportCount: json['reportCount'] as int? ?? 0,
      createdAt: json['createdAt'] != null
          ? (DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now())
          : DateTime.now(),
    );
  }
}
