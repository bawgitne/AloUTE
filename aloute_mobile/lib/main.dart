import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'core/theme/aloute_theme.dart';
import 'screens/auth/login_screen.dart';
import 'screens/main_navigation_screen.dart';
import 'services/auth_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Set system navigation & status bar colors for Light Campus Style
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
      systemNavigationBarColor: AlouteColors.bgSurface,
      systemNavigationBarIconBrightness: Brightness.dark,
    ),
  );

  // Initialize Auth service & cached user
  final authService = AuthService();
  await authService.init();

  runApp(AloUteApp(authService: authService));
}

class AloUteApp extends StatelessWidget {
  final AuthService authService;

  const AloUteApp({super.key, required this.authService});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AloUTE - HCMUTE Social Network',
      debugShowCheckedModeBanner: false,
      theme: AlouteTheme.lightTheme,
      home: authService.isAuthenticated
          ? const MainNavigationScreen()
          : const LoginScreen(),
    );
  }
}
