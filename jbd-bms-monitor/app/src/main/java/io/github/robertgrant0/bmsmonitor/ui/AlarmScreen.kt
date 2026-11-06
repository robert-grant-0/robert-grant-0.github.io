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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Full-screen siren page: pulsing red, rings radiating from a warning symbol, one huge Silence button. */
@Composable
fun AlarmScreen(messages: List<String>, onSilence: () -> Unit, onOpenApp: () -> Unit) {
    val motion = LocalMotion.current
    val t = if (motion) {
        rememberInfiniteTransition(label = "alarm").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
            label = "rings",
        )
    } else {
        null
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF12040A))
            .drawBehind {
                val v = t?.value ?: 0.5f
                val glow = 0.55f + 0.25f * kotlin.math.sin(v * 2f * Math.PI.toFloat())
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFFB91C1C).copy(alpha = glow), Color(0xFF450A0A).copy(alpha = 0.6f), Color.Transparent),
                        center = Offset(size.width / 2, size.height * 0.32f),
                        radius = size.maxDimension * 0.75f,
                    ),
                )
            }
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(180.dp)) {
                    val v = t?.value ?: 0.3f
                    val maxR = size.minDimension / 2
                    for (k in 0 until 3) {
                        val f = (v + k / 3f) % 1f
                        drawCircle(
                            Color.White.copy(alpha = 0.45f * (1f - f)),
                            radius = maxR * (0.38f + 0.62f * f),
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                    drawCircle(Color.White.copy(alpha = 0.12f), maxR * 0.38f)
                }
                Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(52.dp))
            }
            Text(
                "BATTERY ALARM",
                style = Type.title(34.sp).copy(letterSpacing = 0.06.em),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            for (m in messages) {
                val shape = RoundedCornerShape(18.dp)
                Text(
                    m,
                    style = Type.text(20.sp, FontWeight.SemiBold),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            val buttonShape = RoundedCornerShape(36.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(buttonShape)
                    .background(Color.White)
                    .clickable(onClick = onSilence),
                contentAlignment = Alignment.Center,
            ) {
                Text("Silence alarm", style = Type.title(26.sp), color = Color(0xFFB91C1C))
            }
            Text(
                "Open battery dashboard",
                style = Type.text(16.sp, FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenApp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
