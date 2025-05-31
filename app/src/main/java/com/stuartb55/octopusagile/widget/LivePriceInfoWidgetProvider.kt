package com.stuartb55.octopusagile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.stuartb55.octopusagile.MainActivity
import com.stuartb55.octopusagile.R
import com.stuartb55.octopusagile.utils.formatTimeForDisplay
import java.util.Locale
import java.util.concurrent.TimeUnit

class LivePriceInfoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        enqueueOneTimeUpdateWork(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        schedulePeriodicUpdates(context)
        enqueueOneTimeUpdateWork(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    private fun schedulePeriodicUpdates(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<UpdateWidgetWorker>(
            30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }

    private fun enqueueOneTimeUpdateWork(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<UpdateWidgetWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueue(oneTimeWorkRequest)
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "OctopusAgileWidgetPeriodicUpdate"

        private object WidgetRateColors {
            val BLUE_BACKGROUND = Color.rgb(0x19, 0x76, 0xD2)
            val GREEN_BACKGROUND = Color.rgb(0x38, 0x8E, 0x3C)
            val AMBER_BACKGROUND = Color.rgb(0xFF, 0xA0, 0x00)
            val RED_BACKGROUND = Color.rgb(0xD3, 0x2F, 0x2F)
            val LTGRAY_BACKGROUND = Color.LTGRAY

            val WHITE_TEXT = Color.WHITE
            val BLACK_TEXT = Color.BLACK
        }

        private fun getWidgetSectionStyling(rateValue: Double?): Pair<Int, Int> {
            if (rateValue == null) {
                return WidgetRateColors.LTGRAY_BACKGROUND to WidgetRateColors.BLACK_TEXT
            }
            return when {
                rateValue <= 0 -> WidgetRateColors.BLUE_BACKGROUND to WidgetRateColors.WHITE_TEXT
                rateValue <= 15 -> WidgetRateColors.GREEN_BACKGROUND to WidgetRateColors.WHITE_TEXT
                rateValue <= 26 -> WidgetRateColors.AMBER_BACKGROUND to WidgetRateColors.BLACK_TEXT
                else -> WidgetRateColors.RED_BACKGROUND to WidgetRateColors.WHITE_TEXT
            }
        }

        private fun applySectionStyling(
            views: RemoteViews,
            sectionId: Int,
            priceTextViewId: Int,
            timeTextViewId: Int,
            labelTextViewId: Int,
            backgroundColor: Int,
            textColor: Int
        ) {
            views.setInt(sectionId, "setBackgroundColor", backgroundColor)
            views.setTextColor(priceTextViewId, textColor)
            views.setTextColor(timeTextViewId, textColor)
            views.setTextColor(labelTextViewId, textColor)
        }

        internal fun updateWidgetView(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            ratesInfo: WidgetRatesInfo
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_live_price_info)

            val mainActivityIntent = Intent(context, MainActivity::class.java)
            val pendingMainActivityIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                mainActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root_layout, pendingMainActivityIntent)

            // Current Price Section
            val (currentBgColor, currentTextColor) = getWidgetSectionStyling(ratesInfo.currentRate?.valueIncVat)
            applySectionStyling(
                views,
                R.id.section_current_price,
                R.id.tv_current_price_main,
                R.id.tv_current_price_time,
                R.id.tv_current_label,
                currentBgColor,
                currentTextColor
            )
            if (ratesInfo.currentRate != null) {
                views.setTextViewText(
                    R.id.tv_current_price_main,
                    "${String.format(Locale.UK, "%.2f", ratesInfo.currentRate.valueIncVat)} p"
                )
                views.setTextViewText(
                    R.id.tv_current_price_time,
                    "(${formatTimeForDisplay(ratesInfo.currentRate.validFrom)} - ${
                        formatTimeForDisplay(
                            ratesInfo.currentRate.validTo
                        )
                    })"
                )
            } else {
                views.setTextViewText(
                    R.id.tv_current_price_main,
                    if (ratesInfo.errorMessage != null) "Error" else "--"
                )
                views.setTextViewText(
                    R.id.tv_current_price_time,
                    if (ratesInfo.errorMessage != null) "Tap for app" else "Unavailable"
                )
            }

            // Next Price Section
            val (nextBgColor, nextTextColor) = getWidgetSectionStyling(ratesInfo.nextRate?.valueIncVat)
            applySectionStyling(
                views,
                R.id.section_next_price,
                R.id.tv_next_price_main,
                R.id.tv_next_price_time,
                R.id.tv_next_label,
                nextBgColor,
                nextTextColor
            )
            if (ratesInfo.nextRate != null) {
                views.setTextViewText(
                    R.id.tv_next_price_main,
                    "${String.format(Locale.UK, "%.2f", ratesInfo.nextRate.valueIncVat)} p"
                )
                views.setTextViewText(
                    R.id.tv_next_price_time,
                    "(${formatTimeForDisplay(ratesInfo.nextRate.validFrom)} - ${
                        formatTimeForDisplay(
                            ratesInfo.nextRate.validTo
                        )
                    })"
                )
            } else {
                views.setTextViewText(R.id.tv_next_price_main, "--")
                views.setTextViewText(R.id.tv_next_price_time, "Unavailable")
            }

            // Lowest Price Section
            val (lowestBgColor, lowestTextColor) = getWidgetSectionStyling(ratesInfo.lowestRateNext24h?.valueIncVat)
            applySectionStyling(
                views,
                R.id.section_lowest_price,
                R.id.tv_lowest_price_main,
                R.id.tv_lowest_price_time,
                R.id.tv_lowest_label,
                lowestBgColor,
                lowestTextColor
            )
            if (ratesInfo.lowestRateNext24h != null) {
                views.setTextViewText(
                    R.id.tv_lowest_price_main,
                    "${String.format(Locale.UK, "%.2f", ratesInfo.lowestRateNext24h.valueIncVat)} p"
                )
                views.setTextViewText(
                    R.id.tv_lowest_price_time,
                    "(${formatTimeForDisplay(ratesInfo.lowestRateNext24h.validFrom)} - ${
                        formatTimeForDisplay(
                            ratesInfo.lowestRateNext24h.validTo
                        )
                    })"
                )
            } else {
                views.setTextViewText(R.id.tv_lowest_price_main, "--")
                views.setTextViewText(R.id.tv_lowest_price_time, "Unavailable")
            }

            if (ratesInfo.errorMessage != null && ratesInfo.currentRate == null) {
                views.setTextViewText(R.id.tv_current_price_time, ratesInfo.errorMessage)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}