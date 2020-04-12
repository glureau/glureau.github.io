package com.glureau.minimalistdagger

import android.app.Application
import com.glureau.minimalistdagger.di.DaggerAppComponent

class MyApplication : Application() {
    val component = DaggerAppComponent.create()
}