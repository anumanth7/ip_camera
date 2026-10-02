import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:camera/camera.dart';
import '../camera/camera_service.dart';
import '../manager/stream_manager.dart';
import '../vision/vision_mode.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final StreamManager _streamManager = StreamManager.instance;
  final CameraService _cameraService = CameraService.instance;

  @override
  void initState() {
    super.initState();
    _cameraService.initialize();
    _streamManager.startStream();
  }

  @override
  void dispose() {
    _cameraService.dispose();
    _streamManager.stopStream();
    super.dispose();
  }

  Color _getModeColor(VisionMode mode) {
    switch (mode) {
      case VisionMode.normal:
        return const Color(0xFF4CAF50); // Green
      case VisionMode.night:
        return const Color(0xFF29B6F6); // Cyan / Blue
      case VisionMode.thermal:
        return const Color(0xFFFF9800); // Amber / Orange
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF121212),
      appBar: AppBar(
        backgroundColor: const Color(0xFF1E1E1E),
        elevation: 2,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'IP Camera Streamer',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18, color: Colors.white),
            ),
            Text(
              'SIH 2026 • Port 8080 MJPEG',
              style: TextStyle(fontSize: 12, color: Colors.grey.shade400),
            ),
          ],
        ),
        actions: [
          ValueListenableBuilder<VisionMode>(
            valueListenable: _streamManager.activeMode,
            builder: (context, mode, _) {
              final color = _getModeColor(mode);
              return Container(
                margin: const EdgeInsets.only(right: 16),
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: color.withAlpha(50),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: color, width: 1.5),
                ),
                child: Text(
                  mode.shortTag,
                  style: TextStyle(
                    color: color,
                    fontWeight: FontWeight.bold,
                    fontSize: 12,
                  ),
                ),
              );
            },
          ),
        ],
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // 1. Live Camera / Processed Preview
              _buildPreviewBox(),

              const SizedBox(height: 16),

              // 2. Vision Mode Selector
              _buildModeSelector(),

              const SizedBox(height: 16),

              // 3. Network & Streaming Status Card
              _buildStatusCard(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPreviewBox() {
    return Container(
      height: 320,
      decoration: BoxDecoration(
        color: Colors.black,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white24, width: 1),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withAlpha(150),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      clipBehavior: Clip.antiAlias,
      child: Stack(
        fit: StackFit.expand,
        children: [
          // Video Frame
          ValueListenableBuilder<Uint8List?>(
            valueListenable: _streamManager.latestFrame,
            builder: (context, frameBytes, _) {
              if (frameBytes != null && frameBytes.isNotEmpty) {
                return Image.memory(
                  frameBytes,
                  fit: BoxFit.contain,
                  gaplessPlayback: true,
                );
              }

              // Fallback to CameraPreview if available
              if (_cameraService.controller != null &&
                  _cameraService.controller!.value.isInitialized) {
                return Center(
                  child: AspectRatio(
                    aspectRatio: _cameraService.controller!.value.aspectRatio,
                    child: CameraPreview(_cameraService.controller!),
                  ),
                );
              }

              return const Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    CircularProgressIndicator(color: Colors.green),
                    SizedBox(height: 12),
                    Text(
                      'Connecting to camera...',
                      style: TextStyle(color: Colors.grey),
                    ),
                  ],
                ),
              );
            },
          ),

          // Synthetic Feed indicator if active
          ValueListenableBuilder<bool>(
            valueListenable: _cameraService.isSynthetic,
            builder: (context, isSynthetic, _) {
              if (!isSynthetic) return const SizedBox.shrink();
              return Positioned(
                top: 8,
                left: 8,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: Colors.black87,
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(color: Colors.cyan, width: 1),
                  ),
                  child: const Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.videocam_outlined, size: 14, color: Colors.cyan),
                      SizedBox(width: 4),
                      Text(
                        'Simulated Feed',
                        style: TextStyle(color: Colors.cyan, fontSize: 11, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),

          // Live FPS badge
          Positioned(
            top: 8,
            right: 8,
            child: ValueListenableBuilder<double>(
              valueListenable: _streamManager.currentFps,
              builder: (context, fps, _) {
                return Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: Colors.black87,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    '${fps.toStringAsFixed(1)} FPS',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                      fontFamily: 'monospace',
                    ),
                  ),
                );
              },
            ),
          ),

          // Legal Disclaimer Overlay
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: ValueListenableBuilder<VisionMode>(
              valueListenable: _streamManager.activeMode,
              builder: (context, mode, _) {
                final disclaimer = mode.disclaimer;
                if (disclaimer == null) return const SizedBox.shrink();
                final isThermal = mode == VisionMode.thermal;
                return Container(
                  color: isThermal
                      ? Colors.deepOrange.shade900.withAlpha(220)
                      : Colors.blueGrey.shade900.withAlpha(220),
                  padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 12),
                  child: Row(
                    children: [
                      Icon(
                        Icons.warning_amber_rounded,
                        color: isThermal ? Colors.amberAccent : Colors.lightBlueAccent,
                        size: 16,
                      ),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          disclaimer,
                          style: TextStyle(
                            color: isThermal ? Colors.amber.shade100 : Colors.lightBlue.shade100,
                            fontSize: 10,
                            fontWeight: FontWeight.w600,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ),
                    ],
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildModeSelector() {
    return ValueListenableBuilder<VisionMode>(
      valueListenable: _streamManager.activeMode,
      builder: (context, activeMode, _) {
        return Row(
          children: [
            Expanded(
              child: _buildModeButton(
                mode: VisionMode.normal,
                title: 'Normal',
                icon: Icons.camera_alt,
                color: const Color(0xFF4CAF50),
                isSelected: activeMode == VisionMode.normal,
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: _buildModeButton(
                mode: VisionMode.night,
                title: 'Night',
                icon: Icons.nightlight_round,
                color: const Color(0xFF29B6F6),
                isSelected: activeMode == VisionMode.night,
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: _buildModeButton(
                mode: VisionMode.thermal,
                title: 'Thermal',
                icon: Icons.whatshot,
                color: const Color(0xFFFF9800),
                isSelected: activeMode == VisionMode.thermal,
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _buildModeButton({
    required VisionMode mode,
    required String title,
    required IconData icon,
    required Color color,
    required bool isSelected,
  }) {
    return InkWell(
      onTap: () => _streamManager.setVisionMode(mode),
      borderRadius: BorderRadius.circular(12),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(vertical: 12),
        decoration: BoxDecoration(
          color: isSelected ? color.withAlpha(50) : const Color(0xFF1E1E1E),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: isSelected ? color : Colors.white12,
            width: isSelected ? 2 : 1,
          ),
        ),
        child: Column(
          children: [
            Icon(icon, color: isSelected ? color : Colors.grey, size: 22),
            const SizedBox(height: 6),
            Text(
              title,
              style: TextStyle(
                color: isSelected ? Colors.white : Colors.grey,
                fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                fontSize: 13,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatusCard() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF1E1E1E),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'STREAM SERVER',
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 1.2,
                  color: Colors.grey,
                ),
              ),
              ValueListenableBuilder<bool>(
                valueListenable: _streamManager.isStreaming,
                builder: (context, streaming, _) {
                  return Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: streaming ? Colors.green.shade900 : Colors.red.shade900,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(
                          width: 8,
                          height: 8,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: streaming ? Colors.greenAccent : Colors.redAccent,
                          ),
                        ),
                        const SizedBox(width: 6),
                        Text(
                          streaming ? 'ONLINE' : 'OFFLINE',
                          style: TextStyle(
                            color: streaming ? Colors.greenAccent : Colors.redAccent,
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ],
          ),
          const SizedBox(height: 14),

          // URL Row
          ValueListenableBuilder<String>(
            valueListenable: _streamManager.serverUrl,
            builder: (context, url, _) {
              final streamUrl = '$url/stream';
              return Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.black38,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.white10),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.link, size: 20, color: Colors.greenAccent),
                    const SizedBox(width: 8),
                    Expanded(
                      child: SelectableText(
                        streamUrl,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 13,
                          fontFamily: 'monospace',
                        ),
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.copy, size: 18, color: Colors.grey),
                      onPressed: () {
                        Clipboard.setData(ClipboardData(text: streamUrl));
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('Stream URL copied to clipboard!'),
                            duration: Duration(seconds: 2),
                          ),
                        );
                      },
                      tooltip: 'Copy URL',
                    ),
                  ],
                ),
              );
            },
          ),

          const SizedBox(height: 14),

          // Telemetry stats row
          Row(
            children: [
              Expanded(
                child: ValueListenableBuilder<int>(
                  valueListenable: _streamManager.clientCount,
                  builder: (context, clients, _) {
                    return _buildStatBox(
                      label: 'ACTIVE CLIENTS',
                      value: '$clients',
                      icon: Icons.people_outline,
                    );
                  },
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: ValueListenableBuilder<double>(
                  valueListenable: _streamManager.currentFps,
                  builder: (context, fps, _) {
                    return _buildStatBox(
                      label: 'STREAM RATE',
                      value: '${fps.toStringAsFixed(1)} FPS',
                      icon: Icons.speed,
                    );
                  },
                ),
              ),
            ],
          ),

          const SizedBox(height: 16),

          // Start/Stop Toggle Button
          ValueListenableBuilder<bool>(
            valueListenable: _streamManager.isStreaming,
            builder: (context, streaming, _) {
              return SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  onPressed: () {
                    if (streaming) {
                      _streamManager.stopStream();
                    } else {
                      _streamManager.startStream();
                    }
                  },
                  icon: Icon(streaming ? Icons.stop : Icons.play_arrow),
                  label: Text(
                    streaming ? 'STOP SERVER' : 'START SERVER',
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: streaming ? Colors.red.shade700 : Colors.green.shade700,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10),
                    ),
                  ),
                ),
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _buildStatBox({
    required String label,
    required String value,
    required IconData icon,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 12),
      decoration: BoxDecoration(
        color: Colors.black26,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 14, color: Colors.grey),
              const SizedBox(width: 6),
              Text(
                label,
                style: const TextStyle(fontSize: 10, color: Colors.grey, fontWeight: FontWeight.bold),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            value,
            style: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.bold,
              color: Colors.white,
              fontFamily: 'monospace',
            ),
          ),
        ],
      ),
    );
  }
}
