package project.pipepipe.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp



@Composable
private fun toastBackgroundColor(): Color {
    val scheme = MaterialTheme.colorScheme
    val surface = scheme.surface

    val overlay = if (surface.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.6f)
    } else {
        Color.White.copy(alpha = 0.6f)
    }

    return overlay.compositeOver(surface)
}



@Composable
fun Toast(
    message: String,
    modifier: Modifier = Modifier
) {
    val background = toastBackgroundColor()
    val contentColor = MaterialTheme.colorScheme.inverseOnSurface

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 64.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = background,
            contentColor = contentColor,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 6.dp
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                maxLines = 2
            )
        }
    }
}