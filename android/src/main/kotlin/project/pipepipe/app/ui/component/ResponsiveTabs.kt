package project.pipepipe.app.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch


@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ResponsiveTabs(
    titles: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
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
        val tabWidthsPx = remember(titles) {
            titles.map { title ->
                val textLayout = textMeasurer.measure(
                    text = AnnotatedString(title),
                    style = textStyle
                )
                val horizontalPaddingPx = with(density) { 32.dp.roundToPx() }
                textLayout.size.width + horizontalPaddingPx
            }
        }

        val totalTabsWidthPx = tabWidthsPx.sum()
        val containerWidthPx = constraints.maxWidth
        val shouldScroll = totalTabsWidthPx > containerWidthPx

        val tabContent: @Composable () -> Unit = {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = {
                        coroutineScope.launch {
                            onTabSelected(index)
                        }
                    },
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        if (shouldScroll) {
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                modifier = modifier,
                edgePadding = 0.dp,
                tabs = tabContent
            )
        } else {
            TabRow(
                selectedTabIndex = selectedIndex,
                modifier = modifier,
                tabs = tabContent
            )
        }
    }
}
