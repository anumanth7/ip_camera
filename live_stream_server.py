import cv2
import numpy as np
import threading
import time
import webbrowser
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

# Global state
current_mode = "NORMAL"
lock = threading.Lock()
latest_jpeg = None
running = True

# Vision Processing Pipeline matching the Android Module
def process_frame(frame, mode):
    if mode == "NORMAL":
        return frame

    elif mode == "NIGHT":
        # Software-based low-light enhancement:
        # 1. Convert to luminance
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        # 2. Gamma correction (gamma = 0.45) for shadow lifting
        inv_gamma = 1.0 / 0.45
        table = np.array([((i / 255.0) ** (1.0 / inv_gamma)) * 255 for i in np.arange(0, 256)]).astype("uint8")
        boosted = cv2.LUT(gray, table)
        # 3. Contrast stretching (CLAHE)
        clahe = cv2.createCLAHE(clipLimit=2.5, tileGridSize=(8, 8))
        enhanced = clahe.apply(boosted)
        # 4. Night-vision phosphor tactical tint
        b = (enhanced * 0.75).astype(np.uint8)
        g = enhanced
        r = (enhanced * 0.75).astype(np.uint8)
        return cv2.merge([b, g, r])

    elif mode == "THERMAL":
        # Pseudo-Thermal Visualization:
        # Intensity to false-color LUT (Inferno/Ironbow palette)
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        thermal = cv2.applyColorMap(gray, cv2.COLORMAP_INFERNO)
        return thermal

    return frame

def camera_loop():
    global latest_jpeg, running
    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("[WARN] Could not open default camera, generating test pattern...")
        use_test_pattern = True
    else:
        use_test_pattern = False
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

    t0 = time.time()
    while running:
        if not use_test_pattern:
            ret, frame = cap.read()
            if not ret:
                time.sleep(0.05)
                continue
        else:
            # Generate test scene with gradient and moving object
            frame = np.zeros((480, 640, 3), dtype=np.uint8)
            for y in range(480):
                frame[y, :] = int(y / 480 * 200)
            cx = int(320 + 200 * np.sin(time.time() * 2))
            cv2.circle(frame, (cx, 240), 60, (255, 255, 255), -1)

        with lock:
            mode = current_mode

        processed = process_frame(frame, mode)

        # Overlay mode badge on frame
        overlay_text = f"MODE: {mode}"
        if mode == "NIGHT":
            sub_text = "Software Enhanced"
        elif mode == "THERMAL":
            sub_text = "Pseudo-Thermal / No Temp Measurement"
        else:
            sub_text = "Original RGB Stream"

        cv2.putText(processed, overlay_text, (20, 40), cv2.FONT_HERSHEY_SIMPLEX, 0.9, (0, 255, 255), 2, cv2.LINE_AA)
        cv2.putText(processed, sub_text, (20, 70), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (200, 200, 200), 1, cv2.LINE_AA)

        # Encode to JPEG (quality 80)
        _, jpeg = cv2.imencode('.jpg', processed, [cv2.IMWRITE_JPEG_QUALITY, 80])
        with lock:
            latest_jpeg = jpeg.tobytes()

        # Limit to ~15 FPS to match camera module spec
        time.sleep(1.0 / 15.0)

    if not use_test_pattern:
        cap.release()

class StreamHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass # Silence verbose HTTP logs

    def do_GET(self):
        global current_mode
        parsed = urlparse(self.path)

        if parsed.path == "/" or parsed.path == "/index.html":
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.end_headers()
            html = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SIH Camera Stream - Monitor</title>
    <style>
        :root {{
            --bg: #0d1117;
            --card: #161b22;
            --border: #30363d;
            --blue: #388bfd;
            --green: #2ea043;
            --orange: #f0883e;
            --text: #f0f6fc;
            --text-sub: #8b949e;
        }}
        body {{
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: var(--bg);
            color: var(--text);
            margin: 0;
            padding: 24px 16px;
            display: flex;
            flex-direction: column;
            align-items: center;
        }}
        .container {{
            max-width: 800px;
            width: 100%;
        }}
        header {{
            text-align: center;
            margin-bottom: 20px;
        }}
        h1 {{
            margin: 0 0 6px 0;
            font-size: 1.8rem;
            letter-spacing: 1px;
        }}
        .sub {{
            color: var(--text-sub);
            font-size: 0.9rem;
        }}
        .video-card {{
            background: var(--card);
            border: 1px solid var(--border);
            border-radius: 12px;
            overflow: hidden;
            box-shadow: 0 8px 30px rgba(0,0,0,0.6);
            display: flex;
            justify-content: center;
            align-items: center;
            position: relative;
            margin-bottom: 20px;
            min-height: 480px;
        }}
        .stream-img {{
            width: 100%;
            max-width: 640px;
            height: auto;
            display: block;
            border-radius: 8px;
        }}
        .badge {{
            position: absolute;
            top: 12px;
            left: 12px;
            background: rgba(0,0,0,0.75);
            backdrop-filter: blur(4px);
            padding: 6px 12px;
            border-radius: 6px;
            font-size: 0.85rem;
            font-weight: bold;
            letter-spacing: 0.5px;
            border: 1px solid rgba(255,255,255,0.15);
        }}
        .mode-selector {{
            display: flex;
            gap: 12px;
            margin-bottom: 20px;
        }}
        .mode-btn {{
            flex: 1;
            padding: 14px;
            border: 1px solid var(--border);
            background: var(--card);
            color: var(--text);
            font-size: 1rem;
            font-weight: bold;
            border-radius: 8px;
            cursor: pointer;
            transition: all 0.2s;
        }}
        .mode-btn:hover {{
            filter: brightness(1.2);
        }}
        .mode-btn.active {{
            border-color: var(--blue);
            background: #1f2a3a;
            box-shadow: 0 0 12px rgba(56, 139, 253, 0.4);
        }}
        .grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
            gap: 16px;
            margin-bottom: 20px;
        }}
        .card {{
            background: var(--card);
            border: 1px solid var(--border);
            border-radius: 10px;
            padding: 16px;
        }}
        .label {{
            font-size: 0.75rem;
            text-transform: uppercase;
            color: var(--text-sub);
            letter-spacing: 0.8px;
            margin-bottom: 4px;
        }}
        .val {{
            font-size: 1.1rem;
            font-weight: 600;
            display: flex;
            align-items: center;
            gap: 8px;
        }}
        .dot {{
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: var(--green);
            box-shadow: 0 0 8px var(--green);
            animation: pulse 1.8s infinite;
        }}
        @keyframes pulse {{
            0% {{ transform: scale(1); opacity: 1; }}
            50% {{ transform: scale(0.85); opacity: 0.5; }}
            100% {{ transform: scale(1); opacity: 1; }}
        }}
        .url-box {{
            background: #090d13;
            border: 1px solid var(--border);
            border-radius: 6px;
            padding: 10px 14px;
            font-family: monospace;
            font-size: 0.95rem;
            color: var(--blue);
            word-break: break-all;
            user-select: all;
        }}
        .disclaimer {{
            background: rgba(240, 136, 62, 0.1);
            border: 1px solid rgba(240, 136, 62, 0.3);
            border-radius: 8px;
            padding: 14px;
            font-size: 0.85rem;
            color: #ffaa66;
            line-height: 1.4;
        }}
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>SIH CAMERA STREAM</h1>
            <div class="sub">Problem Statement 26034 &bull; Camera & Vision Streaming Module</div>
        </header>

        <div class="video-card">
            <div class="badge" id="modeBadge">MODE: {current_mode}</div>
            <img class="stream-img" src="/stream" alt="Live Camera Feed" />
        </div>

        <div class="label" style="margin-bottom: 8px;">Switch Vision Mode:</div>
        <div class="mode-selector">
            <button class="mode-btn {'active' if current_mode == 'NORMAL' else ''}" onclick="setMode('NORMAL')">[ NORMAL ]</button>
            <button class="mode-btn {'active' if current_mode == 'NIGHT' else ''}" onclick="setMode('NIGHT')">[ NIGHT ]</button>
            <button class="mode-btn {'active' if current_mode == 'THERMAL' else ''}" onclick="setMode('THERMAL')">[ THERMAL ]</button>
        </div>

        <div class="grid">
            <div class="card">
                <div class="label">Status</div>
                <div class="val"><span class="dot"></span> STREAMING</div>
            </div>
            <div class="card">
                <div class="label">Current Mode</div>
                <div class="val" id="modeVal" style="color: var(--blue);">{current_mode} VISION</div>
            </div>
            <div class="card">
                <div class="label">Port</div>
                <div class="val">8080</div>
            </div>
        </div>

        <div class="card" style="margin-bottom: 20px;">
            <div class="label">Stream URL (Direct MJPEG)</div>
            <div class="url-box">http://localhost:8080/stream</div>
        </div>

        <div class="disclaimer">
            <strong>Technical Note:</strong> Night Vision mode is software-enhanced RGB imagery, not true infrared night vision. Thermal mode is a pseudo-thermal visualization and does not measure temperature.
        </div>
    </div>

    <script>
        async function setMode(mode) {{
            await fetch('/set_mode?mode=' + mode);
            document.querySelectorAll('.mode-btn').forEach(btn => {{
                if (btn.innerText.includes(mode)) btn.classList.add('active');
                else btn.classList.remove('active');
            }});
            document.getElementById('modeBadge').innerText = 'MODE: ' + mode;
            document.getElementById('modeVal').innerText = mode + (mode === 'THERMAL' ? ' (PSEUDO)' : ' VISION');
        }}
    </script>
