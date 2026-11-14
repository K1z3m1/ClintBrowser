package com.jhaiian.clint.setup

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.jhaiian.clint.settings.lookandfeel.AddressRow
import com.jhaiian.clint.settings.lookandfeel.GestureStrip
import com.jhaiian.clint.settings.lookandfeel.Line
import com.jhaiian.clint.settings.lookandfeel.NavRow
import com.jhaiian.clint.settings.lookandfeel.PageContent
import com.jhaiian.clint.settings.lookandfeel.StatusStrip
import com.jhaiian.clint.settings.lookandfeel.a
import com.jhaiian.clint.settings.lookandfeel.rememberPreviewPalette
import kotlinx.coroutines.delay

private val MockWidth = 104.dp
private val MockHeight = 184.dp
private val AddressRowHeight = 20.dp
private val NavRowHeight = 16.dp
private val StatusHeight = 9.dp
private val GestureHeight = 8.dp

/**
 * Live phone mock-up for the setup wizard. It re-composes from the wizard's state, so every
 * choice (theme, accent, intensity, address bar, menu style, scroll-hide, status/nav bar) shows up immediately.
 */
@Composable
fun SetupPhonePreview(
    theme: String,
    accent: String,
    intensity: String,
    addressBarPosition: String,
    menuStyle: String,
    scrollHideMode: String,
    hideStatusBar: Boolean,
    hideSystemNavigation: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 0.85f
) {
    val p = rememberPreviewPalette(theme, accent, intensity)
    val shape = RoundedCornerShape(14.dp)

    var menuVisible by remember { mutableStateOf(false) }
    var firstMenuStyle by remember { mutableStateOf(true) }
    LaunchedEffect(menuStyle) {
        if (firstMenuStyle) { firstMenuStyle = false; return@LaunchedEffect }
        menuVisible = true
        delay(2200)
        menuVisible = false
    }

    val topBarCount = if (addressBarPosition != "bottom") 1 else 0
    val hidesTop = scrollHideMode == "search_bar" || scrollHideMode == "both"
    val hidesBottom = scrollHideMode == "navigation_bar" || scrollHideMode == "both"
    val animate = (hidesTop && topBarCount > 0) || (hidesBottom && addressBarPosition != "top")
    val fraction = if (animate) {
        rememberInfiniteTransition(label = "setupMockScroll").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
            label = "fraction"
        ).value
    } else 0f

    val topHeight = if (topBarCount > 0) AddressRowHeight else 0.dp
    val bottomHeight = when (addressBarPosition) {
        "bottom" -> AddressRowHeight
        "split" -> NavRowHeight
        else -> 0.dp
    }
    val topOffset = if (hidesTop) -(topHeight * fraction) else 0.dp
    val bottomOffset = if (hidesBottom) bottomHeight * fraction else 0.dp

    Box(modifier.size(MockWidth * scale, MockHeight * scale)) {
        Box(
            Modifier
                .requiredSize(MockWidth, MockHeight)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .clip(shape)
                .background(p.bg)
                .border(1.dp, p.onSurface.a(0.18f), shape)
        ) {
            Column(Modifier.fillMaxSize()) {
                if (!hideStatusBar) StatusStrip(p, if (topBarCount > 0) p.surface else p.bg)
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                    Box(Modifier.fillMaxSize().padding(top = topHeight + topOffset, bottom = bottomHeight - bottomOffset)) {
                        PageContent(p)
                    }
                    if (topBarCount > 0) {
                        Box(
                            Modifier.align(Alignment.TopCenter).fillMaxWidth().offset(y = topOffset).background(p.surface)
                        ) { AddressRow(p, false, {}, {}) }
                    }
                    if (bottomHeight > 0.dp) {
                        Column(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().offset(y = bottomOffset).background(p.surface)
                        ) {
                            if (addressBarPosition == "bottom") AddressRow(p, false, {}, {}) else NavRow(p) {}
                        }
                    }
                    if (menuVisible && menuStyle != "bottom_sheet") {
                        Column(
                            Modifier
                                .align(if (addressBarPosition == "bottom") Alignment.BottomEnd else Alignment.TopEnd)
                                .padding(4.dp)
                                .size(46.dp, 54.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(p.popupBg)
                                .border(1.dp, p.onSurface.a(0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            repeat(4) { Line(p.onSurface.a(0.3f), if (it % 2 == 0) 30.dp else 22.dp) }
                        }
                    }
                }
                if (!hideSystemNavigation) {
                    GestureStrip(p, if (bottomHeight > 0.dp) p.surface else p.bg)
                }
            }

            if (menuVisible && menuStyle == "bottom_sheet") {
                Box(Modifier.fillMaxSize().background(Color.Black.a(0.3f)))
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(p.popupBg)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Line(p.onSurface.a(0.3f), 14.dp)
                    repeat(2) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            repeat(2) {
                                Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(p.onSurface.a(0.1f)))
                            }
                        }
                    }
                }
            }
        }
    }
}
