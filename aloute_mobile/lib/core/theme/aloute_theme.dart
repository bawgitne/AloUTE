import 'package:flutter/material.dart';

/// AloUTE Design System Theme Tokens
/// Source of truth: UI_DESIGN_SYSTEM.md & tokens.css
/// Concept: Modern University Social Network — Light Campus Style (HCMUTE)
class AlouteColors {
  // Surfaces & Backgrounds
  static const Color bgBody = Color(0xFFF8FAFC); // Slate-50
  static const Color bgSurface = Color(0xFFFFFFFF);
  static const Color bgSurfaceElevated = Color(0xFFFFFFFF);
  static const Color bgSurfaceHover = Color(0xFFF1F5F9); // Slate-100
  static const Color bgSurfaceActive = Color(0xFFE2E8F0); // Slate-200

  // Borders & Dividers
  static const Color borderColor = Color(0xFFE2E8F0); // Slate-200
  static const Color borderSubtle = Color(0xFFF1F5F9); // Slate-100
  static const Color borderStrong = Color(0xFFCBD5E1); // Slate-300
  static const Color borderFocus = Color(0xFF2563EB); // Academic Blue

  // Typography
  static const Color textPrimary = Color(0xFF0F172A); // Slate-900 (15.8:1)
  static const Color textSecondary = Color(0xFF475569); // Slate-600
  static const Color textMuted = Color(0xFF64748B); // Slate-500
  static const Color textInverse = Color(0xFFFFFFFF);

  // University Academic Blue Accent (HCMUTE)
  static const Color accent = Color(0xFF2563EB); // Blue-600
  static const Color accentHover = Color(0xFF1D4ED8); // Blue-700
  static const Color accentActive = Color(0xFF1E40AF); // Blue-800
  static const Color accentSubtle = Color(0xFFEFF6FF); // Blue-50
  static const Color accentContrast = Color(0xFFFFFFFF);

  // Semantic Status Colors
  static const Color success = Color(0xFF16A34A);
  static const Color successBg = Color(0xFFF0FDF4);
  static const Color successBorder = Color(0xFFBBF7D0);

  static const Color danger = Color(0xFFDC2626);
  static const Color dangerBg = Color(0xFFFEF2F2);
  static const Color dangerBorder = Color(0xFFFECACA);

  static const Color warning = Color(0xFFD97706);
  static const Color warningBg = Color(0xFFFFFBEB);
  static const Color warningBorder = Color(0xFFFDE68A);

  static const Color info = Color(0xFF0284C7);
  static const Color infoBg = Color(0xFFF0F9FF);
  static const Color infoBorder = Color(0xFFBAE6FD);

  // Radii
  static const double radiusSm = 6.0;
  static const double radiusMd = 10.0;
  static const double radiusLg = 14.0;
  static const double radiusFull = 9999.0;

  // Shadows
  static const List<BoxShadow> shadowSm = [
    BoxShadow(
      color: Color(0x0D0F172A),
      blurRadius: 3,
      offset: Offset(0, 1),
    ),
    BoxShadow(
      color: Color(0x0A0F172A),
      blurRadius: 2,
      offset: Offset(0, 1),
    ),
  ];

  static const List<BoxShadow> shadowMd = [
    BoxShadow(
      color: Color(0x120F172A),
      blurRadius: 6,
      offset: Offset(0, 4),
    ),
    BoxShadow(
      color: Color(0x0D0F172A),
      blurRadius: 4,
      offset: Offset(0, 2),
    ),
  ];

  static const List<BoxShadow> shadowLg = [
    BoxShadow(
      color: Color(0x140F172A),
      blurRadius: 15,
      offset: Offset(0, 10),
    ),
  ];
}

class AlouteTheme {
  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      scaffoldBackgroundColor: AlouteColors.bgBody,
      primaryColor: AlouteColors.accent,
      colorScheme: const ColorScheme(
        brightness: Brightness.light,
        primary: AlouteColors.accent,
        onPrimary: AlouteColors.accentContrast,
        secondary: AlouteColors.accentSubtle,
        onSecondary: AlouteColors.accent,
        error: AlouteColors.danger,
        onError: Colors.white,
        surface: AlouteColors.bgSurface,
        onSurface: AlouteColors.textPrimary,
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: AlouteColors.bgSurface,
        foregroundColor: AlouteColors.textPrimary,
        elevation: 0,
        scrolledUnderElevation: 0.5,
        centerTitle: false,
        titleTextStyle: TextStyle(
          color: AlouteColors.textPrimary,
          fontSize: 18,
          fontWeight: FontWeight.w700,
          letterSpacing: -0.2,
        ),
        iconTheme: IconThemeData(color: AlouteColors.textPrimary),
      ),
      cardTheme: CardThemeData(
        color: AlouteColors.bgSurface,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AlouteColors.radiusLg),
          side: const BorderSide(color: AlouteColors.borderColor, width: 1),
        ),
        margin: EdgeInsets.zero,
      ),
      dividerTheme: const DividerThemeData(
        color: AlouteColors.borderColor,
        thickness: 1,
        space: 1,
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AlouteColors.accent,
          foregroundColor: AlouteColors.accentContrast,
          elevation: 0,
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AlouteColors.radiusFull),
          ),
          textStyle: const TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.w600,
            letterSpacing: -0.2,
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AlouteColors.bgSurface,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        hintStyle: const TextStyle(color: AlouteColors.textMuted, fontSize: 14),
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
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AlouteColors.radiusMd),
          borderSide: const BorderSide(color: AlouteColors.danger),
        ),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: AlouteColors.bgSurface,
        selectedItemColor: AlouteColors.accent,
        unselectedItemColor: AlouteColors.textMuted,
        type: BottomNavigationBarType.fixed,
        elevation: 8,
        selectedLabelStyle: TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
        unselectedLabelStyle: TextStyle(fontSize: 12, fontWeight: FontWeight.w500),
      ),
    );
  }
}
