package com.ai.assistance.shower;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import com.ai.assistance.shower.shell.FakeContext;
import com.ai.assistance.shower.shell.Workarounds;
import com.ai.assistance.shower.wrappers.ServiceManager;
import com.ai.assistance.shower.wrappers.WindowManager;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Local WebSocket server which can create a virtual display via reflection and
 * stream frames back to the client.
 */
public class Main {

    private static final String TAG = "ShowerMain";
    private static final int DEFAULT_PORT = 8986;
    private static final int DEFAULT_BIT_RATE = 4_000_000;

    private static final int VIRTUAL_DISPLAY_FLAG_PUBLIC = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private static final int VIRTUAL_DISPLAY_FLAG_PRESENTATION = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
    private static final int VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH = 1 << 6;
    private static final int VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT = 1 << 7;
    private static final int VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
    private static final int VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 1 << 9;
    private static final int VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 << 10;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP = 1 << 11;
    private static final int VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED = 1 << 12;
    private static final int VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED = 1 << 13;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_FOCUS = 1 << 14;
    private static final int VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP = 1 << 15;

    private static Main sInstance;

    private final Context appContext;

    private ScreenWebSocketServer server;
    private VirtualDisplay virtualDisplay;
    private int virtualDisplayId = -1;
    private MediaCodec videoEncoder;
    private Surface encoderSurface;
    private Thread encoderThread;
    private volatile boolean encoderRunning;
    private InputController inputController;

    private static PrintWriter fileLog;

    static synchronized void logToFile(String msg, Throwable t) {
        try {
            if (fileLog == null) {
                fileLog = new PrintWriter(new FileWriter("/data/local/tmp/shower.log", true), true);
            }
            long now = System.currentTimeMillis();
            String line = now + " " + msg;
            fileLog.println(line);
            if (t != null) {
                t.printStackTrace(fileLog);
            }
            broadcastLog(line);
        } catch (IOException e) {
            Log.e(TAG, "logToFile failed", e);
        }
    }

    private static void broadcastLog(String msg) {
        Main instance = sInstance;
        if (instance == null) {
            return;
        }
        ScreenWebSocketServer s = instance.server;
        if (s == null) {
            return;
        }
        try {
            s.broadcast(msg);
        } catch (Exception e) {
            Log.e(TAG, "broadcastLog failed", e);
        }
    }

