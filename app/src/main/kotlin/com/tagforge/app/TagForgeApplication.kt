package com.tagforge.app

import android.app.Application

class TagForgeApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
