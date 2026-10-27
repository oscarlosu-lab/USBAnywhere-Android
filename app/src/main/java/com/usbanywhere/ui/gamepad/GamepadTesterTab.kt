package com.usbanywhere.ui.gamepad

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usbanywhere.gamepad.GamepadInputManager
import com.usbanywhere.gamepad.GamepadState
import com.usbanywhere.gamepad.PollingRateStats
import com.usbanywhere.gaming.multiplayer.GamepadSlot
import com.usbanywhere.gaming.multiplayer.MultiplayerHubManager
import com.usbanywhere.gaming.sunshine.HandoffMode
import com.usbanywhere.gaming.sunshine.SunshineHandoffManager
import com.usbanywhere.theme.AccentError
import com.usbanywhere.theme.CardBorder
import com.usbanywhere.theme.DeepDarkBg
import com.usbanywhere.theme.ElectricBlue
import com.usbanywhere.theme.NeonCyan
import com.usbanywhere.theme.NeonGreen
import com.usbanywhere.theme.SurfaceDark

@Composable
fun GamepadTesterTab() {
    val gamepadState by GamepadInputManager.gamepadState.collectAsStateWithLifecycle()
    val stats by GamepadInputManager.pollingStats.collectAsStateWithLifecycle()
    val isSimulating by GamepadInputManager.isSimulating.collectAsStateWithLifecycle()
    val activeProfile by GamepadInputManager.activeProfile.collectAsStateWithLifecycle()
    val handoffMode by SunshineHandoffManager.currentMode.collectAsStateWithLifecycle()
    val comboProgress by SunshineHandoffManager.comboProgress.collectAsStateWithLifecycle()
    val multiplayerSlots by MultiplayerHubManager.slots.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepDarkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header & Polling Rate Meter
        item {
            PollingRateHeroCard(
                stats = stats,
                gamepadState = gamepadState,
                isSimulating = isSimulating,
                onToggleSim = { GamepadInputManager.toggleSimulation() },
                onReset = { GamepadInputManager.resetStats() }
            )
        }

        // Esports Active Profile Badge
        item {
            EsportsProfileCard(profileName = activeProfile)
        }

        // Sunshine Synergy & Auto-Handoff Card
        item {
            SunshineHandoffCard(
                mode = handoffMode,
                comboProgress = comboProgress,
                onToggle = { SunshineHandoffManager.toggleMode() }
            )
        }

        // Multiplayer Hub Slots (P1 - P4)
        item {
            MultiplayerSlotsCard(slots = multiplayerSlots)
        }

        // Live Visual Gamepad Display
        item {
            GamepadVisualizerCard(state = gamepadState)
        }
    }
}

@Composable
fun PollingRateHeroCard(
    stats: PollingRateStats,
    gamepadState: GamepadState,
    isSimulating: Boolean,
    onToggleSim: () -> Unit,
    onReset: () -> Unit
) {
    var isSimBtnFocused by remember { mutableStateOf(false) }
    var isResetBtnFocused by remember { mutableStateOf(false) }

    val rateColor = when {
        stats.currentHz >= 7000.0 -> Color(0xFFD946EF) // Electric Violet
        stats.currentHz >= 3500.0 -> ElectricBlue
        stats.currentHz >= 900.0 -> NeonCyan
        stats.currentHz >= 450.0 -> NeonGreen
        else -> Color(0xFFFFB300)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Device Name & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (gamepadState.isConnected) NeonGreen else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = gamepadState.deviceName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onToggleSim,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSimulating) AccentError else ElectricBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .focusable()
                            .onFocusChanged { isSimBtnFocused = it.isFocused }
                            .border(
                                width = if (isSimBtnFocused) 2.dp else 0.dp,
                                color = if (isSimBtnFocused) NeonCyan else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = if (isSimulating) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSimulating) "Stop Sim" else "1000 Hz Sim",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onReset,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF21262D),
                            contentColor = Color.LightGray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .focusable()
                            .onFocusChanged { isResetBtnFocused = it.isFocused }
                            .border(
                                width = if (isResetBtnFocused) 2.dp else 0.dp,
                                color = if (isResetBtnFocused) NeonCyan else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Giant Speedometer Readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "LIVE POLLING RATE",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format("%.0f", stats.currentHz),
                            color = rateColor,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Hz",
                            color = rateColor.copy(alpha = 0.8f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Latency Badge
                val latencyUs = if (stats.currentHz > 0) (1_000_000.0 / stats.currentHz) else 0.0
                Box(
                    modifier = Modifier
                        .background(Color(0xFF0F261B), RoundedCornerShape(8.dp))
                        .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = String.format("LATENCY: %.2f ms", latencyUs / 1000.0),
                        color = NeonGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub-metrics Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1117), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatColumn(label = "AVERAGE", value = String.format("%.0f Hz", stats.avgHz), color = Color.White)
                StatColumn(label = "PEAK MAX", value = String.format("%.0f Hz", stats.maxHz), color = NeonCyan)
                StatColumn(label = "JITTER", value = String.format("±%.2f ms", stats.jitterMs), color = Color(0xFFFFB300))
                StatColumn(label = "REPORTS", value = "${stats.totalReports}", color = Color.LightGray)
            }
        }
    }
}

