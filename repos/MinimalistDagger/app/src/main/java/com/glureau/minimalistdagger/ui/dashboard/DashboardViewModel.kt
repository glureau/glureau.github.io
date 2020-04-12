package com.glureau.minimalistdagger.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.glureau.minimalistdagger.domain.NotificationManager
import javax.inject.Inject

// Unscoped ViewModel, a new one will be created when view is created.
class DashboardViewModel @Inject constructor(notificationManager: NotificationManager) {

    val text: LiveData<String> = MediatorLiveData<String>().apply {
        addSource(notificationManager.notificationCount) { count ->
            value = "This is dashboard Fragment\nnotif=$count\ninstance=$this"
        }
    }
}