    private static void prepareMainLooper() {
        Looper.prepare();
        synchronized (Looper.class) {
            try {
                Field field = Looper.class.getDeclaredField("sMainLooper");
                field.setAccessible(true);
                field.set(null, Looper.myLooper());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }

    public static void main(String... args) {
        try {
            prepareMainLooper();
            logToFile("prepareMainLooper ok", null);
        } catch (Throwable t) {
            Log.e(TAG, "prepareMainLooper failed", t);
            logToFile("prepareMainLooper failed: " + t.getMessage(), t);
        }

        try {
            Workarounds.apply();
            logToFile("Workarounds.apply ok", null);
        } catch (Throwable t) {
            Log.e(TAG, "Workarounds.apply failed", t);
            logToFile("Workarounds.apply failed: " + t.getMessage(), t);
        }

        Context context = FakeContext.get();
        Main main = new Main(context);
        main.startServer();
        logToFile("server started", null);

        // Simple self-test: connect to the local WebSocket server and perform a basic WebSocket HTTP handshake.
        new Thread(() -> {
            try {
                logToFile("WS self-test starting", null);
                Socket socket = new Socket("127.0.0.1", DEFAULT_PORT);
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));

                String key = "dGVzdGtleQ=="; // any base64-like string
                String request = "GET / HTTP/1.1\r\n" +
                        "Host: 127.0.0.1:" + DEFAULT_PORT + "\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Key: " + key + "\r\n" +
                        "Sec-WebSocket-Version: 13\r\n" +
                        "\r\n";
                out.print(request);
                out.flush();

                String statusLine = in.readLine();
                Log.i(TAG, "WS self-test HTTP status: " + statusLine);
                logToFile("WS self-test HTTP status: " + statusLine, null);

                socket.close();
            } catch (Exception e) {
                Log.e(TAG, "WS self-test failed", e);
                logToFile("WS self-test failed: " + e.getMessage(), e);
            }
        }, "ShowerWsSelfTest").start();

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Log.e(TAG, "Main thread interrupted", e);
        }
    }

    public Main(Context context) {
        this.appContext = context.getApplicationContext();
        sInstance = this;
        try {
            this.inputController = new InputController();
            logToFile("InputController initialized", null);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to init InputController", t);
            logToFile("Failed to init InputController: " + t.getMessage(), t);
            this.inputController = null;
        }
    }

    /**
     * Convenience entry: create a Main instance and start the WebSocket server.
     */
    public static Main start(Context context) {
        Main main = new Main(context);
        main.startServer();
        return main;
    }

    /**
     * Start a WebSocket server bound to 127.0.0.1:DEFAULT_PORT.
     */
    public synchronized void startServer() {
        if (server != null) {
            return;
        }
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", DEFAULT_PORT);
        server = new ScreenWebSocketServer(address);
        // Set connection loss timeout to 15 seconds.
        server.setConnectionLostTimeout(15);
        server.start();
        Log.i(TAG, "WebSocket server starting on " + address);
        logToFile("WebSocket server starting on " + address, null);
    }

    public synchronized void stopServer() {
        logToFile("stopServer called", null);
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping WebSocket server", e);
                logToFile("Error stopping WebSocket server: " + e.getMessage(), e);
            }
            server = null;
        }
        releaseDisplay();
    }

    private synchronized void ensureVirtualDisplay(int width, int height, int dpi, int bitRate) {
        logToFile("ensureVirtualDisplay requested: " + width + "x" + height + " dpi=" + dpi + " bitRate=" + bitRate, null);
        if (virtualDisplay != null) {
            logToFile("ensureVirtualDisplay: virtualDisplay already exists", null);
            return;
        }

        if (videoEncoder != null) {
            logToFile("ensureVirtualDisplay: videoEncoder already exists", null);
            return;
        }

        // Use RGBA_8888 so that we can easily convert to Bitmap
        try {
            int actualBitRate = bitRate > 0 ? bitRate : DEFAULT_BIT_RATE;

            // Many H.264 encoders require width/height to be aligned to a multiple of 8 (or 16).
            // Using odd sizes (like 1080x2319) can cause MediaCodec.configure() to throw
            // CodecException with no clear message. Align the capture size down to the
            // nearest multiple of 8, similar to scrcpy's alignment logic.
            int alignedWidth = width & ~7;  // multiple of 8
            int alignedHeight = height & ~7; // multiple of 8
            if (alignedWidth <= 0 || alignedHeight <= 0) {
                alignedWidth = Math.max(2, width);
                alignedHeight = Math.max(2, height);
            }

            logToFile("ensureVirtualDisplay using aligned size: " + alignedWidth + "x" + alignedHeight, null);

            MediaFormat format = MediaFormat.createVideoFormat("video/avc", alignedWidth, alignedHeight);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, actualBitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            videoEncoder = MediaCodec.createEncoderByType("video/avc");
            videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoderSurface = videoEncoder.createInputSurface();
            videoEncoder.start();

            int flags = VIRTUAL_DISPLAY_FLAG_PUBLIC
                    | VIRTUAL_DISPLAY_FLAG_PRESENTATION
                    | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                    | VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH
                    | VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT
                    | VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL;

            if (Build.VERSION.SDK_INT >= 33) {
                flags |= VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP
                        | VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED
                        | VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED;
            }

            if (Build.VERSION.SDK_INT >= 34) {
                flags |= VIRTUAL_DISPLAY_FLAG_OWN_FOCUS
                        | VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP;
            }

            // 与 scrcpy 的 DisplayManager.createNewVirtualDisplay 一致：
            // 通过隐藏构造函数 DisplayManager(Context) + FakeContext 创建实例，再调用 createVirtualDisplay。
            java.lang.reflect.Constructor<DisplayManager> ctor = DisplayManager.class.getDeclaredConstructor(Context.class);
            ctor.setAccessible(true);
            DisplayManager dm = ctor.newInstance(FakeContext.get());

            virtualDisplay = dm.createVirtualDisplay(
                    "ShowerVirtualDisplay",
                    alignedWidth,
                    alignedHeight,
                    dpi,
                    encoderSurface,
                    flags
            );

            if (virtualDisplay != null && virtualDisplay.getDisplay() != null) {
                virtualDisplayId = virtualDisplay.getDisplay().getDisplayId();
                logToFile("Virtual display id=" + virtualDisplayId, null);
                try {
                    WindowManager wm = ServiceManager.getWindowManager();
                    wm.setDisplayImePolicy(virtualDisplayId, WindowManager.DISPLAY_IME_POLICY_LOCAL);
                    logToFile("WindowManager.setDisplayImePolicy LOCAL for display=" + virtualDisplayId, null);
                } catch (Throwable t) {
                    logToFile("setDisplayImePolicy failed: " + t.getMessage(), t);
                }
            } else {
                virtualDisplayId = -1;
            }

            if (inputController != null) {
                int id = virtualDisplayId > 0 ? virtualDisplayId : 0;
                inputController.setDisplayId(id);
            }

            encoderRunning = true;
            encoderThread = new Thread(this::encodeLoop, "ShowerVideoEncoder");
            encoderThread.start();

            Log.i(TAG, "Created virtual display and started encoder: " + virtualDisplay);
            logToFile("Created virtual display and started encoder: " + virtualDisplay, null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create virtual display or encoder", e);
            logToFile("Failed to create virtual display or encoder: " + e.getMessage(), e);
            stopEncoder();
        }
    }

    /**
     * Handle a SCREENSHOT command from a specific WebSocket connection.
     *
     * This captures a PNG of the current virtual display using the shell `screencap -d` command
     * and sends it back to the requesting client as a Base64-encoded text frame:
     *   SCREENSHOT_DATA <base64_png>
     */
    private void handleScreenshotRequest(WebSocket conn) {
        if (virtualDisplay == null || virtualDisplay.getDisplay() == null || virtualDisplayId == -1) {
            logToFile("SCREENSHOT requested but no virtual display", null);
            try {
                conn.send("SCREENSHOT_ERROR no_virtual_display");
            } catch (Exception e) {
                Log.e(TAG, "Failed to send SCREENSHOT_ERROR", e);
            }
            return;
        }

        String path = "/data/local/tmp/shower_screenshot.png";
        String cmd = "screencap -d " + virtualDisplayId + " -p " + path;
        Process proc = null;
        try {
            logToFile("SCREENSHOT executing: " + cmd, null);
            proc = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            int exit = proc.waitFor();
            if (exit != 0) {
                logToFile("SCREENSHOT screencap exited with code=" + exit, null);
                conn.send("SCREENSHOT_ERROR screencap_failed:" + exit);
                return;
            }

            File f = new File(path);
            if (!f.exists() || f.length() == 0) {
                logToFile("SCREENSHOT file missing or empty: " + path, null);
                conn.send("SCREENSHOT_ERROR file_missing");
                return;
            }

            byte[] data;
            try (FileInputStream fis = new FileInputStream(f)) {
                data = new byte[(int) f.length()];
                int read = fis.read(data);
                if (read != data.length) {
                    logToFile("SCREENSHOT short read: " + read + " / " + data.length, null);
                }
            }

            // Encode as Base64 without line breaks.
            String b64 = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
            logToFile("SCREENSHOT captured, bytes=" + data.length + ", b64_len=" + b64.length(), null);
            conn.send("SCREENSHOT_DATA " + b64);
        } catch (Exception e) {
            Log.e(TAG, "handleScreenshotRequest failed", e);
            logToFile("SCREENSHOT failed: " + e.getMessage(), e);
            try {
                conn.send("SCREENSHOT_ERROR " + e.getClass().getSimpleName() + ":" + e.getMessage());
            } catch (Exception ignored) {
            }
        } finally {
            if (proc != null) {
                try {
                    proc.destroy();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void encodeLoop() {
        MediaCodec codec = videoEncoder;
        if (codec == null) {
            return;
        }

        BufferInfo bufferInfo = new BufferInfo();

        while (encoderRunning) {
            int index;
            try {
                index = codec.dequeueOutputBuffer(bufferInfo, 10_000);
            } catch (IllegalStateException e) {
                Log.e(TAG, "dequeueOutputBuffer failed", e);
                logToFile("dequeueOutputBuffer failed: " + e.getMessage(), e);
                break;
            }

            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                continue;
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat format = codec.getOutputFormat();
                trySendConfig(format);
            } else if (index >= 0) {
                if (bufferInfo.size > 0 && server != null) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                    if (outputBuffer != null) {
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        byte[] data = new byte[bufferInfo.size];
                        outputBuffer.get(data);
                        server.broadcast(data);
                    }
                }
                codec.releaseOutputBuffer(index, false);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }
    }

    private void trySendConfig(MediaFormat format) {
        if (server == null) {
            return;
        }
        ByteBuffer csd0 = format.getByteBuffer("csd-0");
        ByteBuffer csd1 = format.getByteBuffer("csd-1");
        sendConfigBuffer(csd0);
        sendConfigBuffer(csd1);
    }

    private void sendConfigBuffer(ByteBuffer buffer) {
        if (buffer == null || !buffer.hasRemaining() || server == null) {
            return;
        }
        ByteBuffer dup = buffer.duplicate();
        dup.position(0);
        byte[] data = new byte[dup.remaining()];
        dup.get(data);
        server.broadcast(data);
    }

    private synchronized void releaseDisplay() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        virtualDisplayId = -1;
        if (inputController != null) {
            inputController.setDisplayId(0);
        }
        stopEncoder();
    }

    private void stopEncoder() {
        encoderRunning = false;
        MediaCodec codec = videoEncoder;
        if (codec != null) {
            try {
                codec.signalEndOfInputStream();
            } catch (Exception e) {
                Log.e(TAG, "signalEndOfInputStream failed", e);
            }
        }
        if (encoderThread != null) {
            try {
                encoderThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Encoder thread join interrupted", e);
                Thread.currentThread().interrupt();
            }
            encoderThread = null;
        }
        if (codec != null) {
            try {
                codec.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping codec", e);
            }
            codec.release();
        }
        if (encoderSurface != null) {
            encoderSurface.release();
            encoderSurface = null;
        }
        videoEncoder = null;
    }

    private void launchPackageOnVirtualDisplay(String packageName) {
        logToFile("launchPackageOnVirtualDisplay: " + packageName, null);
        try {
            if (virtualDisplay == null || virtualDisplay.getDisplay() == null || virtualDisplayId == -1) {
                logToFile("launchPackageOnVirtualDisplay: no virtual display", null);
                return;
            }

            PackageManager pm = appContext.getPackageManager();
            if (pm == null) {
                logToFile("launchPackageOnVirtualDisplay: PackageManager is null", null);
                return;
            }

            Intent intent = pm.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                logToFile("launchPackageOnVirtualDisplay: no launch intent for " + packageName, null);
                return;
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            android.os.Bundle options = null;
            if (Build.VERSION.SDK_INT >= 26) {
                android.app.ActivityOptions launchOptions = android.app.ActivityOptions.makeBasic();
                launchOptions.setLaunchDisplayId(virtualDisplayId);
                options = launchOptions.toBundle();
            }

            com.ai.assistance.shower.wrappers.ActivityManager am = ServiceManager.getActivityManager();
            // Do not force-stop for now; mirror scrcpy Device.startApp(forceStop=false)
            am.startActivity(intent, options);

            logToFile("launchPackageOnVirtualDisplay: started " + packageName + " on display " + virtualDisplayId, null);
        } catch (Exception e) {
            Log.e(TAG, "launchPackageOnVirtualDisplay failed", e);
            logToFile("launchPackageOnVirtualDisplay failed: " + e.getMessage(), e);
        }
    }

    private final class ScreenWebSocketServer extends WebSocketServer {

        ScreenWebSocketServer(InetSocketAddress address) {
            super(address);
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            Log.i(TAG, "WebSocket client connected: " + conn.getRemoteSocketAddress());
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            Log.i(TAG, "WebSocket client disconnected: code=" + code + ", reason=" + reason);
            new Thread(() -> {
                try {
                    Thread.sleep(15000);
                    if (getConnections().isEmpty()) {
                        Log.i(TAG, "Last WebSocket client disconnected,trying to stop server");
                        stopServer();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            Log.i(TAG, "Received message: " + message);
            logToFile("WS message: " + message, null);
            // Simple text protocol:
            //   CREATE_DISPLAY width height dpi
            //   STOP
            if (message == null) {
                return;
            }

            String trimmed = message.trim();
            if (trimmed.startsWith("CREATE_DISPLAY")) {
                String[] parts = trimmed.split("\\s+");
                int width = 1080;
                int height = 1920;
                int dpi = 320;
                int bitRate = DEFAULT_BIT_RATE;
                if (parts.length >= 4) {
                    try {
                        width = Integer.parseInt(parts[1]);
                        height = Integer.parseInt(parts[2]);
                        dpi = Integer.parseInt(parts[3]);
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Invalid CREATE_DISPLAY parameters, using defaults", e);
                    }
                }
                if (parts.length >= 5) {
                    try {
                        int kbps = Integer.parseInt(parts[4]);
                        if (kbps > 0) {
                            bitRate = kbps * 1000;
                        }
                    } catch (NumberFormatException e) {
                        logToFile("Invalid CREATE_DISPLAY bitrate, using default: " + trimmed, e);
                    }
                }
                ensureVirtualDisplay(width, height, dpi, bitRate);
            } else if (trimmed.startsWith("TAP")) {
                String[] parts = trimmed.split("\\s+");
                if (inputController != null && parts.length >= 3) {
                    try {
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        inputController.injectTap(x, y);
                        logToFile("TAP injected: " + x + "," + y, null);
                    } catch (NumberFormatException e) {
                        logToFile("Invalid TAP params: " + trimmed, e);
                    }
                }
            } else if (trimmed.startsWith("SWIPE")) {
                String[] parts = trimmed.split("\\s+");
                if (inputController != null && parts.length >= 6) {
                    try {
                        float x1 = Float.parseFloat(parts[1]);
                        float y1 = Float.parseFloat(parts[2]);
                        float x2 = Float.parseFloat(parts[3]);
                        float y2 = Float.parseFloat(parts[4]);
                        long duration = Long.parseLong(parts[5]);
                        inputController.injectSwipe(x1, y1, x2, y2, duration);
                        logToFile("SWIPE injected: " + x1 + "," + y1 + " -> " + x2 + "," + y2 + " d=" + duration, null);
                    } catch (NumberFormatException e) {
                        logToFile("Invalid SWIPE params: " + trimmed, e);
                    }
                }
            } else if (trimmed.startsWith("TOUCH_DOWN")) {
                String[] parts = trimmed.split("\\s+");
                if (inputController != null && parts.length >= 3) {
                    try {
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        inputController.touchDown(x, y);
                    } catch (NumberFormatException e) {
                        logToFile("Invalid TOUCH_DOWN params: " + trimmed, e);
                    }
                }
            } else if (trimmed.startsWith("TOUCH_MOVE")) {
                String[] parts = trimmed.split("\\s+");
                if (inputController != null && parts.length >= 3) {
                    try {
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        inputController.touchMove(x, y);
                    } catch (NumberFormatException e) {
                        logToFile("Invalid TOUCH_MOVE params: " + trimmed, e);
                    }
                }
            } else if (trimmed.startsWith("TOUCH_UP")) {
                String[] parts = trimmed.split("\\s+");
                if (inputController != null && parts.length >= 3) {
                    try {
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        inputController.touchUp(x, y);
                    } catch (NumberFormatException e) {
                        logToFile("Invalid TOUCH_UP params: " + trimmed, e);
                    }
                }
            } else if (trimmed.startsWith("KEY")) {
                String[] parts = trimmed.split("\\s+");
                if (inputController != null && parts.length >= 2) {
                    try {
                        int keyCode = Integer.parseInt(parts[1]);
                        inputController.injectKey(keyCode);
                        logToFile("KEY injected: " + keyCode, null);
                    } catch (NumberFormatException e) {
                        logToFile("Invalid KEY params: " + trimmed, e);
                    }
                }
            } else if (trimmed.startsWith("SCREENSHOT")) {
                handleScreenshotRequest(conn);
            } else if (trimmed.startsWith("LAUNCH_APP")) {
                String[] parts = trimmed.split("\\s+", 2);
                if (parts.length >= 2) {
                    String pkg = parts[1].trim();
                    launchPackageOnVirtualDisplay(pkg);
                } else {
                    logToFile("LAUNCH_APP missing package name", null);
                }
            } else if (trimmed.startsWith("DESTROY_DISPLAY")) {
                releaseDisplay();
            } else if (trimmed.startsWith("STOP")) {
                releaseDisplay();
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            Log.e(TAG, "WebSocket error", ex);
        }

        @Override
        public void onStart() {
            Log.i(TAG, "WebSocket server started");
        }
    }
}
