package project.pipepipe.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.settings.PreferenceItem
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.ui.component.CategoryPreference
import project.pipepipe.app.ui.component.ClickablePreference
import project.pipepipe.app.ui.component.ColorPreference
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.component.ListPreference
import project.pipepipe.app.ui.component.SwitchPreference
import project.pipepipe.app.helper.ColorHelper
import project.pipepipe.app.ui.screens.Screen.SponsorBlockCategorySettings
import project.pipepipe.app.ui.component.player.SponsorBlockUtils
import kotlin.collections.buildList

// Use constants from SponsorBlockUtils
private const val COLOR_SPONSOR_DEFAULT = SponsorBlockUtils.COLOR_SPONSOR_DEFAULT
private const val COLOR_INTRO_DEFAULT = SponsorBlockUtils.COLOR_INTRO_DEFAULT
private const val COLOR_OUTRO_DEFAULT = SponsorBlockUtils.COLOR_OUTRO_DEFAULT
private const val COLOR_INTERACTION_DEFAULT = SponsorBlockUtils.COLOR_INTERACTION_DEFAULT
private const val COLOR_HIGHLIGHT_DEFAULT = SponsorBlockUtils.COLOR_HIGHLIGHT_DEFAULT
private const val COLOR_SELF_PROMO_DEFAULT = SponsorBlockUtils.COLOR_SELF_PROMO_DEFAULT
private const val COLOR_NON_MUSIC_DEFAULT = SponsorBlockUtils.COLOR_NON_MUSIC_DEFAULT
private const val COLOR_PREVIEW_DEFAULT = SponsorBlockUtils.COLOR_PREVIEW_DEFAULT
private const val COLOR_FILLER_DEFAULT = SponsorBlockUtils.COLOR_FILLER_DEFAULT
private const val COLOR_PENDING_DEFAULT = SponsorBlockUtils.COLOR_PENDING_DEFAULT

@Composable
fun SponsorBlockSettingsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    onOpenSponsorBlockCategories: () -> Unit = {
        navController.navigate(SponsorBlockCategorySettings.route)
    },
