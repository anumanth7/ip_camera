import 'dart:typed_data';
import 'package:image/image.dart' as img;
import 'vision_mode.dart';

abstract class VisionProcessor {
  VisionMode get mode;

  /// Process an existing RGB/RGBA Image
  img.Image process(img.Image input);

  /// Fast path: process directly from luminance / Y plane
  img.Image processYPlane(Uint8List yPlane, int width, int height, int rowStride);

  /// Helper to encode to JPEG bytes
  Uint8List encodeJpeg(img.Image image, {int quality = 75}) {
    return Uint8List.fromList(img.encodeJpg(image, quality: quality));
  }
}
