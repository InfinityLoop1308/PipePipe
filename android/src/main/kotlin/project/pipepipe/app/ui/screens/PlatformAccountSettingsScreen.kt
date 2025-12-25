package project.pipepipe.app.ui.screens

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.helper.CookieManager
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.helper.isLoggedInCookie
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.screens.settings.PreferenceItem
import android.webkit.CookieManager as AndroidCookieManager

enum class LoginPlatform(
    val displayName: String,
    val id: Int,
    val url: String,
    val cookieIndicator: String,
    val userAgent: String,
    val redirectUrl: String? = null
) {
    BILIBILI(
        id = 5,
        displayName = "BiliBili",
        url = "https://passport.bilibili.com/login",
        cookieIndicator = "SESSDATA=",
        userAgent = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
    ),
    YOUTUBE(
        id = 0,
        displayName = "YouTube",
        url = "https://www.youtube.com/signin",
        cookieIndicator = "LOGIN_INFO=",
        userAgent = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36",
        redirectUrl = "https://music.youtube.com/watch?v=09839DpTctU"
    ),
    NICONICO(
        id = 6,
        displayName = "NicoNico",
        url = "https://account.nicovideo.jp/login",
        cookieIndicator = "user_session=",
        userAgent = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
    )
}

@Composable
fun AccountSettingsScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Track login states for all platforms
    var bilibiliLoggedIn by remember {
        mutableStateOf(CookieManager.getCookie(5)?.isLoggedInCookie()?:false)
    }
    var youtubeLoggedIn by remember {
        mutableStateOf(CookieManager.getCookie(0)?.isLoggedInCookie()?:false)
    }
    var niconicoLoggedIn by remember {
        mutableStateOf(CookieManager.getCookie(6)?.isLoggedInCookie()?:false)
    }

    var showLoginWebView by remember { mutableStateOf<LoginPlatform?>(null) }

    // Get string resources
    val loginTitle = stringResource(MR.strings.account_login)
    val logoutTitle = stringResource(MR.strings.account_logout)
    val loggedOutMessage = stringResource(MR.strings.account_logged_out)
    val successMessage = stringResource(MR.strings.success)
    val tryAgainMessage = stringResource(MR.strings.account_try_again)
    val loginBilibiliSummary = stringResource(MR.strings.account_login_with_webview).format("BiliBili")
    val loginYoutubeSummary = stringResource(MR.strings.account_login_with_webview).format("YouTube")
    val loginNiconicoSummary = stringResource(MR.strings.account_login_with_webview).format("NicoNico")
    val clearWebViewCookiesTitle = stringResource(MR.strings.account_clear_webview_cookies)
    val clearWebViewCookiesSummary = stringResource(MR.strings.account_clear_webview_cookies_summary)
    val webViewCookiesClearedMessage = stringResource(MR.strings.account_webview_cookies_cleared)
    val failedToClearWebViewCookiesMessage = stringResource(MR.strings.account_failed_to_clear_webview_cookies)

    // Build combined preference list for all platforms
    val preferences = remember(
        bilibiliLoggedIn, youtubeLoggedIn, niconicoLoggedIn,
        loginTitle, logoutTitle, loginBilibiliSummary, loginYoutubeSummary, loginNiconicoSummary,
        clearWebViewCookiesTitle, clearWebViewCookiesSummary, webViewCookiesClearedMessage, failedToClearWebViewCookiesMessage
    ) {
        buildList {
            // ===== Clear WebView Cookies =====
            add(
                PreferenceItem.ClickablePref(
                key = "clear_webview_cookies",
                title = clearWebViewCookiesTitle,
                summary = clearWebViewCookiesSummary,
                onClick = {
                    val cookieManager = AndroidCookieManager.getInstance()
                    cookieManager.removeAllCookies { success ->
                        if (success) {
                            cookieManager.flush()
                            ToastManager.show(webViewCookiesClearedMessage)
                        } else {
                            ToastManager.show(failedToClearWebViewCookiesMessage)
                        }
                    }
                }
            ))

            // ===== YouTube Section =====
            add(PreferenceItem.CategoryPref(key = "youtube_category", title = "YouTube"))

            add(
                PreferenceItem.ClickablePref(
                key = "youtube_login",
                title = loginTitle,
                summary = loginYoutubeSummary,
                enabled = !youtubeLoggedIn,
                onClick = { showLoginWebView = LoginPlatform.YOUTUBE }
            ))

            add(
                PreferenceItem.ClickablePref(
                key = "youtube_logout",
                title = logoutTitle,
                enabled = youtubeLoggedIn,
                onClick = {
                    CookieManager.removeLoggedInCookie(0)
                    youtubeLoggedIn = false
                    ToastManager.show(loggedOutMessage)
                }
            ))

            // ===== BiliBili Section =====
            add(PreferenceItem.CategoryPref(key = "bilibili_category", title = "BiliBili"))

            add(
                PreferenceItem.ClickablePref(
                key = "bilibili_login",
                title = loginTitle,
                summary = loginBilibiliSummary,
                enabled = !bilibiliLoggedIn,
                onClick = { showLoginWebView = LoginPlatform.BILIBILI }
            ))

            add(
                PreferenceItem.ClickablePref(
                key = "bilibili_logout",
                title = logoutTitle,
                enabled = bilibiliLoggedIn,
                onClick = {
                    CookieManager.removeLoggedInCookie(5)
                    bilibiliLoggedIn = false
                    ToastManager.show(loggedOutMessage)
                }
            ))


            // ===== NicoNico Section =====
            add(PreferenceItem.CategoryPref(key = "niconico_category", title = "NicoNico"))

            add(
                PreferenceItem.ClickablePref(
                key = "niconico_login",
                title = loginTitle,
                summary = loginNiconicoSummary,
                enabled = !niconicoLoggedIn,
                onClick = { showLoginWebView = LoginPlatform.NICONICO }
            ))

            add(
                PreferenceItem.ClickablePref(
                key = "niconico_logout",
                title = logoutTitle,
                enabled = niconicoLoggedIn,
                onClick = {
                    CookieManager.removeLoggedInCookie(6)
                    niconicoLoggedIn = false
                    ToastManager.show(loggedOutMessage)
                }
            ))
        }
    }

    if (showLoginWebView != null) {
        LoginWebViewScreen(
            navController = navController,
            platform = showLoginWebView!!,
            onLoginSuccess = { cookies ->
                val platform = showLoginWebView!!
                val serviceId = platform.id

                if (serviceId == 0 && (!cookies.contains("SAPISID=") && !cookies.contains("__Secure-3PAPISID="))) {
                    ToastManager.show(tryAgainMessage)
                } else {
                    val timeout = System.currentTimeMillis() / 1000 + 28L * 24 * 60 * 60
                    CookieManager.setCookie(serviceId, cookies, timeout, isLoggedInCookie = true)

                    when (platform) {
                        LoginPlatform.BILIBILI -> bilibiliLoggedIn = true
                        LoginPlatform.YOUTUBE -> youtubeLoggedIn = true
                        LoginPlatform.NICONICO -> niconicoLoggedIn = true
                    }
                    ToastManager.show(successMessage)
                }
                showLoginWebView = null
            },
            onClose = {
                showLoginWebView = null
            }
        )
    } else {
        PreferenceScreen(
            stringResource(MR.strings.settings_section_account),
            preferences
        )
    }
}

