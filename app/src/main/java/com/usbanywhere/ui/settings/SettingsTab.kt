package com.usbanywhere.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usbanywhere.audio.UsbAudioBridge
import com.usbanywhere.net.wan.CyberQrGenerator
import com.usbanywhere.net.wan.WanInterfaceDetector
import com.usbanywhere.net.wan.WanPairingToken
import com.usbanywhere.theme.CardBorder
import com.usbanywhere.theme.DeepDarkBg
import com.usbanywhere.theme.ElectricBlue
import com.usbanywhere.theme.NeonCyan
import com.usbanywhere.theme.NeonGreen
import com.usbanywhere.theme.SurfaceDark

@Composable
fun SettingsTab(viewModel: SettingsViewModel) {
    val esports8000Hz by viewModel.isEsports8000HzEnabled.collectAsStateWithLifecycle()
    val keepAwake by viewModel.isKeepAwakeEnabled.collectAsStateWithLifecycle()
    val autostartOnUsb by viewModel.isAutostartOnUsbEnabled.collectAsStateWithLifecycle()
    val autostartOnBoot by viewModel.isAutostartOnBootEnabled.collectAsStateWithLifecycle()
    val kioskMode by viewModel.isKioskModeEnabled.collectAsStateWithLifecycle()
    val serverPort by viewModel.serverPort.collectAsStateWithLifecycle()
    val sunshinePort by viewModel.sunshinePort.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.isNotificationsEnabled.collectAsStateWithLifecycle()
    val hapticFeedbackEnabled by viewModel.isHapticFeedbackEnabled.collectAsStateWithLifecycle()
    val isRootAvailable by viewModel.isRootAvailable.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepDarkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Esports & Performance Tuning
        item {
            Text(
                text = "ESPORTS & LOW-LATENCY TUNING",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    SettingToggleRow(
                        icon = Icons.Default.Bolt,
                        iconColor = NeonGreen,
                        title = "Esports 8000 Hz / Ultra Low Latency",
                        subtitle = "TCP_NODELAY, 64KB Ring Buffers & Direct URB dispatch (bypasses 1ms OS timer batching)",
                        checked = esports8000Hz,
                        onCheckedChange = { viewModel.toggleEsports8000Hz() }
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
                    Spacer(modifier = Modifier.height(14.dp))

                    SettingToggleRow(
                        icon = Icons.Default.Tune,
                        iconColor = NeonCyan,
                        title = "WakeLock & High-Perf Wi-Fi",
                        subtitle = "Prevents OS Wi-Fi sleep throttling and background CPU clock drops during gameplay",
                        checked = keepAwake,
                        onCheckedChange = { viewModel.toggleKeepAwake() }
                    )
                }
            }
        }

        // Section: Startup & System Integration (Strictly Opt-in / Non-intrusive)
        item {
            Text(
                text = "STARTUP & SYSTEM INTEGRATION (OPTIONAL & NON-INTRUSIVE)",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Non-Intrusive Privacy Notice Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Zero-Intrusion Policy",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "All auto-start hooks are strictly disabled by default. USBAnywhere never runs silently in the background, never wakes on boot, and never triggers OS popups unless you explicitly opt in.",
                                    color = Color.LightGray,
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingToggleRow(
                        icon = Icons.Default.PowerSettingsNew,
                        iconColor = ElectricBlue,
                        title = "Auto-Start on Device Boot",
                        subtitle = "Starts USB/IP server service quietly in background upon boot (ideal for headless Shield/TV boxes)",
                        checked = autostartOnBoot,
                        onCheckedChange = { viewModel.toggleAutostartOnBoot() }
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
                    Spacer(modifier = Modifier.height(14.dp))

                    SettingToggleRow(
                        icon = Icons.Default.Usb,
                        iconColor = NeonCyan,
                        title = "Auto-Start on USB Plug-in",
                        subtitle = "Starts server automatically whenever an OTG device is connected to the USB-C port",
                        checked = autostartOnUsb,
                        onCheckedChange = { viewModel.toggleAutostartOnUsb() }
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
                    Spacer(modifier = Modifier.height(14.dp))

                    SettingToggleRow(
                        icon = Icons.Default.Tv,
                        iconColor = NeonGreen,
                        title = "Kiosk Mode / Dedicated Appliance",
                        subtitle = "Optimizes navigation and prevents accidental UI exits for dedicated 24/7 gaming setups",
                        checked = kioskMode,
                        onCheckedChange = { viewModel.toggleKioskMode() }
                    )
                }
            }
        }

        // Section: User Experience & Feedback
        item {
            Text(
                text = "USER EXPERIENCE & FEEDBACK",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    SettingToggleRow(
                        icon = Icons.Default.Notifications,
                        iconColor = ElectricBlue,
                        title = "Mode Transition HUD Toasts",
                        subtitle = "Shows non-intrusive Android alerts when switching between PC streaming and local TV control",
                        checked = notificationsEnabled,
                        onCheckedChange = { viewModel.toggleNotifications() }
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
                    Spacer(modifier = Modifier.height(14.dp))

                    SettingToggleRow(
                        icon = Icons.Default.Vibration,
                        iconColor = NeonGreen,
                        title = "Haptic Feedback & Rumble",
                        subtitle = "Triggers controller vibration motors during hardware testing and connection events",
                        checked = hapticFeedbackEnabled,
                        onCheckedChange = { viewModel.toggleHapticFeedback() }
                    )
                }
            }
        }

        // Section: Network Ports & Services
        item {
            Text(
                text = "NETWORK PORTS & SERVICES",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    PortSettingRow(
                        icon = Icons.Default.Lan,
                        iconColor = NeonCyan,
                        title = "USB/IP Server Daemon Port",
                        subtitle = "RFC standard USB/IP listening socket for client URB mediation",
                        port = serverPort,
                        defaultPort = com.usbanywhere.data.AppPreferences.DEFAULT_SERVER_PORT,
                        onReset = { viewModel.setServerPort(com.usbanywhere.data.AppPreferences.DEFAULT_SERVER_PORT) }
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
                    Spacer(modifier = Modifier.height(14.dp))

                    PortSettingRow(
                        icon = Icons.Default.Router,
                        iconColor = ElectricBlue,
                        title = "Sunshine Handoff Webhook Port",
                        subtitle = "Local embedded HTTP daemon for Moonlight/Sunshine stream auto-handoff",
                        port = sunshinePort,
                        defaultPort = com.usbanywhere.data.AppPreferences.DEFAULT_SUNSHINE_PORT,
                        onReset = { viewModel.setSunshinePort(com.usbanywhere.data.AppPreferences.DEFAULT_SUNSHINE_PORT) }
                    )
                }
            }
        }

        // Section: System Architecture & Root Status
        item {
            Text(
                text = "SYSTEM ARCHITECTURE",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (isRootAvailable) NeonGreen else NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isRootAvailable) "Root Privileges Detected" else "Standard Userspace (No-Root Required)",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isRootAvailable) {
                            "Full access to kernel /dev/vhci_hcd driver enabled. Native system-level USB attachment available."
                        } else {
                            "Operating via Android android.hardware.usb.UsbManager Host API. Exporting and streaming OTG devices runs 100% without root permissions."
                        },
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // Section: WAN Mesh & Tailscale
        item {
            WanMeshCard()
        }

        // Section: UAC2 USB Audio Bridge
        item {
            UsbAudioBridgeCard()
        }

        // Section: Diagnostic Terminal
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE DIAGNOSTIC LOG",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                IconButton(onClick = { viewModel.clearLogs() }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF070A0F)),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    reverseLayout = false
                ) {
                    items(logs) { logLine ->
                        Text(
                            text = logLine,
                            color = if (logLine.contains("Error", true)) Color(0xFFFF5252) else NeonGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // About / Footer
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "USBAnywhere Android v3.0 • Open Source",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Free VirtualHere Alternative • High-Speed USB/IP Engine",
                    color = Color.DarkGray,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun SettingToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    badgeText: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (badgeText != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = if (checked) NeonGreen else Color.Gray,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = iconColor
            )
        )
    }
}

@Composable
fun PortSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    port: Int,
    defaultPort: Int,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = ":$port",
                    color = NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (port != defaultPort) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = onReset, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset port",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WanMeshCard() {
    val netInfo by WanInterfaceDetector.networkInfo.collectAsStateWithLifecycle()
    var showQr by remember { mutableStateOf(false) }

    val host = netInfo.meshIp ?: "127.0.0.1"
    val token = remember(host) {
        WanPairingToken.PairingData(
            host = host,
            port = 3240,
            serverName = "USBAnywhere-Android",
            isTlsEnabled = false
        ).toCompactToken()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VpnLock,
                        contentDescription = null,
                        tint = if (netInfo.isVpnActive) NeonGreen else ElectricBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "WAN MESH & TAILSCALE ZERO-CONFIG",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = netInfo.networkTypeDesc,
                            color = if (netInfo.isVpnActive) NeonGreen else Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(onClick = { WanInterfaceDetector.refresh() }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Play remotely from outside your home over Tailscale (100.x.y.z) or WireGuard without port forwarding.",
                color = Color.LightGray,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Token Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(text = "WAN PAIRING TOKEN:", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = token,
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showQr = !showQr },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (showQr) "Hide QR Code" else "Show QR Code", fontSize = 11.sp)
                }
            }

            if (showQr) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CyberQrMatrixView(data = token)
                }
            }
        }
    }
}

