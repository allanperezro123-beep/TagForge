package com.tagforge.app.feature.taskrunner

import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tagforge.app.MainActivity
import com.tagforge.app.TagForgeApplication
import com.tagforge.app.ui.theme.TagForgeTheme
import com.tagforge.domain.common.Bytes
import com.tagforge.domain.settings.AppSettings
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Entry point of a scanned task tag (TK-05). A translucent, singleTop Activity: the system starts it for an
 * `application/x-tf` tag from the home screen or any other app, `onNewIntent` replaces a pending task, and it shows
 * the confirmation sheet over whatever was underneath. It never shows over the lock screen. A task runs without the sheet only when the user chose that in Settings (see TaskTrust).
 *
 * Other entries: the Read tab hands it the bytes of a task it just read ([EXTRA_NDEF_BYTES]) and the task list asks
 * to run a saved task ([EXTRA_TASK_ID]); both end up on the same confirmation sheet. They go through the NON-exported
 * [InternalTaskRunnerActivity]: this exported entry ignores those extras, because any app can send them (W5).
 */
open class TaskRunnerActivity : ComponentActivity() {

    /** True only for [InternalTaskRunnerActivity], which no other app can start (W5). */
    protected open val trustedEntry: Boolean = false

    /** True between onStart and onStop: HttpRequest only runs while the runner is on screen (TK-08). */
    private val onScreen = AtomicBoolean(false)

    private val viewModel: TaskRunnerViewModel by viewModels {
        val container = (application as TagForgeApplication).container
        val flag = onScreen
        viewModelFactory {
            initializer {
                TaskRunnerViewModel(container.taskExecutor, container.taskRepository, container.clock, container.settingsStore, isForeground = { flag.get() })
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After a configuration change the ViewModel still holds the pending task; after process death it is empty.
        if (viewModel.uiState.value == TaskRunnerUiState.Idle) handle(intent)

        val container = (application as TagForgeApplication).container
        setContent {
            val settings by container.settingsStore.settings.collectAsStateWithLifecycle(AppSettings())
            TagForgeTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(viewModel) {
                    viewModel.events.collect { event -> if (event == RunnerEvent.Close) finish() }
                }
                TaskConfirmationSheet(
                    state = state,
                    onRun = viewModel::onRun,
                    onCancel = viewModel::onCancel,
                    onClose = viewModel::onClose,
                    onFix = ::openPermissionCenter,
                    onUntrust = viewModel::onUntrust,
                    onAskAlways = viewModel::onAskAlways,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        onScreen.set(true)
    }

    override fun onStop() {
        onScreen.set(false)
        super.onStop()
    }

    private fun handle(intent: Intent) {
        val systemTag = IntentCompat.getParcelableArrayExtra(intent, NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
            ?.firstOrNull()
            ?.let { (it as? NdefMessage)?.toByteArray() }
        val request = TaskRunnerEntry.resolve(
            trusted = trustedEntry,
            action = intent.action,
            taskId = intent.getLongExtra(EXTRA_TASK_ID, NO_TASK),
            extraBytes = intent.getByteArrayExtra(EXTRA_NDEF_BYTES),
            systemTagBytes = systemTag,
        )
        when (request) {
            is EntryRequest.SavedTask -> viewModel.onTaskId(request.id)
            is EntryRequest.Ndef -> viewModel.onNdefBytes(request.bytes?.let(Bytes::copyOf) ?: Bytes.EMPTY)
        }
    }

    private fun openPermissionCenter() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PERMISSION_CENTER, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    companion object {
        const val EXTRA_TASK_ID = "com.tagforge.app.extra.TASK_ID"
        const val EXTRA_NDEF_BYTES = "com.tagforge.app.extra.NDEF_BYTES"
        private const val NO_TASK = TaskRunnerEntry.NO_TASK

        /** Run a saved task, still behind the confirmation sheet. */
        fun forSavedTask(context: Context, taskId: Long): Intent =
            Intent(context, InternalTaskRunnerActivity::class.java).putExtra(EXTRA_TASK_ID, taskId)

        /** Run a task the Read tab just read from a tag: [ndef] is the raw NDEF message. */
        fun forTagMessage(context: Context, ndef: ByteArray): Intent =
            Intent(context, InternalTaskRunnerActivity::class.java).putExtra(EXTRA_NDEF_BYTES, ndef)
    }
}
