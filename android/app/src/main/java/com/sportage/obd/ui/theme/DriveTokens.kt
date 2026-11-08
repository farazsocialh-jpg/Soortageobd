package com.sportage.obd.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Dimension tokens for the driving interface (design doc §6).
 *
 * Every value enforces the NHTSA / Design-for-Driving minimums: 76dp touch
 * targets, 24dp separation (23dp minimum), 24dp card padding and gaps.
 */
object DriveDimens {
    /** Minimum touch target edge, dp. */
    val TouchTarget = 76.dp
    /** Minimum spacing between adjacent touch targets (spec minimum is 23dp). */
    val TouchGap = 24.dp
    /** Slim top status strip height. */
    val StatusStrip = 48.dp
    /** Bottom action bar height. */
    val BottomBar = 96.dp
    val CardPadding = 24.dp
    val CardGap = 24.dp
    val SectionGap = 48.dp
    /** Card corner radius. */
    val CardCorner = 20.dp
    /** Primary button corner radius; chips and pills use the full (pill) shape. */
    val ButtonCorner = 28.dp
    /** Minimum diameter of the hero gauge. */
    val HeroMin = 320.dp
    /** Navigation icon size in the bottom bar. */
    val NavIcon = 32.dp
}
