package com.stuartb55.octopusagile.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed. Scheduling widget alarm.")
            AlarmScheduler.scheduleNextAlarm(context.applicationContext)
        }
    }
}