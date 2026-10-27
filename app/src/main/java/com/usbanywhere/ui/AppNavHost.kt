package com.usbanywhere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.usbanywhere.theme.DeepDarkBg
import com.usbanywhere.theme.ElectricBlue
import com.usbanywhere.theme.NeonCyan
import com.usbanywhere.theme.NeonGreen
import com.usbanywhere.theme.SurfaceDark
import com.usbanywhere.ui.client.ClientTab
import com.usbanywhere.ui.client.ClientViewModel
import com.usbanywhere.ui.gamepad.GamepadTesterTab
import com.usbanywhere.ui.server.ServerTab
import com.usbanywhere.ui.server.ServerViewModel
import com.usbanywhere.ui.settings.SettingsTab
import com.usbanywhere.ui.settings.SettingsViewModel

enum class AppTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SERVER("Server", Icons.Default.Usb),
    CLIENT("Client", Icons.Default.Computer),
    GAMEPAD("Gamepad", Icons.Default.SportsEsports),
    SETTINGS("Settings", Icons.Default.Tune)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    isTvMode: Boolean = false,
    serverViewModel: ServerViewModel = viewModel(),
    clientViewModel: ClientViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var focusedTab by remember { mutableIntStateOf(-1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "USBAnywhere",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isTvMode) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF1E2836), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Tv, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "TV", color = NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark
                ),
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .background(Color(0xFF0F261B), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "8000 Hz READY",
                            color = NeonGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                contentColor = Color.White
            ) {
                AppTab.values().forEachIndexed { index, tab ->
                    val isFocused = focusedTab == index
                    val isSelected = selectedTab == index

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(if (isFocused) 26.dp else 22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                                color = if (isFocused && !isSelected) NeonCyan else Color.Unspecified
                            )
                        },
                        modifier = Modifier
                            .focusable()
                            .onFocusChanged {
                                if (it.isFocused) focusedTab = index
                                else if (focusedTab == index) focusedTab = -1
                            }
                            .border(
                                width = if (isFocused) 2.dp else 0.dp,
                                color = if (isFocused) NeonCyan else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonGreen,
                            selectedTextColor = NeonGreen,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color(0xFF1E2836)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DeepDarkBg)
        ) {
            when (selectedTab) {
                0 -> ServerTab(viewModel = serverViewModel)
                1 -> ClientTab(viewModel = clientViewModel)
                2 -> GamepadTesterTab()
                3 -> SettingsTab(viewModel = settingsViewModel)
            }
        }
    }
}
