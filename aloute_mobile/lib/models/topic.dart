class Topic {
  final int id;
  final String name;
  final String slug;
  final String? description;
  int followerCount;
  int postCount;
  bool isFollowing;

  Topic({
    required this.id,
    required this.name,
    required this.slug,
    this.description,
    this.followerCount = 0,
    this.postCount = 0,
    this.isFollowing = false,
  });

  factory Topic.fromJson(Map<String, dynamic> json) {
    return Topic(
      id: json['id'] is int ? json['id'] as int : int.tryParse(json['id'].toString()) ?? 0,
      name: json['name'] as String? ?? 'Chủ đề',
      slug: json['slug'] as String? ?? 'chu-de',
      description: json['description'] as String?,
      followerCount: json['followerCount'] as int? ?? 0,
      postCount: json['postCount'] as int? ?? 0,
      isFollowing: json['isFollowing'] as bool? ?? false,
    );
  }
}
