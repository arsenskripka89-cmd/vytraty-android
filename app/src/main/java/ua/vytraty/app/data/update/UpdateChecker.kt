package ua.vytraty.app.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.vytraty.app.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * In-app updates from GitHub Releases: checks the latest release of [BuildConfig.UPDATE_REPO],
 * downloads its APK and hands it to the system installer.
 */
class UpdateChecker(private val context: Context) {
    data class ReleaseInfo(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Returns the latest release when it is newer than the installed version, otherwise null. */
    suspend fun check(): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "vytraty-android/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 404) return@use null
                if (!resp.isSuccessful) error("GitHub відповів ${resp.code}")
                val root = json.parseToJsonElement(resp.body!!.string()).jsonObject
                val tag = root["tag_name"]!!.jsonPrimitive.content.removePrefix("v")
                val notes = root["body"]?.jsonPrimitive?.content.orEmpty()
                val apk = root["assets"]?.jsonArray?.map { it.jsonObject }
                    ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
                    ?: return@use null
                val info = ReleaseInfo(
                    version = tag,
                    notes = notes,
                    apkUrl = apk["browser_download_url"]!!.jsonPrimitive.content,
                    sizeBytes = apk["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                )
                if (isNewer(info.version, currentVersion)) info else null
            }
        }
    }

    suspend fun download(info: ReleaseInfo, onProgress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "vytraty-${info.version}.apk")
            val req = Request.Builder().url(info.apkUrl).header("User-Agent", "vytraty-android").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Завантаження не вдалося: ${resp.code}")
                val body = resp.body!!
                val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
                body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var done = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            done += read
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            file
        }
    }

    /** Opens the system package installer; Android asks for the "install unknown apps" permission the first time. */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    companion object {
        /** Compares dotted versions numerically: "1.10.0" > "1.9.2". */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val b = current.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val n = maxOf(a.size, b.size)
            for (i in 0 until n) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
