package com.kaushik.railway

import android.app.Application
import com.kaushik.railway.data.StationDirectory
import com.kaushik.railway.data.work.WatchCheckWorker
import com.kaushik.railway.util.CrashLog
import com.razorpay.Checkout

class RailApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashLog.install(this)
        runCatching { Checkout.preload(applicationContext) }
        // Warm the offline station directory off the main thread so station
        // suggestions are instant from the first keystroke.
        Thread { StationDirectory.load(this) }.start()
        // Train Watch: keep the periodic background check scheduled.
        runCatching { WatchCheckWorker.enqueue(this) }
    }

    companion object {
        lateinit var instance: RailApp
            private set
    }
}
