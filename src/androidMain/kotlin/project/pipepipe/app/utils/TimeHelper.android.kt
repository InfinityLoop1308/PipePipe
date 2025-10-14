package project.pipepipe.app.utils


import org.ocpsoft.prettytime.PrettyTime
import java.util.Date

actual fun formatRelativeTime(timestampMillis: Long): String {
    return PrettyTime().format(Date(timestampMillis))
}