package project.pipepipe.app.ui.component

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.icerock.moko.resources.compose.stringResource
import dev.icerock.moko.resources.desc.desc
import project.pipepipe.app.MR
import project.pipepipe.shared.SharedContext
import project.pipepipe.shared.helper.ToastManager

fun isSealInstalled(context: Context) = try {
    context.packageManager.getPackageInfo("com.junkfood.seal", 0)
    true
} catch (e: Exception) {
    ToastManager.show(MR.strings.seal_not_detected.desc().toString(context = context))
    false
}

private fun launchSeal(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        setClassName("com.junkfood.seal", "com.junkfood.seal.QuickDownloadActivity")
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        ToastManager.show(MR.strings.failed_to_open_seal.desc().toString(context = context))
    }
}

fun handleDownload(
    context: Context,
    url: String,
    onShowDialog: (showDialog: Boolean) -> Unit
) {
    if (!isSealInstalled(context)) {
        onShowDialog(true)
    } else {
        val hasSeenDialog = SharedContext.settingsManager.getBoolean("has_seen_download_dialog", false)
        if (!hasSeenDialog) {
            onShowDialog(true)
        } else {
            launchSeal(context, url)
        }
    }
}

@Composable
fun DownloadInfoDialog(
    url: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(MR.strings.download_dialog_title))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Main message with clickable Seal text
                val messageText = stringResource(MR.strings.download_dialog_message)
                val sealStartIndex = messageText.indexOf("Seal")

                val annotatedMessage = buildAnnotatedString {
                    if (sealStartIndex >= 0) {
                        append(messageText.take(sealStartIndex))

                        pushStringAnnotation(
                            tag = "SEAL_URL",
                            annotation = "https://github.com/JunkFood02/Seal"
                        )
                        withStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append("Seal")
                        }
                        pop()

                        append(messageText.substring(sealStartIndex + 4))
                    } else {
                        append(messageText)
                    }
                }

                ClickableText(
                    text = annotatedMessage,
                    onClick = { offset ->
                        annotatedMessage.getStringAnnotations(
                            tag = "SEAL_URL",
                            start = offset,
                            end = offset
                        ).firstOrNull()?.let { annotation ->
                            val intent = Intent(Intent.ACTION_VIEW, annotation.item.toUri())
                            context.startActivity(intent)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                // Footer with release notes link
                val footerText = stringResource(MR.strings.download_dialog_footer)
                val releaseNotesStartIndex = footerText.indexOf("release notes")

                val annotatedString = buildAnnotatedString {
                    if (releaseNotesStartIndex >= 0) {
                        append(footerText.take(releaseNotesStartIndex))

                        pushStringAnnotation(
                            tag = "URL",
                            annotation = "https://github.com/InfinityLoop1308/PipePipe/releases"
                        )
                        withStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append("release notes")
                        }
                        pop()

                        append(footerText.substring(releaseNotesStartIndex + 13))
                    } else {
                        append(footerText)
                    }
                }

                ClickableText(
                    text = annotatedString,
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(
                            tag = "URL",
                            start = offset,
                            end = offset
                        ).firstOrNull()?.let { annotation ->
                            val intent = Intent(Intent.ACTION_VIEW, annotation.item.toUri())
                            context.startActivity(intent)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        "https://f-droid.org/packages/com.junkfood.seal/".toUri()
                    )
                    context.startActivity(intent)
                }
            ) {
                Text(text = stringResource(MR.strings.download_dialog_seal_link))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                SharedContext.settingsManager.putBoolean("has_seen_download_dialog", true)
                if (isSealInstalled(context)) {
                    launchSeal(context, url)
                }
                onDismiss()
            }) {
                Text(stringResource(MR.strings.ok))
            }
        }
    )
}
