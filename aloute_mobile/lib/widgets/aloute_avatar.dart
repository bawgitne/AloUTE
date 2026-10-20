import 'package:flutter/material.dart';
import '../core/theme/aloute_theme.dart';

class AlouteAvatar extends StatelessWidget {
  final String? imageUrl;
  final String name;
  final double size;
  final double borderWidth;
  final Color borderColor;

  const AlouteAvatar({
    super.key,
    this.imageUrl,
    required this.name,
    this.size = 44.0,
    this.borderWidth = 0.0,
    this.borderColor = Colors.white,
  });

  String _getInitials(String name) {
    final cleaned = name.replaceAll(RegExp(r'[\(\)\[\]\{\}]'), '').trim();
    if (cleaned.isEmpty) return 'U';
    final parts = cleaned.split(RegExp(r'\s+'));
    if (parts.length >= 2) {
      return '${parts[0][0]}${parts[parts.length - 1][0]}'.toUpperCase();
    }
    return parts[0][0].toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        border: borderWidth > 0 ? Border.all(color: borderColor, width: borderWidth) : null,
        boxShadow: borderWidth > 0 ? AlouteColors.shadowSm : null,
      ),
      child: ClipOval(
        child: imageUrl != null && imageUrl!.isNotEmpty
            ? Image.network(
                imageUrl!,
                fit: BoxFit.cover,
                errorBuilder: (context, error, stackTrace) => _buildFallback(),
              )
            : _buildFallback(),
      ),
    );
  }

  Widget _buildFallback() {
    return Container(
      color: AlouteColors.accentSubtle,
      alignment: Alignment.center,
      child: Text(
        _getInitials(name),
        style: TextStyle(
          color: AlouteColors.accent,
          fontSize: size * 0.4,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}
