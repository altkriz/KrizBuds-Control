package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krizbuds.control.FirmwareDownloader
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.TealAccent
import kotlinx.coroutines.launch

@Composable
fun FirmwareCard(
    firmwareVersion: String,
    statusLog: String,
    isDarkTheme: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isLogExpanded by remember { mutableStateOf(false) }
    var isCheckingOta by remember { mutableStateOf(false) }
    var otaProgress by remember { mutableIntStateOf(0) }
    var otaResultText by remember { mutableStateOf<String?>(null) }

    val surfaceColor = if (isDarkTheme) Color(0xFF131B2E) else Color(0xFFFFFFFF)
    val borderColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    Card(
        modifier = modifier
            .testTag("firmware_card")
            .border(1.dp, borderColor, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyanPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Firmware",
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "FIRMWARE & HARDWARE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "OTA Revision: $firmwareVersion",
                            fontSize = 11.sp,
                            color = textMuted
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TealAccent.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Cloud OTA Service",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TealAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real Hearable OTA Cloud Download Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "KrizBuds Cloud OTA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                    Text(
                        text = otaResultText ?: "Official Firmware Server",
                        fontSize = 10.sp,
                        color = if (otaResultText != null) EmeraldConnected else textMuted
                    )
                }

                Button(
                    onClick = {
                        if (!isCheckingOta) {
                            isCheckingOta = true
                            otaProgress = 0
                            otaResultText = "Querying OTA servers..."
                            coroutineScope.launch {
                                val res = FirmwareDownloader.checkAndDownloadFirmware(
                                    productId = 141,
                                    targetDir = context.cacheDir,
                                    onProgress = { otaProgress = it }
                                )
                                isCheckingOta = false
                                otaResultText = if (res.isSuccess) {
                                    val file = res.getOrNull()
                                    "Ready: ${file?.name ?: "Latest firmware verified"}"
                                } else {
                                    "Up to date (v$firmwareVersion)"
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("check_ota_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    if (isCheckingOta) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (otaProgress > 0) "$otaProgress%" else "Checking...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Check OTA",
                            modifier = Modifier.size(13.dp),
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Check OTA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Expandable Diagnostics Log
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                    .clickable { isLogExpanded = !isLogExpanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Diagnostics",
                        tint = textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Real-time BLE Log",
                        fontSize = 11.sp,
                        color = textMuted,
                        fontWeight = FontWeight.Medium
                    )
                }

                Icon(
                    imageVector = if (isLogExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isLogExpanded) "Collapse" else "Expand",
                    tint = textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = isLogExpanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDarkTheme) Color(0xFF0B101D) else Color(0xFFE2E8F0))
                        .padding(10.dp)
                ) {
                    Text(
                        text = statusLog,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isDarkTheme) Color(0xFF5EEAD4) else Color(0xFF0F766E),
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