@Composable
fun StatColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun GamepadVisualizerCard(state: GamepadState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "VISUAL INPUT TESTER",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bumpers & Triggers Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Trigger / Bumper
                TriggerBumperModule(
                    bumperName = "LB (L1)",
                    isBumperPressed = state.btnL1,
                    triggerName = "LT (L2)",
                    triggerValue = state.leftTrigger
                )

                // Right Trigger / Bumper
                TriggerBumperModule(
                    bumperName = "RB (R1)",
                    isBumperPressed = state.btnR1,
                    triggerName = "RT (R2)",
                    triggerValue = state.rightTrigger
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Body: Sticks, D-Pad, Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Stick
                AnalogStickView(
                    label = "LEFT STICK",
                    stickX = state.leftStickX,
                    stickY = state.leftStickY,
                    isPressed = state.btnL3
                )

                // D-Pad Cross
                DPadCrossView(
                    up = state.dpadUp,
                    down = state.dpadDown,
                    left = state.dpadLeft,
                    right = state.dpadRight
                )

                // Center Buttons (Back / Guide / Start)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniButton(label = "BACK", isPressed = state.btnSelect)
                    MiniButton(label = "GUIDE", isPressed = state.btnGuide, isGuide = true)
                    MiniButton(label = "START", isPressed = state.btnStart)
                }

                // ABXY Diamond
                AbxyDiamondView(
                    btnA = state.btnA,
                    btnB = state.btnB,
                    btnX = state.btnX,
                    btnY = state.btnY
                )

                // Right Stick
                AnalogStickView(
                    label = "RIGHT STICK",
                    stickX = state.rightStickX,
                    stickY = state.rightStickY,
                    isPressed = state.btnR3
                )
            }
        }
    }
}

@Composable
fun TriggerBumperModule(
    bumperName: String,
    isBumperPressed: Boolean,
    triggerName: String,
    triggerValue: Float
) {
    val bumperBg by animateColorAsState(if (isBumperPressed) NeonCyan else Color(0xFF21262D))
    val bumperText by animateColorAsState(if (isBumperPressed) Color.Black else Color.White)

    Column(modifier = Modifier.width(140.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bumperBg, RoundedCornerShape(6.dp))
                .border(1.dp, if (isBumperPressed) NeonCyan else CardBorder, RoundedCornerShape(6.dp))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = bumperName, color = bumperText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = triggerName, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                text = String.format("%.0f%%", triggerValue * 100),
                color = if (triggerValue > 0.05f) NeonGreen else Color.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { triggerValue },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = NeonGreen,
            trackColor = Color(0xFF1B222D)
        )
    }
}

