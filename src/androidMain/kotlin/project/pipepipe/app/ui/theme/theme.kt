package project.pipepipe.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.PaletteStyle
import project.pipepipe.app.ui.component.player.SponsorBlockUtils
import project.pipepipe.shared.SharedContext


@Composable
fun PipePipeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = SharedContext.settingsManager

    // Read appearance settings
    val materialYouEnabled = remember {
        settingsManager.getBoolean("material_you_enabled_key", false)
    }

    val themeMode = remember {
        settingsManager.getString("theme_mode_key", "system")
    }

    val themeColorHex = remember {
        settingsManager.getString("theme_color_key", "#e53935")
    }

    // Determine dark theme based on theme mode
    val isDarkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> darkTheme // "system" or default
    }

    // Parse custom theme color
    val customPrimaryColor = SponsorBlockUtils.parseHexColor(themeColorHex, Color(0xFFE53935))

    if (materialYouEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val colorScheme = if (isDarkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        MaterialTheme(colorScheme = colorScheme, content = content)
    } else {
        DynamicMaterialTheme(
            seedColor = customPrimaryColor,
//            motionScheme = MotionScheme.expressive(),
            isDark = isDarkTheme,
            content = content
        )
    }

}

@Composable
fun supportingTextColor(): Color {
    val scheme = MaterialTheme.colorScheme
    val surfaceLuma = scheme.surface.luminance()
    val alpha = if (surfaceLuma > 0.5f) 0.6f else 0.7f
    return scheme.onSurface.copy(alpha = alpha)
}