package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krizbuds.control.AncLevel
import com.krizbuds.control.AncMode
import com.krizbuds.control.DiscoveredDevice
import com.krizbuds.control.EarbudsState
import com.krizbuds.control.EqPreset
import com.example.ui.components.*
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.TealAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KrizBudsApp(
    state: EarbudsState,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onModeSelected: (AncMode) -> Unit,
    onAncLevelSelected: (AncLevel) -> Unit,
    onBeastModeToggled: (Boolean) -> Unit,
    onScanClicked: () -> Unit,
    onDeviceSelected: (DiscoveredDevice) -> Unit,
    onDisconnect: () -> Unit,
    onEqPresetSelected: (EqPreset) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var selectedEqPreset by remember { mutableStateOf(EqPreset.SIGNATURE) }

    val bgColor = if (isDarkTheme) Color(0xFF090D16) else Color(0xFFF8FAFC)
    val textPrimary = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.krizbuds_app_icon),
                        contentDescription = "KrizBuds Icon",
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "KrizBuds Control",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            letterSpacing = (-0.3).sp
                        )
                        Text(
                            text = "Direct BLE • No Account",
                            fontSize = 11.sp,
                            color = textMuted
                        )
                    }
                }

                // Action controls: Scan & Theme Switcher
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Scan button
                    Button(
                        onClick = onScanClicked,
                        modifier = Modifier.testTag("scan_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (state.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Scanning...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan",
                                modifier = Modifier.size(14.dp),
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Scan",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Theme Switcher Button (Light / Dark mode)
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .testTag("theme_switcher_button")
                            .clip(CircleShape)
                            .background(if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDarkTheme) "Switch to Light Mode" else "Switch to Dark Mode",
                            tint = if (isDarkTheme) Color(0xFFFBBF24) else Color(0xFF0F172A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Main scrollable content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero connected card
                HeroConnectedCard(
                    deviceName = state.deviceName,
                    macAddress = state.macAddress,
                    isConnected = state.isConnected,
                    isConnecting = state.isConnecting,
                    statusLog = state.statusLog,
                    onDisconnect = onDisconnect,
                    onScanClicked = onScanClicked,
                    isDarkTheme = isDarkTheme
                )

                // Discovered / Available Devices
                if (!state.isConnected || state.discoveredDevices.isNotEmpty()) {
                    DeviceSelectionCard(
                        discoveredDevices = state.discoveredDevices,
                        isScanning = state.isScanning,
                        onDeviceSelected = onDeviceSelected,
                        isDarkTheme = isDarkTheme
                    )
                }

                // Live Battery Gauges (Left Bud, Right Bud, Case)
                Column {
                    Text(
                        text = "LIVE BATTERY TELEMETRY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textMuted,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BatteryCard(
                            label = "Left Bud",
                            level = state.leftBattery,
                            isCharging = state.isLeftCharging,
                            icon = Icons.Default.Headphones,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.weight(1f)
                        )
                        BatteryCard(
                            label = "Right Bud",
                            level = state.rightBattery,
                            isCharging = state.isRightCharging,
                            icon = Icons.Default.Headphones,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.weight(1f)
                        )
                        BatteryCard(
                            label = "Case",
                            level = state.caseBattery,
                            isCharging = state.isCaseCharging,
                            icon = Icons.Default.Power,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Noise Control (ANC Modes & Depths)
                NoiseControlCard(
                    currentMode = state.currentMode,
                    currentLevel = state.ancLevel,
                    onModeSelected = onModeSelected,
                    onLevelSelected = onAncLevelSelected,
                    isDarkTheme = isDarkTheme
                )

                // Beast Mode (Gaming latency toggle)
                BeastModeCard(
                    isBeastMode = state.isBeastMode,
                    onBeastModeToggled = onBeastModeToggled,
                    isDarkTheme = isDarkTheme
                )

                // Hardware Equalizer Presets
                EqualizerCard(
                    selectedPreset = selectedEqPreset,
                    onPresetSelected = {
                        selectedEqPreset = it
                        onEqPresetSelected(it)
                    },
                    isDarkTheme = isDarkTheme
                )

                // Firmware Info & BLE Log Drawer
                FirmwareCard(
                    firmwareVersion = state.firmwareVersion,
                    statusLog = state.statusLog,
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
