package com.electrokichel.app

import android.app.Application
import com.electrokichel.app.core.data.Repo
import com.electrokichel.app.core.usb.UsbHub

class ElectroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UsbHub.init(this)
        runCatching { Repo.ensure(this) }
    }
}
