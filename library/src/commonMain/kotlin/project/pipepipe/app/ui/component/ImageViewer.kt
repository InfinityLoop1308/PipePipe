package project.pipepipe.app.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.panpf.zoomimage.rememberCoilZoomState
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.helper.ToastManager

@Composable
fun ImageViewer() {
    val imageViewerState by SharedContext.imageViewerState.collectAsState()

    if (imageViewerState.isVisible && imageViewerState.urls.isNotEmpty()) {
        BackHandler {
            SharedContext.hideImageViewer()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding()
        )  {
            ImageViewerContent(
                urls = imageViewerState.urls.map { it.replace("http://", "https://") },
                initialPage = imageViewerState.initialPage
            )
        }
    }
}

@Composable
private fun ImageViewerContent(
    urls: List<String>,
    initialPage: Int
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, urls.size - 1),
        pageCount = { urls.size }
    )
    var controlsVisible by remember { mutableStateOf(true) }
    val savedText = stringResource(MR.strings.saved)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        // Image Pager with ZoomImage
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomableImage(
                url = urls[page],
                onTap = { controlsVisible = !controlsVisible }
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            IconButton(
                onClick = { SharedContext.hideImageViewer() },
                modifier = Modifier
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(MR.strings.close),
                    tint = Color.White
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${urls.size}",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val currentUrl = urls[pagerState.currentPage]

                    // Share Button
                    IconButton(onClick = {
                        SharedContext.platformActions.share(currentUrl, null)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(MR.strings.share),
                            tint = Color.White
                        )
                    }
                    if (SharedContext.androidVersion >= 29) {
                        IconButton(onClick = {
                            SharedContext.platformActions.downloadImage(
                                currentUrl,
                                "${System.currentTimeMillis()}.jpg"
                            )
                            ToastManager.show(savedText)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = stringResource(MR.strings.download),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ZoomableImage(
    url: String,
    onTap: () -> Unit
) {
    val zoomState = rememberCoilZoomState()
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        CoilZoomAsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            zoomState = zoomState,
            onTap = { _ -> onTap() },
            onLoading = {
                isLoading = true
                isError = false
            },
            onSuccess = {
                isLoading = false
                isError = false
            },
            onError = { state ->
                state.result.throwable.printStackTrace()
                isLoading = false
                isError = true
                errorMessage = state.result.throwable.message ?: "Unknown error"
            }
        )

        // Loading overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // Error overlay
        if (isError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(MR.strings.image_viewer_failed_to_load),
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        isLoading = true
                        isError = false
                    }) {
                        Text(stringResource(MR.strings.retry))
                    }
                }
            }
        }
    }
}
