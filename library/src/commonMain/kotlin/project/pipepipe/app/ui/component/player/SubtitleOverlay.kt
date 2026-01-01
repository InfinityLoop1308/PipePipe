package project.pipepipe.app.ui.component.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import project.pipepipe.app.platform.SubtitleCue


/**
 * Platform-independent subtitle overlay component.
 * Renders subtitles from a list of SubtitleCue.
 */

@Composable
expect fun SubtitleOverlay(
    subtitles: List<SubtitleCue>,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    textColor: Color = Color.White,
    backgroundColor: Color = Color.Black.copy(alpha = 0.6f),
)
