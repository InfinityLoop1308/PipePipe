package project.pipepipe.app.ui.component.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.platform.PlatformMediaController

@Composable
fun SpeedPitchDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mediaController = SharedContext.platformMediaController!!
    val playbackSpeed by mediaController.playbackSpeed.collectAsState()
    val playbackPitch by mediaController.playbackPitch.collectAsState()

    val onApply = { speed: Float, pitch: Float ->
        mediaController.setPlaybackParameters(speed, pitch)
        SharedContext.settingsManager.putFloat("playback_speed_key", speed)
        SharedContext.settingsManager.putFloat("playback_pitch_key", pitch)
    }
    var tempSpeed by remember { mutableFloatStateOf(playbackSpeed) }
    var tempPitch by remember { mutableFloatStateOf(playbackPitch) }
    var stepSize by remember { mutableFloatStateOf(0.25f) }

    val initialSpeed = remember { playbackSpeed }
    val initialPitch = remember { playbackPitch }

    fun speedToSlider(speed: Float): Float {
        return if (speed < 1f) (speed - 0.1f) / 1.8f else 0.5f + (speed - 1f) / 18f
    }

    fun sliderToSpeed(slider: Float): Float {
        return if (slider < 0.5f) 0.1f + slider * 1.8f else 1f + (slider - 0.5f) * 18f
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 16.dp)
                    .fillMaxWidth()
            ) {

                // Tempo Section
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(MR.strings.speed_pitch_speed_label, String.format("%.2fx", tempSpeed)),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                tempSpeed = (tempSpeed - stepSize).coerceIn(0.1f, 10f)
                                onApply(tempSpeed, tempPitch)
                            },
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("-${(stepSize * 100).toInt()}%", fontSize = 12.sp)
                        }

                        Slider(
                            value = speedToSlider(tempSpeed),
                            onValueChange = {
                                tempSpeed = sliderToSpeed(it)
                                onApply(tempSpeed, tempPitch)
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedButton(
                            onClick = {
                                tempSpeed = (tempSpeed + stepSize).coerceIn(0.1f, 10f)
                                onApply(tempSpeed, tempPitch)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("+${(stepSize * 100).toInt()}%", fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Pitch Section
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(MR.strings.speed_pitch_pitch_label, "${(tempPitch * 100).toInt()}%"),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                tempPitch = (tempPitch - stepSize).coerceIn(0.1f, 10f)
                                onApply(tempSpeed, tempPitch)
                            },
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("-${(stepSize * 100).toInt()}%", fontSize = 12.sp)
                        }

                        Slider(
                            value = speedToSlider(tempPitch),
                            onValueChange = {
                                tempPitch = sliderToSpeed(it)
                                onApply(tempSpeed, tempPitch)
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedButton(
                            onClick = {
                                tempPitch = (tempPitch + stepSize).coerceIn(0.1f, 10f)
                                onApply(tempSpeed, tempPitch)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("+${(stepSize * 100).toInt()}%", fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Step Size Selector
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val stepOptions = listOf(0.01f, 0.05f, 0.25f, 1.00f)
                    stepOptions.forEach { option ->
                        FilterChip(
                            selected = stepSize == option,
                            onClick = { stepSize = option },
                            label = {
                                Text("${(option * 100).toInt()}%", fontSize = 12.sp)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Bottom buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            tempSpeed = 1.0f
                            tempPitch = 1.0f
                            onApply(tempSpeed, tempPitch)
                        }
                    ) {
                        Text(stringResource(MR.strings.playback_reset), fontSize = 14.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                onApply(initialSpeed, initialPitch)
                                onDismiss()
                            }
                        ) {
                            Text(stringResource(MR.strings.cancel), fontSize = 14.sp)
                        }

                        TextButton(
                            onClick = {
                                onApply(tempSpeed, tempPitch)
                                onDismiss()
                            }
                        ) {
                            Text(stringResource(MR.strings.ok), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
