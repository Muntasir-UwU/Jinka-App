@file:OptIn(ExperimentalTextApi::class)

package com.jinki.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinki.app.R


object Dimens {
    val spaceXs = 6.dp
    val spaceSm = 12.dp
    val spaceMd = 16.dp
    val spaceLg = 20.dp
    val spaceXl = 28.dp

    val rXs = 8.dp
    val rSm = 12.dp
    val rMd = 18.dp
    val rLg = 28.dp
    val rPill = 999.dp
}


private val DisplayFamily = FontFamily(
    Font(R.font.fraunces_var, FontWeight.W600, variationSettings = wght(600)),
    Font(R.font.fraunces_var, FontWeight.W700, variationSettings = wght(700))
)
private val BodyFamily = FontFamily(
    Font(R.font.inter_var, FontWeight.W400, variationSettings = wght(400)),
    Font(R.font.inter_var, FontWeight.W500, variationSettings = wght(500)),
    Font(R.font.inter_var, FontWeight.W600, variationSettings = wght(600))
)

private fun wght(weight: Int) = FontVariation.Settings(FontVariation.weight(weight))

val JinkaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.W700,
        fontSize = 40.sp,
        lineHeight = 46.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.W700,
        fontSize = 30.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.W600,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.W600,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.W600,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.W500,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)
