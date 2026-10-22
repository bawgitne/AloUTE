class PostAuthor {
  final int id;
  final String username;
  final String displayName;
  final String? avatarUrl;
  final String faculty; // e.g. "Khoa CNTT"
  final String? classCode; // e.g. "24110CLA"

  PostAuthor({
    required this.id,
    required this.username,
    required this.displayName,
    this.avatarUrl,
    this.faculty = '',
    this.classCode,
  });

  factory PostAuthor.fromJson(Map<String, dynamic> json) {
    return PostAuthor(
      id: json['id'] is int ? json['id'] : int.tryParse(json['id'].toString()) ?? 0,
      username: json['username'] as String? ?? 'sinhvien',
      displayName: json['displayName'] as String? ?? json['username'] as String? ?? 'Sinh viên AloUTE',
      avatarUrl: json['avatarUrl'] as String?,
      faculty: json['faculty'] as String? ?? json['department'] as String? ?? '',
      classCode: json['classCode'] as String?,
    );
  }
}

class PollOption {
  final int id;
  final String text;
  int voteCount;
  bool isVoted;

  PollOption({
    required this.id,
    required this.text,
    this.voteCount = 0,
    this.isVoted = false,
  });

  factory PollOption.fromJson(Map<String, dynamic> json) {
    return PollOption(
      id: json['id'] is int ? json['id'] : int.tryParse(json['id'].toString()) ?? 0,
      text: json['text'] as String? ?? '',
      voteCount: json['voteCount'] as int? ?? 0,
      isVoted: json['isVoted'] as bool? ?? false,
    );
  }
}

class PostPoll {
  final int id;
  final String question;
  final List<PollOption> options;
  final int totalVotes;
  final bool hasVoted;

  PostPoll({
    required this.id,
    required this.question,
    required this.options,
    required this.totalVotes,
    this.hasVoted = false,
  });

  factory PostPoll.fromJson(Map<String, dynamic> json) {
    final rawOptions = json['options'] as List<dynamic>? ?? [];
    final parsedOptions = rawOptions
        .map((opt) => PollOption.fromJson(opt as Map<String, dynamic>))
        .toList();
    final int total = (json['totalVotes'] as int?) ??
        parsedOptions.fold<int>(0, (sum, item) => sum + item.voteCount);

    return PostPoll(
      id: json['id'] is int ? json['id'] as int : int.tryParse(json['id'].toString()) ?? 0,
      question: json['question'] as String? ?? '',
      options: parsedOptions,
      totalVotes: total,
      hasVoted: json['hasVoted'] as bool? ?? parsedOptions.any((o) => o.isVoted),
    );
  }
}

class Post {
  final int id;
  final PostAuthor author;
  final String content;
  final List<String> mediaUrls;
  final PostPoll? poll;
  final String? audience; // PUBLIC, FOLLOWERS, COMMUNITY
  final String? communityName;
  final DateTime createdAt;
  int likeCount;
  int replyCount;
  int repostCount;
  bool isLiked;
  bool isBookmarked;

  Post({
    required this.id,
    required this.author,
    required this.content,
    this.mediaUrls = const [],
    this.poll,
    this.audience = 'PUBLIC',
    this.communityName,
    required this.createdAt,
    this.likeCount = 0,
    this.replyCount = 0,
    this.repostCount = 0,
    this.isLiked = false,
    this.isBookmarked = false,
  });

  factory Post.fromJson(Map<String, dynamic> json) {
    // Parse media from mediaUrls or media
    final List<dynamic> rawMediaList = (json['mediaUrls'] as List<dynamic>?) ??
        (json['media'] is List ? json['media'] as List<dynamic> : []);
    final mediaUrls = rawMediaList.map((item) {
      if (item is Map) return item['url']?.toString() ?? item['mediaUrl']?.toString() ?? '';
      return item.toString();
    }).where((s) => s.isNotEmpty).toList();

    // Parse viewerState for like/bookmark
    final viewerState = json['viewerState'] as Map<String, dynamic>?;
    final isLiked = json['isLiked'] as bool? ?? (viewerState?['liked'] as bool? ?? false);
    final isBookmarked = json['isBookmarked'] as bool? ?? (viewerState?['bookmarked'] as bool? ?? false);

    return Post(
      id: json['id'] is int ? json['id'] : int.tryParse(json['id'].toString()) ?? 0,
      author: json['author'] != null
          ? PostAuthor.fromJson(json['author'] as Map<String, dynamic>)
          : PostAuthor(id: 0, username: 'anonymous', displayName: 'Người dùng'),
      content: json['content'] as String? ?? '',
      mediaUrls: mediaUrls,
      poll: json['poll'] != null ? PostPoll.fromJson(json['poll'] as Map<String, dynamic>) : null,
      audience: json['audience'] as String? ?? json['visibility'] as String? ?? 'PUBLIC',
      communityName: json['communityName'] as String?,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
      likeCount: json['likeCount'] as int? ?? 0,
      replyCount: json['replyCount'] as int? ?? 0,
      repostCount: json['repostCount'] as int? ?? json['quoteCount'] as int? ?? 0,
      isLiked: isLiked,
      isBookmarked: isBookmarked,
    );
  }
}
