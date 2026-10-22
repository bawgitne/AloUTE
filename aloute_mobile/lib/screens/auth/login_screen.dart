import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../services/auth_service.dart';
import '../../widgets/aloute_button.dart';
import '../main_navigation_screen.dart';
import 'forgot_password_screen.dart';
import 'register_screen.dart';
import 'two_factor_screen.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final TextEditingController _usernameController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final AuthService _authService = AuthService();

  bool _obscurePassword = true;
  String? _errorMessage;
  bool _isLoading = false;

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  void _handleLogin({String? prefillUser, String? prefillPass}) async {
    final username = prefillUser ?? _usernameController.text.trim();
    final password = prefillPass ?? _passwordController.text.trim();

    if (username.isEmpty || password.isEmpty) {
      setState(() => _errorMessage = 'Vui lòng nhập tên đăng nhập và mật khẩu.');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final success = await _authService.login(username, password);

    if (mounted) {
      setState(() => _isLoading = false);
      if (_authService.isTwoFactorRequired) {
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => TwoFactorScreen(
              maskedEmail: _authService.twoFactorMaskedEmail,
            ),
          ),
        );
      } else if (success) {
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => const MainNavigationScreen()),
        );
      } else {
        setState(() => _errorMessage = 'Tên đăng nhập hoặc mật khẩu không chính xác.');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 20),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: AlouteColors.bgSurface,
                  borderRadius: BorderRadius.circular(AlouteColors.radiusLg),
                  border: Border.all(color: AlouteColors.borderColor),
                  boxShadow: AlouteColors.shadowMd,
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    // Brand Logo
                    Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          ClipRRect(
                            borderRadius: BorderRadius.circular(16),
                            child: Image.asset(
                              'assets/images/logo.png',
                              width: 64,
                              height: 64,
                              fit: BoxFit.cover,
                            ),
                          ),
                          const SizedBox(height: 12),
                          Image.asset(
                            'assets/images/logo_text.png',
                            height: 24,
                            fit: BoxFit.contain,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      'Đăng nhập tài khoản',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w800,
                        color: AlouteColors.textPrimary,
                        letterSpacing: -0.3,
                      ),
                    ),
                    const SizedBox(height: 6),
                    const Text(
                      'Mạng xã hội học thuật sinh viên HCMUTE',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 13, color: AlouteColors.textSecondary),
                    ),
                    const SizedBox(height: 24),

                    // Inline Error Banner
                    if (_errorMessage != null) ...[
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: AlouteColors.dangerBg,
                          borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                          border: Border.all(color: AlouteColors.dangerBorder),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.error_outline, color: AlouteColors.danger, size: 20),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                _errorMessage!,
                                style: const TextStyle(
                                  color: AlouteColors.danger,
                                  fontSize: 13,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 16),
                    ],

                    // Username Input
                    const Text(
                      'Tên đăng nhập hoặc Email UTE',
                      style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AlouteColors.textPrimary),
                    ),
                    const SizedBox(height: 6),
                    TextField(
                      controller: _usernameController,
                      decoration: const InputDecoration(
                        hintText: 'Nhập username hoặc @hcmute.edu.vn',
                        prefixIcon: Icon(Icons.person_outline, size: 20),
                      ),
                    ),
                    const SizedBox(height: 16),

                    // Password Input
                    const Text(
                      'Mật khẩu',
                      style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AlouteColors.textPrimary),
                    ),
                    const SizedBox(height: 6),
                    TextField(
                      controller: _passwordController,
                      obscureText: _obscurePassword,
                      decoration: InputDecoration(
                        hintText: '••••••••',
                        prefixIcon: const Icon(Icons.lock_outline, size: 20),
                        suffixIcon: IconButton(
                          icon: Icon(
                            _obscurePassword ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                            size: 20,
                          ),
                          onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                        ),
                      ),
                    ),
                    // Forgot Password Link
                    Align(
                      alignment: Alignment.centerRight,
                      child: GestureDetector(
                        onTap: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(builder: (_) => const ForgotPasswordScreen()),
                          );
                        },
                        child: const Text(
                          'Quên mật khẩu?',
                          style: TextStyle(
                            color: AlouteColors.accent,
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 20),

                    // Submit Button
                    AlouteButton(
                      text: 'Đăng nhập',
                      icon: Icons.login,
                      isLoading: _isLoading,
                      onPressed: () => _handleLogin(),
                    ),
                    const SizedBox(height: 12),

                    // Fast Demo Login
                    AlouteButton(
                      text: '⚡ Đăng nhập mẫu (Sinh viên)',
                      variant: AlouteButtonVariant.secondary,
                      onPressed: () => _handleLogin(prefillUser: 'nguyenvanan', prefillPass: 'ute123456'),
                    ),
                    const SizedBox(height: 8),

                    AlouteButton(
                      text: '🛡️ Đăng nhập Quản trị viên (Admin)',
                      variant: AlouteButtonVariant.outline,
                      onPressed: () => _handleLogin(prefillUser: 'admin', prefillPass: 'admin123'),
                    ),
                    const SizedBox(height: 20),

                    // Register Link
                    Wrap(
                      alignment: WrapAlignment.center,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        const Text(
                          'Chưa có tài khoản? ',
                          style: TextStyle(color: AlouteColors.textSecondary, fontSize: 13),
                        ),
                        GestureDetector(
                          onTap: () {
                            Navigator.push(
                              context,
                              MaterialPageRoute(builder: (_) => const RegisterScreen()),
                            );
                          },
                          child: const Text(
                            'Đăng ký ngay',
                            style: TextStyle(
                              color: AlouteColors.accent,
                              fontSize: 13,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),

                    // Footer
                    const Text(
                      'Phiên bản 1.0.0 • Trường ĐH Sư phạm Kỹ thuật TP.HCM',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 11, color: AlouteColors.textMuted),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