@Composable
fun UsbAudioBridgeCard() {
    val isEnabled by UsbAudioBridge.isEnabled.collectAsStateWithLifecycle()
    val latency by UsbAudioBridge.latencyProfile.collectAsStateWithLifecycle()
    val stats by UsbAudioBridge.stats.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Headset,
                        contentDescription = null,
                        tint = if (isEnabled) NeonGreen else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "UAC2 USB AUDIO & MIC BRIDGE",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (isEnabled) "48 kHz • 16-bit PCM Stereo / Voice Chat" else "Audio Bridge Disabled",
                            color = if (isEnabled) NeonGreen else Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { UsbAudioBridge.setEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = NeonGreen)
                )
            }

            if (isEnabled) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "JITTER BUFFER LATENCY TARGET:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UsbAudioBridge.AudioBufferLatency.values().forEach { profile ->
                        FilterChip(
                            selected = latency == profile,
                            onClick = { UsbAudioBridge.setLatencyProfile(profile) },
                            label = { Text(text = "${profile.ms}ms", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CyberQrMatrixView(data: String) {
    val matrix = remember(data) { CyberQrGenerator.generateMatrix(data) }
    val size = CyberQrGenerator.MATRIX_SIZE

    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .size(150.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        val cellSize = this.size.width / size
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (matrix[r][c]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = androidx.compose.ui.geometry.Offset(c * cellSize, r * cellSize),
                        size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                    )
                }
            }
        }
    }
}
