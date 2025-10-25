package project.pipepipe.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import project.pipepipe.app.ui.component.player.SponsorBlockUtils
import project.pipepipe.app.SharedContext

private fun getCustomDarkColor(color: Color): Color {
    return when (color) {
        SponsorBlockUtils.parseHexColor("#e53935") -> SponsorBlockUtils.parseHexColor("#992722")
        SponsorBlockUtils.parseHexColor("#f57c00") -> SponsorBlockUtils.parseHexColor("#a35300")
        SponsorBlockUtils.parseHexColor("#9e9e9e") -> SponsorBlockUtils.parseHexColor("#878787")
        SponsorBlockUtils.parseHexColor("#FB7299") -> SponsorBlockUtils.parseHexColor("#D94E74")
        else -> color
    }
}

private var currentMaterialYouEnabled = false
private var currentCustomPrimaryColor = Color(0xFFE53935)
private var isDarkTheme = false

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PipePipeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val settingsManager = SharedContext.settingsManager


    var materialYouEnabled by remember {
        mutableStateOf(settingsManager.getBoolean("material_you_enabled_key", false))
    }

    var themeMode by remember {
        mutableStateOf(settingsManager.getString("theme_mode_key", "system"))
    }

    var themeColorHex by remember {
        mutableStateOf(settingsManager.getString("theme_color_key", "#E53935"))
    }

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
            "#FFFFFF"
        ) { newValue ->
            themeColorHex = newValue
        }

        onDispose {
            materialYouListener?.deactivate()
            themeModeListener?.deactivate()
            themeColorListener?.deactivate()
        }
    }

    isDarkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> darkTheme // "system" or default
    }

    val customPrimaryColor = SponsorBlockUtils.parseHexColor(themeColorHex, Color(0xFFE53935))

    currentMaterialYouEnabled = materialYouEnabled
    currentCustomPrimaryColor = customPrimaryColor

    val colorScheme = if (materialYouEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDarkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    } else {
        if (isDarkTheme) {
            darkColorScheme()
        } else {
            lightColorScheme()
        }
    }

    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        val insetsController = WindowCompat.getInsetsController(window, view)
        applySystemBarColors(insetsController, colorScheme)
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun supportingTextColor(): Color {
    val scheme = MaterialTheme.colorScheme
    val surfaceLuma = scheme.surface.luminance()
    val alpha = if (surfaceLuma > 0.5f) 0.6f else 0.7f
    return scheme.onSurface.copy(alpha = alpha)
}

@Composable
fun customTopBarColor(): Color {
    return if (currentMaterialYouEnabled || currentCustomPrimaryColor == Color.White) {
        MaterialTheme.colorScheme.surface
    } else {
        if (isDarkTheme) {
            getCustomDarkColor(currentCustomPrimaryColor)
        } else {
            currentCustomPrimaryColor
        }
    }
}

@Composable
fun onCustomTopBarColor(): Color {
    return if (currentMaterialYouEnabled || currentCustomPrimaryColor == Color.White) {
        LocalContentColor.current
    } else {
        getContrastingColor(currentCustomPrimaryColor)
    }
}

fun getContrastingColor(backgroundColor: Color): Color {
    val luminance = backgroundColor.luminance()
    return if (luminance > 0.5f) Color.Black.copy(alpha = 0.87f)
    else Color.White.copy(alpha = 0.95f)
}

fun applySystemBarColors(insetsController: WindowInsetsControllerCompat, colorScheme: androidx.compose.material3.ColorScheme) {
    val topBarColor = if (currentMaterialYouEnabled || currentCustomPrimaryColor == Color.White) {
        colorScheme.surface
    } else {
        if (isDarkTheme) {
            getCustomDarkColor(currentCustomPrimaryColor)
        } else {
            currentCustomPrimaryColor
        }
    }
    val useLightIcons = topBarColor.luminance() < 0.5f
    insetsController.isAppearanceLightStatusBars = !useLightIcons
    insetsController.isAppearanceLightNavigationBars = !isDarkTheme
}