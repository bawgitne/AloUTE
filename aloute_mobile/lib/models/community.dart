class Community {
  final int id;
  final String name;
  final String slug;
  final String? description;
  final String? avatarUrl;
  final String? coverUrl;
  int memberCount;
  int postCount;
  bool isMember;
  bool isOwner;
  final String? currentUserRole; // OWNER, MODERATOR, MEMBER, null
  final int? ownerId;
  final bool isPrivate;

  Community({
    required this.id,
    required this.name,
    required this.slug,
    this.description,
    this.avatarUrl,
    this.coverUrl,
    this.memberCount = 0,
    this.postCount = 0,
    this.isMember = false,
    this.isOwner = false,
    this.currentUserRole,
    this.ownerId,
    this.isPrivate = false,
  });

  factory Community.fromJson(Map<String, dynamic> json) {
    final role = json['currentUserRole'] as String?;
    final bool ownerFlag = json['isOwner'] as bool? ?? (role == 'OWNER');
    return Community(
      id: json['id'] is int ? json['id'] as int : int.tryParse(json['id'].toString()) ?? 0,
      name: json['name'] as String? ?? 'Cộng đồng',
      slug: json['slug'] as String? ?? 'cong-dong',
      description: json['description'] as String?,
      avatarUrl: json['avatarUrl'] as String?,
      coverUrl: json['coverUrl'] as String?,
      memberCount: json['memberCount'] as int? ?? 0,
      postCount: json['postCount'] as int? ?? 0,
      isMember: json['isMember'] as bool? ?? (role != null),
      isOwner: ownerFlag,
      currentUserRole: role,
      ownerId: json['ownerId'] as int?,
      isPrivate: json['isPrivate'] as bool? ?? false,
    );
  }
}
