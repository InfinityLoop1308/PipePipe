package project.pipepipe.app.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import project.pipepipe.app.ui.theme.onCustomTopBarColor


@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ResponsiveTabs(
    titles: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    icons: List<ImageVector>? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onTabSelected: (Int) -> Unit,
) {
    if (titles.isEmpty()) return

    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints{
        val textStyle = MaterialTheme.typography.labelLarge.copy(
            fontSize = 14.sp
        )
        val tabWidthsPx = remember(titles, icons) {
            titles.mapIndexed { index, title ->
                val hasIcon = icons != null && index < icons.size
                val textLayout = if (title.isNotEmpty()) {
                    textMeasurer.measure(
                        text = AnnotatedString(title),
                        style = textStyle
                    )
                } else null

                val textWidth = textLayout?.size?.width ?: 0
                val iconWidth = if (hasIcon) with(density) { 24.dp.roundToPx() } else 0
                val horizontalPaddingPx = with(density) { 24.dp.roundToPx() }

                textWidth + iconWidth + horizontalPaddingPx
            }
        }

        val maxTabWidthPx = tabWidthsPx.maxOrNull() ?: 0
        val requiredWidthForFixedTabRow = maxTabWidthPx * titles.size
        val containerWidthPx = constraints.maxWidth
        val shouldScroll = requiredWidthForFixedTabRow > containerWidthPx

        val tabContent: @Composable () -> Unit = {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = {
                        coroutineScope.launch {
                            onTabSelected(index)
                        }
                    },
                    modifier = Modifier.height(44.dp),
                    icon = if (icons != null && index < icons.size) {
                        {
                            Icon(
                                imageVector = icons[index],
                                contentDescription = title.ifEmpty { null },
                                tint = onCustomTopBarColor()
                            )
                        }
                    } else null,
                    text = if (title.isNotEmpty()) {
                        {
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else null
                )
            }
        }

        if (shouldScroll) {
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                modifier = modifier,
                edgePadding = 0.dp,
                containerColor = containerColor,
                tabs = tabContent
            )
        } else {
            TabRow(
                selectedTabIndex = selectedIndex,
                modifier = modifier,
                containerColor = containerColor,
                tabs = tabContent
            )
        }
    }
}
