package project.pipepipe.app

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

actual class SystemBarColorsManager(val insetsController: WindowInsetsControllerCompat) {
    actual fun applySystemBarColors(colorScheme: ColorScheme, isSystemDark: Boolean) {
        val pureBlackEnabled = isPureBlackEnabled()
        val materialYouEnabled = isMaterialYouEnabled()
        val customPrimaryColor = getCustomPrimaryColor()
        val isDark = isDarkTheme(isSystemDark)

        val topBarColor = if (pureBlackEnabled && isDark) {
            Color.Black
        } else if (materialYouEnabled || customPrimaryColor == Color.White) {
            colorScheme.surface
        } else {
            if (isDark) {
                getCustomDarkColor(customPrimaryColor)
            } else {
                customPrimaryColor
            }
        }
        val useLightIcons = topBarColor.luminance() < 0.5f
        runCatching {
            insetsController.isAppearanceLightStatusBars = !useLightIcons
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }
}


