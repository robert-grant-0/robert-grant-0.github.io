package io.github.robertgrant0.bmsmonitor.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Tone { NORMAL, WARNING, ALARM }

/** Soft coloured glows behind everything, like light falling on a dark surface. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val p = LocalPalette.current
    if (p.flat) {
        Box(modifier.background(p.bg), content = content)
        return
    }
    Box(
        modifier
            .background(p.bg)
            .drawBehind {
                val w = size.width
                val h = size.height
                drawCircle(
                    Brush.radialGradient(listOf(p.glowA, Color.Transparent), Offset(w * 0.08f, -h * 0.10f), w * 0.45f),
                    radius = w * 0.45f,
                    center = Offset(w * 0.08f, -h * 0.10f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(p.glowB, Color.Transparent), Offset(w * 0.96f, -h * 0.06f), w * 0.42f),
                    radius = w * 0.42f,
                    center = Offset(w * 0.96f, -h * 0.06f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(p.glowC, Color.Transparent), Offset(w * 0.55f, h * 1.15f), w * 0.55f),
                    radius = w * 0.55f,
                    center = Offset(w * 0.55f, h * 1.15f),
                )
            },
        content = content,
    )
}

/** The main surface: subtle vertical gradient, hairline border and a faint highlight on the top edge. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tone: Tone = Tone.NORMAL,
    corner: Dp = 28.dp,
    padding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(corner)
    val top = when (tone) {
        Tone.NORMAL -> p.card1
        Tone.WARNING -> p.warnCard
        Tone.ALARM -> p.alarmCard
    }
    val border = when (tone) {
        Tone.NORMAL -> p.line
        Tone.WARNING -> p.orange.copy(alpha = 0.42f)
        Tone.ALARM -> p.red.copy(alpha = 0.55f)
    }
    Column(
        modifier
            .clip(shape)
            .then(
                if (p.flat) {
                    Modifier
                        .background(top)
                        .then(if (tone == Tone.NORMAL) Modifier else Modifier.border(1.5.dp, border, shape))
                } else {
                    Modifier
                        .background(Brush.verticalGradient(0f to top, 0.6f to p.card2, 1f to p.card2))
                        .border(1.dp, border, shape)
                        .drawBehind {
                            val inset = corner.toPx()
                            drawLine(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, p.highlight, Color.Transparent),
                                    startX = inset,
                                    endX = size.width - inset,
                                ),
                                start = Offset(inset, 0.5f),
                                end = Offset(size.width - inset, 0.5f),
                                strokeWidth = 1f,
                            )
                        }
                },
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** Dashed outline used for empty battery slots. */
fun Modifier.dashedOutline(color: Color, corner: Dp): Modifier = drawBehind {
    val stroke = 1.5.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(corner.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
    )
}

/** Recessed panel inside a card. */
fun Modifier.well(p: Palette, corner: Dp = 20.dp): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .clip(shape)
        .background(p.well)
        .border(1.dp, p.line2, shape)
}

/** A dot with a soft halo; the halo breathes when [pulse] is on and motion is enabled. */
@Composable
fun StatusDot(color: Color, size: Dp = 7.dp, pulse: Boolean = false) {
    val motion = LocalMotion.current
    val halo = if (pulse && motion) {
        rememberInfiniteTransition(label = "dot").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
            label = "halo",
        )
    } else {
        null
    }
    Canvas(Modifier.size(size + 8.dp)) {
        val r = size.toPx() / 2
        val c = center
        val t = halo?.value
        if (t != null) {
            drawCircle(color.copy(alpha = 0.35f * (1f - t)), radius = r + 4.dp.toPx() * t, center = c)
        } else {
            drawCircle(color.copy(alpha = 0.2f), radius = r + 3.dp.toPx(), center = c)
        }
        drawCircle(color, radius = r, center = c)
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, pulse: Boolean = false) {
    Row(
        modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color, pulse = pulse)
        Spacer(Modifier.width(3.dp))
        Text(text, style = Type.text(12.5.sp, FontWeight.SemiBold), color = color, maxLines = 1)
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = LocalPalette.current.t3) {
    Text(text.uppercase(), style = Type.label(), color = color, modifier = modifier, maxLines = 1)
}

/** "82" with a smaller, muted "%" – both share one baseline. */
@Composable
fun ValueUnit(
    value: String,
    unit: String,
    style: TextStyle,
    unitSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.t1,
    unitColor: Color = LocalPalette.current.t2,
    unitGap: String = " ",
) {
    val fonts = LocalAppFonts.current
    Text(
        buildAnnotatedString {
            append(value)
            if (unit.isNotEmpty()) {
                withStyle(
                    SpanStyle(
                        fontSize = unitSize,
                        color = unitColor,
                        fontFamily = fonts.text,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.sp,
                    ),
                ) { append(unitGap + unit) }
            }
        },
        style = style,
        color = color,
        modifier = modifier,
        maxLines = 1,
    )
}

