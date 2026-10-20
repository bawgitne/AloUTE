import 'package:flutter/material.dart';
import '../core/theme/aloute_theme.dart';

enum AlouteButtonVariant {
  primary,
  secondary,
  outline,
  danger,
}

class AlouteButton extends StatelessWidget {
  final String text;
  final VoidCallback? onPressed;
  final AlouteButtonVariant variant;
  final bool isLoading;
  final IconData? icon;
  final double? width;

  const AlouteButton({
    super.key,
    required this.text,
    this.onPressed,
    this.variant = AlouteButtonVariant.primary,
    this.isLoading = false,
    this.icon,
    this.width,
  });

  @override
  Widget build(BuildContext context) {
    Color bg = AlouteColors.accent;
    Color fg = AlouteColors.accentContrast;
    BorderSide? border;

    switch (variant) {
      case AlouteButtonVariant.primary:
        bg = AlouteColors.accent;
        fg = Colors.white;
        break;
      case AlouteButtonVariant.secondary:
        bg = AlouteColors.bgSurfaceHover;
        fg = AlouteColors.textPrimary;
        break;
      case AlouteButtonVariant.outline:
        bg = Colors.transparent;
        fg = AlouteColors.accent;
        border = const BorderSide(color: AlouteColors.accent, width: 1.5);
        break;
      case AlouteButtonVariant.danger:
        bg = AlouteColors.danger;
        fg = Colors.white;
        break;
    }

    final child = isLoading
        ? SizedBox(
            height: 20,
            width: 20,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              valueColor: AlwaysStoppedAnimation<Color>(fg),
            ),
          )
        : Row(
            mainAxisSize: MainAxisSize.min,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (icon != null) ...[
                Icon(icon, size: 18, color: fg),
                const SizedBox(width: 8),
              ],
              Flexible(
                child: Text(
                  text,
                  style: TextStyle(
                    color: fg,
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    letterSpacing: -0.2,
                  ),
                  overflow: TextOverflow.ellipsis,
                  maxLines: 1,
                ),
              ),
            ],
          );

    return SizedBox(
      width: width,
      height: 44,
      child: ElevatedButton(
        onPressed: isLoading ? null : onPressed,
        style: ElevatedButton.styleFrom(
          backgroundColor: bg,
          foregroundColor: fg,
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
            side: border ?? BorderSide.none,
          ),
          padding: const EdgeInsets.symmetric(horizontal: 20),
        ),
        child: child,
      ),
    );
  }
}
