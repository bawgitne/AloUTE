/// API Constants and Endpoints matching skills/aloute-openapi.yaml
class ApiConstants {
  /// Default base URL for production backend (Cloud Run via justneed.food)
  static const String defaultProdUrl = 'https://justneed.food/api';
  /// Fallback direct Cloud Run URL
  static const String defaultCloudRunUrl = 'https://aloute-app-536335604006.asia-southeast1.run.app/api';
  /// Default base URL for local development
  static const String defaultLocalUrl = 'http://localhost:8080/api';
  /// Cloudflare worker backend URL
  static const String defaultWorkerUrl = 'https://aloute-backend-worker.bawg.workers.dev/api';

  // Auth endpoints
  static const String login = '/auth/login';
  static const String register = '/auth/register';
  static const String verifyRegistrationOtp = '/auth/verify-registration-otp';
  static const String resendRegistrationOtp = '/auth/resend-registration-otp';
  static const String forgotPassword = '/auth/forgot-password';
  static const String resetPassword = '/auth/reset-password';
  static const String logout = '/auth/logout';
  static const String me = '/auth/me';
  static const String verify2fa = '/auth/2fa/verify-login';
  static const String send2faEmailFallback = '/auth/2fa/send-email-fallback';
  static const String user2faStatus = '/users/me/2fa/status';
  static const String user2faSetup = '/users/me/2fa/setup';
  static const String user2faEnable = '/users/me/2fa/enable';
  static const String user2faDisable = '/users/me/2fa/disable';
  static const String user2faBackupCodes = '/users/me/2fa/backup-codes';

  // Feed endpoints
  static const String homeFeed = '/feed/home';
  static const String followingFeed = '/feed/following';
  static const String exploreFeed = '/feed/explore';
  static const String recommendedFeed = '/feed/recommended';

  // Post endpoints
  static const String posts = '/posts';
  static const String savedPosts = '/posts/bookmarks';
  static String postById(int id) => '/posts/$id';
  static String postLikes(int id) => '/posts/$id/likes/me';
  static String postBookmarks(int id) => '/posts/$id/bookmarks/me';
  static String postReplies(int id) => '/posts/$id/replies';
  static String postQuotes(int id) => '/posts/$id/quotes';
  static String pollVote(int pollId) => '/polls/$pollId/votes';

  // Communities endpoints
  static const String communities = '/communities';
  static String communityById(int id) => '/communities/$id';
  static String communityMembers(int id) => '/communities/$id/members';
  static String communityPosts(int id) => '/communities/$id/posts';
  static String joinCommunity(int id) => '/communities/$id/join';
  static String leaveCommunity(int id) => '/communities/$id/members/me';

  // Topics endpoints
  static const String topics = '/topics';
  static String topicById(int id) => '/topics/$id';
  static String topicPosts(int id) => '/topics/$id/posts';
  static String followTopic(int id) => '/users/me/topics/$id';

  // Messaging endpoints
  static const String conversations = '/conversations';
  static String conversationMessages(int id) => '/conversations/$id/messages';

  // Notification endpoints
  static const String notifications = '/notifications';
  static const String markAllNotificationsRead = '/notifications/read-all';

  // User & Social endpoints
  static const String users = '/users';
  static String userProfile(String username) => '/users/by-username/$username';
  static String userById(int id) => '/users/$id';
  static String userPosts(int id) => '/users/$id/posts';
  static String userFollowers(int id) => '/users/$id/followers';
  static String userFollowing(int id) => '/users/$id/following';
  static String followUser(int id) => '/social/follows/$id';
  static const String updateProfile = '/users/me/profile';

  // Admin endpoints (for ROLE_SYSTEM_ADMIN)
  static const String adminOverview = '/admin/overview';
  static const String adminReports = '/admin/reports';
  static String adminReportAction(int id) => '/admin/reports/$id/decisions';
  static const String adminUsers = '/admin/users';
  static String adminUserById(int id) => '/admin/users/$id';
  static const String adminPosts = '/admin/posts';
  static String adminPostById(int id) => '/admin/posts/$id';
  static String adminPostRemove(int id) => '/admin/posts/$id/remove';
  static String adminPostRestore(int id) => '/admin/posts/$id/restore';
}
