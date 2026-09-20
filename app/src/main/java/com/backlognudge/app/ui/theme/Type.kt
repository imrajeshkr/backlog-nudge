package com.backlognudge.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.backlognudge.app.R

/**
 * Three families, three jobs. No serif anywhere.
 *
 *  - Poster (Big Shoulders Display): headings and titles. ALWAYS uppercase,
 *    tight tracking, line-height under 1. It is the only loud thing here.
 *  - Ui (Hanken Grotesk): everything a person reads as a sentence — task
 *    titles, body copy, buttons, settings labels. Sentence case.
 *  - Data (Spline Sans Mono): numbers and labels — durations, timestamps,
 *    section headers. Uppercase, wide tracking, small.
 *
 * Sizes are the web mockup's px scaled ~1.125x for a 392dp phone.
 */
val Poster: FontFamily = FontFamily(
    Font(R.font.big_shoulders_display_bold, FontWeight.Bold),
    Font(R.font.big_shoulders_display_extrabold, FontWeight.ExtraBold)
)

val Ui: FontFamily = FontFamily(
    Font(R.font.hanken_grotesk_regular, FontWeight.Normal),
    Font(R.font.hanken_grotesk_medium, FontWeight.Medium),
    Font(R.font.hanken_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.hanken_grotesk_bold, FontWeight.Bold)
)

val DataMono: FontFamily = FontFamily(
    Font(R.font.spline_sans_mono_regular, FontWeight.Normal),
    Font(R.font.spline_sans_mono_medium, FontWeight.Medium)
)

/**
 * Durations, timestamps, swipe labels (`15 MIN · 3 DAYS OLD`). Mockup `.min`.
 * Callers pass already-uppercased text.
 */
val MetaStyle = TextStyle(
    fontFamily = DataMono,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    letterSpacing = 1.0.sp, // ~0.09em
    fontWeight = FontWeight.Normal
)

/** Section/bucket headers — same data voice, a notch more air. Mockup `.sg`. */
val BucketStyle = MetaStyle.copy(
    fontSize = 10.5.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.3.sp, // ~0.12em
    fontWeight = FontWeight.Medium
)

/** The kicker over the nudge sheet — mono, green, wide. Mockup `.slb`. */
val KickerStyle = MetaStyle.copy(
    fontSize = 11.sp,
    letterSpacing = 1.1.sp // ~0.1em
)

/** Task row titles. Mockup `.rt` — sentence case, never uppercase. */
val RowTitle = TextStyle(
    fontFamily = Ui,
    fontSize = 18.sp,
    lineHeight = 23.sp,
    letterSpacing = (-0.18).sp, // ~-0.01em
    fontWeight = FontWeight.SemiBold
)

/** Body copy. Mockup `.obs` / `.under p`. */
val BodyCopy = TextStyle(
    fontFamily = Ui,
    fontSize = 15.sp,
    lineHeight = 22.sp,
    fontWeight = FontWeight.Normal
)

/**
 * The poster voice. Callers must uppercase the text — the face is drawn for it
 * and the mockup never sets it any other way.
 */
val PosterHeadline = TextStyle(
    fontFamily = Poster,
    fontWeight = FontWeight.Bold,
    fontSize = 36.sp,
    lineHeight = 34.sp, // ~0.95
    letterSpacing = 0.36.sp // ~0.01em
)

/** Screen titles and sheet titles. Mockup `.htitle` / `.stt`. */
val PosterTitle = PosterHeadline.copy(
    fontSize = 34.sp,
    lineHeight = 34.sp
)

val BacklogTypography = Typography(
    headlineLarge = PosterHeadline,
    headlineMedium = PosterHeadline.copy(fontSize = 30.sp, lineHeight = 29.sp),
    headlineSmall = PosterHeadline.copy(fontSize = 25.sp, lineHeight = 25.sp),
    titleLarge = RowTitle,
    titleMedium = RowTitle.copy(fontSize = 16.sp, lineHeight = 21.sp),
    bodyLarge = BodyCopy,
    bodyMedium = BodyCopy.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = BodyCopy.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(
        fontFamily = Ui,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    ),
    labelMedium = TextStyle(
        fontFamily = Ui,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = MetaStyle
)
