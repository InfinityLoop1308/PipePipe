package project.pipepipe.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.PaletteStyle
import project.pipepipe.app.ui.component.player.SponsorBlockUtils
import project.pipepipe.shared.SharedContext


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PipePipeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = SharedContext.settingsManager

    // Read appearance settings
    var materialYouEnabled by remember {
        mutableStateOf(settingsManager.getBoolean("material_you_enabled_key", false))
    }

    var themeMode by remember {
        mutableStateOf(settingsManager.getString("theme_mode_key", "system"))
    }

    var themeColorHex by remember {
        mutableStateOf(settingsManager.getString("theme_color_key", "#e53935"))
    }

    // Add listeners for settings changes
    DisposableEffect(Unit) {
        val materialYouListener = settingsManager.addBooleanListener(
            "material_you_enabled_key",
            false
        ) { newValue ->
            materialYouEnabled = newValue
        }

        val themeModeListener = settingsManager.addStringListener(
            "theme_mode_key",
            "system"
        ) { newValue ->
            themeMode = newValue
        }

        val themeColorListener = settingsManager.addStringListener(
            "theme_color_key",
            "#e53935"
        ) { newValue ->
            themeColorHex = newValue
        }

        onDispose {
            materialYouListener?.deactivate()
            themeModeListener?.deactivate()
            themeColorListener?.deactivate()
        }
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
        DynamicMaterialExpressiveTheme(
            seedColor = customPrimaryColor,
            motionScheme = MotionScheme.expressive(),
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