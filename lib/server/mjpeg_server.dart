import 'dart:typed_data';
import 'mjpeg_server_stub.dart' if (dart.library.io) 'mjpeg_server_io.dart';

abstract class MjpegServer {
  bool get isRunning;
  int get port;
  int get clientCount;
  String? get serverUrl;

  Future<bool> start({int port = 8080, required String Function() getActiveModeName});
  Future<void> stop();
  void broadcastFrame(Uint8List jpegBytes);

  factory MjpegServer() => createMjpegServer();
}
