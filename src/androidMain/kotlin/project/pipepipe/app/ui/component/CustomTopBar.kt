package project.pipepipe.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.ui.theme.customTopBarColor
import project.pipepipe.app.ui.theme.onCustomTopBarColor

@Composable
fun CustomTopBar(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    defaultTitleText: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    defaultNavigationOnClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    height: Dp = 56.dp,
    shadowElevation: Dp = 3.dp,
    titlePadding: Dp = 16.dp
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .background(customTopBarColor())
                .statusBarsPadding()
                .height(height),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().background(customTopBarColor()),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (navigationIcon != null) {
                    Box(modifier = Modifier.padding(start = 4.dp)) {
                        navigationIcon()
                    }
                } else if (defaultNavigationOnClick != null) {
                    IconButton(onClick = defaultNavigationOnClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MR.strings.back),
                            tint = onCustomTopBarColor()
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = titlePadding)
                ) {
                    if(title != null) {
                        title()
                    }else {
                        Text(
                            text = defaultTitleText!!,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 18.sp,
                            color = onCustomTopBarColor()
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    content = actions
                )
            }
        }

        // 底部阴影
        if (shadowElevation > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(shadowElevation)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}
