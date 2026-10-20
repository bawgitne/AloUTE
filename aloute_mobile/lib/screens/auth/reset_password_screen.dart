import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../services/auth_service.dart';
import '../../widgets/aloute_button.dart';
import 'login_screen.dart';

class ResetPasswordScreen extends StatefulWidget {
  final String email;

  const ResetPasswordScreen({super.key, required this.email});

  @override
  State<ResetPasswordScreen> createState() => _ResetPasswordScreenState();
}

class _ResetPasswordScreenState extends State<ResetPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _otpController = TextEditingController();
  final _newPasswordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();

  bool _obscureNew = true;
  bool _obscureConfirm = true;
  bool _isLoading = false;
  String? _errorMessage;
  String? _successMessage;

  @override
  void dispose() {
    _otpController.dispose();
    _newPasswordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  Future<void> _handleReset() async {
    if (!_formKey.currentState!.validate()) return;

    if (_newPasswordController.text != _confirmPasswordController.text) {
      setState(() => _errorMessage = 'Mật khẩu xác nhận không khớp.');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
      _successMessage = null;
    });

    final success = await AuthService().resetPassword(
      widget.email,
      _otpController.text.trim(),
      _newPasswordController.text,
    );

    setState(() => _isLoading = false);

    if (success && mounted) {
      setState(() {
        _successMessage = 'Đổi mật khẩu thành công! Bạn có thể đăng nhập bằng mật khẩu mới.';
      });
      Future.delayed(const Duration(seconds: 2), () {
        if (mounted) {
          Navigator.pushAndRemoveUntil(
            context,
            MaterialPageRoute(builder: (_) => const LoginScreen()),
            (route) => false,
          );
        }
      });
    } else if (mounted) {
      setState(() {
        _errorMessage = 'Mã xác thực không đúng hoặc đã hết hạn. Vui lòng thử lại.';
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
                  Center(
                    child: Container(
                      width: 68,
                      height: 68,
                      decoration: BoxDecoration(
                        color: AlouteColors.accentSubtle,
                        borderRadius: BorderRadius.circular(18),
                        border: Border.all(color: AlouteColors.borderColor),
                      ),
                      child: const Center(
                        child: Icon(
                          Icons.vpn_key_outlined,
                          color: AlouteColors.accent,
                          size: 34,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Đặt lại mật khẩu',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 24,
                      fontWeight: FontWeight.w800,
                      color: AlouteColors.textPrimary,
                      letterSpacing: -0.5,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Nhập mã xác thực gửi đến ${widget.email} và tạo mật khẩu mới.',
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      fontSize: 14,
                      color: AlouteColors.textSecondary,
                    ),
                  ),
                  const SizedBox(height: 28),

                  if (_successMessage != null)
                    Container(
                      margin: const EdgeInsets.only(bottom: 20),
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AlouteColors.successBg,
                        border: Border.all(color: AlouteColors.successBorder),
                        borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.check_circle_outline, color: AlouteColors.success, size: 20),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _successMessage!,
                              style: const TextStyle(
                                color: AlouteColors.success,
                                fontSize: 13,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),

                  if (_errorMessage != null)
                    Container(
                      margin: const EdgeInsets.only(bottom: 20),
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AlouteColors.dangerBg,
                        border: Border.all(color: AlouteColors.dangerBorder),
                        borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
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
                          'Mã xác thực (OTP)',
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: AlouteColors.textPrimary,
                          ),
                        ),
                        const SizedBox(height: 6),
                        TextFormField(
                          controller: _otpController,
                          keyboardType: TextInputType.number,
                          maxLength: 6,
                          textAlign: TextAlign.center,
                          style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, letterSpacing: 6),
                          decoration: InputDecoration(
                            hintText: '123456',
                            counterText: '',
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
                            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                          ),
                          validator: (v) => (v == null || v.length < 4) ? 'Nhập mã xác thực' : null,
                        ),
                        const SizedBox(height: 16),

                        const Text(
                          'Mật khẩu mới',
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: AlouteColors.textPrimary,
                          ),
                        ),
                        const SizedBox(height: 6),
                        TextFormField(
                          controller: _newPasswordController,
                          obscureText: _obscureNew,
                          decoration: InputDecoration(
                            hintText: 'Tối thiểu 8 ký tự',
                            hintStyle: const TextStyle(color: AlouteColors.textMuted, fontSize: 14),
                            prefixIcon: const Icon(Icons.lock_outline, color: AlouteColors.textMuted, size: 20),
                            suffixIcon: IconButton(
                              icon: Icon(
                                _obscureNew ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                                color: AlouteColors.textMuted,
                                size: 20,
                              ),
                              onPressed: () => setState(() => _obscureNew = !_obscureNew),
                            ),
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
                          validator: (v) => (v == null || v.length < 8) ? 'Tối thiểu 8 ký tự' : null,
                        ),
                        const SizedBox(height: 16),

                        const Text(
                          'Xác nhận mật khẩu mới',
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: AlouteColors.textPrimary,
                          ),
                        ),
                        const SizedBox(height: 6),
                        TextFormField(
                          controller: _confirmPasswordController,
                          obscureText: _obscureConfirm,
                          decoration: InputDecoration(
                            hintText: 'Nhập lại mật khẩu mới',
                            hintStyle: const TextStyle(color: AlouteColors.textMuted, fontSize: 14),
                            prefixIcon: const Icon(Icons.lock_reset_outlined, color: AlouteColors.textMuted, size: 20),
                            suffixIcon: IconButton(
                              icon: Icon(
                                _obscureConfirm ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                                color: AlouteColors.textMuted,
                                size: 20,
                              ),
                              onPressed: () => setState(() => _obscureConfirm = !_obscureConfirm),
                            ),
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
                          validator: (v) => (v == null || v.isEmpty) ? 'Vui lòng xác nhận mật khẩu' : null,
                        ),
                        const SizedBox(height: 24),

                        AlouteButton(
                          text: 'Cập nhật mật khẩu',
                          icon: Icons.check,
                          isLoading: _isLoading,
                          onPressed: _handleReset,
                        ),
                      ],
                    ),
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
