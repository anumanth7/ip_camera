package com.example.ip_camera

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ip_camera.manager.StreamManager
import com.example.ip_camera.network.NetworkManager
import com.example.ip_camera.ui.theme.Ip_cameraTheme
import com.example.ip_camera.vision.VisionMode

class MainActivity : ComponentActivity() {

    private lateinit var streamManager: StreamManager
    private lateinit var networkManager: NetworkManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep phone screen awake while streaming
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        streamManager = StreamManager(context = applicationContext, port = 8080)
        networkManager = NetworkManager(context = applicationContext)

        enableEdgeToEdge()
        setContent {
            Ip_cameraTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F141C)
                ) {
                    CameraStreamScreen(
                        streamManager = streamManager,
                        networkManager = networkManager,
                        activity = this
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        networkManager.refreshNetworkInfo()
    }

    override fun onDestroy() {
        super.onDestroy()
        streamManager.release()
        networkManager.release()
    }
}

@Composable
fun CameraStreamScreen(
    streamManager: StreamManager,
    networkManager: NetworkManager,
    activity: ComponentActivity
) {
    val context = LocalContext.current

    // Observables from StreamManager & NetworkManager
    val currentMode by streamManager.currentMode.collectAsState()
    val isStreaming by streamManager.isStreaming.collectAsState()
    val previewBitmap by streamManager.previewBitmap.collectAsState()
    val errorMessage by streamManager.errorMessage.collectAsState()

    val ipAddress by networkManager.ipAddress.collectAsState()
    val isWifiConnected by networkManager.isWifiConnected.collectAsState()

    // Permission handling
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            streamManager.startStreaming(activity)
        } else {
            Toast.makeText(context, "Camera permission is required for IP streaming", Toast.LENGTH_LONG).show()
        }
    }

    // Auto-start streaming on initial launch once permission is granted
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission && !isStreaming) {
            streamManager.startStreaming(activity)
        }
    }

    // Auto cleanup if activity exits
    DisposableEffect(Unit) {
        onDispose {
            streamManager.stopStreaming()
        }
    }

    val streamPort = streamManager.port
    val displayIp = ipAddress ?: "Detecting IP..."
    val streamUrl = if (ipAddress != null) "http://$ipAddress:$streamPort/stream" else "http://<PHONE_IP>:$streamPort/stream"

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0F141C)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "SIH CAMERA STREAM",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = Color(0xFFE2E8F0),
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )

            // Camera Preview Box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18202F)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3D52))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isStreaming && previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Live Camera Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        // Mode badge on preview
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = currentMode.title,
                                color = when (currentMode) {
                                    VisionMode.NORMAL -> Color(0xFF60A5FA)
                                    VisionMode.NIGHT -> Color(0xFF4ADE80)
                                    VisionMode.THERMAL -> Color(0xFFF97316)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (!hasCameraPermission) "Camera Permission Required"
                                else if (!isStreaming) "[ CAMERA PREVIEW ]\nStream Stopped"
                                else "Initializing Camera...",
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                            if (!hasCameraPermission) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                                ) {
                                    Text("Grant Camera Permission")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Vision Mode Selector Header
            Text(
                text = "Vision Mode:",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFCBD5E1),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            // Mode Selector Buttons: [ NORMAL ] [ NIGHT ] [ THERMAL ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VisionModeButton(
                    title = "NORMAL",
                    isSelected = currentMode == VisionMode.NORMAL,
                    modifier = Modifier.weight(1f),
                    activeColor = Color(0xFF2563EB)
                ) {
                    streamManager.setVisionMode(VisionMode.NORMAL)
                }

                VisionModeButton(
                    title = "NIGHT",
                    isSelected = currentMode == VisionMode.NIGHT,
                    modifier = Modifier.weight(1f),
                    activeColor = Color(0xFF16A34A)
                ) {
                    streamManager.setVisionMode(VisionMode.NIGHT)
                }

                VisionModeButton(
                    title = "THERMAL",
                    isSelected = currentMode == VisionMode.THERMAL,
                    modifier = Modifier.weight(1f),
                    activeColor = Color(0xFFEA580C)
                ) {
                    streamManager.setVisionMode(VisionMode.THERMAL)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Current Mode Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Current Mode:",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentMode.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (currentMode) {
                            VisionMode.NORMAL -> Color(0xFF60A5FA)
                            VisionMode.NIGHT -> Color(0xFF4ADE80)
                            VisionMode.THERMAL -> Color(0xFFFB923C)
                        }
                    )
                    if (currentMode.subtitle.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentMode.subtitle,
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status & Network Information Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status row
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status:  ",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isStreaming) Color(0xFF22C55E) else Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isStreaming) "STREAMING" else "STOPPED",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isStreaming) Color(0xFF4ADE80) else Color(0xFFF87171)
                        )
                    }

                    // Phone IP row
                    Column {
                        Text(
                            text = "Phone IP:",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = displayIp,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF1F5F9)
                        )
                    }

                    // Port row
                    Column {
                        Text(
                            text = "Port:",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "$streamPort",
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF1F5F9)
                        )
                    }

                    // Stream URL row
                    Column {
                        Text(
                            text = "Stream URL:",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = streamUrl,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF60A5FA)
                        )
                    }

                    if (!isWifiConnected && ipAddress == null) {
                        Text(
                            text = "Notice: Connect phone and dashboard laptop to the same Wi-Fi network.",
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Error display if any
            AnimatedVisibility(visible = errorMessage != null) {
                errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = Color(0xFFF87171),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons: [ START STREAM ] [ STOP STREAM ] [ COPY STREAM URL ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (!hasCameraPermission) {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            streamManager.startStreaming(activity)
                        }
                    },
                    enabled = !isStreaming,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF15803D),
                        disabledContainerColor = Color(0xFF1E293B)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "START STREAM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!isStreaming) Color.White else Color(0xFF64748B)
                    )
                }

                Button(
                    onClick = { streamManager.stopStreaming() },
                    enabled = isStreaming,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB91C1C),
                        disabledContainerColor = Color(0xFF1E293B)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "STOP STREAM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isStreaming) Color.White else Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("IP Camera Stream URL", streamUrl)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Stream URL copied: $streamUrl", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF60A5FA))
            ) {
                Text(
                    text = "COPY STREAM URL",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun VisionModeButton(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) activeColor else Color(0xFF1E293B),
            contentColor = if (isSelected) Color.White else Color(0xFF94A3B8)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) activeColor else Color(0xFF334155)
        )
    ) {
        Text(
            text = title,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp
        )
    }
}