package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class BubbleNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    GALLERY("gallery", "Gallery", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    DRIVE("drive", "Drive", Icons.Filled.Cloud, Icons.Outlined.Cloud),
    CAMERA("camera", "Camera", Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

/**
 * Reusable scroll listener that hides the bottom navigation bar when scrolling down,
 * and reveals it immediately when scrolling up.
 */
@Composable
fun rememberScrollAwareNavBarVisibility(): Pair<Boolean, NestedScrollConnection> {
    var isVisible by remember { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Dragging upwards to scroll down into the list -> hide
                if (available.y < -10f) {
                    isVisible = false
                } else if (available.y > 10f) {
                    // Dragging downwards to scroll up toward top -> show
                    isVisible = true
                }
                return Offset.Zero
            }
        }
    }
    return Pair(isVisible, nestedScrollConnection)
}

@Composable
fun FloatingBubbleNavBar(
    currentTab: BubbleNavTab,
    onTabSelected: (BubbleNavTab) -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    isCameraMode: Boolean = false
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(
                    horizontal = if (isCameraMode) 16.dp else 20.dp,
                    vertical = if (isCameraMode) 4.dp else 10.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = if (isCameraMode) {
                    Color.Black.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                },
                tonalElevation = if (isCameraMode) 0.dp else 8.dp,
                shadowElevation = if (isCameraMode) 4.dp else 14.dp,
                modifier = Modifier
                    .widthIn(max = if (isCameraMode) 340.dp else 420.dp)
                    .shadow(
                        elevation = if (isCameraMode) 6.dp else 16.dp,
                        shape = RoundedCornerShape(32.dp),
                        ambientColor = if (isCameraMode) Color.Black.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        spotColor = if (isCameraMode) Color.Black.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    )
                    .border(
                        width = 1.dp,
                        brush = if (isCameraMode) {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.White.copy(alpha = 0.06f)
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                )
                            )
                        },
                        shape = RoundedCornerShape(32.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .padding(
                            horizontal = if (isCameraMode) 8.dp else 10.dp,
                            vertical = if (isCameraMode) 4.dp else 8.dp
                        )
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BubbleNavTab.values().forEach { tab ->
                        val isSelected = tab == currentTab

                        val animatedScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.05f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "scale_${tab.name}"
                        )

                        val pillBgColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                if (isCameraMode) Color.White.copy(alpha = 0.20f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                Color.Transparent
                            },
                            label = "pill_bg_${tab.name}"
                        )

                        val iconTint by animateColorAsState(
                            targetValue = if (isCameraMode) {
                                if (isSelected) Color.White else Color.White.copy(alpha = 0.65f)
                            } else {
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            label = "tint_${tab.name}"
                        )

                        Box(
                            modifier = Modifier
                                .scale(animatedScale)
                                .clip(RoundedCornerShape(20.dp))
                                .background(pillBgColor)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onTabSelected(tab)
                                }
                                .padding(
                                    horizontal = if (isCameraMode) 10.dp else 14.dp,
                                    vertical = if (isCameraMode) 3.dp else 6.dp
                                )
                                .testTag("bubble_nav_${tab.route}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                    tint = iconTint,
                                    modifier = Modifier.size(if (isCameraMode) 19.dp else 24.dp)
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = tab.label,
                                    fontSize = if (isCameraMode) 9.sp else 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = iconTint
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
