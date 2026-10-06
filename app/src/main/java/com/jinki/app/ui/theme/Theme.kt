package com.jinki.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

val JinkaShapes = Shapes(
    extraSmall = RoundedCornerShape(Dimens.rXs),
    small = RoundedCornerShape(Dimens.rSm),
    medium = RoundedCornerShape(Dimens.rMd),
    large = RoundedCornerShape(Dimens.rLg),
    extraLarge = RoundedCornerShape(Dimens.rLg)
)

private val JinkaColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentSoft,
    onPrimaryContainer = TextPrimary,
    secondary = Accent,
    onSecondary = OnAccent,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceAlt,
    onSurfaceVariant = TextSecondary,
    outline = HairlineStrong,
    outlineVariant = Hairline
)

@Composable
fun JinkaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JinkaColorScheme,
        typography = JinkaTypography,
        shapes = JinkaShapes,
        content = content
    )
}
