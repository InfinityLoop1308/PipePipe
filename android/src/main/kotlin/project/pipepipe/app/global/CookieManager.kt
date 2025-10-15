package project.pipepipe.app.global
import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import project.pipepipe.shared.infoitem.CookieInfo

class CookieManager private constructor(context: Context): project.pipepipe.shared.downloader.CookieManager {

    companion object {
        private const val PREFS_NAME = "cookie_manager_prefs"
        private const val COOKIE_PREFIX = "cookie_"

        @Volatile
        private var INSTANCE: CookieManager? = null

        fun getInstance(context: Context): CookieManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CookieManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
        val cookieInfoJson = sharedPreferences.getString(key, null) ?: return null

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

        return sharedPreferences.edit()
            .putString(key, cookieInfoJson)
            .commit()
    }
    fun setCookie(id: String, cookie: String?, timeOut: Long): Boolean {
        return setCookieInfo(id, CookieInfo(cookie, timeOut))
    }
    fun removeCookie(id: String): Boolean {
        val key = COOKIE_PREFIX + id
        return sharedPreferences.edit()
            .remove(key)
            .commit()
    }
    fun cleanExpiredCookies(): Int {
        var cleanedCount = 0

        val allEntries = sharedPreferences.all
        val editor = sharedPreferences.edit()

        for ((key, _) in allEntries) {
            if (key.startsWith(COOKIE_PREFIX)) {
                val id = key.removePrefix(COOKIE_PREFIX)
                if (isCookieExpired(id)) {
                    editor.remove(key)
                    cleanedCount++
                }
            }
        }

        editor.apply()
        return cleanedCount
    }
    fun getValidCookieIds(): List<String> {
        val validIds = mutableListOf<String>()

        val allEntries = sharedPreferences.all
        for ((key, _) in allEntries) {
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
        val editor = sharedPreferences.edit()
        val allEntries = sharedPreferences.all

        for (key in allEntries.keys) {
            if (key.startsWith(COOKIE_PREFIX)) {
                editor.remove(key)
            }
        }

        editor.apply()
    }
}
