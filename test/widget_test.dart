import 'package:flutter_test/flutter_test.dart';
import 'package:ip_camera/main.dart';

void main() {
  testWidgets('IpCameraApp basic smoke test', (WidgetTester tester) async {
    await tester.pumpWidget(const IpCameraApp());
    expect(find.text('IP Camera Streamer'), findsOneWidget);
    expect(find.text('Normal'), findsOneWidget);
    expect(find.text('Night'), findsOneWidget);
    expect(find.text('Thermal'), findsOneWidget);
  });
}
