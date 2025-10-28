package project.pipepipe.app.ui.component.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.shared.infoitem.helper.SponsorBlockCategory
import project.pipepipe.app.helper.ColorHelper

/**
 * Utility object for SponsorBlock related constants and functions
 */
object SponsorBlockUtils {
    // Default color constants for each SponsorBlock category
    const val COLOR_SPONSOR_DEFAULT = "#00D400"
    const val COLOR_INTRO_DEFAULT = "#00FFFF"
    const val COLOR_OUTRO_DEFAULT = "#0202ED"
    const val COLOR_INTERACTION_DEFAULT = "#CC00FF"
    const val COLOR_HIGHLIGHT_DEFAULT = "#FF1983"
    const val COLOR_SELF_PROMO_DEFAULT = "#FFFF00"
    const val COLOR_NON_MUSIC_DEFAULT = "#FF9900"
    const val COLOR_PREVIEW_DEFAULT = "#008FD6"
    const val COLOR_FILLER_DEFAULT = "#7300FF"
    const val COLOR_PENDING_DEFAULT = "#FFFFFF"

    /**
     * Get the color for a specific SponsorBlock category from settings
     * @param category The SponsorBlock category
     * @return The color for the category
     */
    @Composable
    fun getCategoryColor(category: SponsorBlockCategory): Color {
        val settingsManager = SharedContext.settingsManager

        val (colorKey, defaultColor) = when (category) {
            SponsorBlockCategory.SPONSOR -> "sponsor_block_category_sponsor_color_key" to COLOR_SPONSOR_DEFAULT
            SponsorBlockCategory.INTRO -> "sponsor_block_category_intro_color_key" to COLOR_INTRO_DEFAULT
            SponsorBlockCategory.OUTRO -> "sponsor_block_category_outro_color_key" to COLOR_OUTRO_DEFAULT
            SponsorBlockCategory.INTERACTION -> "sponsor_block_category_interaction_color_key" to COLOR_INTERACTION_DEFAULT
            SponsorBlockCategory.HIGHLIGHT -> "sponsor_block_category_highlight_color_key" to COLOR_HIGHLIGHT_DEFAULT
            SponsorBlockCategory.SELF_PROMO -> "sponsor_block_category_self_promo_color_key" to COLOR_SELF_PROMO_DEFAULT
            SponsorBlockCategory.NON_MUSIC -> "sponsor_block_category_non_music_color_key" to COLOR_NON_MUSIC_DEFAULT
            SponsorBlockCategory.PREVIEW -> "sponsor_block_category_preview_color_key" to COLOR_PREVIEW_DEFAULT
            SponsorBlockCategory.FILLER -> "sponsor_block_category_filler_color_key" to COLOR_FILLER_DEFAULT
            SponsorBlockCategory.PENDING -> "sponsor_block_category_pending_color_key" to COLOR_PENDING_DEFAULT
        }

        val hexColor = ColorHelper.sanitizeHexColorInput(
            settingsManager.getString(colorKey, defaultColor)
        ) ?: defaultColor

        return ColorHelper.parseHexColor(hexColor)
    }

    /**
     * Get the localized name for a specific SponsorBlock category
     * @param category The SponsorBlock category
     * @return The localized category name
     */
    @Composable
    fun getCategoryName(category: SponsorBlockCategory): String {
        return when (category) {
            SponsorBlockCategory.SPONSOR -> stringResource(MR.strings.sponsor_block_category_sponsor)
            SponsorBlockCategory.INTRO -> stringResource(MR.strings.sponsor_block_category_intro)
            SponsorBlockCategory.OUTRO -> stringResource(MR.strings.sponsor_block_category_outro)
            SponsorBlockCategory.INTERACTION -> stringResource(MR.strings.sponsor_block_category_interaction)
            SponsorBlockCategory.HIGHLIGHT -> stringResource(MR.strings.sponsor_block_category_highlight)
            SponsorBlockCategory.SELF_PROMO -> stringResource(MR.strings.sponsor_block_category_self_promo)
            SponsorBlockCategory.NON_MUSIC -> stringResource(MR.strings.sponsor_block_category_non_music)
            SponsorBlockCategory.PREVIEW -> stringResource(MR.strings.sponsor_block_category_preview)
            SponsorBlockCategory.FILLER -> stringResource(MR.strings.sponsor_block_category_filler)
            SponsorBlockCategory.PENDING -> stringResource(MR.strings.missions_header_pending)
        }
    }
}
