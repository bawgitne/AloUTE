import 'package:flutter_test/flutter_test.dart';
import 'package:aloute_mobile/main.dart';

void main() {
  testWidgets('AloUTE app initialization smoke test', (WidgetTester tester) async {
    await tester.pumpWidget(const AloUteApp());
    expect(find.byType(AloUteApp), findsOneWidget);
    await tester.pump(const Duration(milliseconds: 1500));
    await tester.pump(const Duration(milliseconds: 500));
  });
}
