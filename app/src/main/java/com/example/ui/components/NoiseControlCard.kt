package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.NoiseAware
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krizbuds.control.AncLevel
import com.krizbuds.control.AncMode
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.TealAccent

@Composable
fun NoiseControlCard(
    currentMode: AncMode,
    currentLevel: AncLevel,
    onModeSelected: (AncMode) -> Unit,
    onLevelSelected: (AncLevel) -> Unit,
    isDarkTheme: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Preserve active mode so selection never drops back or flickers
    var activeMode by remember(currentMode) { mutableStateOf(currentMode) }
    var activeLevel by remember(currentLevel) { mutableStateOf(currentLevel) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val surfaceColor = if (isDarkTheme) Color(0xFF131B2E) else Color(0xFFFFFFFF)
    val borderColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    Card(
        modifier = modifier
            .testTag("noise_control_card")
            .border(1.dp, borderColor, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
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
                            imageVector = Icons.Default.NoiseAware,
                            contentDescription = "Noise Control",
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "NOISE CONTROL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Airoha / BES Active Acoustic Isolation",
                            fontSize = 11.sp,
                            color = textMuted
                        )
                    }
                }

                // Active Mode Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CyanPrimary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = when (activeMode) {
                            AncMode.ANC -> "ANC Active"
                            AncMode.SPEAKTHROUGH -> "Ambient"
                            AncMode.NORMAL -> "Off"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CyanPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Three Main Modes: Normal, ANC, Ambient
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeItem(
                    title = "Normal",
                    subtitle = "Passive",
                    icon = Icons.Default.VolumeOff,
                    isSelected = activeMode == AncMode.NORMAL,
                    onClick = {
                        activeMode = AncMode.NORMAL
                        onModeSelected(AncMode.NORMAL)
                    },
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier.weight(1f)
                )
                ModeItem(
                    title = "ANC",
                    subtitle = "Cancellation",
                    icon = Icons.Default.NoiseAware,
                    isSelected = activeMode == AncMode.ANC,
                    onClick = {
                        activeMode = AncMode.ANC
                        onModeSelected(AncMode.ANC)
                    },
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier.weight(1f)
                )
                ModeItem(
                    title = "Ambient",
                    subtitle = "SpeakThrough",
                    icon = Icons.Default.Hearing,
                    isSelected = activeMode == AncMode.SPEAKTHROUGH,
                    onClick = {
                        activeMode = AncMode.SPEAKTHROUGH
                        onModeSelected(AncMode.SPEAKTHROUGH)
                    },
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier.weight(1f)
                )
            }

            // ANC Profile Dropdown & Intensity Selection (Shown when ANC is on)
            AnimatedVisibility(
                visible = activeMode == AncMode.ANC,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ANC PROFILE DROPDOWN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textMuted,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Tap to choose profile",
                            fontSize = 10.sp,
                            color = CyanPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dropdown Trigger Button
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val dropdownBg = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                        val dropdownBorder = CyanPrimary.copy(alpha = 0.6f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("anc_profile_dropdown_trigger")
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.5.dp, dropdownBorder, RoundedCornerShape(12.dp))
                                .background(dropdownBg)
                                .clickable { isDropdownExpanded = !isDropdownExpanded }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = activeLevel.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                    Text(
                                        text = activeLevel.subtitle,
                                        fontSize = 11.sp,
                                        color = textMuted
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (isDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = "Toggle Dropdown",
                                tint = CyanPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // M3 Dropdown Menu
                        DropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .background(if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFFFFFFF))
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        ) {
                            AncLevel.values().forEach { level ->
                                val isSelected = activeLevel == level
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = level.title,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 13.sp,
                                                    color = if (isSelected) CyanPrimary else textPrimary
                                                )
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = CyanPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = level.subtitle,
                                                fontSize = 11.sp,
                                                color = textMuted
                                            )
                                        }
                                    },
                                    onClick = {
                                        activeLevel = level
                                        activeMode = AncMode.ANC
                                        onLevelSelected(level)
                                        onModeSelected(AncMode.ANC)
                                        isDropdownExpanded = false
                                    },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                    modifier = Modifier.testTag("anc_dropdown_item_${level.name.lowercase()}")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick-Touch Profile Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AncLevel.values().forEach { level ->
                            val isSelected = activeLevel == level
                            val itemBg = if (isSelected) {
                                CyanPrimary.copy(alpha = if (isDarkTheme) 0.22f else 0.15f)
                            } else {
                                if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                            }
                            val itemBorder = if (isSelected) CyanPrimary else Color.Transparent

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, itemBorder, RoundedCornerShape(12.dp))
                                    .background(itemBg)
                                    .clickable {
                                        activeLevel = level
                                        activeMode = AncMode.ANC
                                        onLevelSelected(level)
                                        onModeSelected(AncMode.ANC)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = when (level) {
                                            AncLevel.DEFAULT -> "Mild"
                                            AncLevel.BALANCED -> "Balanced"
                                            AncLevel.MAX_DEEP -> "Max Deep"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) CyanPrimary else textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = when (level) {
                                            AncLevel.DEFAULT -> "Office"
                                            AncLevel.BALANCED -> "Commute"
                                            AncLevel.MAX_DEEP -> "Airplane"
                                        },
                                        fontSize = 10.sp,
                                        color = textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val textPrimary = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    val containerColor = if (isSelected) {
        CyanPrimary.copy(alpha = if (isDarkTheme) 0.22f else 0.15f)
    } else {
        if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    }
    val borderColor = if (isSelected) CyanPrimary else Color.Transparent

    Box(
        modifier = modifier
            .testTag("anc_mode_button_${title.lowercase()}")
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) CyanPrimary else textMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) CyanPrimary else textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = textMuted
            )
        }
    }
}
