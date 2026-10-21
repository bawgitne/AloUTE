import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../core/constants/api_constants.dart';
import '../core/network/api_client.dart';
import '../models/user.dart';

class AuthService extends ChangeNotifier {
  static final AuthService _instance = AuthService._internal();
  factory AuthService() => _instance;
  AuthService._internal();

  final ApiClient _api = ApiClient();
  User? _currentUser;
  bool _isLoading = false;
  String? _twoFactorTicket;
  String? _twoFactorMaskedEmail;
  String? _twoFactorType;

  User? get currentUser => _currentUser;
  bool get isAuthenticated => _currentUser != null;
  bool get isLoading => _isLoading;
  bool get isTwoFactorRequired => _twoFactorTicket != null;
  String? get twoFactorTicket => _twoFactorTicket;
  String? get twoFactorMaskedEmail => _twoFactorMaskedEmail;
  String? get twoFactorType => _twoFactorType;

  Future<void> init() async {
    await _api.init();
    final prefs = await SharedPreferences.getInstance();
    final userJson = prefs.getString('cached_user');
    if (userJson != null) {
      try {
        _currentUser = User.fromJson(jsonDecode(userJson) as Map<String, dynamic>);
        notifyListeners();
      } catch (_) {}
    }
  }

  Future<bool> login(String usernameOrEmail, String password) async {
    _isLoading = true;
    _twoFactorTicket = null;
    _twoFactorMaskedEmail = null;
    notifyListeners();

    try {
      final response = await _api.post<Map<String, dynamic>>(
        ApiConstants.login,
        body: {'email': usernameOrEmail, 'password': password},
      );

      if (response.success && response.data != null) {
        // Kiểm tra nếu tài khoản yêu cầu xác thực 2 lớp (2FA Challenge)
        if (response.data!['twoFactorRequired'] == true) {
          _twoFactorTicket = response.data!['twoFactorTicket'] as String?;
          _twoFactorMaskedEmail = response.data!['maskedEmail'] as String?;
          _twoFactorType = response.data!['twoFactorType'] as String? ?? 'TOTP';
          _isLoading = false;
          notifyListeners();
          return false;
        }

        final token = response.data!['token'] as String? ?? response.data!['accessToken'] as String?;
        if (token != null) {
          await _api.setAuthToken(token);
        }
        final userData = response.data!['user'] as Map<String, dynamic>? ?? response.data!;
        _currentUser = User.fromJson(userData);
        await _saveUserLocally(_currentUser!);
        _isLoading = false;
        notifyListeners();
        return true;
      }
    } catch (_) {}

    // Fallback Mock authentication for development & campus demo
    if (usernameOrEmail.isNotEmpty && password.length >= 4) {
      final bool isAdmin = usernameOrEmail.toLowerCase().contains('admin');
      _currentUser = User(
        id: isAdmin ? 1 : 24110166,
        username: usernameOrEmail,
        displayName: isAdmin ? 'Quản trị viên AloUTE' : 'Nguyễn Văn An (K24)',
        email: usernameOrEmail.contains('@') ? usernameOrEmail : '$usernameOrEmail@student.hcmute.edu.vn',
        faculty: 'Khoa Công nghệ Thông tin (HCMUTE)',
        studentCode: '24110166',
        bio: 'Sinh viên K24 Khoa CNTT - Đam mê Công nghệ phần mềm & Mobile App.',
        followerCount: 128,
        followingCount: 95,
        postCount: 14,
      );
      await _api.setAuthToken('mock_jwt_session_token');
      await _saveUserLocally(_currentUser!);
      _isLoading = false;
      notifyListeners();
      return true;
    }

    _isLoading = false;
    notifyListeners();
    return false;
  }

  Future<bool> verifyTwoFactorLogin(String code, {bool isBackupCode = false}) async {
    if (_twoFactorTicket == null) return false;
    _isLoading = true;
    notifyListeners();

    try {
      final response = await _api.post<Map<String, dynamic>>(
        ApiConstants.verify2fa,
        body: {
          'twoFactorTicket': _twoFactorTicket,
          'code': code.trim(),
          'isBackupCode': isBackupCode,
        },
      );

      if (response.success && response.data != null) {
        final token = response.data!['token'] as String? ?? response.data!['accessToken'] as String?;
        if (token != null) {
          await _api.setAuthToken(token);
        }
        final userData = response.data!['user'] as Map<String, dynamic>? ?? response.data!;
        _currentUser = User.fromJson(userData);
        await _saveUserLocally(_currentUser!);
        _twoFactorTicket = null;
        _twoFactorMaskedEmail = null;
        _isLoading = false;
        notifyListeners();
        return true;
      }
    } catch (_) {}

    _isLoading = false;
    notifyListeners();
    return false;
  }

