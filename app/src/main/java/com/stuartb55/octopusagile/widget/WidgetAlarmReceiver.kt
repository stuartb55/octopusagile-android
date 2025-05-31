package com.stuartb55.octopusagile.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class WidgetAlarmReceiver : BroadcastReceiver() {

    private val TAG = "WidgetAlarmReceiver"

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "Alarm received. Enqueuing widget update work.")

        // Enqueue the work to update the widget
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val updateWorkRequest = OneTimeWorkRequestBuilder<UpdateWidgetWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(updateWorkRequest)

        // Schedule the next alarm
        AlarmScheduler.scheduleNextAlarm(context.applicationContext)
    }
}