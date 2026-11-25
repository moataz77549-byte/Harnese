package com.jarves.mh.ui

import android.app.Application
import android.os.Looper

/** One runtime per Android process. No Activity, Compose or ViewModel is retained. */
object HarneseRuntimeOwner {
    private var controller: HarneseRuntimeController? = null
    fun get(application: Application): HarneseRuntimeController {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Initialize runtime on the main thread" }
        return controller ?: HarneseRuntimeController(application).also { controller = it }
    }
}
