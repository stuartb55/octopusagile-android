package com.stuartb55.octopusagile.widget

import android.util.Log
import com.stuartb55.octopusagile.data.EnergyRate
import com.stuartb55.octopusagile.network.RetrofitInstance
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
        try {
            val response = RetrofitInstance.api.getStandardUnitRates(params = emptyMap())

            if (!response.isSuccessful) {
                Log.e(TAG, "API Error: ${response.code()} ${response.message()}")
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
                    Log.e(TAG, "Error parsing rate times for active check: ${rate.validFrom}", e)
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
                    if (firstUpcomingRateIndex > 0) {
                        determinedCurrentRate = sortedRates[firstUpcomingRateIndex - 1]
                    } else {
                        determinedCurrentRate = null
                    }
                } else {
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
            } catch (e: ClassCastException) {
                Log.e(TAG, "Type error during minByOrNull for lowest rate.", e)
                null
            }


            return WidgetRatesInfo(determinedCurrentRate, determinedNextRate, lowestRateInNext24h)

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in fetchAndProcessRates", e)
            return WidgetRatesInfo(null, null, null, "Error: ${e.message ?: "Unknown error"}")
        }
    }
}