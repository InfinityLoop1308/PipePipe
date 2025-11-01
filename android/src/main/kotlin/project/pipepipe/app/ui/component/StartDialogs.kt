package project.pipepipe.app.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR


/**
 * Welcome dialog shown on first app launch to introduce PipePipe 5 Beta.
 * TODO: Update dialog text after the official release.
 */
@Composable
fun WelcomeDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(MR.strings.welcome_dialog_title))
        },
        text = {
            Text(text = stringResource(MR.strings.welcome_dialog_message))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.welcome_dialog_button))
            }
        }
    )
}

/**
 * Data migration dialog shown after welcome dialog to explain package name change
 * and guide users through the migration process.
 */
@Composable
fun DataMigrationDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(MR.strings.data_migration_dialog_title))
        },
        text = {
            Text(text = stringResource(MR.strings.data_migration_dialog_message))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.data_migration_dialog_button))
            }
        }
    )
}

/**
 * Error handling dialog shown after data migration dialog to explain
 * the new error handling mechanism.
 */
@Composable
fun ErrorHandlingDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(MR.strings.error_handling_dialog_title))
        },
        text = {
            Text(text = stringResource(MR.strings.error_handling_dialog_message))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.error_handling_dialog_button))
            }
        }
    )
}

/**
 * First run dialog shown after error handling dialog to prompt user to enable update checker.
 */
@Composable
fun FirstRunDialog(
    onDismiss: () -> Unit,
    onEnableUpdateChecker: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(MR.strings.first_run_dialog_title))
        },
        text = {
            Text(text = stringResource(MR.strings.first_run_dialog_message))
        },
        confirmButton = {
            TextButton(onClick = onEnableUpdateChecker) {
                Text(text = stringResource(MR.strings.first_run_dialog_enable))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.first_run_dialog_no_thanks))
            }
        }
    )
}