@Composable
fun LoginWebViewScreen(
    navController: NavController,
    platform: LoginPlatform,
    onLoginSuccess: (cookies: String) -> Unit,
    onClose: () -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let { view ->
                view.stopLoading()
                view.loadUrl("about:blank")
                view.onPause()
                view.removeAllViews()
                view.clearHistory()
                view.clearCache(true)
                view.destroy()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CustomTopBar(
            defaultTitleText = "${platform.displayName} ${stringResource(MR.strings.account_login)}",
            defaultNavigationOnClick = { onClose() },
            actions = {
                IconButton(onClick = { onClose() }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(MR.strings.account_close))
                }
            }
        )

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webView = this

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        userAgentString = platform.userAgent
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            val cookies = AndroidCookieManager.getInstance().getCookie(url) ?: ""

                            if (cookies.contains(platform.cookieIndicator)) {
                                onLoginSuccess(cookies)
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            view.loadUrl(request.url.toString())
                            return true
                        }
                    }

                    // Load URL with custom headers for BiliBili
                    if (platform == LoginPlatform.BILIBILI) {
                        val headers = mapOf(
                            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                            "Accept-Language" to "en-US,en;q=0.5",
                            "Accept-Encoding" to "gzip, deflate, br",
                            "DNT" to "1",
                            "Connection" to "keep-alive",
                            "Upgrade-Insecure-Requests" to "1",
                            "Sec-Fetch-Dest" to "document",
                            "Sec-Fetch-Mode" to "navigate",
                            "Sec-Fetch-Site" to "none",
                            "Sec-Fetch-User" to "?1"
                        )
                        loadUrl(platform.url, headers)
                    } else {
                        loadUrl(platform.url)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}