package com.glureau.minimalistdagger.di

import com.glureau.minimalistdagger.ui.dashboard.DashboardFragment
import com.glureau.minimalistdagger.ui.notifications.NotificationsFragment
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component
interface AppComponent {
    // List all the classes where you want to inject fields (not required when injecting via constructor)
    fun inject(f: DashboardFragment)

    fun inject(f: NotificationsFragment)
}