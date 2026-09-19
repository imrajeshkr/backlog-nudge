package com.backlognudge.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.backlognudge.app.R

/**
 * Two voices, one rule: anything the USER said is set in a serif; anything the
 * APP says is sans. That's what makes the list read like the user's own notes
 * instead of system output.
 *
 * Instrument Serif (regular + italic) ships in res/font; FontFamily.Serif is
 * the fallback if the files are ever missing.
 */
val SerifVoice: FontFamily = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic)
)

/** Durations and timestamps: monospace-ish, uppercase, wide-tracked, small. */
val MetaStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.8.sp, // ~0.08em at 10sp
    fontWeight = FontWeight.Medium
)

/** Section/bucket headers — same mono voice, a notch of extra air. */
val BucketStyle = MetaStyle.copy(
    fontSize = 10.sp,
    letterSpacing = 1.2.sp
)

/** The serif voice at item-title size. */
val TitleSerif = TextStyle(
    fontFamily = SerifVoice,
    fontSize = 21.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.sp
)

/** The serif voice at headline size (nudge headline, empty state, onboarding). */
val HeadlineSerif = TextStyle(
    fontFamily = SerifVoice,
    fontSize = 34.sp,
    lineHeight = 38.sp,
    letterSpacing = (-0.3).sp
)

/** The big wordmark. */
val WordmarkSerif = TextStyle(
    fontFamily = SerifVoice,
    fontSize = 40.sp,
    lineHeight = 44.sp,
    letterSpacing = (-0.5).sp
)

/** UI chrome stays on the platform sans — only the defaults are re-spaced. */
val BacklogTypography = Typography(
    headlineLarge = HeadlineSerif,
    headlineMedium = HeadlineSerif.copy(fontSize = 28.sp, lineHeight = 32.sp),
    headlineSmall = HeadlineSerif.copy(fontSize = 24.sp, lineHeight = 28.sp),
    titleLarge = TitleSerif,
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = MetaStyle
)
