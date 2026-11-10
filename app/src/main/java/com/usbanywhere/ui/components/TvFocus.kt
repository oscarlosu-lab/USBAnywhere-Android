package com.usbanywhere.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Colour of the D-pad focus ring: white is visible on every button colour of the app. */
val TvFocusColor = Color.White

/**
 * Makes the D-pad focus obvious on Android TV: thick white ring + slight zoom.
 *
 * Must be placed on the modifier of a component that is already focusable (Button,
 * Switch, IconButton, NavigationBarItem, Modifier.clickable...). Do NOT add an extra
 * `.focusable()`: it creates a second focus stop that swallows the OK key.
 */
fun Modifier.tvFocusRing(shape: Shape = RoundedCornerShape(12.dp), zoom: Float = 1.06f): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) zoom else 1f, label = "tvFocusScale")
    this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .border(if (focused) 3.dp else 0.dp, if (focused) TvFocusColor else Color.Transparent, shape)
}

private val TvRippleAlpha = RippleAlpha(
    draggedAlpha = 0.16f,
    focusedAlpha = 0.35f,
    hoveredAlpha = 0.12f,
    pressedAlpha = 0.24f
)

/** Raises the Material focus highlight (default ~10%, invisible on a TV) for every component. */
@Composable
fun TvFocusHighlight(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalRippleConfiguration provides RippleConfiguration(color = Color.White, rippleAlpha = TvRippleAlpha),
        content = content
    )
}

/**
 * Switch whose focus ring hugs the track (the plain Material Switch has an invisible
 * 48dp touch area, so a ring drawn on its modifier looked like a loose oval).
 */
@Composable
fun TvSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    colors: SwitchColors = SwitchDefaults.colors()
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "tvSwitchScale")
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Box(
            modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .border(3.dp, if (focused) TvFocusColor else Color.Transparent, RoundedCornerShape(50))
                .padding(5.dp)
        ) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = colors,
                interactionSource = interaction
            )
        }
    }
}
