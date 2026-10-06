package online.assadawut.atom.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AtomCyan,
    secondary = FridayOrange,
    tertiary = UltronRed,
    background = DarkBackground,
    surface = DarkBackground
)

@Composable
fun ATOMTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}