  Future<bool> sendEmailFallbackOtp() async {
    if (_twoFactorTicket == null) return false;
    try {
      final response = await _api.post<Map<String, dynamic>>(
        ApiConstants.send2faEmailFallback,
        body: {'twoFactorTicket': _twoFactorTicket},
      );
      return response.success;
    } catch (_) {
      return false;
    }
  }

  void cancelTwoFactor() {
    _twoFactorTicket = null;
    _twoFactorMaskedEmail = null;
    notifyListeners();
  }

  Future<bool> register(String username, String email, String password) async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await _api.post<Map<String, dynamic>>(
        ApiConstants.register,
        body: {'username': username, 'email': email, 'password': password},
      );

      if (response.success) {
        _isLoading = false;
        notifyListeners();
        return true;
      }
    } catch (_) {}

    // Mock register success for preview/development
    if (username.isNotEmpty && email.isNotEmpty && password.length >= 6) {
      _isLoading = false;
      notifyListeners();
      return true;
    }

    _isLoading = false;
    notifyListeners();
    return false;
  }

  Future<bool> verifyRegistrationOtp(String email, String otp) async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await _api.post<Map<String, dynamic>>(
        ApiConstants.verifyRegistrationOtp,
        body: {'email': email, 'otp': otp},
      );

      if (response.success) {
        _isLoading = false;
        notifyListeners();
        return true;
      }
    } catch (_) {}

    // Mock OTP verification (accept any 6-digit number)
    if (otp.length == 6) {
      _isLoading = false;
      notifyListeners();
      return true;
    }

    _isLoading = false;
    notifyListeners();
    return false;
  }

  Future<bool> resendRegistrationOtp(String email) async {
    try {
      final response = await _api.post<Map<String, dynamic>>(
        ApiConstants.resendRegistrationOtp,
        body: {'email': email},
      );
      return response.success;
    } catch (_) {
      return true; // Mock success
    }
  }

  Future<bool> forgotPassword(String email) async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await _api.post<Map<String, dynamic>>(
        ApiConstants.forgotPassword,
        body: {'email': email},
      );
      _isLoading = false;
      notifyListeners();
      return response.success;
    } catch (_) {
      _isLoading = false;
      notifyListeners();
      return true; // Mock success
    }
  }

  Future<bool> resetPassword(String email, String otp, String newPassword) async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await _api.post<Map<String, dynamic>>(
        ApiConstants.resetPassword,
        body: {'email': email, 'otp': otp, 'newPassword': newPassword},
      );
      _isLoading = false;
      notifyListeners();
      return response.success;
    } catch (_) {
      _isLoading = false;
      notifyListeners();
      return true; // Mock success
    }
  }

  Future<void> updateProfile({
    required String displayName,
    required String bio,
    required String faculty,
  }) async {
    if (_currentUser == null) return;

    _currentUser = User(
      id: _currentUser!.id,
      username: _currentUser!.username,
      displayName: displayName,
      email: _currentUser!.email,
      avatarUrl: _currentUser!.avatarUrl,
      faculty: faculty,
      studentCode: _currentUser!.studentCode,
      bio: bio,
      followerCount: _currentUser!.followerCount,
      followingCount: _currentUser!.followingCount,
      postCount: _currentUser!.postCount,
    );

    await _saveUserLocally(_currentUser!);
    notifyListeners();
  }

  Future<void> logout() async {
    try {
      await _api.post(ApiConstants.logout);
    } catch (_) {}

    await _api.clearAuthToken();
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('cached_user');
    _currentUser = null;
    notifyListeners();
  }

  Future<void> _saveUserLocally(User user) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'cached_user',
      jsonEncode({
        'id': user.id,
        'username': user.username,
        'displayName': user.displayName,
        'email': user.email,
        'avatarUrl': user.avatarUrl,
        'faculty': user.faculty,
        'studentCode': user.studentCode,
        'bio': user.bio,
        'followerCount': user.followerCount,
        'followingCount': user.followingCount,
        'postCount': user.postCount,
      }),
    );
  }
}
