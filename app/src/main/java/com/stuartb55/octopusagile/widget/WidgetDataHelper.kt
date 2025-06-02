package com.stuartb55.octopusagile.widget

import android.util.Log
import com.stuartb55.octopusagile.data.EnergyRate
import com.stuartb55.octopusagile.network.RetrofitInstance
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class WidgetRatesInfo(
    val currentRate: EnergyRate?,
    val nextRate: EnergyRate?,
    val lowestRateNext24h: EnergyRate?,
    val errorMessage: String? = null
)

object WidgetDataHelper {

    private const val TAG = "WidgetDataHelper"

    suspend fun fetchAndProcessRates(): WidgetRatesInfo {
        val maxRetries = 3
        var currentRetry = 0
        var lastException: Exception? = null

        while (currentRetry < maxRetries) {
            try {
                Log.d(TAG, "Attempting to fetch rates, try ${currentRetry + 1}")
                val response = RetrofitInstance.api.getStandardUnitRates(params = emptyMap())

                if (!response.isSuccessful) {
                    Log.e(TAG, "API Error: ${response.code()} ${response.message()}")
                    // For server-side errors (5xx), retrying might help.
                    if (response.code() >= 500) {
                        lastException = IOException("API Server Error: ${response.code()}")
                        currentRetry++
                        delay(1500L * currentRetry) // Basic backoff
                        continue
                    }
                    return WidgetRatesInfo(null, null, null, "API Error: ${response.code()}")
                }

                val ratesResponse = response.body()
                if (ratesResponse == null || ratesResponse.results.isEmpty()) {
                    Log.e(TAG, "Empty response body or no rates results.")
                    return WidgetRatesInfo(null, null, null, "No data available.")
                }

                val sortedRates: List<EnergyRate> = try {
                    ratesResponse.results.sortedBy { OffsetDateTime.parse(it.validFrom) }
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Error parsing dates for sorting, using unsorted results as fallback.",
                        e
                    )
                    ratesResponse.results
                }

                val now = OffsetDateTime.now(ZoneOffset.UTC)
                var determinedCurrentRate: EnergyRate? = null
                var determinedNextRate: EnergyRate? = null

                val activeRateIndex = sortedRates.indexOfFirst { rate ->
                    try {
                        val from = OffsetDateTime.parse(rate.validFrom)
                        val to = OffsetDateTime.parse(rate.validTo)
                        !now.isBefore(from) && now.isBefore(to) // now >= from && now < to
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Error parsing rate times for active check: ${rate.validFrom}",
                            e
                        )
                        false
                    }
                }

                if (activeRateIndex != -1) {
                    determinedCurrentRate = sortedRates[activeRateIndex]
                    if (activeRateIndex + 1 < sortedRates.size) {
                        determinedNextRate = sortedRates[activeRateIndex + 1]
                    }
                } else {
                    val firstUpcomingRateIndex = sortedRates.indexOfFirst { rate ->
                        try {
                            !OffsetDateTime.parse(rate.validFrom).isBefore(now) // from >= now
                        } catch (e: Exception) {
                            Log.e(
                                TAG,
                                "Error parsing rate times for upcoming check: ${rate.validFrom}",
                                e
                            )
                            false
                        }
                    }

                    if (firstUpcomingRateIndex != -1) {
                        determinedNextRate = sortedRates[firstUpcomingRateIndex]
                        // If there's an upcoming rate, the current rate is the one just before it,
                        // or null if the upcoming rate is the very first one in the list.
                        determinedCurrentRate = if (firstUpcomingRateIndex > 0) {
                            sortedRates[firstUpcomingRateIndex - 1]
                        } else {
                            // Fallback: if no rate is strictly "current" and the first rate is in the future,
                            // we might consider the last known rate as "current" if the list isn't empty.
                            // Or, stick to null if no rate truly covers 'now'. For simplicity, let's keep it potentially null.
                            null
                        }
                    } else {
                        // All rates are in the past
                        determinedCurrentRate = sortedRates.lastOrNull()
                        determinedNextRate = null
                    }
                }

                var lowestRateInNext24h: EnergyRate? = null
                val twentyFourHoursLater = now.plusHours(24)

                val relevantRatesForLowest = sortedRates.filter { rate ->
                    try {
                        val from = OffsetDateTime.parse(rate.validFrom)
                        val to = OffsetDateTime.parse(rate.validTo)
                        // Rate should start before 24h from now AND end after now
                        from.isBefore(twentyFourHoursLater) && to.isAfter(now)
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Error parsing rate times for lowest rate check: ${rate.validFrom}",
                            e
                        )
                        false
                    }
                }

                lowestRateInNext24h = try {
                    relevantRatesForLowest.minByOrNull { it.valueIncVat }
                } catch (e: ClassCastException) { // Should not happen with Double
                    Log.e(TAG, "Type error during minByOrNull for lowest rate.", e)
                    null
                }

                Log.d(TAG, "Successfully fetched and processed rates.")
                return WidgetRatesInfo(
                    determinedCurrentRate,
                    determinedNextRate,
                    lowestRateInNext24h
                )

            } catch (e: UnknownHostException) {
                lastException = e
                Log.w(
                    TAG,
                    "Retry ${currentRetry + 1}/$maxRetries: Failed to resolve host 'api.octopus.energy'",
                    e
                )
            } catch (e: ConnectException) {
                lastException = e
                Log.w(TAG, "Retry ${currentRetry + 1}/$maxRetries: Connection exception", e)
            } catch (e: SocketTimeoutException) {
                lastException = e
                Log.w(TAG, "Retry ${currentRetry + 1}/$maxRetries: Socket timeout exception", e)
            } catch (e: IOException) { // Catch other IOExceptions that might benefit from a retry
                lastException = e
                Log.w(TAG, "Retry ${currentRetry + 1}/$maxRetries: IOException during fetch", e)
            } catch (e: Exception) { // Catch-all for other unexpected errors
                Log.e(TAG, "Unexpected error in fetchAndProcessRates", e)
                return WidgetRatesInfo(null, null, null, "Error: ${e.message ?: "Unknown error"}")
            }

            currentRetry++
            if (currentRetry < maxRetries) {
                delay(2000L * currentRetry) // Exponential backoff: 2s, 4s
            }
        }

        // If all retries fail
        val finalErrorMessage = lastException?.message ?: "Failed after $maxRetries retries"
        Log.e(TAG, "All retries failed. Last error: $finalErrorMessage", lastException)
        return WidgetRatesInfo(null, null, null, "Error: $finalErrorMessage")
    }
}