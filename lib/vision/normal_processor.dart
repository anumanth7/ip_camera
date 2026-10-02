import 'dart:typed_data';
import 'package:image/image.dart' as img;
import 'vision_mode.dart';
import 'vision_processor.dart';

class NormalVisionProcessor extends VisionProcessor {
  @override
  VisionMode get mode => VisionMode.normal;

  @override
  img.Image process(img.Image input) {
    // Standard RGB pass-through (or cloned if needed)
    return input;
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
        output.setPixelRgb(x, y, lum, lum, lum);
      }
    }
    return output;
  }
}
