package com.karelisio.somna.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

sealed interface InstallState {
    data object Idle : InstallState
    data object NeedsPermission : InstallState
    data class Downloading(val progress: Float, val bytes: Long, val total: Long) : InstallState
    data object Verifying : InstallState
    data object Installing : InstallState
    data object WaitingForUser : InstallState
    data object Success : InstallState
    data class Failed(val message: String) : InstallState
}

/** Downloads the release APK and installs it with [PackageInstaller]. */
object UpdateInstaller {
    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state

    /** Android's "install this update?" screen, kept to reopen it if it couldn't be shown. */
    @Volatile var pendingConfirmation: Intent? = null
        internal set

    internal fun publish(state: InstallState) {
        _state.value = state
    }

    fun reset() {
        pendingConfirmation = null
        _state.value = InstallState.Idle
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    suspend fun downloadAndInstall(context: Context, info: ReleaseInfo) {
        if (!canInstall(context)) {
            _state.value = InstallState.NeedsPermission
            return
        }
        try {
            val file = download(context, info)
            _state.value = InstallState.Verifying
            verify(context, file)?.let {
                _state.value = InstallState.Failed(it)
                return
            }
            _state.value = InstallState.Installing
            install(context, file)
        } catch (e: Exception) {
            _state.value = InstallState.Failed(e.message ?: "Téléchargement interrompu")
        }
    }

    private suspend fun download(context: Context, info: ReleaseInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "somna-${info.version}.apk")
        val connection = URL(info.apkUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/octet-stream")
            if (connection.responseCode !in 200..299) throw IllegalStateException("Téléchargement impossible (${connection.responseCode})")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.apkSize
            var read = 0L
            var lastEmit = 0L
            _state.value = InstallState.Downloading(0f, 0, total)
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (isActive) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 50) {
                            lastEmit = now
                            _state.value = InstallState.Downloading(if (total > 0) read / total.toFloat() else 0f, read, total)
                        }
                    }
                }
            }
            _state.value = InstallState.Downloading(1f, read, total)
            target
        } finally {
            connection.disconnect()
        }
    }

    /** Returns an error message if the APK isn't a valid update (other app, other signing key…). */
    private fun verify(context: Context, file: File): String? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: return "Le fichier téléchargé est invalide."
        if (archive.packageName != context.packageName) return "Ce fichier n'est pas une mise à jour de Somna."
        val installed = pm.getPackageInfo(context.packageName, flags)
        val a = signatures(archive)
        val b = signatures(installed)
        if (a.isNotEmpty() && b.isNotEmpty() && a.intersect(b).isEmpty()) {
            return "La mise à jour n'est pas signée avec la même clé que l'appli installée : Android refuserait l'installation."
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): Set<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { s ->
                (if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory)?.map { it.toCharsString() }
            }.orEmpty().toSet()
        } else {
            info.signatures?.map { it.toCharsString() }.orEmpty().toSet()
        }

    private suspend fun install(context: Context, file: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("somna.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val intent = Intent(context, InstallResultReceiver::class.java).setAction(InstallResultReceiver.ACTION)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }
}