</body>
</html>"""
            self.wfile.write(html.encode('utf-8'))

        elif parsed.path == "/stream":
            self.send_response(200)
            self.send_header('Content-Type', 'multipart/x-mixed-replace; boundary=--frame')
            self.send_header('Cache-Control', 'no-cache, no-store, must-revalidate')
            self.send_header('Pragma', 'no-cache')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()

            try:
                while running:
                    with lock:
                        frame_bytes = latest_jpeg

                    if frame_bytes is not None:
                        self.wfile.write(b"--frame\r\n")
                        self.wfile.write(b"Content-Type: image/jpeg\r\n")
                        self.wfile.write(f"Content-Length: {len(frame_bytes)}\r\n\r\n".encode('ascii'))
                        self.wfile.write(frame_bytes)
                        self.wfile.write(b"\r\n")
                        self.wfile.flush()

                    time.sleep(1.0 / 15.0)
            except (BrokenPipeError, ConnectionResetError):
                pass

        elif parsed.path == "/set_mode":
            query = parse_qs(parsed.query)
            new_mode = query.get('mode', ['NORMAL'])[0].upper()
            if new_mode in ["NORMAL", "NIGHT", "THERMAL"]:
                with lock:
                    current_mode = new_mode
                print(f"[INFO] Mode switched to {new_mode}")
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(b'{"status":"ok"}')

        elif parsed.path == "/status":
            with lock:
                m = current_mode
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(f'{{"status":"streaming","mode":"{m}"}}'.encode('utf-8'))

        else:
            self.send_response(404)
            self.end_headers()

def run_server():
    server = HTTPServer(('0.0.0.0', 8080), StreamHandler)
    print("=" * 60)
    print(" SIH IP Camera Server running on http://localhost:8080/")
    print(" Direct MJPEG Stream: http://localhost:8080/stream")
    print("=" * 60)
    server.serve_forever()

if __name__ == '__main__':
    t_cam = threading.Thread(target=camera_loop, daemon=True)
    t_cam.start()

    t_srv = threading.Thread(target=run_server, daemon=True)
    t_srv.start()

    time.sleep(1)
    webbrowser.open("http://localhost:8080/")

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        running = False