//    onClearWhitelist: () -> Unit = {}
) {
    val settingsManager = SharedContext.settingsManager
    val sponsorBlockEnabledState = remember {
        mutableStateOf(settingsManager.getBoolean("sponsor_block_enable_key", true))
    }

    val enableTitle = stringResource(MR.strings.sponsor_block_enable_title)
    val enableSummary = stringResource(MR.strings.sponsor_block_enable_summary)
//    val gracedRewindTitle = stringResource(MR.strings.sponsor_block_graced_rewind_title)
//    val gracedRewindSummary = stringResource(MR.strings.sponsor_block_graced_rewind_summary)
    val notificationsTitle = stringResource(MR.strings.sponsor_block_notifications_title)
    val notificationsSummary = stringResource(MR.strings.sponsor_block_notifications_summary)
    val categoriesTitle = stringResource(MR.strings.settings_category_sponsor_block_categories_title)
    val categoriesSummary = stringResource(MR.strings.settings_category_sponsor_block_categories_summary)
//    val clearWhitelistTitle = stringResource(MR.strings.sponsor_block_clear_whitelist_title)
//    val clearWhitelistSummary = stringResource(MR.strings.sponsor_block_clear_whitelist_summary)

    val preferenceItems = listOf(
        PreferenceItem.SwitchPref(
            key = "sponsor_block_enable_key",
            title = enableTitle,
            summary = enableSummary,
            defaultValue = true,
            onValueChange = { sponsorBlockEnabledState.value = it }
        ),
//        PreferenceItem.SwitchPref(
//            key = "sponsor_block_graced_rewind_key",
//            title = gracedRewindTitle,
//            summary = gracedRewindSummary,
//            enabled = sponsorBlockEnabledState.value,
//            defaultValue = true
//        ),
        PreferenceItem.SwitchPref(
            key = "sponsor_block_notifications_key",
            title = notificationsTitle,
            summary = notificationsSummary,
            enabled = sponsorBlockEnabledState.value,
            defaultValue = true
        ),
        PreferenceItem.ClickablePref(
            key = "sponsor_block_categories_key",
            title = categoriesTitle,
            summary = categoriesSummary,
            enabled = sponsorBlockEnabledState.value,
            onClick = onOpenSponsorBlockCategories
        ),
//        PreferenceItem.ClickablePref(
//            key = "sponsor_block_clear_whitelist_key",
//            title = clearWhitelistTitle,
//            summary = clearWhitelistSummary,
//            enabled = sponsorBlockEnabledState.value,
//            onClick = onClearWhitelist
//        )
    )

    val switchStateMap: Map<String, MutableState<Boolean>> = mapOf(
        "sponsor_block_enable_key" to sponsorBlockEnabledState
    )

    Column {
        CustomTopBar(
            defaultTitleText = stringResource(MR.strings.sponsor_block)
        )

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(
                items = preferenceItems,
                key = PreferenceItem::key
            ) { item ->
                when (item) {
                    is PreferenceItem.SwitchPref -> {
                        val state = switchStateMap[item.key]
                        if (state != null) {
                            key("${item.key}_${state.value}") {
                                SwitchPreference(item = item)
                            }
                        } else {
                            SwitchPreference(item = item)
                        }
                    }

                    is PreferenceItem.ClickablePref -> ClickablePreference(item = item)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
fun SponsorBlockCategoriesSettingsScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val settingsManager = SharedContext.settingsManager

    val sponsorCategoryEnabledState = remember {
        mutableStateOf(settingsManager.getBoolean("sponsor_block_category_sponsor_key", true))
    }
    val introCategoryEnabledState = remember {
        mutableStateOf(settingsManager.getBoolean("sponsor_block_category_intro_key", true))
    }
    val outroCategoryEnabledState = remember {
        mutableStateOf(settingsManager.getBoolean("sponsor_block_category_outro_key", true))
    }
    val interactionCategoryEnabledState = remember {
        mutableStateOf(settingsManager.getBoolean("sponsor_block_category_interaction_key", true))
    }
    val highlightCategoryEnabledState = remember {
        mutableStateOf(settingsManager.getBoolean("sponsor_block_category_highlight_key", true))
    }
    val selfPromoCategoryEnabledState = remember {
        mutableStateOf(settingsManager.getBoolean("sponsor_block_category_self_promo_key", true))
    }
    val nonMusicCategoryEnabledState = remember {
        mutableStateOf(settingsManager.getBoolean("sponsor_block_category_non_music_key"))
    }
    val previewCategoryEnabledState = remember {
        mutableStateOf(settingsManager.getBoolean("sponsor_block_category_preview_key", true))
    }
    val fillerCategoryEnabledState = remember {
        mutableStateOf(settingsManager.getBoolean("sponsor_block_category_filler_key", true))
    }

    val sponsorColorState = remember {
        mutableStateOf(
            ColorHelper.sanitizeHexColorInput(
                settingsManager.getString(
                    "sponsor_block_category_sponsor_color_key",
                    COLOR_SPONSOR_DEFAULT
                )
            ) ?: COLOR_SPONSOR_DEFAULT
        )
    }
    val introColorState = remember {
        mutableStateOf(
            ColorHelper.sanitizeHexColorInput(
                settingsManager.getString(
                    "sponsor_block_category_intro_color_key",
                    COLOR_INTRO_DEFAULT
                )
            ) ?: COLOR_INTRO_DEFAULT
        )
    }
    val outroColorState = remember {
        mutableStateOf(
            ColorHelper.sanitizeHexColorInput(
                settingsManager.getString(
                    "sponsor_block_category_outro_color_key",
                    COLOR_OUTRO_DEFAULT
                )
            ) ?: COLOR_OUTRO_DEFAULT
        )
    }
    val interactionColorState = remember {
        mutableStateOf(
            ColorHelper.sanitizeHexColorInput(
                settingsManager.getString(
                    "sponsor_block_category_interaction_color_key",
                    COLOR_INTERACTION_DEFAULT
                )
            ) ?: COLOR_INTERACTION_DEFAULT
        )
    }
    val highlightColorState = remember {
        mutableStateOf(
            ColorHelper.sanitizeHexColorInput(
                settingsManager.getString(
                    "sponsor_block_category_highlight_color_key",
                    COLOR_HIGHLIGHT_DEFAULT
                )
            ) ?: COLOR_HIGHLIGHT_DEFAULT
        )
    }
    val selfPromoColorState = remember {
        mutableStateOf(
            ColorHelper.sanitizeHexColorInput(
                settingsManager.getString(
                    "sponsor_block_category_self_promo_color_key",
                    COLOR_SELF_PROMO_DEFAULT
                )
            ) ?: COLOR_SELF_PROMO_DEFAULT
        )
    }
    val nonMusicColorState = remember {
        mutableStateOf(
            ColorHelper.sanitizeHexColorInput(
                settingsManager.getString(
                    "sponsor_block_category_non_music_color_key",
                    COLOR_NON_MUSIC_DEFAULT
                )
            ) ?: COLOR_NON_MUSIC_DEFAULT
        )
    }
    val previewColorState = remember {
        mutableStateOf(
            ColorHelper.sanitizeHexColorInput(
                settingsManager.getString(
                    "sponsor_block_category_preview_color_key",
                    COLOR_PREVIEW_DEFAULT
                )
            ) ?: COLOR_PREVIEW_DEFAULT
        )
    }
    val fillerColorState = remember {
        mutableStateOf(
            ColorHelper.sanitizeHexColorInput(
                settingsManager.getString(
                    "sponsor_block_category_filler_color_key",
                    COLOR_FILLER_DEFAULT
                )
            ) ?: COLOR_FILLER_DEFAULT
        )
    }
    val pendingColorState = remember {
        mutableStateOf(
            ColorHelper.sanitizeHexColorInput(
                settingsManager.getString(
                    "sponsor_block_category_pending_color_key",
                    COLOR_PENDING_DEFAULT
                )
            ) ?: COLOR_PENDING_DEFAULT
        )
    }

    val categorySwitchStates: Map<String, MutableState<Boolean>> = mapOf(
        "sponsor_block_category_sponsor_key" to sponsorCategoryEnabledState,
        "sponsor_block_category_intro_key" to introCategoryEnabledState,
        "sponsor_block_category_outro_key" to outroCategoryEnabledState,
        "sponsor_block_category_interaction_key" to interactionCategoryEnabledState,
        "sponsor_block_category_highlight_key" to highlightCategoryEnabledState,
        "sponsor_block_category_self_promo_key" to selfPromoCategoryEnabledState,
        "sponsor_block_category_non_music_key" to nonMusicCategoryEnabledState,
        "sponsor_block_category_preview_key" to previewCategoryEnabledState,
        "sponsor_block_category_filler_key" to fillerCategoryEnabledState
    )

    val categoryColorStates: Map<String, Pair<MutableState<String>, String>> = mapOf(
        "sponsor_block_category_sponsor_color_key" to (sponsorColorState to COLOR_SPONSOR_DEFAULT),
        "sponsor_block_category_intro_color_key" to (introColorState to COLOR_INTRO_DEFAULT),
        "sponsor_block_category_outro_color_key" to (outroColorState to COLOR_OUTRO_DEFAULT),
        "sponsor_block_category_interaction_color_key" to (interactionColorState to COLOR_INTERACTION_DEFAULT),
        "sponsor_block_category_highlight_color_key" to (highlightColorState to COLOR_HIGHLIGHT_DEFAULT),
        "sponsor_block_category_self_promo_color_key" to (selfPromoColorState to COLOR_SELF_PROMO_DEFAULT),
        "sponsor_block_category_non_music_color_key" to (nonMusicColorState to COLOR_NON_MUSIC_DEFAULT),
        "sponsor_block_category_preview_color_key" to (previewColorState to COLOR_PREVIEW_DEFAULT),
        "sponsor_block_category_filler_color_key" to (fillerColorState to COLOR_FILLER_DEFAULT),
        "sponsor_block_category_pending_color_key" to (pendingColorState to COLOR_PENDING_DEFAULT)
    )

    val skipModeEntries = listOf(
        stringResource(MR.strings.sponsor_block_skip_mode_enabled),
        stringResource(MR.strings.sponsor_block_skip_mode_manual),
        stringResource(MR.strings.sponsor_block_skip_mode_highlight)
    )
    val skipModeValues = listOf(
        "Automatic",
        "Manual",
        "Highlight Only"
    )
    val skipModeDefault = skipModeValues.firstOrNull().orEmpty()
    val hasSkipModeOptions = skipModeEntries.isNotEmpty()

    val enableCategoryTitle = stringResource(MR.strings.settings_category_sponsor_block_category_enable_title)
    val enableModeTitle = stringResource(MR.strings.settings_category_sponsor_block_category_enable_mode_title)
    val categoryColorTitle = stringResource(MR.strings.settings_category_sponsor_block_category_color)

    val preferenceItems = buildList<PreferenceItem> {
        add(
            PreferenceItem.CategoryPref(
                key = "sponsor_block_categories_quick_actions_header",
                title = stringResource(MR.strings.settings_category_sponsor_block_categories_quick_actions)
            )
        )
        add(
            PreferenceItem.ClickablePref(
                key = "sponsor_block_category_all_on_key",
                title = stringResource(MR.strings.settings_category_sponsor_block_categories_all_colors_on_title),
                onClick = {
                    categorySwitchStates.forEach { (key, state) ->
                        state.value = true
                        settingsManager.putBoolean(key, true)
                    }
                }
            )
        )
        add(
            PreferenceItem.ClickablePref(
                key = "sponsor_block_category_all_off_key",
                title = stringResource(MR.strings.settings_category_sponsor_block_categories_all_colors_off_title),
                onClick = {
                    categorySwitchStates.forEach { (key, state) ->
                        state.value = false
                        settingsManager.putBoolean(key, false)
                    }
                }
            )
        )
        add(
            PreferenceItem.ClickablePref(
                key = "sponsor_block_category_reset_key",
                title = stringResource(MR.strings.settings_category_sponsor_block_categories_reset_colors_title),
                onClick = {
                    categoryColorStates.forEach { (key, colorStatePair) ->
                        val (state, defaultColor) = colorStatePair
                        state.value = defaultColor
                        settingsManager.putString(key, defaultColor)
                    }
                }
            )
        )

        addSponsorBlockCategory(
            headerKey = "sponsor_block_category_header_sponsor",
            headerTitle = stringResource(MR.strings.sponsor_block_category_sponsor),
            summaryKey = "sponsor_block_category_sponsor_summary_pref",
            summaryText = stringResource(MR.strings.settings_category_sponsor_block_category_sponsor_summary),
            switchKey = "sponsor_block_category_sponsor_key",
            switchState = sponsorCategoryEnabledState,
            modeKey = "sponsor_block_category_sponsor_mode_key",
            colorKey = "sponsor_block_category_sponsor_color_key",
            colorState = sponsorColorState,
            defaultColor = COLOR_SPONSOR_DEFAULT,
            hasModeSelector = hasSkipModeOptions,
            enableCategoryTitle = enableCategoryTitle,
            enableModeTitle = enableModeTitle,
            colorTitle = categoryColorTitle,
            skipModeEntries = skipModeEntries,
            skipModeValues = skipModeValues,
            skipModeDefault = skipModeDefault
        )

        addSponsorBlockCategory(
            headerKey = "sponsor_block_category_header_intro",
            headerTitle = stringResource(MR.strings.sponsor_block_category_intro),
            summaryKey = "sponsor_block_category_intro_summary_pref",
            summaryText = stringResource(MR.strings.settings_category_sponsor_block_category_intro_summary),
            switchKey = "sponsor_block_category_intro_key",
            switchState = introCategoryEnabledState,
            modeKey = "sponsor_block_category_intro_mode_key",
            colorKey = "sponsor_block_category_intro_color_key",
            colorState = introColorState,
            defaultColor = COLOR_INTRO_DEFAULT,
            hasModeSelector = hasSkipModeOptions,
            enableCategoryTitle = enableCategoryTitle,
            enableModeTitle = enableModeTitle,
            colorTitle = categoryColorTitle,
            skipModeEntries = skipModeEntries,
            skipModeValues = skipModeValues,
            skipModeDefault = skipModeDefault
        )

        addSponsorBlockCategory(
            headerKey = "sponsor_block_category_header_outro",
            headerTitle = stringResource(MR.strings.sponsor_block_category_outro),
            summaryKey = "sponsor_block_category_outro_summary_pref",
            summaryText = stringResource(MR.strings.settings_category_sponsor_block_category_outro_summary),
            switchKey = "sponsor_block_category_outro_key",
            switchState = outroCategoryEnabledState,
            modeKey = "sponsor_block_category_outro_mode_key",
            colorKey = "sponsor_block_category_outro_color_key",
            colorState = outroColorState,
            defaultColor = COLOR_OUTRO_DEFAULT,
            hasModeSelector = hasSkipModeOptions,
            enableCategoryTitle = enableCategoryTitle,
            enableModeTitle = enableModeTitle,
            colorTitle = categoryColorTitle,
            skipModeEntries = skipModeEntries,
            skipModeValues = skipModeValues,
            skipModeDefault = skipModeDefault
        )

        addSponsorBlockCategory(
            headerKey = "sponsor_block_category_header_interaction",
            headerTitle = stringResource(MR.strings.sponsor_block_category_interaction),
            summaryKey = "sponsor_block_category_interaction_summary_pref",
            summaryText = stringResource(MR.strings.settings_category_sponsor_block_category_interaction_summary),
            switchKey = "sponsor_block_category_interaction_key",
            switchState = interactionCategoryEnabledState,
            modeKey = "sponsor_block_category_interaction_mode_key",
            colorKey = "sponsor_block_category_interaction_color_key",
            colorState = interactionColorState,
            defaultColor = COLOR_INTERACTION_DEFAULT,
            hasModeSelector = hasSkipModeOptions,
            enableCategoryTitle = enableCategoryTitle,
            enableModeTitle = enableModeTitle,
            colorTitle = categoryColorTitle,
            skipModeEntries = skipModeEntries,
            skipModeValues = skipModeValues,
            skipModeDefault = skipModeDefault
        )

        addSponsorBlockCategory(
            headerKey = "sponsor_block_category_header_highlight",
            headerTitle = stringResource(MR.strings.sponsor_block_category_highlight),
            summaryKey = "sponsor_block_category_highlight_summary_pref",
            summaryText = stringResource(MR.strings.settings_category_sponsor_block_category_highlight_summary),
            switchKey = "sponsor_block_category_highlight_key",
            switchState = highlightCategoryEnabledState,
            modeKey = null,
            colorKey = "sponsor_block_category_highlight_color_key",
            colorState = highlightColorState,
            defaultColor = COLOR_HIGHLIGHT_DEFAULT,
            hasModeSelector = false,
            enableCategoryTitle = enableCategoryTitle,
            enableModeTitle = enableModeTitle,
            colorTitle = categoryColorTitle,
            skipModeEntries = skipModeEntries,
            skipModeValues = skipModeValues,
            skipModeDefault = skipModeDefault
        )

        addSponsorBlockCategory(
            headerKey = "sponsor_block_category_header_self_promo",
            headerTitle = stringResource(MR.strings.sponsor_block_category_self_promo),
            summaryKey = "sponsor_block_category_self_promo_summary_pref",
            summaryText = stringResource(MR.strings.settings_category_sponsor_block_category_self_promo_summary),
            switchKey = "sponsor_block_category_self_promo_key",
            switchState = selfPromoCategoryEnabledState,
            modeKey = "sponsor_block_category_self_promo_mode_key",
            colorKey = "sponsor_block_category_self_promo_color_key",
            colorState = selfPromoColorState,
            defaultColor = COLOR_SELF_PROMO_DEFAULT,
            hasModeSelector = hasSkipModeOptions,
            enableCategoryTitle = enableCategoryTitle,
            enableModeTitle = enableModeTitle,
            colorTitle = categoryColorTitle,
            skipModeEntries = skipModeEntries,
            skipModeValues = skipModeValues,
            skipModeDefault = skipModeDefault
        )

        addSponsorBlockCategory(
            headerKey = "sponsor_block_category_header_non_music",
            headerTitle = stringResource(MR.strings.sponsor_block_category_non_music),
            summaryKey = "sponsor_block_category_non_music_summary_pref",
            summaryText = stringResource(MR.strings.settings_category_sponsor_block_category_non_music_summary),
            switchKey = "sponsor_block_category_non_music_key",
            switchState = nonMusicCategoryEnabledState,
            modeKey = "sponsor_block_category_non_music_mode_key",
            colorKey = "sponsor_block_category_non_music_color_key",
            colorState = nonMusicColorState,
            defaultColor = COLOR_NON_MUSIC_DEFAULT,
            hasModeSelector = hasSkipModeOptions,
            enableCategoryTitle = enableCategoryTitle,
            enableModeTitle = enableModeTitle,
            colorTitle = categoryColorTitle,
            skipModeEntries = skipModeEntries,
            skipModeValues = skipModeValues,
            skipModeDefault = skipModeDefault
        )

        addSponsorBlockCategory(
            headerKey = "sponsor_block_category_header_preview",
            headerTitle = stringResource(MR.strings.sponsor_block_category_preview),
            summaryKey = "sponsor_block_category_preview_summary_pref",
            summaryText = stringResource(MR.strings.settings_category_sponsor_block_category_preview_summary),
            switchKey = "sponsor_block_category_preview_key",
            switchState = previewCategoryEnabledState,
            modeKey = "sponsor_block_category_preview_mode_key",
            colorKey = "sponsor_block_category_preview_color_key",
            colorState = previewColorState,
            defaultColor = COLOR_PREVIEW_DEFAULT,
            hasModeSelector = hasSkipModeOptions,
            enableCategoryTitle = enableCategoryTitle,
            enableModeTitle = enableModeTitle,
            colorTitle = categoryColorTitle,
            skipModeEntries = skipModeEntries,
            skipModeValues = skipModeValues,
            skipModeDefault = skipModeDefault
        )

        addSponsorBlockCategory(
            headerKey = "sponsor_block_category_header_filler",
            headerTitle = stringResource(MR.strings.sponsor_block_category_filler),
            summaryKey = "sponsor_block_category_filler_summary_pref",
            summaryText = stringResource(MR.strings.settings_category_sponsor_block_category_filler_summary),
            switchKey = "sponsor_block_category_filler_key",
            switchState = fillerCategoryEnabledState,
            modeKey = "sponsor_block_category_filler_mode_key",
            colorKey = "sponsor_block_category_filler_color_key",
            colorState = fillerColorState,
            defaultColor = COLOR_FILLER_DEFAULT,
            hasModeSelector = hasSkipModeOptions,
            enableCategoryTitle = enableCategoryTitle,
            enableModeTitle = enableModeTitle,
            colorTitle = categoryColorTitle,
            skipModeEntries = skipModeEntries,
            skipModeValues = skipModeValues,
            skipModeDefault = skipModeDefault
        )
    }

    Column {
        CustomTopBar(
            defaultTitleText = stringResource(MR.strings.settings_category_sponsor_block_categories_title)
        )

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(
                items = preferenceItems,
                key = PreferenceItem::key
            ) { item ->
                when (item) {
                    is PreferenceItem.CategoryPref -> CategoryPreference(item = item)
                    is PreferenceItem.ClickablePref -> ClickablePreference(item = item)
                    is PreferenceItem.SwitchPref -> {
                        val state = categorySwitchStates[item.key]
                        if (state != null) {
                            key("${item.key}_${state.value}") {
                                SwitchPreference(item = item)
                            }
                        } else {
                            SwitchPreference(item = item)
                        }
                    }

                    is PreferenceItem.ListPref -> ListPreference(item = item)

                    is PreferenceItem.ColorPref -> key("${item.key}_${item.currentColor}") {
                        ColorPreference(item = item)
                    }

                    else -> Unit
                }
            }
        }
    }
}

private fun MutableList<PreferenceItem>.addSponsorBlockCategory(
    headerKey: String,
    headerTitle: String,
    summaryKey: String,
    summaryText: String,
    switchKey: String?,
    switchState: MutableState<Boolean>?,
    modeKey: String?,
    colorKey: String,
    colorState: MutableState<String>,
    defaultColor: String,
    hasModeSelector: Boolean,
    enableCategoryTitle: String,
    enableModeTitle: String,
    colorTitle: String,
    skipModeEntries: List<String>,
    skipModeValues: List<String>,
    skipModeDefault: String
) {
    add(
        PreferenceItem.CategoryPref(
            key = headerKey,
            title = headerTitle,
            summary = summaryText
        )
    )

    val enabledState = switchState?.value ?: true

    if (switchKey != null && switchState != null) {
        add(
            PreferenceItem.SwitchPref(
                key = switchKey,
                title = enableCategoryTitle,
                defaultValue = true,
                onValueChange = { switchState.value = it }
            )
        )
    }

    if (hasModeSelector && modeKey != null) {
        add(
            PreferenceItem.ListPref(
                key = modeKey,
                title = enableModeTitle,
                entries = skipModeEntries,
                entryValues = skipModeValues,
                defaultValue = skipModeDefault,
                enabled = enabledState
            )
        )
    }

    add(
        PreferenceItem.ColorPref(
            key = colorKey,
            title = colorTitle,
            currentColor = colorState.value,
            defaultColor = defaultColor,
            summary = colorState.value,
            enabled = enabledState,
            onColorChange = { colorState.value = it }
        )
    )
}