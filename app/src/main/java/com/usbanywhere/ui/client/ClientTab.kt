package com.usbanywhere.ui.client

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.usbanywhere.catalog.UsbCategory
import com.usbanywhere.data.RemoteServer
import com.usbanywhere.data.TunnelState
import com.usbanywhere.data.UsbExportedDevice
import com.usbanywhere.theme.AccentError
import com.usbanywhere.theme.CardBorder
import com.usbanywhere.theme.DeepDarkBg
import com.usbanywhere.theme.ElectricBlue
import com.usbanywhere.theme.NeonCyan
import com.usbanywhere.theme.NeonGreen
import com.usbanywhere.theme.SurfaceDark
import com.usbanywhere.ui.components.CategoryBadge
import com.usbanywhere.ui.server.BadgeText

@Composable
fun ClientTab(viewModel: ClientViewModel) {
    val servers by viewModel.discoveredServers.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()
    val remoteDevices by viewModel.remoteDevices.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val attachedDevice by viewModel.attachedDevice.collectAsStateWithLifecycle()
    val tunnelState by viewModel.tunnelState.collectAsStateWithLifecycle()
    val reconnectAttempt by viewModel.reconnectAttempt.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var manualIp by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(UsbCategory.ALL) }

    val filteredDevices = remember(remoteDevices, selectedFilter) {
        if (selectedFilter == UsbCategory.ALL) remoteDevices
        else remoteDevices.filter { it.category == selectedFilter }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepDarkBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Banner
        statusMessage?.let { msg ->
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF13233A), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = msg,
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Active Connection / Attached Device Banner
        attachedDevice?.let { dev ->
            item {
                AttachedDeviceBanner(
                    device = dev,
                    server = selectedServer,
                    tunnelState = tunnelState,
                    reconnectAttempt = reconnectAttempt,
                    onDisconnect = { viewModel.detachCurrentDevice() }
                )
            }
        }

        // Connection Card (Scan & Manual IP)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "CONNECT TO REMOTE HOST",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it },
                            placeholder = { Text("e.g. 192.168.1.100", color = Color.Gray, fontSize = 14.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = NeonCyan
                            )
                        )

                        Button(
                            onClick = {
                                if (manualIp.isNotBlank()) {
                                    viewModel.connectToServer(manualIp.trim())
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricBlue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isConnecting && manualIp.isNotBlank()
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                            } else {
                                Text("Connect", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.scanNetwork() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF21262D),
                            contentColor = NeonCyan
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isScanning
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scanning (mDNS + UDP)...", fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Auto-Scan Local Network (mDNS + UDP 3241)", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Section: Discovered Servers
        if (servers.isNotEmpty()) {
            item {
                Text(
                    text = "DISCOVERED SERVERS (${servers.size})",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            items(servers, key = { it.ipAddress }) { srv ->
                DiscoveredServerCard(
                    server = srv,
                    isSelected = selectedServer?.ipAddress == srv.ipAddress,
                    onSelect = { viewModel.connectToServer(srv.ipAddress, srv.port) }
                )
            }
        }

        // Section: Remote Devices from Server
        if (remoteDevices.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EXPORTED USB DEVICES ON ${selectedServer?.ipAddress ?: ""}",
                        color = NeonGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${remoteDevices.size} available",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Category Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val availableCategories = listOf(UsbCategory.ALL) + remoteDevices.map { it.category }.distinct()
                    items(availableCategories) { cat ->
                        val isSelected = selectedFilter == cat
                        val count = if (cat == UsbCategory.ALL) remoteDevices.size else remoteDevices.count { it.category == cat }
                        val chipColor = Color(cat.badgeColorHex)
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) chipColor.copy(alpha = 0.25f) else Color(0xFF1B222D),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) chipColor else CardBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedFilter = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${cat.emoji} ${cat.title} ($count)",
                                color = if (isSelected) Color.White else Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            items(filteredDevices, key = { it.busId }) { dev ->
                RemoteDeviceCard(
                    device = dev,
                    isAttached = attachedDevice?.busId == dev.busId,
                    onAttach = { viewModel.attachDevice(dev.busId) }
                )
            }
        }
    }
}

@Composable
fun AttachedDeviceBanner(
    device: UsbExportedDevice,
    server: RemoteServer?,
    tunnelState: TunnelState,
    reconnectAttempt: Int,
    onDisconnect: () -> Unit
) {
    val isReconnecting = tunnelState == TunnelState.RECONNECTING
    val bannerBg = if (isReconnecting) Color(0xFF2E2005) else Color(0xFF0E2A18)
    val borderColor = if (isReconnecting) Color(0xFFFFB300) else NeonGreen
    val statusColor = if (isReconnecting) Color(0xFFFFB300) else NeonGreen

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bannerBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryBadge(category = device.category)
                    if (isReconnecting) {
                        CircularProgressIndicator(
                            color = Color(0xFFFFB300),
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "RECONNECTING (ATTEMPT $reconnectAttempt/5)...",
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    } else {
                        Text(
                            text = "ACTIVE USB TUNNEL",
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${device.friendlyName} (${device.vendorHex}:${device.productHex})",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isReconnecting) {
                        "Host: ${server?.ipAddress ?: "Remote"} • Restoring Wi-Fi connection..."
                    } else {
                        "Host: ${server?.ipAddress ?: "Remote"} • Bus ${device.busId} • ${device.speedLabel}"
                    },
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(containerColor = AccentError, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isReconnecting) "Cancel" else "Detach",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun DiscoveredServerCard(
    server: RemoteServer,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1B2A3F) else SurfaceDark
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) NeonCyan else CardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = null,
                    tint = if (isSelected) NeonCyan else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = server.hostName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${server.ipAddress}:${server.port}",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Button(
                onClick = onSelect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) NeonCyan else Color(0xFF21262D),
                    contentColor = if (isSelected) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = if (isSelected) "Selected" else "Inspect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RemoteDeviceCard(
    device: UsbExportedDevice,
    isAttached: Boolean,
    onAttach: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isAttached) NeonGreen else CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryBadge(category = device.category)
                    Text(
                        text = device.friendlyName,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BadgeText("BUS ${device.busId}", NeonCyan)
                    BadgeText("VID:${device.vendorHex} PID:${device.productHex}", Color.Gray)
                    BadgeText(device.speedLabel, Color.LightGray)
                }
            }

            Button(
                onClick = onAttach,
                enabled = !isAttached,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAttached) Color.Gray else NeonGreen,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Cable, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = if (isAttached) "Attached" else "Attach", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