@Composable
fun HeaderChip(text: String, dot: Color?, modifier: Modifier = Modifier, icon: ImageVector? = null, iconTint: Color? = null) {
    val p = LocalPalette.current
    Row(
        modifier
            .height(36.dp)
            .well(p, 18.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = iconTint ?: p.t2, modifier = Modifier.size(16.dp))
        if (dot != null) Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Text(text, style = Type.text(13.sp, FontWeight.Medium), color = p.t2, maxLines = 1)
    }
}

@Composable
fun CircleButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(p.well)
            .border(1.dp, p.line2, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = p.t2, modifier = Modifier.size(21.dp))
    }
}

/** Small tile: icon + label on top, value below. */
@Composable
fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalPalette.current.t1,
    iconColor: Color = LocalPalette.current.t3,
    showIcon: Boolean = true,
) {
    val p = LocalPalette.current
    Column(
        modifier
            .well(p, 14.dp)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showIcon) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(
                label.uppercase(),
                style = Type.label().copy(fontSize = 10.sp),
                color = p.t3,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(value, style = Type.figures(15.sp), color = valueColor, maxLines = 1)
    }
}

@Composable
fun FetChip(label: String, on: Boolean) {
    val p = LocalPalette.current
    Row(
        Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (on) p.t1.copy(alpha = 0.05f) else p.red.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) p.green else p.red))
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = Type.label().copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
            color = if (on) p.t2 else p.red,
        )
    }
}

/** Four bars like a phone's signal indicator. */
@Composable
fun SignalBars(rssi: Int?, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val n = when {
        rssi == null -> 0
        rssi > -60 -> 4
        rssi > -70 -> 3
        rssi > -80 -> 2
        else -> 1
    }
    Row(modifier.height(12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(4.dp, 6.5.dp, 9.dp, 12.dp).forEachIndexed { i, h ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(h)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i < n) p.t2 else p.t1.copy(alpha = 0.12f)),
            )
        }
    }
}

/** Label on the left, value on the right, hairline underneath. */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalPalette.current.t1,
    divider: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = LocalPalette.current
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = Type.text(14.sp), color = p.t2, modifier = Modifier.weight(1f))
            if (trailing != null) trailing() else Text(value, style = Type.figures(14.sp), color = valueColor)
        }
        if (divider) Hairline()
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalPalette.current.line2),
    )
}

/** Rounded, tinted button ("Allow", "Choose", "Scan again"). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.accent,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val p = LocalPalette.current
    val bg = when {
        !enabled -> p.t1.copy(alpha = 0.05f)
        filled -> color
        else -> color.copy(alpha = 0.14f)
    }
    val fg = when {
        !enabled -> p.t3
        filled -> p.onAccent
        else -> color
    }
    Box(
        modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(bg)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.text(14.sp, FontWeight.SemiBold), color = fg, maxLines = 1)
    }
}

/** Slim message strip with an optional action, e.g. "Bluetooth is off – Turn on". */
@Composable
fun Banner(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    dismissLabel: String? = null,
    onDismiss: () -> Unit = {},
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.16f), color.copy(alpha = 0.05f))))
            .border(1.dp, color.copy(alpha = 0.28f), shape)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color)
        Spacer(Modifier.width(8.dp))
        Text(text, style = Type.text(14.sp, FontWeight.Medium), color = p.t1, modifier = Modifier.weight(1f))
        if (dismissLabel != null) {
            Text(
                dismissLabel,
                style = Type.text(14.sp, FontWeight.SemiBold),
                color = p.t2,
                modifier = Modifier
                    .clip(RoundedCornerShape(17.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (actionLabel != null) PillButton(actionLabel, onAction, color = color)
    }
}

/** Title + caption on the left, any control on the right. Used throughout Settings. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.text(15.sp, FontWeight.Medium), color = if (enabled) p.t1 else p.t3)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Type.text(12.5.sp), color = p.t2)
            }
        }
        Spacer(Modifier.width(14.dp))
        trailing()
    }
}

/** A group of settings rows inside one card, separated by inset hairlines. */
@Composable
fun SettingsGroup(title: String, modifier: Modifier = Modifier, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(modifier) {
        Label(title, Modifier.padding(start = 6.dp, bottom = 8.dp))
        GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, padding = PaddingValues(vertical = 4.dp), content = content)
        if (footer != null) {
            Text(
                footer,
                style = Type.text(12.5.sp),
                color = p.t3,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
            )
        }
    }
}

@Composable
fun GroupDivider() {
    Box(
        Modifier
            .padding(start = 18.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalPalette.current.line2),
    )
}
