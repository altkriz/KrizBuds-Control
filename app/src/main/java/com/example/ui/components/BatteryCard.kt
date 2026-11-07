package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberCharging
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.TealAccent

@Composable
fun BatteryCard(
    label: String,
    level: Int,
    isCharging: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (level >= 0) (level / 100f).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "battery_progress"
    )

    val surfaceColor = if (isDarkTheme) Color(0xFF131B2E) else Color(0xFFFFFFFF)
    val borderColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    // Soothing accent color based on status
    val gaugeColor = when {
        isCharging -> AmberCharging
        level > 20 -> TealAccent
        level >= 0 -> AmberCharging
        else -> textMuted
    }

    Card(
        modifier = modifier
            .testTag("battery_card_${label.lowercase().replace(" ", "_")}")
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon & label header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(gaugeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCharging) Icons.Default.Bolt else icon,
                        contentDescription = label,
                        modifier = Modifier.size(14.dp),
                        tint = gaugeColor
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = textMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Percentage readout
            Text(
                text = if (level >= 0) "$level%" else "--",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (level >= 0) textPrimary else textMuted,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Soothing segmented progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    gaugeColor.copy(alpha = 0.7f),
                                    gaugeColor
                                )
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status chip
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = when {
                    isCharging -> AmberCharging.copy(alpha = 0.15f)
                    level >= 0 -> EmeraldConnected.copy(alpha = 0.15f)
                    else -> borderColor
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCharging) {
                        Text(
                            text = "⚡ Charging",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AmberCharging
                        )
                    } else if (level >= 0) {
                        Text(
                            text = "Active",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldConnected
                        )
                    } else {
                        Text(
                            text = "Offline",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            color = textMuted
                        )
                    }
                }
            }
        }
    }
}
