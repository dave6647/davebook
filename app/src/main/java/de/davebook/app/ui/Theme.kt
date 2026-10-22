package de.davebook.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val HeartPink = Color(0xFFFF5C8A)
val HeartDeep = Color(0xFFD81B60)
val SetupInk = Color(0xFF4A0E25)

val LightColors = lightColorScheme(
    primary = Color(0xFFC2185B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E0021),
    secondary = Color(0xFF74565F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF2B151C),
    tertiary = Color(0xFF7C5635),
    onTertiary = Color.White,
    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF22191B),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF22191B),
    surfaceVariant = Color(0xFFF2DDE2),
    onSurfaceVariant = Color(0xFF514347),
    outline = Color(0xFF837377),
    outlineVariant = Color(0xFFD5C2C6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F2),
    surfaceContainer = Color(0xFFFCEAED),
    surfaceContainerHigh = Color(0xFFF6E4E7),
    surfaceContainerHighest = Color(0xFFF0DEE1),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF5E1133),
    primaryContainer = Color(0xFF8E2A4F),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFE2BDC6),
    onSecondary = Color(0xFF422931),
    secondaryContainer = Color(0xFF5A3F47),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFEFBD94),
    onTertiary = Color(0xFF48290C),
    background = Color(0xFF191113),
    onBackground = Color(0xFFEFDFE1),
    surface = Color(0xFF191113),
    onSurface = Color(0xFFEFDFE1),
    surfaceVariant = Color(0xFF514347),
    onSurfaceVariant = Color(0xFFD5C2C6),
    outline = Color(0xFF9E8C90),
    outlineVariant = Color(0xFF514347),
    surfaceContainerLowest = Color(0xFF140C0E),
    surfaceContainerLow = Color(0xFF22191B),
    surfaceContainer = Color(0xFF261D1F),
    surfaceContainerHigh = Color(0xFF31282A),
    surfaceContainerHighest = Color(0xFF3C3235),
)

@Composable
fun DavebookTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
