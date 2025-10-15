package project.pipepipe.app.utils

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Format timestamp as relative time (e.g., "2 hours ago")
 */
expect fun formatRelativeTime(timestampMillis: Long): String

/**
 * Format timestamp as absolute time with pattern "yyyy/MM/dd"
 */
@OptIn(ExperimentalTime::class)
fun formatAbsoluteTime(
    timestampMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): String {
    val instant = Instant.fromEpochMilliseconds(timestampMillis)
    val localDateTime = instant.toLocalDateTime(timeZone)

    val year = localDateTime.year
    val month = localDateTime.month.number.toString().padStart(2, '0')
    val day = localDateTime.day.toString().padStart(2, '0')

    return "$year/$month/$day"
}

fun Long.toDurationString(isMillis: Boolean = false): String {
    var totalSeconds = this
    if (isMillis) totalSeconds /= 1000
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return buildString {
        if (days > 0) {
            append(String.format("%02d:", days))
        }
        if (hours > 0 || days > 0) {
            append(String.format("%02d:", hours))
        }
        append(String.format("%02d:%02d", minutes, seconds))
    }
}