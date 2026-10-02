# SIH 2026 IP Camera & Vision Streaming Module (Problem Statement 26034)

A high-performance, multiplatform **Flutter** camera and local IP streaming application that turns an Android smartphone or web-connected device into a network camera. It captures live camera frames, applies one of three real-time vision processing modes (**Normal Vision**, **Night Vision**, and **Pseudo-Thermal Vision**), and streams live video directly over the local network using an embedded MJPEG HTTP server on port 8080.

---

> [!IMPORTANT]
> ### Technical Limitation & Integrity Disclaimers
> **"The Night Vision mode is software-enhanced RGB imagery, not true infrared night vision. The Thermal mode is a pseudo-thermal visualization and does not measure temperature. True thermal imaging requires a thermal sensor."**
> - **Night Vision Mode**: Standard smartphone camera sensors are standard CMOS RGB sensors. This mode uses a real-time mathematical software enhancement pipeline (gamma curve adjustment $\gamma \approx 0.45$, contrast expansion, and luminance boosting). It does not contain active physical infrared illumination.
> - **Thermal Vision Mode**: Standard phone cameras cannot measure radiometric physical temperature. This mode applies an intensity Look-Up Table (256-color Ironbow colormap palette) false-color visualization to represent pixel brightness gradients. No simulated temperature readings are displayed.

---

## Features

- **Multiplatform Architecture**: Built with Flutter 3.47 / Dart 3.13 supporting Android devices/emulators, Web (Chrome & Edge), and Windows desktop.
- **3 Real-Time Vision Modes**:
  - `Normal Vision`: Standard pass-through feed.
  - `Night Vision`: Software-enhanced low-light pipeline ($\gamma \approx 0.45$, histogram stretching, luminance boosting).
  - `Pseudo-Thermal`: 256-color Ironbow false-color mapping based on relative luminance.
- **In-Flight Mode Switching**: Seamlessly toggle vision modes without stopping the HTTP server or dropping connected clients.
- **Dual-Dispatch Pipeline**: The active vision mode alters both the on-screen preview and the outgoing network stream simultaneously.
- **Background Isolate Processing**: Frame color transformations and JPEG compression run in background isolates via `compute()` to ensure silky smooth 60 FPS UI performance.
- **Embedded MJPEG HTTP Server**:
  - `/` : Live HTML5 web viewer dashboard with embedded stream and stats.
  - `/stream` : Standard `multipart/x-mixed-replace; boundary=frame` MJPEG stream compatible with any browser, VLC, OpenCV, or Python client.
  - `/status` : Real-time JSON telemetry endpoint (`mode`, `status`, `port`, `clients`, `timestamp`).

---

## Project Structure

```
ip_camera/
├── lib/
│   ├── main.dart                      # Application entrypoint & Material 3 Dark theme
│   ├── camera/
│   │   └── camera_service.dart        # Camera lifecycle, CameraX capture & test feed fallback
│   ├── manager/
│   │   └── stream_manager.dart        # Coordinator, frame rate throttling & background isolate dispatch
│   ├── server/
│   │   ├── mjpeg_server.dart          # HTTP Server abstract interface
│   │   ├── mjpeg_server_io.dart       # Pure Dart HttpServer implementation (Android / Desktop)
│   │   └── mjpeg_server_stub.dart     # Web-safe stub implementation
│   ├── ui/
│   │   └── home_screen.dart           # UI layout: live preview, mode buttons, telemetry & controls
│   └── vision/
│       ├── vision_mode.dart           # Mode enum with mandatory disclaimers
│       ├── vision_processor.dart      # Base vision processor contract
│       ├── normal_processor.dart      # Normal pass-through
│       ├── night_processor.dart       # Low-light gamma enhancement
│       └── thermal_processor.dart     # 256-color Ironbow false-color colormap
├── test/
│   ├── vision_processor_test.dart     # Unit tests verifying LUT mapping and gamma curves
│   └── widget_test.dart               # Widget smoke tests
└── android/                           # Native Android host project configured for CameraX
```

---

## How to Build and Run

### 1. Prerequisites
- **Flutter SDK** (3.24+ or 3.47+ recommended)
- **Android SDK** (API 24+) or **Chrome/Edge**
- **Java 17** (Temurin 17 or Android Studio JBR)

### 2. Run in Browser (Chrome / Edge)
```bash
flutter run -d chrome
# or
flutter run -d edge
```

### 3. Run on Android Phone or Emulator
```bash
# Verify connected devices
flutter devices

# Run on selected device
flutter run -d <DEVICE_ID>
```

### 4. Build Debug APK
```bash
flutter build apk --debug
```
Output: `build/app/outputs/flutter-apk/app-debug.apk`

---

## Accessing the Stream

When running on an Android device connected to Wi-Fi:
- **Web Dashboard**: `http://<PHONE_IP>:8080/`
- **Direct MJPEG Stream**: `http://<PHONE_IP>:8080/stream`
- **JSON Telemetry**: `http://<PHONE_IP>:8080/status`

### Opening in VLC Media Player
1. Open VLC &rarr; **Media &rarr; Open Network Stream** (`Ctrl + N`).
2. Enter `http://<PHONE_IP>:8080/stream`.
3. Click **Play**.

### Reading via Python / OpenCV
```python
import cv2

stream_url = "http://<PHONE_IP>:8080/stream"
cap = cv2.VideoCapture(stream_url)

while True:
    ret, frame = cap.read()
    if not ret:
        break
    cv2.imshow("IP Camera Feed", frame)
    if cv2.waitKey(1) == 27:  # ESC to exit
        break

cap.release()
cv2.destroyAllWindows()
```

---

## Testing

Run the automated test suite:
```bash
flutter test
```
All unit tests and widget smoke tests will execute and verify the algorithms.
