package com.alvault.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class AlVaultApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Auto-lock: ProcessLifecycleOwner melaporkan "seluruh aplikasi" tidak terlihat / terlihat kembali.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.session.onAppForegrounded()
            }

            override fun onStop(owner: LifecycleOwner) {
                container.session.onAppBackgrounded()
            }
        })
    }
}
