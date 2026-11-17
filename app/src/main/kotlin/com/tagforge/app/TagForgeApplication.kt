package com.tagforge.app

import android.app.Application
import com.tagforge.app.infrastructure.android.ExportFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TagForgeApplication : Application() {
    private var createdContainer: AppContainer? = null
    val container: AppContainer by lazy { AppContainer(this).also { createdContainer = it } }

    override fun onCreate() {
        super.onCreate()
        sweepStaleExports()
    }

    override fun onTerminate() {
        createdContainer?.sessionCredentials?.forgetAll()
        super.onTerminate()
    }

    /**
     * S9: a dump or QR picture shared in an earlier run may still sit in the cache (the share sheet only cleans up on the
     * NEXT share). Remove what is stale now, off the main thread, and never let a failure reach app start.
     * Deliberately independent of [container] so it does not force the object graph to be built.
     */
    private fun sweepStaleExports() {
        val cache = cacheDir
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            ExportFiles.sweepQuietly(ExportFiles.folder(cache), System.currentTimeMillis(), ExportFiles.STARTUP_MAX_AGE_MILLIS)
        }
    }
}
