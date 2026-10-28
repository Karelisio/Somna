package com.karelisio.somna.update

import android.content.Context
import com.karelisio.somna.BuildConfig
import com.karelisio.somna.data.SettingsRepository
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

@Serializable
data class ReleaseInfo(
    val version: String,
    val tag: String,
    val name: String,
    val changelog: String,
    val apkUrl: String,
    val apkSize: Long,
    val publishedAt: String,
)

sealed interface CheckResult {
    data class Available(val info: ReleaseInfo) : CheckResult
    data object UpToDate : CheckResult
    data object Throttled : CheckResult
    data class Error(val message: String) : CheckResult
}

/** Checks GitHub Releases (releases/latest) for a newer signed APK. */
class UpdateRepository(private val context: Context, private val settings: SettingsRepository) {
    val currentVersion: String = BuildConfig.VERSION_NAME.removeSuffix("-debug")

    /** Latest known release, if newer than the installed version. */
    val available: Flow<ReleaseInfo?> = settings.settings.map { s ->
        decode(s.latestReleaseJson)?.takeIf { isNewer(it.version, currentVersion) }
    }

    suspend fun check(force: Boolean): CheckResult {
        val s = settings.current()
        val now = System.currentTimeMillis()
        if (!force && now - s.lastUpdateCheck < MIN_INTERVAL_MS) {
            return decode(s.latestReleaseJson)?.takeIf { isNewer(it.version, currentVersion) }
                ?.let { CheckResult.Available(it) } ?: CheckResult.Throttled
        }
        return try {
            val info = fetchLatest()
            settings.setUpdateCheckResult(now, info?.let { json.encodeToString(ReleaseInfo.serializer(), it) }.orEmpty())
            if (info != null && isNewer(info.version, currentVersion)) CheckResult.Available(info) else CheckResult.UpToDate
        } catch (e: Exception) {
            CheckResult.Error(e.message ?: "Erreur réseau")
        }
    }

    private suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "Somna/${BuildConfig.VERSION_NAME}")
            when (val code = connection.responseCode) {
                200 -> parse(connection.inputStream.bufferedReader().use { it.readText() })
                404 -> null // no release yet
                else -> throw IllegalStateException("GitHub a répondu $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): ReleaseInfo? {
        val root = json.parseToJsonElement(body).jsonObject
        val tag = root.string("tag_name") ?: return null
        val apk = root["assets"]?.jsonArray?.map { it.jsonObject }
            ?.firstOrNull { it.string("name")?.endsWith(".apk") == true } ?: return null
        return ReleaseInfo(
            version = tag.removePrefix("v"),
            tag = tag,
            name = root.string("name").orEmpty().ifBlank { tag },
            changelog = root.string("body").orEmpty().trim(),
            apkUrl = apk.string("browser_download_url") ?: return null,
            apkSize = apk["size"]?.jsonPrimitive?.long ?: 0L,
            publishedAt = root.string("published_at").orEmpty(),
        )
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.takeIf { it.isString }?.content

    private fun decode(value: String): ReleaseInfo? =
        value.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(ReleaseInfo.serializer(), it) }.getOrNull() }

    companion object {
        const val MIN_INTERVAL_MS = 6 * 3_600_000L
        private val json = Json { ignoreUnknownKeys = true }

        /** Semantic version comparison; a pre-release suffix (1.2.0-dev) is older than 1.2.0. */
        fun isNewer(remote: String, local: String): Boolean = compare(remote, local) > 0

        fun compare(a: String, b: String): Int {
            fun parts(v: String) = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val pa = parts(a)
            val pb = parts(b)
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val d = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
                if (d != 0) return d
            }
            val sa = a.contains('-')
            val sb = b.contains('-')
            return when {
                sa == sb -> 0
                sa -> -1
                else -> 1
            }
        }
    }
}
