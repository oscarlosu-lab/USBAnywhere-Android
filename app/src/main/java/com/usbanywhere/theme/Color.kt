package com.usbanywhere.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// OBSIDIAN PRECISION DESIGN SYSTEM TOKENS (v3.2.0)
// Minimalist developer-grade monochrome with Electric Acid/Phosphor Lime accent
// ============================================================================

val ObsidianBg = Color(0xFF0B0C0E)              // Deep obsidian black
val ObsidianSurface = Color(0xFF131417)         // Primary card surface
val ObsidianSurfaceElevated = Color(0xFF16171B) // Topbars / elevated panels
val ObsidianBorder = Color(0xFF22242A)          // Crisp 1px boundary
val ObsidianBorderFocused = Color(0xFFD2F800)   // High-contrast highlight
val PhosphorLime = Color(0xFFD2F800)            // Signature electric action accent
val PhosphorLimeMuted = Color(0xFFB8DA00)
val TextPrimary = Color(0xFFEDEDED)             // Sharp white text
val TextSecondary = Color(0xFF8E919A)           // Refined secondary muted text
val TextHardware = Color(0xFFA1A1AA)            // Monospace hardware IDs

val AccentWarning = Color(0xFFF59E0B)           // Amber warning
val AccentError = Color(0xFFEF4444)             // Crisp ruby error / detach

// Compatibility Aliases for seamless ecosystem upgrade:
val DeepDarkBg = ObsidianBg
val SurfaceDark = ObsidianSurface
val CardBorder = ObsidianBorder
val NeonGreen = PhosphorLime
val NeonCyan = TextHardware
val ElectricBlue = PhosphorLime

val Purple80 = PhosphorLime
val PurpleGrey80 = TextHardware
val Pink80 = PhosphorLimeMuted

val Purple40 = PhosphorLime
val PurpleGrey40 = TextSecondary
val Pink40 = PhosphorLime
