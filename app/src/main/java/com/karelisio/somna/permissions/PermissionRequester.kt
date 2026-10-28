package com.karelisio.somna.permissions

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Requests an [AppPermission]: runtime dialog, or system settings screen. For settings screens it
 * watches the state and brings the user back to Somna as soon as the permission is granted.
 */
class PermissionRequester internal constructor(
    private val activity: Activity?,
    private val scope: CoroutineScope,
    private val launchRuntime: (String) -> Unit,
    private val onChanged: () -> Unit,
) {
    private var pending: AppPermission? = null
    private var watcher: Job? = null

    /** Bumped on every resume so callers can re-read permission states. */
    var version by mutableIntStateOf(0)
        internal set

    fun request(permission: AppPermission) {
        val activity = activity ?: return
        val runtime = permission.runtimePermission
        if (runtime != null) {
            pending = permission
            launchRuntime(runtime)
            return
        }
        val intent = permission.settingsIntent?.invoke(activity) ?: AppPermissions.appDetailsIntent(activity)
        val opened = runCatching {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQUEST_SETTINGS)
        }.isSuccess || runCatching {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(AppPermissions.appDetailsIntent(activity), REQUEST_SETTINGS)
        }.isSuccess
        if (opened) watch(permission)
    }

    private fun watch(permission: AppPermission) {
        val activity = activity ?: return
        watcher?.cancel()
        if (permission.isGranted(activity)) return
        watcher = scope.launch {
            // Poll for up to 3 minutes; return to the app automatically once granted.
            repeat(600) {
                delay(300)
                if (permission.isGranted(activity)) {
                    activity.finishActivity(REQUEST_SETTINGS)
                    runCatching {
                        activity.startActivity(
                            Intent(activity, activity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        )
                    }
                    onChanged()
                    return@launch
                }
            }
        }
    }

    internal fun onRuntimeResult() {
        pending = null
        onChanged()
    }

    internal fun onResume() {
        version++
        onChanged()
    }

    private companion object {
        const val REQUEST_SETTINGS = 4242
    }
}

@Composable
fun rememberPermissionRequester(onChanged: () -> Unit = {}): PermissionRequester {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnChanged by rememberUpdatedState(onChanged)
    val holder = remember { arrayOfNulls<PermissionRequester>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        holder[0]?.onRuntimeResult()
    }
    val requester = remember(activity, context) {
        PermissionRequester(activity, scope, { launcher.launch(it) }) { latestOnChanged() }.also { holder[0] = it }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { requester.onResume() }
    return requester
}
