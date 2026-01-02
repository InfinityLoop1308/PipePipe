package project.pipepipe.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.supportingTextColor

@Composable
fun AboutScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val appName = stringResource(MR.strings.app_name)

    Column {
        CustomTopBar(
            defaultTitleText = stringResource(MR.strings.settings_section_about)
        )

        Surface(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Image(
                        painter = painterResource(MR.images.ic_launcher_foreground),
                        contentDescription = "$appName icon",
                        modifier = Modifier.size(80.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(MR.strings.about_app_version, SharedContext.platformActions.getAppVersion()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    Text(
                        text = stringResource(MR.strings.about_app_description),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = supportingTextColor()
                    )
                }
                item {
                    Spacer(Modifier.height(16.dp))
                }
                item {
                    CompactSection(
                        title = stringResource(MR.strings.about_contribute_title),
                        description = stringResource(MR.strings.about_contribute_description),
                        buttonLabel = stringResource(MR.strings.about_view_on_github),
                        onButtonClick = {
                            SharedContext.platformActions.openUrl("https://github.com/InfinityLoop1308/PipePipe/issues")
                        }
                    )
                }
                item {
                    CompactSection(
                        title = stringResource(MR.strings.about_donate_title),
                        description = stringResource(MR.strings.about_donate_description),
                        buttonLabel = stringResource(MR.strings.about_become_supporter),
                        onButtonClick = {
                            SharedContext.platformActions.openUrl("https://ko-fi.com/pipepipe")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSection(
    title: String,
    description: String,
    buttonLabel: String,
    onButtonClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 18.sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = description,
            fontSize = 13.5.sp,
            style = TextStyle(
                platformStyle = PlatformTextStyle(
                    includeFontPadding = false
                )
            ),
            color = supportingTextColor()
        )
        TextButton(
            onClick = onButtonClick,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(text = buttonLabel)
        }
    }
}
