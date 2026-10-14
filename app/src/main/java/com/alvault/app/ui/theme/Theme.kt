package com.alvault.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/*
 * Arah visual: brankas logam berwarna tosca tua (sama dengan ikon aplikasi) dengan satu aksen kuningan,
 * seperti penunjuk pada dial kombinasi. Hijau = aman, kuningan = peringatan, merah = bocor.
 * Judul layar memakai serif sistem; isi memakai sans sistem; monospace hanya untuk password.
 */

private val Tosca = Color(0xFF0F4C43)
private val Mint = Color(0xFF7FD1BC)
private val Brass = Color(0xFF8A6A1F)
private val BrassLight = Color(0xFFE0B65A)
private val Leaf = Color(0xFF2E7D5B)
private val LeafLight = Color(0xFF7ED3A4)
private val Crimson = Color(0xFFB42334)
private val CrimsonLight = Color(0xFFFF8A93)

private val LightColors = lightColorScheme(
    primary = Tosca,
    onPrimary = Color(0xFFF4FAF7),
    primaryContainer = Color(0xFFD4EFE5),
    onPrimaryContainer = Color(0xFF06241F),
    secondary = Brass,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E6C4),
    onSecondaryContainer = Color(0xFF3A2C08),
    tertiary = Leaf,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCDEFDD),
    onTertiaryContainer = Color(0xFF0B2E1F),
    background = Color(0xFFF4FAF7),
    onBackground = Color(0xFF0E1F1C),
    surface = Color(0xFFF4FAF7),
    onSurface = Color(0xFF0E1F1C),
    surfaceVariant = Color(0xFFE3EEE9),
    onSurfaceVariant = Color(0xFF425751),
    outline = Color(0xFF6F8981),
    outlineVariant = Color(0xFFC9D9D3),
    error = Crimson,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBDDE0),
    onErrorContainer = Color(0xFF410009),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF6F2),
    surfaceContainer = Color(0xFFE8F1EC),
    surfaceContainerHigh = Color(0xFFE1ECE6),
    surfaceContainerHighest = Color(0xFFDBE7E1)
)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF06241F),
    primaryContainer = Color(0xFF14544A),
    onPrimaryContainer = Color(0xFFCFF3E8),
    secondary = BrassLight,
    onSecondary = Color(0xFF3A2C08),
    secondaryContainer = Color(0xFF4F3E10),
    onSecondaryContainer = Color(0xFFF7E7BE),
    tertiary = LeafLight,
    onTertiary = Color(0xFF07301D),
    tertiaryContainer = Color(0xFF14472F),
    onTertiaryContainer = Color(0xFFCDEFDD),
    background = Color(0xFF0B1412),
    onBackground = Color(0xFFE1EEE9),
    surface = Color(0xFF0B1412),
    onSurface = Color(0xFFE1EEE9),
    surfaceVariant = Color(0xFF1B2B27),
    onSurfaceVariant = Color(0xFFA8BDB5),
    outline = Color(0xFF6F8A81),
    outlineVariant = Color(0xFF2B3F39),
    error = CrimsonLight,
    onError = Color(0xFF5C1620),
    errorContainer = Color(0xFF5C1620),
    onErrorContainer = Color(0xFFFFDADD),
    surfaceContainerLowest = Color(0xFF070E0C),
    surfaceContainerLow = Color(0xFF101A17),
    surfaceContainer = Color(0xFF14201D),
    surfaceContainerHigh = Color(0xFF1B2B27),
    surfaceContainerHighest = Color(0xFF223630)
)

private val Serif = FontFamily.Serif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.25.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp)
)

/** Gaya teks untuk password: lebar karakter tetap agar `0/O` dan `l/1` mudah dibedakan. */
val PasswordTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 17.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.5.sp
)

// Sudut tidak seragam: kontrol kecil rapat, sheet/dialog lebih lega.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AlVaultTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

/** Pasangan (latar, teks) untuk avatar entri; dipilih dari hash kategori agar konsisten antar sesi. */
@Composable
fun avatarColors(seed: String): Pair<Color, Color> {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) AvatarDark else AvatarLight
    return palette[Math.floorMod(seed.lowercase().hashCode(), palette.size)]
}

private val AvatarLight = listOf(
    Color(0xFFD4EFE5) to Color(0xFF0B3B33),
    Color(0xFFF3E6C4) to Color(0xFF4A3708),
    Color(0xFFDCE6F5) to Color(0xFF1B3358),
    Color(0xFFEBDDF0) to Color(0xFF47244F),
    Color(0xFFF6DADA) to Color(0xFF5A1D22),
    Color(0xFFDDE8C9) to Color(0xFF2D4112)
)

private val AvatarDark = listOf(
    Color(0xFF14544A) to Color(0xFFCFF3E8),
    Color(0xFF4F3E10) to Color(0xFFF7E7BE),
    Color(0xFF24395C) to Color(0xFFD6E3FA),
    Color(0xFF4A2D52) to Color(0xFFF1DDF6),
    Color(0xFF5C2B2F) to Color(0xFFFADADD),
    Color(0xFF384A1F) to Color(0xFFE3EFD0)
)
