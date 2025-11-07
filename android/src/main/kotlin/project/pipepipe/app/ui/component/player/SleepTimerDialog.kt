package project.pipepipe.app.ui.component.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR

@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val quickOptions = remember { listOf(10, 15, 30, 45, 60, 90) }

    var minutesText by remember { mutableStateOf("15") }
    var selectedQuickOption by remember { mutableStateOf<Int?>(15) }

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

                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            minutesText = newValue
                            val minutes = newValue.toIntOrNull()
                            selectedQuickOption = if (minutes in quickOptions) minutes else null
                        }
                    },
                    label = { Text(stringResource(MR.strings.sleep_timer_minutes_unit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickOptions.forEach { minutes ->
                        val label = stringResource(
                            MR.strings.sleep_timer_quick_option_minutes,
                            minutes
                        )
                        FilterChip(
                            selected = selectedQuickOption == minutes,
                            onClick = {
                                selectedQuickOption = minutes
                                minutesText = minutes.toString()
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
                            val totalMinutes = minutesText.toIntOrNull()?.coerceAtLeast(1) ?: 15
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
