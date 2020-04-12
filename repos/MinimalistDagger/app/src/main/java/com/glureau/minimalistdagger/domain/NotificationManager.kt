package com.glureau.minimalistdagger.domain

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.lang.Thread.sleep
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class NotificationManager @Inject constructor() {

    private val _notificationCount = MutableLiveData<Int>()
    val notificationCount: LiveData<Int> = _notificationCount

    init {
        clearNotification()
        Thread(Runnable {
            while (true) {
                sleep(500)
                if (Random.nextFloat() > 0.8) {
                    _notificationCount.postValue((_notificationCount.value ?: 0) + 1)
                }
            }
        }).start()
    }

    fun clearNotification() {
        _notificationCount.postValue(0)
    }
}