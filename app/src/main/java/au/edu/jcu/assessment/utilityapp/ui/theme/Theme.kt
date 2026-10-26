package au.edu.jcu.assessment.utilityapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A shared palette keeps screens, dialogs and system controls visually consistent.
private val LightColors = lightColorScheme(
    primary = Color(0xFF286653), onPrimary = Color.White,
    primaryContainer = Color(0xFF163F35), onPrimaryContainer = Color(0xFFF0F8F2),
    secondary = Color(0xFF5C7165), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0ECE3), onSecondaryContainer = Color(0xFF234638),
    tertiary = Color(0xFF806541), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0E9DD), onTertiaryContainer = Color(0xFF504633),
    background = Color(0xFFF6F7F2), onBackground = Color(0xFF202D27),
    surface = Color(0xFFFEFFFB), onSurface = Color(0xFF202D27),
    surfaceContainerHighest = Color(0xFFE9EEE7),
    surfaceVariant = Color(0xFFE9EEE7), onSurfaceVariant = Color(0xFF657168),
    outline = Color(0xFF89958B), outlineVariant = Color(0xFFDFE5DB),
    error = Color(0xFFAF4039), onError = Color.White,
    errorContainer = Color(0xFFFBE8E3), onErrorContainer = Color(0xFF782D28)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA0D4B5), onPrimary = Color(0xFF153A2B),
    primaryContainer = Color(0xFF214D3E), onPrimaryContainer = Color(0xFFF0F8F2),
    secondary = Color(0xFFB6CBB9), onSecondary = Color(0xFF263C2D),
    secondaryContainer = Color(0xFF2A4033), onSecondaryContainer = Color(0xFFD8EBDA),
    tertiary = Color(0xFFD9C4A1), onTertiary = Color(0xFF3D3220),
    tertiaryContainer = Color(0xFF39372D), onTertiaryContainer = Color(0xFFE6DCC8),
    background = Color(0xFF111A15), onBackground = Color(0xFFE4EBE1),
    surface = Color(0xFF1B251E), onSurface = Color(0xFFE4EBE1),
    surfaceContainerHighest = Color(0xFF2A362D),
    surfaceVariant = Color(0xFF2A362D), onSurfaceVariant = Color(0xFFB0BDB1),
    outline = Color(0xFF879589), outlineVariant = Color(0xFF344238),
    error = Color(0xFFFFB4A9), onError = Color(0xFF601D19),
    errorContainer = Color(0xFF512B27), onErrorContainer = Color(0xFFFFDAD2)
)

private val StudyTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 23.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.6.sp)
)

@Composable
fun StudyDayTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = StudyTypography,
        shapes = Shapes(extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content
    )
}
