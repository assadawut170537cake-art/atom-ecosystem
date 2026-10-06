package online.assadawut.atom.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val OledDarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = Secondary,
    background = OledBlack,
    surface = OledBlack,
    onPrimary = OnPrimary,
    onSecondary = OnPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun AtomTheme(
    content: @Composable () -> Unit
) {
    // Always use deep OLED dark mode
    val colorScheme = OledDarkColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
