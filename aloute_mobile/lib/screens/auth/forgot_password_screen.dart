import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../services/auth_service.dart';
import '../../widgets/aloute_button.dart';
import 'reset_password_screen.dart';

class ForgotPasswordScreen extends StatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  State<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends State<ForgotPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  bool _isLoading = false;
  String? _statusMessage;
  bool _isSuccess = false;

  @override
  void dispose() {
    _emailController.dispose();
    super.dispose();
  }

  Future<void> _handleSubmit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _isLoading = true;
      _statusMessage = null;
    });

    final success = await AuthService().forgotPassword(_emailController.text.trim());

    setState(() {
      _isLoading = false;
      _isSuccess = success;
      _statusMessage = success
          ? 'Nếu tài khoản tồn tại trên hệ thống, email hướng dẫn đặt lại mật khẩu đã được gửi.'
          : 'Không thể xử lý yêu cầu. Vui lòng kiểm tra lại địa chỉ email.';
    });

    if (success && mounted) {
      Future.delayed(const Duration(seconds: 2), () {
        if (mounted) {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => ResetPasswordScreen(email: _emailController.text.trim()),
            ),
          );
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AlouteColors.textPrimary),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 16.0),
            child: Form(
              key: _formKey,
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // Icon
                  Center(
                    child: Container(
                      width: 68,
                      height: 68,
                      decoration: BoxDecoration(
                        color: AlouteColors.warningBg,
                        borderRadius: BorderRadius.circular(18),
                        border: Border.all(color: AlouteColors.warningBorder),
                      ),
                      child: const Center(
                        child: Icon(
                          Icons.lock_reset,
                          color: AlouteColors.warning,
                          size: 36,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Quên mật khẩu',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 24,
                      fontWeight: FontWeight.w800,
                      color: AlouteColors.textPrimary,
                      letterSpacing: -0.5,
                    ),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Nhập địa chỉ email tài khoản UTE của bạn. Hệ thống sẽ gửi mã xác thực đặt lại mật khẩu.',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 14,
                      color: AlouteColors.textSecondary,
                      height: 1.4,
                    ),
                  ),
                  const SizedBox(height: 28),

                  // Status Alert
                  if (_statusMessage != null)
                    Container(
                      margin: const EdgeInsets.only(bottom: 20),
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: _isSuccess ? AlouteColors.successBg : AlouteColors.dangerBg,
                        border: Border.all(color: _isSuccess ? AlouteColors.successBorder : AlouteColors.dangerBorder),
                        borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                      ),
                      child: Row(
                        children: [
                          Icon(
                            _isSuccess ? Icons.check_circle_outline : Icons.error_outline,
                            color: _isSuccess ? AlouteColors.success : AlouteColors.danger,
                            size: 20,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _statusMessage!,
                              style: TextStyle(
                                color: _isSuccess ? AlouteColors.success : AlouteColors.danger,
                                fontSize: 13,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),

                  // Surface Card Container
                  Container(
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      color: AlouteColors.bgSurface,
                      borderRadius: BorderRadius.circular(AlouteColors.radiusLg),
                      border: Border.all(color: AlouteColors.borderColor),
                      boxShadow: AlouteColors.shadowSm,
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        const Text(
                          'Email UTE',
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: AlouteColors.textPrimary,
                          ),
                        ),
                        const SizedBox(height: 6),
                        TextFormField(
                          controller: _emailController,
                          keyboardType: TextInputType.emailAddress,
                          decoration: InputDecoration(
                            hintText: 'user@student.hcmute.edu.vn',
                            hintStyle: const TextStyle(color: AlouteColors.textMuted, fontSize: 14),
                            prefixIcon: const Icon(Icons.email_outlined, color: AlouteColors.textMuted, size: 20),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                              borderSide: const BorderSide(color: AlouteColors.borderStrong),
                            ),
                            enabledBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                              borderSide: const BorderSide(color: AlouteColors.borderColor),
                            ),
                            focusedBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                              borderSide: const BorderSide(color: AlouteColors.borderFocus, width: 2),
                            ),
                            filled: true,
                            fillColor: Colors.white,
                            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                          ),
                          validator: (value) {
                            if (value == null || !value.contains('@')) {
                              return 'Vui lòng nhập địa chỉ email hợp lệ';
                            }
                            return null;
                          },
                        ),
                        const SizedBox(height: 20),

                        AlouteButton(
                          text: 'Gửi liên kết đặt lại',
                          icon: Icons.send_outlined,
                          isLoading: _isLoading,
                          onPressed: _handleSubmit,
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 24),
                  // Back to Login Link
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Text(
                        'Đã nhớ mật khẩu? ',
                        style: TextStyle(color: AlouteColors.textSecondary, fontSize: 14),
                      ),
                      GestureDetector(
                        onTap: () => Navigator.pop(context),
                        child: const Text(
                          'Đăng nhập',
                          style: TextStyle(
                            color: AlouteColors.accent,
                            fontSize: 14,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
