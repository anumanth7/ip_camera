import 'package:flutter/material.dart';
import 'ui/home_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const IpCameraApp());
}

class IpCameraApp extends StatelessWidget {
  const IpCameraApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'IP Camera Streamer',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF121212),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF4CAF50),
          secondary: Color(0xFF29B6F6),
          surface: Color(0xFF1E1E1E),
        ),
        useMaterial3: true,
      ),
      home: const HomeScreen(),
    );
  }
}
