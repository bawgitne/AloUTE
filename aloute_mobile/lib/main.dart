import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'core/theme/aloute_theme.dart';
import 'screens/splash_screen.dart';

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

  runApp(const AloUteApp());
}

class AloUteApp extends StatelessWidget {
  const AloUteApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AloUTE - HCMUTE Social Network',
      debugShowCheckedModeBanner: false,
      theme: AlouteTheme.lightTheme,
      home: const SplashScreen(),
    );
  }
}
