package project.pipepipe.app.utils


import org.ocpsoft.prettytime.PrettyTime
import org.ocpsoft.prettytime.units.Decade
import java.util.Date

private val prettyTime: PrettyTime by lazy {
    PrettyTime().apply {
        // Do not use decades as YouTube doesn't either.
        removeUnit(Decade::class.java)
    }
}

actual fun formatRelativeTime(timestampMillis: Long): String {
    return prettyTime.format(Date(timestampMillis))
}