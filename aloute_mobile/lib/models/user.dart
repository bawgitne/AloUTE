class User {
  final int id;
  final String username;
  final String displayName;
  final String? email;
  final String? avatarUrl;
  final String? coverUrl;
  final String? bio;
  final String? faculty; // Khoa CNTT, v.v.
  final String? studentCode; // MSSV: 24110xxx
  final int followerCount;
  final int followingCount;
  final int postCount;
  final bool isFollowing;
  final String role;

  User({
    required this.id,
    required this.username,
    required this.displayName,
    this.email,
    this.avatarUrl,
    this.coverUrl,
    this.bio,
    this.faculty,
    this.studentCode,
    this.followerCount = 0,
    this.followingCount = 0,
    this.postCount = 0,
    this.isFollowing = false,
    this.role = 'USER',
  });

  bool get isAdmin =>
      role.toUpperCase() == 'SYSTEM_ADMIN' ||
      role.toUpperCase() == 'ROLE_SYSTEM_ADMIN' ||
      role.toUpperCase() == 'ADMIN';

  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: json['id'] is int ? json['id'] : int.tryParse(json['id'].toString()) ?? 0,
      username: json['username'] as String? ?? '',
      displayName: json['displayName'] as String? ?? json['username'] as String? ?? 'Sinh viên HCMUTE',
      email: json['email'] as String?,
      avatarUrl: json['avatarUrl'] as String?,
      coverUrl: json['coverUrl'] as String?,
      bio: json['bio'] as String?,
      faculty: json['faculty'] as String? ?? json['department'] as String?,
      studentCode: json['studentCode'] as String?,
      followerCount: json['followerCount'] as int? ?? 0,
      followingCount: json['followingCount'] as int? ?? 0,
      postCount: json['postCount'] as int? ?? 0,
      isFollowing: json['isFollowing'] as bool? ?? false,
      role: json['role'] as String? ?? 'USER',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'username': username,
      'displayName': displayName,
      'email': email,
      'avatarUrl': avatarUrl,
      'coverUrl': coverUrl,
      'bio': bio,
      'faculty': faculty,
      'studentCode': studentCode,
      'followerCount': followerCount,
      'followingCount': followingCount,
      'postCount': postCount,
      'isFollowing': isFollowing,
      'role': role,
    };
  }
}
