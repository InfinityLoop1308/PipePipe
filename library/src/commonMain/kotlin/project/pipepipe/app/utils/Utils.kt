package project.pipepipe.app.utils

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Bitmap
import io.ktor.http.encodeURLParameter
import project.pipepipe.shared.infoitem.helper.SearchType

expect fun formatCount(count: Long?): String

expect fun Bitmap.toComposeImageBitmap(): ImageBitmap
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