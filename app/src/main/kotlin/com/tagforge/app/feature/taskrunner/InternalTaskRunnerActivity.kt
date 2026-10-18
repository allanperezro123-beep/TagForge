package com.tagforge.app.feature.taskrunner

/**
 * The runner as started by this app itself (Read tab, saved tasks). Declared with exported=false in the manifest, so no
 * other app can reach it and the saved-task id and raw NDEF bytes it receives can be believed (W5).
 */
class InternalTaskRunnerActivity : TaskRunnerActivity() {
    override val trustedEntry: Boolean = true
}
