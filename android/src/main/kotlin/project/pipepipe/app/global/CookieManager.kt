package project.pipepipe.app.global
import kotlinx.serialization.json.Json
import project.pipepipe.app.SharedContext
import project.pipepipe.shared.downloader.isLoggedInCookie
import project.pipepipe.shared.infoitem.CookieInfo

class CookieManager: project.pipepipe.shared.downloader.CookieManager {

    companion object {
        private const val COOKIE_PREFIX = "cookie_"
        private const val LOGGED_IN_COOKIE_PREFIX = "logged_in_cookie_"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    override fun getCookie(id: Int): String? {
        val cookieInfo = getCookieInfo(id) ?: return null
        return if (isCookieExpired(id)) null else cookieInfo.cookie
    }

    override fun getCookieInfo(id: Int): CookieInfo? {
        var cookieInfoJson = getLoggedInCookieInfoJson(id)
        if (cookieInfoJson == null) {
            val key = COOKIE_PREFIX + id
            cookieInfoJson = SharedContext.settingsManager.getString(key, "")
            if (cookieInfoJson.isEmpty()) return null
        }
        return json.decodeFromString<CookieInfo>(cookieInfoJson)
    }
    override fun isCookieExpired(id: Int): Boolean {
        val cookieInfo = getCookieInfo(id) ?: return true
        val currentEpochSecond = System.currentTimeMillis() / 1000
        val expired = currentEpochSecond >= cookieInfo.timeOut
        if (expired && cookieInfo.cookie?.isLoggedInCookie() == true) {
            removeLoggedInCookie(id)
        }
        return expired
    }
    override fun setCookieInfo(id: Int, cookieInfo: CookieInfo, isLoggedInCookie: Boolean) {
        val key = (if (isLoggedInCookie) LOGGED_IN_COOKIE_PREFIX else COOKIE_PREFIX) + id
        val cookieInfoJson = json.encodeToString(cookieInfo)
        SharedContext.settingsManager.putString(key, cookieInfoJson)
    }
    override fun setCookie(id: Int, cookies: String, timeout: Long, isLoggedInCookie: Boolean) {
        setCookieInfo(id, CookieInfo(cookies + if (isLoggedInCookie)";is_logged_in=1" else "", timeout), isLoggedInCookie)
    }
    override fun removeLoggedInCookie(id: Int) {
        val key = LOGGED_IN_COOKIE_PREFIX + id
        SharedContext.settingsManager.remove(key)
    }
    fun getLoggedInCookieInfoJson(id: Int): String? {
        val key = LOGGED_IN_COOKIE_PREFIX + id
        val cookieInfoJson = SharedContext.settingsManager.getString(key, "")
        if (cookieInfoJson.isEmpty()) return null
        return cookieInfoJson
    }
}
