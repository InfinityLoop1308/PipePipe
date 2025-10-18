package project.pipepipe.app.global
import kotlinx.serialization.json.Json
import project.pipepipe.app.SharedContext
import project.pipepipe.shared.infoitem.CookieInfo

class CookieManager: project.pipepipe.shared.downloader.CookieManager {

    companion object {
        private const val COOKIE_PREFIX = "cookie_"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    override fun getCookie(id: String): String? {
        val cookieInfo = getCookieInfo(id) ?: return null
        return if (isCookieExpired(id)) null else cookieInfo.cookie
    }

    override fun getCookieInfo(id: String): CookieInfo? {
        val key = COOKIE_PREFIX + id
        val cookieInfoJson = SharedContext.settingsManager.getString(key, "")
        if (cookieInfoJson.isEmpty()) return null

        return try {
            json.decodeFromString<CookieInfo>(cookieInfoJson)
        } catch (e: Exception) {
            // 如果反序列化失败，删除损坏的数据
            removeCookie(id)
            null
        }
    }
    override fun isCookieExpired(id: String): Boolean {
        val cookieInfo = getCookieInfo(id) ?: return true
        val currentEpochSecond = System.currentTimeMillis() / 1000
        return currentEpochSecond >= cookieInfo.timeOut
    }
    override fun setCookieInfo(id: String, cookieInfo: CookieInfo): Boolean {
        val key = COOKIE_PREFIX + id
        val cookieInfoJson = try {
            json.encodeToString(cookieInfo)
        } catch (e: Exception) {
            return false
        }

        return try {
            SharedContext.settingsManager.putString(key, cookieInfoJson)
            true
        } catch (e: Exception) {
            false
        }
    }
    fun setCookie(id: String, cookie: String?, timeOut: Long): Boolean {
        return setCookieInfo(id, CookieInfo(cookie, timeOut))
    }
    fun removeCookie(id: String): Boolean {
        val key = COOKIE_PREFIX + id
        return try {
            SharedContext.settingsManager.remove(key)
            true
        } catch (e: Exception) {
            false
        }
    }
    fun cleanExpiredCookies(): Int {
        var cleanedCount = 0

        val snapshot = SharedContext.settingsManager.snapshot()

        for ((key, _) in snapshot) {
            if (key.startsWith(COOKIE_PREFIX)) {
                val id = key.removePrefix(COOKIE_PREFIX)
                if (isCookieExpired(id)) {
                    SharedContext.settingsManager.remove(key)
                    cleanedCount++
                }
            }
        }

        return cleanedCount
    }
    fun getValidCookieIds(): List<String> {
        val validIds = mutableListOf<String>()

        val snapshot = SharedContext.settingsManager.snapshot()
        for ((key, _) in snapshot) {
            if (key.startsWith(COOKIE_PREFIX)) {
                val id = key.removePrefix(COOKIE_PREFIX)
                if (!isCookieExpired(id)) {
                    validIds.add(id)
                }
            }
        }

        return validIds
    }
    fun clearAllCookies() {
        val snapshot = SharedContext.settingsManager.snapshot()

        for (key in snapshot.keys) {
            if (key.startsWith(COOKIE_PREFIX)) {
                SharedContext.settingsManager.remove(key)
            }
        }
    }
}
