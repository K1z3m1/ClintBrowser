package com.jhaiian.clint.settings.lookandfeel

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhaiian.clint.R
import com.jhaiian.clint.customtheme.isCustomAccent
import com.jhaiian.clint.customtheme.resolveCustomPalette
import com.jhaiian.clint.customtheme.toClintColors
import com.jhaiian.clint.tabs.TabBarPlacement
import com.jhaiian.clint.ui.theme.ClintColors
import com.jhaiian.clint.ui.theme.LocalClintColors
import com.jhaiian.clint.ui.theme.rememberIsLightTheme
import com.jhaiian.clint.ui.theme.resolveClintTheme

private enum class PreviewMode { PAGE, MENU, ALERT }

internal class PreviewPalette(
    val bg: Color,
    val surface: Color,
    val onSurface: Color,
    val card: Color,
    val primary: Color,
    val popupBg: Color,
    val addressBar: Color
)

internal fun Color.a(alpha: Float): Color = copy(alpha = alpha)

private fun tabBarAtTop(position: String, addressBarPosition: String): Boolean =
    when (TabBarPlacement.normalize(position)) {
        TabBarPlacement.TOP -> true
        TabBarPlacement.BOTTOM -> false
        else -> addressBarPosition != "bottom"
    }

@Composable
internal fun rememberPreviewPalette(theme: String, accent: String, intensity: String): PreviewPalette {
    val context = LocalContext.current
    val isLight = rememberIsLightTheme(theme)
    return remember(theme, accent, intensity, isLight) {
        val custom = resolveCustomPalette(context, accent, isLight)?.toClintColors(isLight)
        if (custom != null) {
            PreviewPalette(
                bg = custom.background, surface = custom.surface, onSurface = custom.onSurface,
                card = custom.cardBackground, primary = custom.primary,
                popupBg = custom.popupBackground, addressBar = custom.addressBarColor
            )
        } else {
            val safeAccent = if (isCustomAccent(accent)) "material_you" else accent
            val r = resolveClintTheme(context, if (isLight) "light" else "dark", safeAccent, intensity)
            PreviewPalette(
                bg = r.background, surface = r.surface, onSurface = r.onSurface,
                card = r.cardBackground, primary = r.primary,
                popupBg = r.popupBackground, addressBar = r.addressBar
            )
        }
    }
}

