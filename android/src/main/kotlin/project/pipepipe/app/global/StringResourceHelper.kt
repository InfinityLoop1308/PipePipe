package project.pipepipe.app.global

import androidx.compose.runtime.Composable
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR

object StringResourceHelper {
    @Composable
    fun getTranslatedFilterString(filter: String): String {
        return when (filter) {
            "all" -> stringResource(MR.strings.all)
            // Search Types
            "video" -> stringResource(MR.strings.videos_string)
            "videos" -> stringResource(MR.strings.videos_string)
            "playlist" -> stringResource(MR.strings.playlists)
            "playlists" -> stringResource(MR.strings.playlists)
            "channel" -> stringResource(MR.strings.channels)
            "channels" -> stringResource(MR.strings.channels)
            "lives" -> stringResource(MR.strings.lives)
            "animes" -> stringResource(MR.strings.animes)
            "movies_and_tv" -> stringResource(MR.strings.movies_and_tv)
            "movie" -> stringResource(MR.strings.movies_and_tv)

            // Filter Groups
            "sortby" -> stringResource(MR.strings.sortby)
            "sortorder" -> stringResource(MR.strings.sortorder)
            "duration" -> stringResource(MR.strings.duration)
            "features" -> stringResource(MR.strings.features)
            "upload_date" -> stringResource(MR.strings.upload_date)

            // Sort Options
            "sort_overall" -> stringResource(MR.strings.sort_overall)
            "sort_view" -> stringResource(MR.strings.sort_view)
            "sort_publish_time" -> stringResource(MR.strings.sort_publish_time)
            "sort_bullet_comments" -> stringResource(MR.strings.sort_bullet_comments)
            "sort_comments" -> stringResource(MR.strings.sort_comments)
            "sort_bookmark" -> stringResource(MR.strings.sort_bookmark)
            "sort_popular" -> stringResource(MR.strings.sort_popular)
            "sort_likes" -> stringResource(MR.strings.sort_likes)
            "sort_last_comment_time" -> stringResource(MR.strings.sort_last_comment_time)
            "sort_video_count" -> stringResource(MR.strings.sort_video_count)
            "sort_relevance" -> stringResource(MR.strings.sort_relevance)
            "sort_rating" -> stringResource(MR.strings.sort_rating)
            "sort_ascending" -> stringResource(MR.strings.sort_ascending)

            // Duration Options
            "duration_short" -> stringResource(MR.strings.duration_short)
            "duration_medium" -> stringResource(MR.strings.duration_medium)
            "duration_long" -> stringResource(MR.strings.duration_long)

            // Upload Date
            "hour" -> stringResource(MR.strings.upload_hour)
            "day" -> stringResource(MR.strings.upload_day)
            "week" -> stringResource(MR.strings.upload_week)
            "month" -> stringResource(MR.strings.upload_month)
            "year" -> stringResource(MR.strings.upload_year)

            "sort_descending" -> stringResource(MR.strings.sort_descending)
            "sort_ascending" ->stringResource(MR.strings.sort_ascending)

            else -> filter
        }
    }

    @Composable
    fun getTranslatedTabString(tab: String): String {
        return when (tab) {
            "videos" -> stringResource(MR.strings.channel_tab_videos)
            "playlists" -> stringResource(MR.strings.channel_tab_playlists)
            "channels" -> stringResource(MR.strings.channel_tab_channels)
            "tracks" -> stringResource(MR.strings.channel_tab_tracks)
            "shorts" -> stringResource(MR.strings.channel_tab_shorts)
            "albums" -> stringResource(MR.strings.channel_tab_albums)
            "info" -> stringResource(MR.strings.channel_tab_info)
            else -> tab
        }
    }

    @Composable
    fun getTranslatedTrendingName(name: String): String {
        return when (name) {
            "Trending" -> stringResource(MR.strings.trending)
            "Recommended Lives" -> stringResource(MR.strings.recommended_lives)
            else -> name
        }
    }
}