package io.github.robertgrant0.bmsmonitor.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.robertgrant0.bmsmonitor.data.FlowUnit
import io.github.robertgrant0.bmsmonitor.data.PowerSample
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

fun socColor(p: Palette, soc: Int?): Color = when {
    soc == null -> p.t3
    soc <= 20 -> p.red
    soc <= 40 -> p.orange
    else -> p.green
}

private fun socColorDeep(p: Palette, soc: Int?): Color = when {
    soc == null -> p.t3
    soc <= 20 -> p.redDeep
    soc <= 40 -> p.orangeDeep
    else -> p.greenDeep
}

@Composable
private fun phase(periodMs: Int, enabled: Boolean): State<Float>? {
    if (!enabled) return null
    return rememberInfiniteTransition(label = "phase").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "phaseValue",
    )
}

/** 270° ring with a gradient sweep and a soft glow, with the percentage in the middle. */
@Composable
fun SocRing(soc: Int?, diameter: Dp, modifier: Modifier = Modifier, caption: String? = null) {
    val p = LocalPalette.current
    val color = socColor(p, soc)
    val deep = socColorDeep(p, soc)
    val sweep by animateFloatAsState((soc ?: 0) / 100f, tween(900), label = "soc")
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = diameter.toPx() * 0.08f
            val r = (size.minDimension - stroke) / 2f - 6.dp.toPx()
            val topLeft = Offset(center.x - r, center.y - r)
            val arcSize = Size(r * 2, r * 2)
            if (!p.flat) {
                drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = 0.20f), Color.Transparent), center, r + stroke * 1.6f),
                    radius = r + stroke * 1.6f,
                )
            }
            drawArc(p.track, 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (soc != null && sweep > 0.001f && p.flat) {
                drawArc(color, 135f, 270f * sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            } else if (soc != null && sweep > 0.001f) {
                drawArc(
                    color.copy(alpha = 0.16f), 135f, 270f * sweep, false, topLeft, arcSize,
                    style = Stroke(stroke + 8.dp.toPx(), cap = StrokeCap.Round),
                )
                rotate(135f, center) {
                    drawArc(
                        Brush.sweepGradient(0f to deep, 0.75f to color, 1f to deep, center = center),
                        0f, 270f * sweep, false, topLeft, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ValueUnit(
                value = soc?.toString() ?: "--",
                unit = "%",
                style = Type.number((diameter.value * 0.32f).sp),
                unitSize = (diameter.value * 0.12f).sp,
            )
            if (caption != null) {
                Spacer(Modifier.height(4.dp))
                Label(caption)
            }
        }
    }
}

/**
 * Upright battery that fills to [soc]. While charging the surface ripples and a bolt
 * shows; the ripple only animates when motion is enabled.
 */
@Composable
fun BatteryGlyph(soc: Int?, charging: Boolean, modifier: Modifier = Modifier, width: Dp = 56.dp, height: Dp = 100.dp) {
    val p = LocalPalette.current
    val color = socColor(p, soc)
    val level by animateFloatAsState((soc ?: 0) / 100f, tween(900), label = "level")
    val wave = phase(2600, charging && LocalMotion.current)
    val bolt = rememberVectorPainter(AppIcons.Bolt)
    Canvas(modifier.size(width, height)) {
        val w = size.width
        val h = size.height
        val pad = 5.dp.toPx()
        val cap = 9.dp.toPx()
        val bodyTop = cap
        val bodyH = h - cap
        // Terminal
        drawRoundRect(
            p.glyphOutline, Offset(w / 2 - 10.dp.toPx(), 0f), Size(20.dp.toPx(), cap + 4.dp.toPx()),
            CornerRadius(4.dp.toPx()),
        )
        // Body
        drawRoundRect(p.glyphBody, Offset(1.dp.toPx(), bodyTop), Size(w - 2.dp.toPx(), bodyH - 1.dp.toPx()), CornerRadius(14.dp.toPx()))
        drawRoundRect(
            p.glyphOutline, Offset(1.dp.toPx(), bodyTop), Size(w - 2.dp.toPx(), bodyH - 1.dp.toPx()),
            CornerRadius(14.dp.toPx()), style = Stroke(2.dp.toPx()),
        )
        if (soc == null) return@Canvas
        val innerTop = bodyTop + pad
        val innerH = bodyH - pad * 2
        val innerW = w - pad * 2
        val fillTop = innerTop + innerH * (1f - level)
        val clip = Path().apply {
            addRoundRect(RoundRect(pad, innerTop, pad + innerW, innerTop + innerH, CornerRadius(9.dp.toPx())))
        }
        clipPath(clip) {
            val brush = if (p.flat) SolidColor(color) else Brush.verticalGradient(listOf(color, color.copy(alpha = 0.5f)), fillTop, innerTop + innerH)
            if (charging) {
                val t = wave?.value ?: 0f
                val amp = 2.dp.toPx()
                val path = Path().apply {
                    moveTo(pad, innerTop + innerH)
                    var x = 0f
                    while (x <= innerW) {
                        val y = fillTop + 2.dp.toPx() + sin((x / 7.dp.toPx()) + t * 2f * PI.toFloat()) * amp
                        lineTo(pad + x, y)
                        x += 2.dp.toPx()
                    }
                    lineTo(pad + innerW, innerTop + innerH)
                    close()
                }
                drawPath(path, brush)
            } else {
                drawRect(brush, Offset(pad, fillTop), Size(innerW, innerH - (fillTop - innerTop)))
            }
            if (!p.flat) drawRect(Color.White.copy(alpha = 0.10f), Offset(pad, fillTop), Size(innerW * 0.32f, innerTop + innerH - fillTop))
        }
        if (charging) {
            val s = 20.dp.toPx()
            translate(w / 2 - s / 2, bodyTop + bodyH / 2 - s / 2 + 4.dp.toPx()) {
                with(bolt) { draw(Size(s, s), colorFilter = ColorFilter.tint(p.onAccent.copy(alpha = 0.85f))) }
            }
        }
    }
}

/**
 * One bar per cell. Scaled to at least a 50 mV window so a healthy pack looks level,
 * and the high/low cells are only highlighted once the spread is worth noticing.
 */
@Composable
fun CellBars(
    cells: List<Double>,
    isBalancing: (Int) -> Boolean,
    modifier: Modifier = Modifier,
    barHeight: Dp = 40.dp,
    gap: Dp = 8.dp,
    big: Boolean = false,
) {
    if (cells.isEmpty()) return
    val p = LocalPalette.current
    val mn = cells.min()
    val mx = cells.max()
    val spread = mx - mn
    val window = max(0.05, spread) * 1.25
    val bottom = mx - window
    val mark = spread >= 0.015
    val showValues = cells.size <= 8 || big
    val many = cells.size > 8
    val bigValueStyle = Type.figures(if (many) 11.sp else 15.sp)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) {
        cells.forEachIndexed { i, v ->
            val isMax = mark && v == mx
            val isMin = mark && v == mn
            val c = when {
                isMax -> p.orange
                isMin -> p.sky
                else -> p.green
            }
            val valueColor = when {
                isMax -> p.orange
                isMin -> p.sky
                else -> if (big) p.t1 else p.t2
            }
            val f = ((v - bottom) / window).coerceIn(0.0, 1.0).toFloat()
            val balancing = isBalancing(i)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (big && showValues) {
                    Text(Fmt.cell(v), style = bigValueStyle, color = valueColor, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                }
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(barHeight),
                ) {
                    val h = size.height * (0.3f + 0.7f * f)
                    val top = size.height - h
                    val strong = isMax || isMin
                    drawRoundRect(
                        if (p.flat) {
                            SolidColor(c.copy(alpha = if (strong) 1f else 0.72f))
                        } else {
                            Brush.verticalGradient(
                                listOf(c.copy(alpha = if (strong) 1f else 0.7f), c.copy(alpha = 0.2f)),
                                top, size.height,
                            )
                        },
                        Offset(0f, top), Size(size.width, h),
                        CornerRadius(if (big) 10.dp.toPx() else 6.dp.toPx()),
                    )
                    if (balancing) {
                        val r = 3.dp.toPx()
                        val cy = if (big) top + 14.dp.toPx() else max(r * 2, top - 7.dp.toPx())
                        drawCircle(p.gold.copy(alpha = 0.22f), r * 2.2f, Offset(size.width / 2, cy))
                        drawCircle(p.gold, r, Offset(size.width / 2, cy))
                    }
                }
                if (big) {
                    Spacer(Modifier.height(8.dp))
                    Label(if (many) "${i + 1}" else "Cell ${i + 1}")
                } else if (showValues) {
                    Spacer(Modifier.height(6.dp))
                    Text(Fmt.cell(v), style = Type.figures(12.sp, FontWeight.Medium), color = valueColor, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Solar → Batteries ← DC-DC & loads, with particles flowing in the direction of the
 * energy. All three values share one unit (W or A); positive bank/other = into the batteries.
 */
@Composable
fun EnergyFlow(
    solar: Double?,
    bank: Double?,
    other: Double?,
    unit: FlowUnit,
    solarCaption: String,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val t = phase(1800, LocalMotion.current)
    val sun = rememberVectorPainter(AppIcons.Sun)
    val battery = rememberVectorPainter(AppIcons.BatteryCharging)
    val van = rememberVectorPainter(AppIcons.Van)
    val amps = unit == FlowUnit.AMPS
    val threshold = if (amps) 0.4 else 5.0
    fun plain(v: Double) = if (amps) "${Fmt.signed(v, 1).trimStart('+')} A" else "${Math.round(v)} W"
    fun signed(v: Double) = if (amps) Fmt.signedAmps(v, 1) else Fmt.signedWatts(v)
    val bankColor = flowColor(p, bank, threshold)
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(60.dp),
        ) {
            val y = size.height / 2
            val xs = size.width / 6
            val xb = size.width / 2
            val xo = size.width * 5 / 6
            val rs = 20.dp.toPx()
            val rb = 26.dp.toPx()
            val phaseNow = t?.value ?: 0.5f
            link(xs + rs + 6.dp.toPx(), xb - rb - 6.dp.toPx(), y, p.gold, solar, threshold, towardEnd = true, phaseNow)
            link(xb + rb + 6.dp.toPx(), xo - rs - 6.dp.toPx(), y, p.violet, other, threshold, towardEnd = (other ?: 0.0) < 0, phaseNow)
            node(Offset(xs, y), rs, p.gold, sun, p.flat, p.onAccent)
            node(Offset(xb, y), rb, bankColor, battery, p.flat, p.onAccent)
            node(Offset(xo, y), rs, p.violet, van, p.flat, p.onAccent)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            FlowCaption(solar?.let { plain(it) } ?: "--", solarCaption, p.gold, Modifier.weight(1f))
            FlowCaption(bank?.let { signed(it) } ?: "--", "Batteries", bankColor, Modifier.weight(1f))
            FlowCaption(other?.let { signed(it) } ?: "--", "DC-DC & loads", p.violet, Modifier.weight(1f))
        }
    }
}

private fun flowColor(p: Palette, value: Double?, threshold: Double): Color = when {
    value == null -> p.t3
    value > threshold -> p.green
    value < -threshold -> p.sky
    else -> p.t2
}

@Composable
private fun FlowCaption(value: String, caption: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Type.figures(14.sp), color = color, maxLines = 1)
        Text(caption, style = Type.text(11.sp, FontWeight.Medium), color = LocalPalette.current.t3, maxLines = 1, textAlign = TextAlign.Center)
    }
}

private fun DrawScope.link(
    x1: Float,
    x2: Float,
    y: Float,
    color: Color,
    value: Double?,
    threshold: Double,
    towardEnd: Boolean,
    phase: Float,
) {
    drawLine(color.copy(alpha = 0.22f), Offset(x1, y), Offset(x2, y), 2.dp.toPx(), StrokeCap.Round)
    val active = value != null && abs(value) >= threshold
    if (!active) return
    val n = 4
    val r = 2.6.dp.toPx()
    val len = x2 - x1
    for (i in 0 until n) {
        var f = (i.toFloat() / n + phase) % 1f
        if (!towardEnd) f = 1f - f
        val alpha = min(1f, min(f, 1f - f) * 6f)
        drawCircle(color.copy(alpha = alpha), r, Offset(x1 + len * f, y))
    }
}

private fun DrawScope.node(
    c: Offset,
    r: Float,
    color: Color,
    icon: androidx.compose.ui.graphics.painter.Painter,
    flat: Boolean,
    iconOnFill: Color,
) {
    if (flat) {
        drawCircle(color, r, c)
    } else {
        drawCircle(color.copy(alpha = 0.10f), r, c)
        drawCircle(color.copy(alpha = 0.40f), r, c, style = Stroke(1.2.dp.toPx()))
    }
    val s = r * 0.9f
    translate(c.x - s / 2, c.y - s / 2) {
        with(icon) { draw(Size(s, s), colorFilter = ColorFilter.tint(if (flat) iconOnFill else color)) }
    }
}

/**
 * Bank power (or current) over the last [windowMs]: area above zero in green (charging),
 * below in sky (discharging), with solar as a thin gold line.
 */
@Composable
fun PowerChart(
    samples: List<PowerSample>,
    now: Long,
    windowMs: Long,
    modifier: Modifier = Modifier,
    height: Dp = 84.dp,
    unit: FlowUnit = FlowUnit.WATTS,
) {
    val p = LocalPalette.current
    val amps = unit == FlowUnit.AMPS
    val series = remember(samples, now, unit) {
        samples.filter { now - it.time <= windowMs }
            .mapNotNull { s -> (if (amps) s.bankAmps else s.bankWatts)?.let { s.time to it } }
    }
    val solarSeries = remember(samples, now, unit) {
        samples.filter { now - it.time <= windowMs }
            .mapNotNull { s -> (if (amps) s.solarAmps else s.solarWatts)?.let { s.time to it } }
    }
    if (series.size < 2) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text("Collecting data…", style = Type.text(13.sp), color = p.t3)
        }
        return
    }
    val floor = if (amps) 5f else 50f
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val w = size.width
        val h = size.height
        val values = series.map { it.second } + solarSeries.map { it.second }
        var top = max(values.max(), floor)
        var bottom = min(values.min(), -floor)
        val padding = (top - bottom) * 0.08f
        top += padding
        bottom -= padding
        fun x(time: Long) = w * (1f - (now - time).toFloat() / windowMs)
        fun y(v: Float) = h * (top - v) / (top - bottom)
        val zero = y(0f)

        drawLine(
            p.t1.copy(alpha = 0.12f), Offset(0f, zero), Offset(w, zero), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
        )
        val line = Path()
        val area = Path()
        series.forEachIndexed { i, (time, v) ->
            val px = x(time)
            val py = y(v)
            if (i == 0) {
                line.moveTo(px, py)
                area.moveTo(px, zero)
                area.lineTo(px, py)
            } else {
                line.lineTo(px, py)
                area.lineTo(px, py)
            }
        }
        area.lineTo(x(series.last().first), zero)
        area.close()

        clipRect(bottom = zero) {
            drawPath(area, Brush.verticalGradient(listOf(p.green.copy(alpha = 0.32f), Color.Transparent), 0f, zero))
            drawPath(line, p.green, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        clipRect(top = zero) {
            drawPath(area, Brush.verticalGradient(listOf(Color.Transparent, p.sky.copy(alpha = 0.32f)), zero, h))
            drawPath(line, p.sky, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (solarSeries.size >= 2) {
            val solar = Path()
            solarSeries.forEachIndexed { i, (time, v) ->
                val px = x(time)
                val py = y(v)
                if (i == 0) solar.moveTo(px, py) else solar.lineTo(px, py)
            }
            drawPath(
                solar, p.gold.copy(alpha = 0.85f),
                style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        val (lastTime, lastValue) = series.last()
        val end = Offset(x(lastTime), y(lastValue))
        val endColor = if (lastValue >= 0) p.green else p.sky
        drawCircle(endColor.copy(alpha = 0.18f), 9.dp.toPx(), end)
        drawCircle(p.card2, 6.5.dp.toPx(), end)
        drawCircle(endColor, 4.5.dp.toPx(), end)
    }
}

/** Expanding rings, used while scanning for devices. */
@Composable
fun RadarPulse(color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp, icon: ImageVector = AppIcons.Bluetooth) {
    val t = phase(1800, LocalMotion.current)
    val painter = rememberVectorPainter(icon)
    Canvas(modifier.size(size)) {
        val maxR = this.size.minDimension / 2
        val v = t?.value ?: 0.4f
        for (k in 0 until 2) {
            val f = (v + k * 0.5f) % 1f
            drawCircle(color.copy(alpha = 0.35f * (1f - f)), maxR * (0.45f + 0.55f * f), style = Stroke(1.5.dp.toPx()))
        }
        drawCircle(color.copy(alpha = 0.14f), maxR * 0.45f)
        val s = maxR * 0.55f
        translate(center.x - s / 2, center.y - s / 2) {
            with(painter) { draw(Size(s, s), colorFilter = ColorFilter.tint(color)) }
        }
    }
}

@Composable
fun Gap(size: Dp) {
    Spacer(Modifier.size(size))
}

@Composable
fun HGap(size: Dp) {
    Spacer(Modifier.width(size))
}
