import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import 'mjpeg_server.dart';

class MjpegServerIo implements MjpegServer {
  HttpServer? _server;
  final List<HttpResponse> _clients = [];
  int _port = 8080;
  String Function()? _getActiveModeName;
  String? _localIp;

  @override
  bool get isRunning => _server != null;

  @override
  int get port => _port;

  @override
  int get clientCount => _clients.length;

  @override
  String? get serverUrl => _localIp != null ? 'http://$_localIp:$_port' : 'http://localhost:$_port';

  Future<String> _findLocalIp() async {
    try {
      final interfaces = await NetworkInterface.list(
        type: InternetAddressType.IPv4,
        includeLoopback: false,
      );
      for (final interface in interfaces) {
        for (final addr in interface.addresses) {
          if (!addr.isLoopback) {
            return addr.address;
          }
        }
      }
    } catch (_) {}
    return '127.0.0.1';
  }

  @override
  Future<bool> start({int port = 8080, required String Function() getActiveModeName}) async {
    if (_server != null) return true;
    _port = port;
    _getActiveModeName = getActiveModeName;
    _localIp = await _findLocalIp();

    try {
      _server = await HttpServer.bind(InternetAddress.anyIPv4, _port);
      _server!.listen(_handleRequest, onError: (e) {
        // Server listener error
      });
      return true;
    } catch (e) {
      return false;
    }
  }

  void _handleRequest(HttpRequest request) {
    // CORS headers for all responses
    request.response.headers.set('Access-Control-Allow-Origin', '*');
    request.response.headers.set('Access-Control-Allow-Methods', 'GET, OPTIONS');
    request.response.headers.set('Access-Control-Allow-Headers', '*');

    if (request.method == 'OPTIONS') {
      request.response.statusCode = HttpStatus.ok;
      request.response.close();
      return;
    }

    final path = request.uri.path;
    if (path == '/stream') {
      _handleStream(request);
    } else if (path == '/status') {
      _handleStatus(request);
    } else {
      _handleDashboard(request);
    }
  }

  void _handleStream(HttpRequest request) {
    final response = request.response;
    response.headers.set('Content-Type', 'multipart/x-mixed-replace; boundary=frame');
    response.headers.set('Cache-Control', 'no-cache, private, no-store, must-revalidate, max-age=0, post-check=0, pre-check=0');
    response.headers.set('Pragma', 'no-cache');
    response.headers.set('Connection', 'close');

    _clients.add(response);
    response.done.then((_) {
      _clients.remove(response);
    }).catchError((_) {
      _clients.remove(response);
    });
  }

  void _handleStatus(HttpRequest request) {
    final status = {
      'server': 'IP Camera Streamer (Flutter)',
      'status': isRunning ? 'running' : 'stopped',
      'port': _port,
      'mode': _getActiveModeName?.call() ?? 'NORMAL',
      'clients': _clients.length,
      'timestamp': DateTime.now().toIso8601String(),
    };
    request.response.headers.contentType = ContentType.json;
    request.response.write(jsonEncode(status));
    request.response.close();
  }

  void _handleDashboard(HttpRequest request) {
    final mode = _getActiveModeName?.call() ?? 'NORMAL';
    final html = '''<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>IP Camera Streamer</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #121212; color: #fff; margin: 0; padding: 20px; text-align: center; }
    .card { max-width: 720px; margin: 0 auto; background: #1e1e1e; border-radius: 12px; padding: 24px; box-shadow: 0 4px 20px rgba(0,0,0,0.5); }
    h1 { margin-top: 0; font-size: 24px; color: #4caf50; }
    .stream-box { width: 100%; max-width: 640px; min-height: 360px; background: #000; border-radius: 8px; overflow: hidden; margin: 20px auto; display: flex; align-items: center; justify-content: center; border: 2px solid #333; }
    .stream-box img { width: 100%; height: auto; max-height: 480px; object-fit: contain; }
    .badge { display: inline-block; padding: 6px 14px; border-radius: 20px; background: #333; font-weight: bold; font-size: 14px; margin: 8px; }
    .status-badge { background: #2e7d32; }
    .info { color: #aaa; font-size: 14px; margin: 8px 0; }
    a { color: #64b5f6; text-decoration: none; }
  </style>
</head>
<body>
  <div class="card">
    <h1>SIH 2026 - IP Camera Feed</h1>
    <div>
      <span class="badge status-badge">STREAMING LIVE</span>
      <span class="badge">Mode: $mode</span>
      <span class="badge">Clients: ${_clients.length}</span>
    </div>
    <div class="stream-box">
      <img src="/stream" alt="Live Camera MJPEG Stream" />
    </div>
    <p class="info">Direct Stream URL: <a href="/stream">/stream</a> &bull; Status API: <a href="/status">/status</a></p>
  </div>
</body>
</html>''';
    request.response.headers.contentType = ContentType.html;
    request.response.write(html);
    request.response.close();
  }

  @override
  void broadcastFrame(Uint8List jpegBytes) {
    if (_clients.isEmpty) return;
    final header = ascii.encode('--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpegBytes.length}\r\n\r\n');
    final footer = ascii.encode('\r\n');

    for (final client in List.of(_clients)) {
      try {
        client.add(header);
        client.add(jpegBytes);
        client.add(footer);
        client.flush().catchError((_) {
          _clients.remove(client);
        });
      } catch (_) {
        _clients.remove(client);
      }
    }
  }

  @override
  Future<void> stop() async {
    for (final client in List.of(_clients)) {
      try {
        await client.close();
      } catch (_) {}
    }
    _clients.clear();
    await _server?.close(force: true);
    _server = null;
  }
}

MjpegServer createMjpegServer() => MjpegServerIo();
