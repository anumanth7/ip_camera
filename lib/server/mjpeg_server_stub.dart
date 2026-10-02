import 'dart:typed_data';
import 'mjpeg_server.dart';

class MjpegServerStub implements MjpegServer {
  bool _running = false;
  int _port = 8080;

  @override
  bool get isRunning => _running;

  @override
  int get port => _port;

  @override
  int get clientCount => 0;

  @override
  String? get serverUrl => 'Web Preview Mode (TCP Server active on Android/Desktop)';

  @override
  Future<bool> start({int port = 8080, required String Function() getActiveModeName}) async {
    _port = port;
    _running = true;
    return true;
  }

  @override
  Future<void> stop() async {
    _running = false;
  }

  @override
  void broadcastFrame(Uint8List jpegBytes) {
    // In Web browser, raw TCP server socket binding is not permitted by browser sandbox.
  }
}

MjpegServer createMjpegServer() => MjpegServerStub();
