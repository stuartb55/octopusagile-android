package com.stuartb55.octopusagile.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class UpdateWidgetWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val componentName = ComponentName(appContext, LivePriceInfoWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        if (appWidgetIds.isEmpty()) {
            return Result.success()
        }

        val ratesInfo = WidgetDataHelper.fetchAndProcessRates()

        appWidgetIds.forEach { appWidgetId ->
            LivePriceInfoWidgetProvider.updateWidgetView(
                appContext,
                appWidgetManager,
                appWidgetId,
                ratesInfo
            )
        }

        return Result.success()
    }
}