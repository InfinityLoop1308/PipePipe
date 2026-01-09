package project.pipepipe.app.ui.component.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SpatialAudioOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.platform.ResolutionInfo
import project.pipepipe.app.platform.SubtitleInfo
import project.pipepipe.shared.infoitem.StreamInfo
import java.util.Locale
import kotlin.collections.forEach

@Composable
fun ResolutionMenu(
    availableResolutions: List<ResolutionInfo>,
    showMenu: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onResolutionSelected: (ResolutionInfo) -> Unit,
    onResolutionAuto: () -> Unit
) {
    fun hasVideoOverride(): Boolean = availableResolutions.count { it.isSelected } == 1

    Box {
        TextButton(onClick = { onMenuChange(true) }) {
            Text(
                text = if (hasVideoOverride()) availableResolutions.first { it.isSelected }.displayLabel else stringResource(
                    MR.strings.auto
                ),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { onMenuChange(false) }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(MR.strings.auto),
                        color = if (!hasVideoOverride())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = {
                    onResolutionAuto()
                    onMenuChange(false)
                }
            )
            availableResolutions.forEach { resolution ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = resolution.displayLabel,
                            color = if (resolution.isSelected && hasVideoOverride())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onResolutionSelected(resolution)
                        onMenuChange(false)
                    }
                )
            }
        }
    }
}

@Composable
fun MoreMenu(
    streamInfo: StreamInfo,
    danmakuEnabled: Boolean,
    availableSubtitles: List<SubtitleInfo>,
    availableLanguages: Set<Pair<String, Boolean>>,
    showMenu: Boolean,
    onMenuChange: (Boolean) -> Unit,
    showAudioLanguageMenu: Boolean,
    onAudioLanguageMenuChange: (Boolean) -> Unit,
    showSubtitleMenu: Boolean,
    onSubtitleMenuChange: (Boolean) -> Unit,
    currentLanguage: String,
    onToggleDanmaku: () -> Unit,
    onAudioLanguageSelected: (String) -> Unit,
    onSubtitleSelected: (SubtitleInfo) -> Unit,
    onSubtitleDisabled: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onPipClick: () -> Unit
) {
    Box {
        TextButton(onClick = { onMenuChange(true) }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { onMenuChange(false) }
        ) {
            // Picture-in-Picture
            DropdownMenuItem(
                modifier = Modifier.height(44.dp),
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPicture,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(stringResource(MR.strings.pip))
                    }
                },
                onClick = {
                    onPipClick()
                    onMenuChange(false)
                }
            )

            // Play Queue
            DropdownMenuItem(
                modifier = Modifier.height(44.dp),
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(stringResource(MR.strings.play_queue))
                    }
                },
                onClick = {
                    SharedContext.toggleShowPlayQueueVisibility()
                    onMenuChange(false)
                }
            )

            // Danmaku toggle
            streamInfo.danmakuUrl?.let {
                DropdownMenuItem(
                    modifier = Modifier.height(44.dp),
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (!danmakuEnabled) {
                                    Icons.Default.Visibility
                                } else {
                                    Icons.Default.VisibilityOff
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (danmakuEnabled) {
                                    stringResource(MR.strings.player_disable_danmaku)
                                } else {
                                    stringResource(MR.strings.player_enable_danmaku)
                                }
                            )
                        }
                    },
                    onClick = {
                        onToggleDanmaku()
                        onMenuChange(false)
                    }
                )
            }

            // Subtitles
            if (availableSubtitles.isNotEmpty()) {
                DropdownMenuItem(
                    modifier = Modifier.height(44.dp),
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(stringResource(MR.strings.caption_setting_title))
                        }
                    },
                    onClick = {
                        onMenuChange(false)
                        onSubtitleMenuChange(true)
                    }
                )
            }

            // Audio language
            if (availableLanguages.size > 1) {
                DropdownMenuItem(
                    modifier = Modifier.height(44.dp),
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SpatialAudioOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(stringResource(MR.strings.player_audio_language))
                        }
                    },
                    onClick = {
                        onMenuChange(false)
                        onAudioLanguageMenuChange(true)
                    }
                )
            }

            // Sleep timer
            DropdownMenuItem(
                modifier = Modifier.height(44.dp),
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(stringResource(MR.strings.player_sleep_timer))
                    }
                },
                onClick = onSleepTimerClick
            )
        }

        // Audio Language Menu
        DropdownMenu(
            expanded = showAudioLanguageMenu,
            onDismissRequest = { onAudioLanguageMenuChange(false) }
        ) {
            val originText = stringResource(MR.strings.original)
            availableLanguages.forEach { language ->
                // Get localized language name using Locale
                val languageCode = language.first
                val locale = Locale.forLanguageTag(languageCode)
                val localizedName = locale.getDisplayLanguage(Locale.getDefault())
                val displayText = if (localizedName.isNotBlank()) {
                    if (language.second) {
                        "$localizedName ($originText)"
                    } else {
                        localizedName
                    }
                } else {
                    if (language.second) {
                        "$languageCode ($originText)"
                    } else {
                        languageCode
                    }
                }

                DropdownMenuItem(
                    text = {
                        Text(
                            text = displayText,
                            color = if (currentLanguage == languageCode)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onAudioLanguageSelected(languageCode)
                        onAudioLanguageMenuChange(false)
                    }
                )
            }
        }

        // Subtitle Menu
        DropdownMenu(
            expanded = showSubtitleMenu,
            onDismissRequest = { onSubtitleMenuChange(false) }
        ) {
            // Disable option
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(MR.strings.player_disable_subtitle),
                        color = if (availableSubtitles.find { it.isSelected } == null)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = {
                    onSubtitleDisabled()
                    onSubtitleMenuChange(false)
                }
            )

            // Available subtitles
            val autoGeneratedText = stringResource(MR.strings.player_subtitle_auto_generated)
            availableSubtitles.forEach { subtitle ->
                // Get localized language name using Locale
                val locale = Locale.forLanguageTag(subtitle.language)
                val localizedName = locale.getDisplayLanguage(Locale.getDefault())
                val displayText = if (localizedName.isNotBlank()) {
                    if (subtitle.isAutoGenerated) {
                        "$localizedName ($autoGeneratedText)"
                    } else {
                        localizedName
                    }
                } else {
                    if (subtitle.isAutoGenerated) {
                        "${subtitle.language} ($autoGeneratedText)"
                    } else {
                        subtitle.language
                    }
                }

                DropdownMenuItem(
                    text = {
                        Text(
                            text = displayText,
                            color = if (subtitle.isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onSubtitleSelected(subtitle)
                        onSubtitleMenuChange(false)
                    }
                )
            }
        }
    }
}