@Composable
fun LookAndFeelPreview(state: LookAndFeelUiState, onOpenDialog: (LookAndFeelDialog) -> Unit) {
    val colors = LocalClintColors.current
    val palette = rememberPreviewPalette(state.theme, state.accent, state.intensity)
    var expanded by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf(PreviewMode.PAGE) }

    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBackground)
            .padding(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.look_feel_preview_title),
                color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null, tint = colors.secondaryText
            )
        }
        if (expanded) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PhoneMock(palette, state, mode, onOpenDialog) { mode = PreviewMode.MENU }
                Column(
                    Modifier.weight(1f).padding(start = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeChip(stringResource(R.string.look_feel_preview_page), mode == PreviewMode.PAGE, colors) { mode = PreviewMode.PAGE }
                    ModeChip(stringResource(R.string.menu), mode == PreviewMode.MENU, colors) { mode = PreviewMode.MENU }
                    ModeChip(stringResource(R.string.look_feel_preview_alert), mode == PreviewMode.ALERT, colors) { mode = PreviewMode.ALERT }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, colors: ClintColors, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) colors.primary else colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) colors.onPrimary else colors.onSurface,
            fontSize = 12.sp, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PhoneMock(
    p: PreviewPalette,
    s: LookAndFeelUiState,
    mode: PreviewMode,
    onOpenDialog: (LookAndFeelDialog) -> Unit,
    onMenuTap: () -> Unit
) {
    val ab = s.addressBarPosition
    val showStrip = s.showTabStrip
    val showGroup = s.tabGroupsEnabled
    val stripTop = tabBarAtTop(s.tabStripPosition, ab)
    val groupTop = tabBarAtTop(s.tabGroupBarPosition, ab)
    val topHasContent = ab != "bottom" || (showStrip && stripTop) || (showGroup && groupTop)
    val bottomHasContent = ab != "top" || (showStrip && !stripTop) || (showGroup && !groupTop)
    val shape = RoundedCornerShape(14.dp)

    Box(
        Modifier
            .size(104.dp, 184.dp)
            .clip(shape)
            .background(p.bg)
            .border(1.dp, p.onSurface.a(0.18f), shape)
    ) {
        Column(Modifier.fillMaxSize()) {
            if (!s.hideStatusBar) StatusStrip(p, if (topHasContent) p.surface else p.bg)
            Column(Modifier.fillMaxWidth().background(p.surface)) {
                if (showStrip && stripTop) StripBar(p) { onOpenDialog(LookAndFeelDialog.TAB_STRIP_POSITION) }
                if (ab != "bottom") {
                    AddressRow(p, s.showHomeButton, onMenuTap) { onOpenDialog(LookAndFeelDialog.ADDRESS_BAR_POSITION) }
                }
                if (showGroup && groupTop) GroupBar(p) { onOpenDialog(LookAndFeelDialog.TAB_GROUP_BAR_POSITION) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                PageContent(p)
                if (mode == PreviewMode.MENU && s.menuStyle != "bottom_sheet") {
                    Column(
                        Modifier
                            .align(if (ab == "bottom") Alignment.BottomEnd else Alignment.TopEnd)
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
                if (mode == PreviewMode.ALERT && s.popupAlertStyle == "snackbar") {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(6.dp)
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(p.popupBg)
                            .border(1.dp, p.onSurface.a(0.15f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Line(p.onSurface.a(0.4f), 34.dp)
                        Spacer(Modifier.weight(1f))
                        Line(p.primary, 12.dp)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(p.surface)) {
                if (showGroup && !groupTop) GroupBar(p) { onOpenDialog(LookAndFeelDialog.TAB_GROUP_BAR_POSITION) }
                if (showStrip && !stripTop) StripBar(p) { onOpenDialog(LookAndFeelDialog.TAB_STRIP_POSITION) }
                if (ab == "bottom") {
                    AddressRow(p, s.showHomeButton, onMenuTap) { onOpenDialog(LookAndFeelDialog.ADDRESS_BAR_POSITION) }
                }
                if (ab == "split") NavRow(p) { onOpenDialog(LookAndFeelDialog.ADDRESS_BAR_POSITION) }
            }
            if (!s.hideSystemNavigation) GestureStrip(p, if (bottomHasContent) p.surface else p.bg)
        }

        if (mode == PreviewMode.MENU && s.menuStyle == "bottom_sheet") {
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
        if (mode == PreviewMode.ALERT && s.popupAlertStyle != "snackbar") {
            Box(Modifier.fillMaxSize().background(Color.Black.a(0.3f)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .size(72.dp, 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(p.popupBg)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Line(p.onSurface.a(0.45f), 40.dp)
                    Line(p.onSurface.a(0.25f), 52.dp)
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Line(p.primary, 14.dp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun Line(color: Color, width: androidx.compose.ui.unit.Dp) {
    Box(Modifier.width(width).height(3.dp).clip(RoundedCornerShape(2.dp)).background(color))
}

@Composable
internal fun StatusStrip(p: PreviewPalette, bg: Color) {
    Row(
        Modifier.fillMaxWidth().height(9.dp).background(bg).padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Line(p.onSurface.a(0.5f), 12.dp)
        Spacer(Modifier.weight(1f))
        repeat(3) {
            Box(Modifier.padding(start = 2.dp).size(3.dp).clip(CircleShape).background(p.onSurface.a(0.5f)))
        }
    }
}

@Composable
internal fun GestureStrip(p: PreviewPalette, bg: Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).background(bg), contentAlignment = Alignment.Center) {
        Box(Modifier.width(24.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(p.onSurface.a(0.35f)))
    }
}

@Composable
internal fun AddressRow(p: PreviewPalette, showHome: Boolean, onMenu: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(20.dp).clickable(onClick = onClick).padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (showHome) Box(Modifier.size(6.dp).clip(CircleShape).background(p.primary))
        Row(
            Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(p.addressBar).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(4.dp).clip(CircleShape).background(p.primary))
            Box(Modifier.padding(start = 3.dp)) { Line(p.onSurface.a(0.4f), 24.dp) }
        }
        Box(Modifier.size(8.dp).border(1.dp, p.onSurface.a(0.5f), RoundedCornerShape(2.dp)))
        Column(
            Modifier.clickable(onClick = onMenu).padding(horizontal = 1.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            repeat(3) { Box(Modifier.size(2.dp).clip(CircleShape).background(p.onSurface.a(0.6f))) }
        }
    }
}

@Composable
private fun StripBar(p: PreviewPalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(12.dp).clickable(onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(3) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(if (i == 0) p.primary.a(0.3f) else p.onSurface.a(0.1f))
            )
        }
    }
}

@Composable
private fun GroupBar(p: PreviewPalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(10.dp).clickable(onClick = onClick).padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(if (i == 0) 16.dp else 12.dp, 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i == 0) p.primary else p.onSurface.a(0.2f))
            )
        }
    }
}

@Composable
internal fun NavRow(p: PreviewPalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(16.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        repeat(5) { i ->
            Box(
                Modifier
                    .size(6.dp)
                    .clip(if (i == 2) CircleShape else RoundedCornerShape(2.dp))
                    .background(if (i == 2) p.primary else p.onSurface.a(0.5f))
            )
        }
    }
}

@Composable
internal fun PageContent(p: PreviewPalette) {
    Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(6.dp)).background(p.primary.a(0.2f)))
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(p.onSurface.a(0.15f)))
        Line(p.onSurface.a(0.15f), 48.dp)
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(p.onSurface.a(0.15f)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(2) {
                Box(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(5.dp)).background(p.card))
            }
        }
    }
}
