package ua.vytraty.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val ExpenseRed = Color(0xFFD32F2F)
val IncomeGreen = Color(0xFF2E7D32)
val TransferBlue = Color(0xFF1565C0)
val WarnAmber = Color(0xFFF9A825)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E5EFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF00897B),
    tertiary = Color(0xFF7E57C2),
    background = Color(0xFFF6F7FB),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB6FF),
    onPrimary = Color(0xFF00296B),
    primaryContainer = Color(0xFF1B3E99),
    secondary = Color(0xFF4DB6AC),
    tertiary = Color(0xFFB39DDB),
)

@Composable
fun VytratyTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
