import 'package:flutter_test/flutter_test.dart';
import 'package:aloute_mobile/main.dart';
import 'package:aloute_mobile/services/auth_service.dart';

void main() {
  testWidgets('AloUTE app initialization smoke test', (WidgetTester tester) async {
    final authService = AuthService();
    await tester.pumpWidget(AloUteApp(authService: authService));
    expect(find.text('AloUTE'), findsWidgets);
  });
}
