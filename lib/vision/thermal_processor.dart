import 'dart:typed_data';
import 'package:image/image.dart' as img;
import 'vision_mode.dart';
import 'vision_processor.dart';

class PseudoThermalProcessor extends VisionProcessor {
  late final Uint8List _ironbowLutR;
  late final Uint8List _ironbowLutG;
  late final Uint8List _ironbowLutB;

  PseudoThermalProcessor() {
    _ironbowLutR = Uint8List(256);
    _ironbowLutG = Uint8List(256);
    _ironbowLutB = Uint8List(256);
    _generateIronbowLut();
  }

  /// Generates the standard 256-color FLIR / Ironbow false-color palette
  void _generateIronbowLut() {
    for (int i = 0; i < 256; i++) {
      final t = i / 255.0;
      double r = 0, g = 0, b = 0;

      if (t < 0.15) {
        // Black to Dark Indigo/Blue
        final s = t / 0.15;
        r = 0.1 * s;
        g = 0.0;
        b = 0.4 * s;
      } else if (t < 0.35) {
        // Dark Indigo to Purple / Magenta
        final s = (t - 0.15) / 0.20;
        r = 0.1 + 0.5 * s;
        g = 0.0;
        b = 0.4 + 0.3 * s;
      } else if (t < 0.55) {
        // Magenta to Crimson / Red
        final s = (t - 0.35) / 0.20;
        r = 0.6 + 0.4 * s;
        g = 0.1 * s;
        b = 0.7 * (1.0 - s);
      } else if (t < 0.75) {
        // Red to Orange / Amber
        final s = (t - 0.55) / 0.20;
        r = 1.0;
        g = 0.1 + 0.6 * s;
        b = 0.0;
      } else if (t < 0.90) {
        // Orange to Yellow
        final s = (t - 0.75) / 0.15;
        r = 1.0;
        g = 0.7 + 0.3 * s;
        b = 0.2 * s;
      } else {
        // Yellow to Bright White
        final s = (t - 0.90) / 0.10;
        r = 1.0;
        g = 1.0;
        b = 0.2 + 0.8 * s;
      }

      _ironbowLutR[i] = (r * 255.0).clamp(0.0, 255.0).round();
      _ironbowLutG[i] = (g * 255.0).clamp(0.0, 255.0).round();
      _ironbowLutB[i] = (b * 255.0).clamp(0.0, 255.0).round();
    }
  }

  @override
  VisionMode get mode => VisionMode.thermal;

  @override
  img.Image process(img.Image input) {
    final output = img.Image(width: input.width, height: input.height, numChannels: 3);
    for (final pixel in input) {
      // Relative luminance formula: L = 0.299R + 0.587G + 0.114B
      final lum = (0.299 * pixel.r + 0.587 * pixel.g + 0.114 * pixel.b).round().clamp(0, 255);
      output.setPixelRgb(
        pixel.x,
        pixel.y,
        _ironbowLutR[lum],
        _ironbowLutG[lum],
        _ironbowLutB[lum],
      );
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
        output.setPixelRgb(
          x,
          y,
          _ironbowLutR[lum],
          _ironbowLutG[lum],
          _ironbowLutB[lum],
        );
      }
    }
    return output;
  }

  /// Expose LUT for unit testing
  List<int> getRgbAtLuminance(int l) => [_ironbowLutR[l], _ironbowLutG[l], _ironbowLutB[l]];
}
