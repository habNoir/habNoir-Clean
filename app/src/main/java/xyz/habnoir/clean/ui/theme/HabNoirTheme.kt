package xyz.habnoir.clean.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HabNoirDarkScheme = darkColorScheme(
    primary = HabNoirColors.WarmAlabaster,
    onPrimary = HabNoirColors.ObsidianVoid,
    secondary = HabNoirColors.TonalFolio,
    onSecondary = HabNoirColors.WarmAlabaster,
    tertiary = HabNoirColors.WarmChampagne,
    onTertiary = HabNoirColors.ObsidianVoid,
    background = HabNoirColors.ObsidianVoid,
    onBackground = HabNoirColors.WarmAlabaster,
    surface = HabNoirColors.ObsidianVoid,
    onSurface = HabNoirColors.WarmAlabaster,
    surfaceVariant = HabNoirColors.TonalFolio,
    onSurfaceVariant = HabNoirColors.MutedSlate,
    surfaceContainerLow = HabNoirColors.DockedBar,
    surfaceContainer = HabNoirColors.RaisedFolio,
    surfaceContainerHigh = HabNoirColors.TonalFolio,
    outline = HabNoirColors.ActiveHairline,
    outlineVariant = HabNoirColors.Hairline,
    error = HabNoirColors.MutedClayRose,
    onError = HabNoirColors.ObsidianVoid
)

@Composable
fun HabNoirTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HabNoirDarkScheme,
        typography = HabNoirTypography,
        content = content
    )
}
