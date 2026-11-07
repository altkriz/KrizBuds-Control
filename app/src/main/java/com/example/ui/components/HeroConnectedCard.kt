package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AmberCharging
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.SlateDisconnect

@Composable
fun HeroConnectedCard(
    deviceName: String,
    macAddress: String,
    isConnected: Boolean,
    isConnecting: Boolean,
    statusLog: String,
    onDisconnect: () -> Unit,
    onScanClicked: () -> Unit,
    isDarkTheme: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val surfaceColor = if (isDarkTheme) Color(0xFF131B2E) else Color(0xFFFFFFFF)
    val borderColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    Card(
        modifier = modifier
            .testTag("hero_connected_card")
            .border(1.dp, borderColor, RoundedCornerShape(22.dp)),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 4.dp)
    ) {
        Column {
            // Hero Image Container with smooth gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.krizbuds_hero),
                    contentDescription = "KrizBuds 3D Render",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Gentle gradient fade into card surface
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    surfaceColor.copy(alpha = 0.5f),
                                    surfaceColor
                                )
                            )
                        )
                )

                // Top Connection Status Pill
                Surface(
                    modifier = Modifier
                        .padding(14.dp)
                        .align(Alignment.TopStart),
                    shape = RoundedCornerShape(20.dp),
                    color = surfaceColor.copy(alpha = 0.85f),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isConnected -> EmeraldConnected.copy(alpha = pulseAlpha)
                                        isConnecting -> AmberCharging.copy(alpha = pulseAlpha)
                                        else -> textMuted
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isConnected -> "CONNECTED"
                                isConnecting -> "CONNECTING..."
                                else -> "READY TO PAIR"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isConnected -> EmeraldConnected
                                isConnecting -> AmberCharging
                                else -> textMuted
                            },
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Card details body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = deviceName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            letterSpacing = (-0.3).sp
                        )

                        if (macAddress.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "MAC: $macAddress",
                                fontSize = 11.sp,
                                color = textMuted
                            )
                        }
                    }

                    if (isConnected) {
                        // Soothing Disconnect Button (NO RED)
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier.testTag("disconnect_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = SlateDisconnect
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.linearGradient(listOf(SlateDisconnect.copy(alpha = 0.4f), SlateDisconnect.copy(alpha = 0.4f)))
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BluetoothDisabled,
                                contentDescription = "Disconnect",
                                modifier = Modifier.size(14.dp),
                                tint = SlateDisconnect
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Disconnect",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Button(
                            onClick = onScanClicked,
                            modifier = Modifier.testTag("quick_scan_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BluetoothConnected,
                                contentDescription = "Scan",
                                modifier = Modifier.size(14.dp),
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Find Device",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Status ticker
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status: ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textMuted
                        )
                        Text(
                            text = statusLog,
                            fontSize = 11.sp,
                            color = textPrimary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
