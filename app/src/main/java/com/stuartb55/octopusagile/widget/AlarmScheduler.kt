package com.stuartb55.octopusagile.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object AlarmScheduler {

    private const val ALARM_REQUEST_CODE = 1001
    private const val TAG = "AlarmScheduler"

    fun scheduleNextAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            // Calculate next 00 or 30 minute mark
            val currentMinutes = get(Calendar.MINUTE)

            if (currentMinutes < 30) {
                set(Calendar.MINUTE, 30)
            } else {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
            }
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (System.currentTimeMillis() > timeInMillis) {
                if (get(Calendar.MINUTE) == 0) { // Was 00, now try 30
                    set(Calendar.MINUTE, 30)
                } else { // Was 30, now try next hour 00
                    add(Calendar.HOUR_OF_DAY, 1)
                    set(Calendar.MINUTE, 0)
                }
            }
        }

        val pendingIntent = getPendingIntent(context)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w(
                    TAG,
                    "Cannot schedule exact alarms. Consider requesting permission or using inexact alarms."
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
            Log.d(TAG, "Alarm scheduled for: ${calendar.time}")

        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException: Cannot schedule exact alarm. Check permissions.", se)
        }
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = getPendingIntent(context)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel() // Also cancel the PendingIntent itself
        Log.d(TAG, "Alarm cancelled.")
    }

    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}