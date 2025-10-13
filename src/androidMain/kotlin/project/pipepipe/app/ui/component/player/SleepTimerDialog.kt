package project.pipepipe.app.ui.component.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.darkokoa.datetimewheelpicker.WheelTimePicker
import dev.darkokoa.datetimewheelpicker.core.WheelPickerDefaults
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.datetime.LocalTime
import project.pipepipe.app.MR

@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val quickOptions = remember { listOf(15, 30, 45, 60, 90) }

    var wheelRebuildKey by remember { mutableIntStateOf(0) }

    var selectedTime by remember {
        mutableStateOf(LocalTime(hour = 0, minute = quickOptions.first()))
    }
    var selectedQuickOption by remember {
        mutableStateOf<Int?>(quickOptions.first())
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(MR.strings.sleep_timer_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Text(
                    text = stringResource(MR.strings.sleep_timer_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp)
                ) {
                    Text(
                        text = stringResource(MR.strings.sleep_timer_hours_unit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(MR.strings.sleep_timer_minutes_unit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                key(wheelRebuildKey) {
                    WheelTimePicker(
                        startTime = selectedTime,
                        size = DpSize(200.dp, 150.dp),
                        rowCount = 5,
                        textStyle = MaterialTheme.typography.titleMedium,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        selectorProperties = WheelPickerDefaults.selectorProperties(
                            enabled = true,
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            border = null
                        ),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) { snappedTime ->
                        selectedTime = snappedTime
                        val totalMinutes = snappedTime.hour * 60 + snappedTime.minute
                        selectedQuickOption = quickOptions.firstOrNull { it == totalMinutes }
                    }
                }


                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickOptions) { minutes ->
                        val label = stringResource(
                            MR.strings.sleep_timer_quick_option_minutes,
                            minutes
                        )
                        FilterChip(
                            selected = selectedQuickOption == minutes,
                            onClick = {
                                selectedQuickOption = minutes
                                selectedTime = LocalTime(
                                    hour = minutes / 60,
                                    minute = minutes % 60
                                )
                                wheelRebuildKey++
                            },
                            label = { Text(label) },
                            shape = RoundedCornerShape(24.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }


                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(MR.strings.cancel), fontSize = 14.sp)
                    }

                    TextButton(
                        onClick = {
                            val totalMinutes = selectedTime.hour * 60 + selectedTime.minute
                            onConfirm(totalMinutes)
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