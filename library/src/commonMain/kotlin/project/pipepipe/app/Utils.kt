package project.pipepipe.app

import project.pipepipe.shared.infoitem.helper.SearchType
import io.ktor.http.*

object Utils {
    fun formatCount(count: Long?): String {
        return when {
            count == null -> "..."
            count > 1_000_000_000 -> "%.1fB".format(count / 1_000_000_000.0)
            count > 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
            count > 1_000 -> "%.1fK".format(count / 1_000.0)
            else -> count.toString()
        }
    }

    fun generateQueryUrl(query: String, searchType: SearchType): String = buildString {
        append(searchType.baseUrl)
        append(query.encodeURLParameter())
        searchType.availableSearchFilterGroups?.forEach { group ->
            group.selectedSearchFilters.forEach {
                val separator = if (contains("?")) "&" else "?"
                append(separator).append(it.parameter)
            }
        }
    }
}