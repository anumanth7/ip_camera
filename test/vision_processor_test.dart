import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:ip_camera/vision/night_processor.dart';
import 'package:ip_camera/vision/normal_processor.dart';
import 'package:ip_camera/vision/thermal_processor.dart';
import 'package:ip_camera/vision/vision_mode.dart';

void main() {
  group('VisionMode Tests', () {
    test('VisionMode labels and disclaimers are present', () {
      expect(VisionMode.normal.displayName, equals('NORMAL VISION'));
      expect(VisionMode.normal.disclaimer, isNull);

      expect(VisionMode.night.displayName, equals('NIGHT VISION'));
      expect(VisionMode.night.disclaimer, contains('NIGHT VISION - Software Enhanced'));

      expect(VisionMode.thermal.displayName, equals('PSEUDO-THERMAL'));
      expect(VisionMode.thermal.disclaimer, contains('PSEUDO-THERMAL - No Temperature Measurement'));
    });
  });

  group('NormalVisionProcessor Tests', () {
    test('Normal processing passes image through', () {
      final proc = NormalVisionProcessor();
      final image = img.Image(width: 10, height: 10);
      final processed = proc.process(image);
      expect(processed.width, equals(10));
      expect(processed.height, equals(10));
    });
  });

  group('NightVisionProcessor Tests', () {
    test('Night vision enhances low-light luminance', () {
      final proc = NightVisionProcessor(gamma: 0.45, gain: 1.3);
      final image = img.Image(width: 2, height: 2);
      // Set dark pixels
      for (final p in image) {
        image.setPixelRgb(p.x, p.y, 25, 25, 25);
      }

      final processed = proc.process(image);
      final sample = processed.getPixel(0, 0);

      // Low luminance (25/255 = 0.098) boosted by gamma 0.45: (0.098)^0.45 * 1.3 ~ 0.45 * 255 = 115+
      expect(sample.r.toInt(), greaterThan(50));
      expect(sample.g.toInt(), greaterThan(50));
    });
  });

  group('PseudoThermalProcessor Tests', () {
    test('Ironbow LUT maps 0-255 luminance gradient correctly', () {
      final proc = PseudoThermalProcessor();

      // Luminance 0 (coldest): should be dark indigo/black
      final cold = proc.getRgbAtLuminance(0);
      expect(cold[0], lessThan(30)); // low red
      expect(cold[1], equals(0));    // zero green

      // Luminance 255 (hottest): should be white
      final hot = proc.getRgbAtLuminance(255);
      expect(hot[0], equals(255)); // max red
      expect(hot[1], equals(255)); // max green
      expect(hot[2], equals(255)); // max blue

      // Luminance 180 (mid-high): should be warm yellow/orange (high red, high green, low blue)
      final mid = proc.getRgbAtLuminance(180);
      expect(mid[0], equals(255)); // max red
      expect(mid[1], greaterThan(120)); // strong green (creates orange/amber)
      expect(mid[2], lessThan(80)); // low blue
    });

    test('Full image colorization preserves dimensions', () {
      final proc = PseudoThermalProcessor();
      final image = img.Image(width: 64, height: 48);
      final processed = proc.process(image);
      expect(processed.width, equals(64));
      expect(processed.height, equals(48));
    });
  });
}
