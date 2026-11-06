package io.github.robertgrant0.bmsmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** First-run screen that explains why Bluetooth access is needed. */
@Composable
fun WelcomeScreen(locationWording: Boolean, onGrant: () -> Unit, onOpenAppSettings: () -> Unit) {
    val p = LocalPalette.current
    AuroraBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                    BatteryGlyph(86, charging = true, width = 52.dp, height = 92.dp)
                    BatteryGlyph(74, charging = true, width = 60.dp, height = 108.dp)
                    BatteryGlyph(91, charging = true, width = 52.dp, height = 92.dp)
                }
                RadarPulse(p.sky, Modifier.padding(top = 0.dp).size(46.dp))
            }
            Spacer(Modifier.height(30.dp))
            Text(
                "Your batteries, at a glance",
                style = Type.title(32.sp),
                color = p.t1,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Connect to the JBD battery management system in each of your batteries and keep an eye on all " +
                    "three at once.",
                style = Type.text(16.sp),
                color = p.t2,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 520.dp),
            )
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Feature(AppIcons.BatteryCharging, p.green, "Charge, current, voltage and every cell, live")
                Feature(AppIcons.Bluetooth, p.sky, "Stays connected in the background and reconnects by itself")
                Feature(AppIcons.Shield, p.orange, "Alarms with a loud siren when a BMS reports a fault")
            }
            Spacer(Modifier.height(32.dp))
            PillButton(
                "Allow Bluetooth access",
                onGrant,
                filled = true,
                modifier = Modifier
                    .widthIn(min = 280.dp)
                    .height(52.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (locationWording) {
                    "Android calls this permission “Location” on this version. The app never uses your location."
                } else {
                    "Notifications are used for battery alarms."
                },
                style = Type.text(13.sp),
                color = p.t3,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 440.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Open app settings",
                style = Type.text(14.sp, FontWeight.SemiBold),
                color = p.t2,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenAppSettings)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, color: Color, text: String) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = Type.text(15.sp, FontWeight.Medium), color = p.t1)
    }
}
