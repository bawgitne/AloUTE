import 'dart:async';
import 'package:flutter/material.dart';
import '../../core/theme/aloute_theme.dart';
import '../../services/auth_service.dart';
import '../../widgets/aloute_button.dart';
import '../main/main_navigation_screen.dart';

class TwoFactorScreen extends StatefulWidget {
  final String? maskedEmail;

  const TwoFactorScreen({super.key, this.maskedEmail});

  @override
  State<TwoFactorScreen> createState() => _TwoFactorScreenState();
}

class _TwoFactorScreenState extends State<TwoFactorScreen> {
  final _codeController = TextEditingController();
  final _authService = AuthService();
  bool _isLoading = false;
  bool _isBackupMode = false;
  String? _errorMessage;
  String? _infoMessage;

  int _emailCooldown = 0;
  Timer? _cooldownTimer;

  @override
  void initState() {
    super.initState();
    _codeController.addListener(_onCodeChanged);
  }

  void _onCodeChanged() {
    final text = _codeController.text.trim();
    if (!_isBackupMode && text.length == 6 && RegExp(r'^\d{6}$').hasMatch(text)) {
      _handleVerify();
    }
  }

  @override
  void dispose() {
    _cooldownTimer?.cancel();
    _codeController.removeListener(_onCodeChanged);
    _codeController.dispose();
    super.dispose();
  }

  void _startEmailCooldown() {
    setState(() => _emailCooldown = 60);
    _cooldownTimer?.cancel();
    _cooldownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_emailCooldown > 0) {
        setState(() => _emailCooldown--);
      } else {
        timer.cancel();
      }
    });
  }

  Future<void> _handleVerify() async {
    final code = _codeController.text.trim();
    if (code.isEmpty) {
      setState(() => _errorMessage = 'Vui lòng nhập mã xác thực.');
      return;
    }

    if (!_isBackupMode && code.length != 6) {
      setState(() => _errorMessage = 'Mã Google Authenticator phải có đúng 6 chữ số.');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final success = await _authService.verifyTwoFactorLogin(code, isBackupCode: _isBackupMode);

    if (mounted) {
      setState(() => _isLoading = false);
      if (success) {
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(builder: (_) => const MainNavigationScreen()),
          (route) => false,
        );
      } else {
        setState(() {
          _errorMessage = _isBackupMode
              ? 'Mã dự phòng không chính xác hoặc đã qua sử dụng.'
              : 'Mã xác thực không đúng hoặc đã hết hạn. Vui lòng thử lại.';
          _codeController.clear();
        });
      }
    }
  }

  Future<void> _handleSendEmailOtp() async {
    if (_emailCooldown > 0) return;

    setState(() {
      _errorMessage = null;
      _infoMessage = 'Đang gửi mã xác thực đến email ${widget.maskedEmail ?? "sinh viên"}...';
    });

    final sent = await _authService.sendEmailFallbackOtp();
    if (mounted) {
      if (sent) {
        setState(() {
          _infoMessage = 'Đã gửi mã xác thực 6 số đến email. Vui lòng kiểm tra hộp thư.';
        });
        _startEmailCooldown();
      } else {
        setState(() {
          _errorMessage = 'Không thể gửi email OTP lúc này. Vui lòng thử lại sau.';
          _infoMessage = null;
        });
      }
    }
  }

  void _toggleBackupMode() {
    setState(() {
      _isBackupMode = !_isBackupMode;
      _codeController.clear();
      _errorMessage = null;
      _infoMessage = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AlouteColors.bgBody,
      appBar: AppBar(
        title: const Text('Xác thực 2 lớp'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () {
            _authService.cancelTwoFactor();
            Navigator.pop(context);
          },
        ),
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 20),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Container(
                padding: const EdgeInsets.all(24),
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
                    // Shield Icon
                    Center(
                      child: Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: AlouteColors.accentSubtle,
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(
                          Icons.shield_outlined,
                          size: 40,
                          color: AlouteColors.accent,
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),

                    Text(
                      _isBackupMode ? 'Nhập mã khôi phục dự phòng' : 'Bảo mật tài khoản 2 lớp',
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                        color: AlouteColors.textPrimary,
                        letterSpacing: -0.3,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      _isBackupMode
                          ? 'Nhập 1 trong các mã dự phòng (8 ký tự) bạn đã lưu khi bật tính năng 2FA.'
                          : 'Nhập mã 6 chữ số từ ứng dụng Google Authenticator trên điện thoại của bạn.',
                      textAlign: TextAlign.center,
                      style: const TextStyle(fontSize: 13, color: AlouteColors.textSecondary),
                    ),
                    const SizedBox(height: 24),

                    // Error Banner
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

                    // Info Banner
                    if (_infoMessage != null) ...[
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: AlouteColors.infoBg,
                          borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                          border: Border.all(color: AlouteColors.borderColor),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.info_outline, color: AlouteColors.info, size: 20),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                _infoMessage!,
                                style: const TextStyle(
                                  color: AlouteColors.info,
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

                    // Input Field
                    TextField(
                      controller: _codeController,
                      keyboardType: _isBackupMode ? TextInputType.text : TextInputType.number,
                      textAlign: TextAlign.center,
                      maxLength: _isBackupMode ? 10 : 6,
                      autofocus: true,
                      style: const TextStyle(
                        fontSize: 26,
                        fontWeight: FontWeight.bold,
                        fontFamily: 'monospace',
                        letterSpacing: 6,
                      ),
                      decoration: InputDecoration(
                        counterText: '',
                        hintText: _isBackupMode ? 'XXXX-XXXX' : '------',
                        hintStyle: TextStyle(
                          color: AlouteColors.textSecondary.withOpacity(0.4),
                          letterSpacing: 6,
                        ),
                        filled: true,
                        fillColor: AlouteColors.bgBody,
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                          borderSide: const BorderSide(color: AlouteColors.borderColor),
                        ),
                        focusedBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
                          borderSide: const BorderSide(color: AlouteColors.accent, width: 2),
                        ),
                      ),
                    ),
                    const SizedBox(height: 20),

                    // Submit Button
                    AlouteButton(
                      text: 'Xác nhận đăng nhập',
                      icon: Icons.check_circle_outline,
                      isLoading: _isLoading,
                      onPressed: _handleVerify,
                    ),
                    const SizedBox(height: 16),

                    const Divider(height: 24),

                    // Fallback Email Option
                    if (!_isBackupMode)
                      TextButton.icon(
                        onPressed: _emailCooldown > 0 ? null : _handleSendEmailOtp,
                        icon: const Icon(Icons.mail_outline, size: 18),
                        label: Text(
                          _emailCooldown > 0
                              ? 'Gửi lại mã qua Email (${_emailCooldown}s)'
                              : 'Nhận mã OTP qua Email sinh viên',
                          style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                        ),
                      ),

                    // Toggle Backup Mode Option
                    TextButton.icon(
                      onPressed: _toggleBackupMode,
                      icon: Icon(_isBackupMode ? Icons.phone_android : Icons.vpn_key_outlined, size: 18),
                      label: Text(
                        _isBackupMode
                            ? 'Dùng mã Google Authenticator'
                            : 'Không có điện thoại? Dùng mã dự phòng',
                        style: const TextStyle(fontSize: 13, color: AlouteColors.textSecondary),
                      ),
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
