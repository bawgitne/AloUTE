import 'dart:async';
import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../services/auth_service.dart';
import '../../widgets/aloute_button.dart';
import 'login_screen.dart';

class VerifyOtpScreen extends StatefulWidget {
  final String email;

  const VerifyOtpScreen({super.key, required this.email});

  @override
  State<VerifyOtpScreen> createState() => _VerifyOtpScreenState();
}

class _VerifyOtpScreenState extends State<VerifyOtpScreen> {
  final _otpController = TextEditingController();
  bool _isLoading = false;
  String? _errorMessage;
  String? _successMessage;

  int _resendCountdown = 60;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _startCountdown();
  }

  void _startCountdown() {
    _resendCountdown = 60;
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_resendCountdown > 0) {
        setState(() => _resendCountdown--);
      } else {
        timer.cancel();
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    _otpController.dispose();
    super.dispose();
  }

  Future<void> _handleVerify() async {
    final otp = _otpController.text.trim();
    if (otp.length != 6) {
      setState(() => _errorMessage = 'Vui lòng nhập đúng 6 chữ số.');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
      _successMessage = null;
    });

    final success = await AuthService().verifyRegistrationOtp(widget.email, otp);

    setState(() => _isLoading = false);

    if (success && mounted) {
      setState(() {
        _successMessage = 'Xác thực tài khoản thành công! Bạn có thể đăng nhập ngay.';
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
        _errorMessage = 'Mã xác thực không chính xác hoặc đã hết hạn (5 phút).';
      });
    }
  }

  Future<void> _handleResend() async {
    if (_resendCountdown > 0) return;

    setState(() {
      _errorMessage = null;
      _successMessage = 'Đã gửi lại mã xác thực mới đến email của bạn.';
    });
    await AuthService().resendRegistrationOtp(widget.email);
    _startCountdown();
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
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Shield / Verification Icon
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
                        Icons.mark_email_read_outlined,
                        color: AlouteColors.accent,
                        size: 34,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                const Text(
                  'Xác thực tài khoản',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                    color: AlouteColors.textPrimary,
                    letterSpacing: -0.5,
                  ),
                ),
                const SizedBox(height: 8),
                RichText(
                  textAlign: TextAlign.center,
                  text: TextSpan(
                    style: const TextStyle(fontSize: 14, color: AlouteColors.textSecondary, height: 1.4),
                    children: [
                      const TextSpan(text: 'Mã xác thực 6 chữ số đã được gửi đến:\n'),
                      TextSpan(
                        text: widget.email,
                        style: const TextStyle(
                          color: AlouteColors.textPrimary,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const TextSpan(text: '\nVui lòng nhập mã để kích hoạt (hiệu lực 5 phút).'),
                    ],
                  ),
                ),
                const SizedBox(height: 28),

                // Success Feedback Banner
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

                // Error Feedback Banner
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

                // Surface Card Container
                Container(
                  padding: const EdgeInsets.all(24),
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
                        'Mã xác thực (6 số)',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: AlouteColors.textPrimary,
                        ),
                      ),
                      const SizedBox(height: 10),
                      TextFormField(
                        controller: _otpController,
                        keyboardType: TextInputType.number,
                        maxLength: 6,
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          fontSize: 28,
                          fontWeight: FontWeight.w800,
                          letterSpacing: 12,
                          color: AlouteColors.textPrimary,
                        ),
                        decoration: InputDecoration(
                          hintText: '123456',
                          hintStyle: TextStyle(
                            color: AlouteColors.textMuted.withValues(alpha: 0.5),
                            letterSpacing: 12,
                            fontSize: 26,
                          ),
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
                          fillColor: AlouteColors.bgBody,
                          contentPadding: const EdgeInsets.symmetric(vertical: 14),
                        ),
                      ),
                      const SizedBox(height: 20),

                      // Submit Verification Button
                      AlouteButton(
                        text: 'Xác thực tài khoản',
                        icon: Icons.shield_outlined,
                        isLoading: _isLoading,
                        onPressed: _handleVerify,
                      ),
                      const SizedBox(height: 16),

                      // Resend Action
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text(
                            'Chưa nhận được mã?',
                            style: TextStyle(color: AlouteColors.textSecondary, fontSize: 13),
                          ),
                          TextButton(
                            onPressed: _resendCountdown > 0 ? null : _handleResend,
                            style: TextButton.styleFrom(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                            ),
                            child: Text(
                              _resendCountdown > 0 ? 'Gửi lại ($_resendCountdown s)' : 'Gửi lại mã',
                              style: TextStyle(
                                color: _resendCountdown > 0 ? AlouteColors.textMuted : AlouteColors.accent,
                                fontWeight: FontWeight.w600,
                                fontSize: 13,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 24),
                // Back to Login
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Text(
                      'Đã xác thực tài khoản? ',
                      style: TextStyle(color: AlouteColors.textSecondary, fontSize: 14),
                    ),
                    GestureDetector(
                      onTap: () {
                        Navigator.pushAndRemoveUntil(
                          context,
                          MaterialPageRoute(builder: (_) => const LoginScreen()),
                          (route) => false,
                        );
                      },
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
    );
  }
}
