package project.pipepipe.app.utils

import io.ktor.http.encodeURLParameter
import project.pipepipe.shared.infoitem.helper.SearchType

expect fun formatCount(count: Long?): String
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