@Composable
fun AnalogStickView(
    label: String,
    stickX: Float,
    stickY: Float,
    isPressed: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .size(110.dp)
                .background(Color(0xFF0F141C), CircleShape)
                .border(2.dp, if (isPressed) NeonCyan else CardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val maxRadius = (size.width / 2) * 0.7f

                // Crosshairs
                drawLine(
                    color = Color(0xFF2A3649),
                    start = Offset(center.x, center.y - maxRadius),
                    end = Offset(center.x, center.y + maxRadius),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color(0xFF2A3649),
                    start = Offset(center.x - maxRadius, center.y),
                    end = Offset(center.x + maxRadius, center.y),
                    strokeWidth = 1.dp.toPx()
                )

                // Stick dot position
                val dotX = center.x + (stickX.coerceIn(-1f, 1f) * maxRadius)
                val dotY = center.y + (stickY.coerceIn(-1f, 1f) * maxRadius)

                // Glow ring
                drawCircle(
                    color = if (isPressed) NeonCyan.copy(alpha = 0.4f) else ElectricBlue.copy(alpha = 0.3f),
                    radius = 16.dp.toPx(),
                    center = Offset(dotX, dotY)
                )

                // Center stick head
                drawCircle(
                    color = if (isPressed) NeonCyan else ElectricBlue,
                    radius = 10.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = String.format("X:%+.2f Y:%+.2f", stickX, stickY),
            color = Color.LightGray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun DPadCrossView(
    up: Boolean,
    down: Boolean,
    left: Boolean,
    right: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DPadButton(label = "▲", isPressed = up)
        Row {
            DPadButton(label = "◀", isPressed = left)
            Box(modifier = Modifier.size(32.dp).background(Color(0xFF1B222D)))
            DPadButton(label = "▶", isPressed = right)
        }
        DPadButton(label = "▼", isPressed = down)
    }
}

@Composable
fun DPadButton(label: String, isPressed: Boolean) {
    val bg by animateColorAsState(if (isPressed) NeonCyan else Color(0xFF21262D))
    val text by animateColorAsState(if (isPressed) Color.Black else Color.White)

    Box(
        modifier = Modifier
            .size(32.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, if (isPressed) NeonCyan else CardBorder, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AbxyDiamondView(
    btnA: Boolean,
    btnB: Boolean,
    btnX: Boolean,
    btnY: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FaceButton(label = "Y", isPressed = btnY, activeColor = Color(0xFFFFB300))
        Row {
            FaceButton(label = "X", isPressed = btnX, activeColor = ElectricBlue)
            Spacer(modifier = Modifier.width(32.dp))
            FaceButton(label = "B", isPressed = btnB, activeColor = AccentError)
        }
        FaceButton(label = "A", isPressed = btnA, activeColor = NeonGreen)
    }
}

@Composable
fun FaceButton(label: String, isPressed: Boolean, activeColor: Color) {
    val bg by animateColorAsState(if (isPressed) activeColor else Color(0xFF21262D))
    val text by animateColorAsState(if (isPressed) Color.Black else activeColor)

    Box(
        modifier = Modifier
            .size(36.dp)
            .background(bg, CircleShape)
            .border(1.5.dp, if (isPressed) activeColor else CardBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun MiniButton(label: String, isPressed: Boolean, isGuide: Boolean = false) {
    val bg by animateColorAsState(if (isPressed) NeonCyan else Color(0xFF21262D))
    val text by animateColorAsState(if (isPressed) Color.Black else Color.LightGray)

    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.dp, if (isPressed) NeonCyan else CardBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = if (isGuide) 14.dp else 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EsportsProfileCard(profileName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = ElectricBlue,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ACTIVE ESPORTS PROFILE",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = profileName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "HARDWARE TUNED",
                    color = NeonCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SunshineHandoffCard(
    mode: HandoffMode,
    comboProgress: Float,
    onToggle: () -> Unit
) {
    var isToggleFocused by remember { mutableStateOf(false) }

    val modeColor = when (mode) {
        HandoffMode.LOCAL_ANDROID_TV -> NeonGreen
        HandoffMode.STREAMING_PC -> NeonCyan
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SettingsRemote,
                        contentDescription = null,
                        tint = modeColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SUNSHINE & MOONLIGHT SYNERGY",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .background(modeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .border(1.dp, modeColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = mode.badgeText,
                        color = modeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = mode.description,
                color = Color.LightGray,
                fontSize = 12.sp
            )

            if (comboProgress > 0f) {
                Spacer(modifier = Modifier.height(10.dp))
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Quick-Toggle (Hold Guide + Select)",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(comboProgress * 100).toInt()}%",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { comboProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NeonCyan,
                        trackColor = Color(0xFF1E293B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sunshine Webhook Hook:",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "POST http://<IP>:3242/api/stream/start",
                        color = ElectricBlue,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mode == HandoffMode.LOCAL_ANDROID_TV) Color(0xFF1E293B) else Color(0xFF0F3D3E),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .focusable()
                        .onFocusChanged { isToggleFocused = it.isFocused }
                        .border(
                            width = if (isToggleFocused) 2.dp else 1.dp,
                            color = if (isToggleFocused) NeonCyan else CardBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (mode == HandoffMode.LOCAL_ANDROID_TV) "Switch to PC" else "Switch to TV",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MultiplayerSlotsCard(slots: List<GamepadSlot>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "MULTIPLAYER HUB (P1 - P4)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Deterministic Player Slots • Zero Reconnection Scramble",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                slots.forEach { slot ->
                    val slotColor = try {
                        Color(android.graphics.Color.parseColor(slot.slotColorHex))
                    } catch (e: Exception) {
                        ElectricBlue
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF161B22), RoundedCornerShape(10.dp))
                            .border(
                                width = 1.dp,
                                color = if (slot.isConnected) slotColor else CardBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(slotColor.copy(alpha = 0.2f), CircleShape)
                                .border(1.5.dp, slotColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "P${slot.slotNumber}",
                                color = slotColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (slot.isOccupied) slot.deviceName.take(12) else "Empty",
                            color = if (slot.isOccupied) Color.White else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        slot.isConnected -> NeonGreen
                                        slot.isOccupied -> Color(0xFFFFB300)
                                        else -> Color.DarkGray
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}
