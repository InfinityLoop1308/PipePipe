package project.pipepipe.app

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

expect class SystemBarColorsManager {
    fun applySystemBarColors(
        colorScheme: ColorScheme, isSystemDark: Boolean
    )
}
