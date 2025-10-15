package project.pipepipe.app.ui.component.player

import androidx.compose.runtime.Composable
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.shared.SharedContext
import project.pipepipe.shared.infoitem.SponsorBlockSegmentInfo

enum class SponsorBlockSkipMode {
    AUTOMATIC,
    MANUAL,
    HIGHLIGHT_ONLY,
    DISABLED;

    companion object {
        fun fromString(value: String): SponsorBlockSkipMode {
            return when (value) {
                "Automatic" -> AUTOMATIC
                "Manual" -> MANUAL
                "Highlight Only" -> HIGHLIGHT_ONLY
                else -> DISABLED
            }
        }
    }
}

object SponsorBlockHelper {
    fun isEnabled(): Boolean {
        return SharedContext.settingsManager.getBoolean("sponsor_block_enable_key", true)
    }

    fun isNotificationsEnabled(): Boolean {
        return SharedContext.settingsManager.getBoolean("sponsor_block_notifications_key", true)
    }

    fun getSkipMode(category: String): SponsorBlockSkipMode {
        val key = "sponsor_block_category_${category}_mode_key"
        val value = SharedContext.settingsManager.getString(key, "Automatic")
        return SponsorBlockSkipMode.fromString(value)
    }

    fun isCategoryEnabled(category: String): Boolean {
        val key = "sponsor_block_category_${category}_key"
        return SharedContext.settingsManager.getBoolean(key, true)
    }

    fun shouldSkipSegment(segment: SponsorBlockSegmentInfo): Boolean {
        if (!isEnabled()) return false
        if (!isCategoryEnabled(segment.category.apiName)) return false

        val mode = getSkipMode(segment.category.apiName)
        return mode == SponsorBlockSkipMode.AUTOMATIC
    }

    fun shouldShowSkipButton(segment: SponsorBlockSegmentInfo): Boolean {
        if (!isEnabled()) return false
        if (!isCategoryEnabled(segment.category.apiName)) return false

        val mode = getSkipMode(segment.category.apiName)
        return mode == SponsorBlockSkipMode.MANUAL
    }

}
