package com.glureau.minimalistdagger.ui.notifications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.glureau.minimalistdagger.domain.NotificationManager
import javax.inject.Inject

class NotificationsViewModel @Inject constructor(private val notificationManager: NotificationManager) {

    val count: Int
        get() = notificationManager.notificationCount.value ?: 0

    val text: LiveData<String> = MediatorLiveData<String>().apply {
        addSource(notificationManager.notificationCount) { count ->
            value =
                "This is notifications Fragment\nnotif=$count\ninstance VM =${this@NotificationsViewModel.hashCode()}"
        }
    }

    fun clearNotification() {
        notificationManager.clearNotification()
    }
}