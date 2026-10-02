import 'dart:math' as math;
import 'dart:typed_data';
import 'package:image/image.dart' as img;
import 'vision_mode.dart';
import 'vision_processor.dart';

class NightVisionProcessor extends VisionProcessor {
  final double gamma;
  final double gain;
  late final Uint8List _gammaTable;

  NightVisionProcessor({this.gamma = 0.45, this.gain = 1.3}) {
    _gammaTable = Uint8List(256);
    for (int i = 0; i < 256; i++) {
      final normalized = i / 255.0;
      final enhanced = math.pow(normalized, gamma) * gain;
      _gammaTable[i] = (enhanced * 255.0).clamp(0.0, 255.0).round();
    }
  }

  @override
  VisionMode get mode => VisionMode.night;

  @override
  img.Image process(img.Image input) {
    final output = img.Image(width: input.width, height: input.height, numChannels: 3);
    for (final pixel in input) {
      final r = _gammaTable[pixel.r.toInt().clamp(0, 255)];
      final g = _gammaTable[pixel.g.toInt().clamp(0, 255)];
      final b = _gammaTable[pixel.b.toInt().clamp(0, 255)];
      output.setPixelRgb(pixel.x, pixel.y, r, g, b);
    }
    return output;
  }

  @override
  img.Image processYPlane(Uint8List yPlane, int width, int height, int rowStride) {
    final step = width > 320 ? 2 : 1;
    final outWidth = width ~/ step;
    final outHeight = height ~/ step;
    final output = img.Image(width: outWidth, height: outHeight, numChannels: 3);
    for (int y = 0; y < outHeight; y++) {
      final inY = y * step;
      final rowOffset = inY * rowStride;
      for (int x = 0; x < outWidth; x++) {
        final inX = x * step;
        final lum = yPlane[rowOffset + inX];
        final enhanced = _gammaTable[lum];
        final g = (enhanced * 1.1).clamp(0, 255).toInt();
        final r = (enhanced * 0.85).clamp(0, 255).toInt();
        final b = (enhanced * 0.85).clamp(0, 255).toInt();
        output.setPixelRgb(x, y, r, g, b);
      }
    }
    return output;
  }
}
