package io.github.robertgrant0.bmsmonitor.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.robertgrant0.bmsmonitor.R

/** Inter (SIL Open Font License 1.1), bundled so the app looks the same on every tablet. */
val InterFonts = AppFonts(
    text = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    ),
    display = FontFamily(
        Font(R.font.inter_display_medium, FontWeight.Medium),
        Font(R.font.inter_display_semibold, FontWeight.SemiBold),
    ),
)
