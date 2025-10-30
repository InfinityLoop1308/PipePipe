package project.pipepipe.app.helper

import com.fasterxml.jackson.databind.JsonNode
import project.pipepipe.app.SharedContext

object UpdateHelper {

    data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val betaVersion: Int?
    )

    data class UpdateCheckResult(
        val hasUpdate: Boolean,
        val versionName: String?,
        val downloadUrl: String?
    )

    fun checkForUpdate(
        responseBody: String,
        currentVersionName: String,
        supportedAbis: Array<String>,
        includePreRelease: Boolean
    ): UpdateCheckResult {
        try {
            val githubReleases = SharedContext.objectMapper.readTree(responseBody)
            if (!githubReleases.isArray) {
                return UpdateCheckResult(false, null, null)
            }

            var selectedRelease: JsonNode? = null

            for (release in githubReleases) {
                if (!includePreRelease && release.get("prerelease")?.asBoolean() == true) continue
                if (selectedRelease == null || isNewerRelease(release, selectedRelease)) {
                    selectedRelease = release
                }
            }

            selectedRelease?.let { release ->
                val versionName = release.get("name")?.asText()?.removePrefix("v") ?: return UpdateCheckResult(false, null, null)
                val currentVersion = parseVersion(currentVersionName) ?: return UpdateCheckResult(false, null, null)
                val newVersion = parseVersion(versionName) ?: return UpdateCheckResult(false, null, null)

                if (compareVersions(currentVersion, newVersion) >= 0) {
                    return UpdateCheckResult(false, null, null)
                }

                val apkUrl = findCompatibleApkUrl(release, supportedAbis)
                return UpdateCheckResult(true, versionName, apkUrl)
            }

            return UpdateCheckResult(false, null, null)
        } catch (e: Exception) {
            return UpdateCheckResult(false, null, null)
        }
    }

    private fun isNewerRelease(newRelease: JsonNode, currentRelease: JsonNode): Boolean {
        val newVersionStr = newRelease.get("name")?.asText()?.removePrefix("v") ?: return false
        val currentVersionStr = currentRelease.get("name")?.asText()?.removePrefix("v") ?: return false

        val newVersion = parseVersion(newVersionStr) ?: return false
        val currentVersion = parseVersion(currentVersionStr) ?: return false

        return compareVersions(newVersion, currentVersion) > 0
    }

    private fun findCompatibleApkUrl(release: JsonNode, abis: Array<String>): String? {
        val assets = release.get("assets") ?: return null
        var universalUrl: String? = null

        for (asset in assets) {
            val name = asset.get("name")?.asText() ?: continue
            if (name.endsWith(".apk")) {
                when {
                    name.contains("universal") -> universalUrl = asset.get("browser_download_url")?.asText()
                    abis.any { name.contains(it) } -> return asset.get("browser_download_url")?.asText()
                }
            }
        }
        return universalUrl
    }

    fun parseVersion(versionStr: String): Version? {
        return try {
            val normalized = versionStr.removePrefix("v")
            val parts = normalized.split("-beta", limit = 2)
            val mainPart = parts[0]

            val mainParts = mainPart.split(".").map { it.toIntOrNull() ?: return null }
            if (mainParts.size < 3) return null

            val (major, minor, patch) = mainParts

            val betaVersion = when {
                parts.size == 1 -> null
                parts[1].isEmpty() -> 0
                else -> parts[1].toIntOrNull()
            }

            Version(major, minor, patch, betaVersion)
        } catch (e: Exception) {
            null
        }
    }

    fun compareVersions(v1: Version, v2: Version): Int {
        val mainCompare = when {
            v1.major != v2.major -> v1.major.compareTo(v2.major)
            v1.minor != v2.minor -> v1.minor.compareTo(v2.minor)
            v1.patch != v2.patch -> v1.patch.compareTo(v2.patch)
            else -> 0
        }

        if (mainCompare != 0) return mainCompare

        return when {
            v1.betaVersion == null && v2.betaVersion == null -> 0
            v1.betaVersion == null -> 1
            v2.betaVersion == null -> -1
            else -> v1.betaVersion.compareTo(v2.betaVersion)
        }
    }
}
