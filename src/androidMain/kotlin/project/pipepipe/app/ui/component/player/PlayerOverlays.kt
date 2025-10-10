package project.pipepipe.app.ui.component.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.fontscaling.MathUtils.lerp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import kotlin.math.absoluteValue
import kotlin.math.min

@Composable
fun PlayerOverlays(
    isLoading: Boolean,
    showVolumeOverlay: Boolean,
    volumeProgress: Float,
    showBrightnessOverlay: Boolean,
    brightnessProgress: Float,
    swipeSeekState: SwipeSeekUiState?,
    doubleTapOverlayState: DoubleTapOverlayState?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = showVolumeOverlay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            VolumeOverlay(
                progress = volumeProgress,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = showBrightnessOverlay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            BrightnessOverlay(
                progress = brightnessProgress,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = swipeSeekState != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            swipeSeekState?.let {
                SwipeSeekOverlay(
                    state = it,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        AnimatedVisibility(
            visible = doubleTapOverlayState != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            doubleTapOverlayState?.let {
                DoubleTapOverlay(
                    state = it,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

enum class SeekDirection { Forward, Backward }


@Composable
private fun FastSeekIndicator(
    direction: SeekDirection,
    modifier: Modifier = Modifier,
    triangleCount: Int = 3,
    baseAlpha: Float = 0.35f,
    highlightAlpha: Float = 1f,
    cycleDuration: Int = 1000
) {
    val infiniteTransition = rememberInfiniteTransition("fast-seek")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = cycleDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val FastSeekTriangle = ImageVector.Builder(
        name = "FastSeekTriangle",
        defaultWidth = 16.dp,
        defaultHeight = 20.dp,
        viewportWidth = 20f,
        viewportHeight = 20f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF000000)),
            fillAlpha = 1f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(0f, 0f)
            lineTo(0f, 20f)
            lineTo(20f, 10f)
            close()
        }
    }.build()



    Row(
        modifier = modifier
            .wrapContentSize()
            .padding(horizontal = 16.dp),
        horizontalArrangement = when (direction) {
            SeekDirection.Forward -> Arrangement.End
            SeekDirection.Backward -> Arrangement.Start
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val rawPosition = phase * triangleCount
        val stepIndex = rawPosition.toInt() % triangleCount
        val stepProgress = rawPosition - stepIndex.toFloat()

        val (activeIndex, nextIndex, transitionProgress) =
            if (direction == SeekDirection.Forward) {
                val active = stepIndex
                val next = (active + 1) % triangleCount
                Triple(active, next, stepProgress)
            } else {
                val active = (triangleCount - 1 - stepIndex + triangleCount) % triangleCount
                val next = (active + triangleCount - 1) % triangleCount
                Triple(active, next, stepProgress)
            }

        repeat(triangleCount) { index ->
            val alpha = when (index) {
                activeIndex -> lerp(highlightAlpha, baseAlpha, transitionProgress)
                nextIndex -> lerp(baseAlpha, highlightAlpha, transitionProgress)
                else -> baseAlpha
            }

            Icon(
                painter = rememberVectorPainter(FastSeekTriangle),
                contentDescription = null,
                tint = Color.White.copy(alpha = alpha),
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        if (direction == SeekDirection.Backward) {
                            scaleX = -1f
                        }
                    }
            )
        }
    }
}

@Composable
fun DoubleTapOverlay(
    state: DoubleTapOverlayState,
    modifier: Modifier = Modifier
) {

    val LeftInnerArcShape = GenericShape { size, _ ->
        val arcRadius = min(size.height / 2f, size.width)
        moveTo(0f, 0f)
        lineTo(size.width - arcRadius, 0f)
        arcTo(
            rect = Rect(
                offset = Offset(size.width - 2 * arcRadius, 0f),
                size = Size(arcRadius * 2, size.height)
            ),
            startAngleDegrees = -90f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false
        )
        lineTo(0f, size.height)
        close()
    }

    val RightInnerArcShape = GenericShape { size, _ ->
        val arcRadius = min(size.height / 2f, size.width)
        moveTo(size.width, 0f)
        lineTo(arcRadius, 0f)
        arcTo(
            rect = Rect(
                offset = Offset(0f, 0f),
                size = Size(arcRadius * 2, size.height)
            ),
            startAngleDegrees = -90f,
            sweepAngleDegrees = -180f,
            forceMoveTo = false
        )
        lineTo(size.width, size.height)
        close()
    }

    val alignment = when (state.portion) {
        DisplayPortion.Left -> Alignment.CenterStart
        DisplayPortion.Right -> Alignment.CenterEnd
        DisplayPortion.Middle -> Alignment.Center // 不会用到
    }

    val shape = when (state.portion) {
        DisplayPortion.Left -> LeftInnerArcShape
        DisplayPortion.Right -> RightInnerArcShape
        DisplayPortion.Middle -> RoundedCornerShape(16.dp)
    }

    val direction = when (state.portion) {
        DisplayPortion.Left -> SeekDirection.Backward
        DisplayPortion.Right -> SeekDirection.Forward
        DisplayPortion.Middle -> null
    }



    val totalSeconds = (state.accumulatedSeekMs.absoluteValue / 1000f)
    val label = if (totalSeconds % 1f == 0f) {
        "${totalSeconds.toInt()}s"
    } else {
        String.format("%.1fs", totalSeconds)
    }

    Box(
        modifier = modifier,
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .background(Color.Black.copy(alpha = 0.55f), shape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (direction == null) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    FastSeekIndicator(
                        direction = direction,
                        modifier = Modifier.height(36.dp)
                    )
                }
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun VolumeOverlay(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val coerced = progress.coerceIn(0f, 1f)
    val icon = when {
        coerced <= 0f -> Icons.Default.VolumeOff
        coerced < 0.5f -> Icons.Default.VolumeDown
        else -> Icons.AutoMirrored.Filled.VolumeUp
    }

    Box(
        modifier = modifier
            .size(128.dp)
            .background(Color.Black.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = coerced,
            modifier = Modifier.size(128.dp),
            color = Color.White,
            strokeWidth = 4.dp
        )
        Icon(
            icon,
            contentDescription = stringResource(MR.strings.player_volume),
            tint = Color.White,
            modifier = Modifier.size(70.dp)
        )
    }
}

@Composable
fun BrightnessOverlay(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val coerced = progress.coerceIn(0f, 1f)
    val icon = when {
        coerced < 0.33f -> Icons.Default.BrightnessLow
        coerced < 0.66f -> Icons.Default.BrightnessMedium
        else -> Icons.Default.BrightnessHigh
    }

    Box(
        modifier = modifier
            .size(128.dp)
            .background(Color.Black.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = coerced,
            modifier = Modifier.size(128.dp),
            color = Color.White,
            strokeWidth = 4.dp
        )
        Icon(
            icon,
            contentDescription = stringResource(MR.strings.player_brightness),
            tint = Color.White,
            modifier = Modifier.size(70.dp)
        )
    }
}

@Composable
fun SwipeSeekOverlay(
    state: SwipeSeekUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = state.deltaLabel,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.positionLabel,
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

@Preview
@Composable
fun test() {
    DoubleTapOverlay(DoubleTapOverlayState(DisplayPortion.Right, 20000), Modifier.height(144.dp))
}