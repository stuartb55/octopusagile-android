package com.stuartb55.octopusagile.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stuartb55.octopusagile.data.EnergyRate
import com.stuartb55.octopusagile.network.EnergyRatesResponse
import com.stuartb55.octopusagile.network.RetrofitInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response // Ensure this is the correct Response import
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.TreeSet

// UiState remains the same
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class EnergyRatesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<EnergyRate>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<EnergyRate>>> = _uiState.asStateFlow()

    private val TAG = "EnergyRatesViewModel"

    // Stores all fetched rates, sorted chronologically (ascending validFrom)
    private val allFetchedRates = TreeSet<EnergyRate>(
        // Primary sort by validFrom (parsed as OffsetDateTime for correctness)
        // Secondary sort by valueIncVat as a tie-breaker if dates were identical or unparseable
        compareBy<EnergyRate> {
            try {
                OffsetDateTime.parse(it.validFrom)
            } catch (e: DateTimeParseException) {
                Log.w(TAG, "Could not parse validFrom for sorting: ${it.validFrom}", e)
                null // Treat unparseable dates to sort them consistently (e.g., at the end or beginning)
            }
        }.thenBy { it.valueIncVat }
    )

    private var urlToFetchOlder: String? = null // API's 'next' URL
    private var urlToFetchNewer: String? = null // API's 'previous' URL

    @Volatile
    private var isLoadingOlder = false
    @Volatile
    private var isLoadingNewer = false

    init {
        initialLoad()
    }

    fun initialLoad() {
        if (_uiState.value is UiState.Loading && allFetchedRates.isNotEmpty()) { // Fix: prevent reload if already loaded by checking allFetchedRates
            Log.d(TAG, "Initial load skipped: already have data or currently loading.")
            _uiState.value = UiState.Success(allFetchedRates.toList()) // Emit current data if any
            return
        }
        if (_uiState.value is UiState.Loading && allFetchedRates.isEmpty()) { // If stuck in loading without data
            Log.d(TAG, "Initial load: was stuck in loading, retrying.")
        } else if (_uiState.value is UiState.Loading) { // General loading state
            Log.d(TAG, "Initial load skipped: already loading.")
            return
        }

        Log.d(TAG, "Initial load triggered")
        fetchRates(
            fetchUrl = null, // Let fetchRates use the default endpoint
            isInitialLoad = true
        )
    }

    fun loadOlderRates() {
        if (isLoadingOlder || urlToFetchOlder == null) {
            Log.d(TAG, "Load Older: Skipped (loading: $isLoadingOlder, url: $urlToFetchOlder)")
            return
        }
        Log.d(TAG, "Loading older rates from: $urlToFetchOlder")
        fetchRates(urlToFetchOlder!!, isLoadingOlderData = true)
    }

    fun loadNewerRates() {
        if (isLoadingNewer || urlToFetchNewer == null) {
            Log.d(TAG, "Load Newer: Skipped (loading: $isLoadingNewer, url: $urlToFetchNewer)")
            return
        }
        Log.d(TAG, "Loading newer rates from: $urlToFetchNewer")
        fetchRates(urlToFetchNewer!!, isLoadingNewerData = true)
    }

    private fun fetchRates(
        fetchUrl: String?,
        isInitialLoad: Boolean = false,
        isLoadingOlderData: Boolean = false,
        isLoadingNewerData: Boolean = false
    ) {
        viewModelScope.launch {
            if (isInitialLoad) {
                _uiState.value = UiState.Loading
            } else if (isLoadingOlderData) {
                isLoadingOlder = true
            } else if (isLoadingNewerData) {
                isLoadingNewer = true
            }

            try {
                // Simplified response assignment using if/else
                val response: Response<EnergyRatesResponse>
                if (fetchUrl != null) {
                    Log.d(TAG, "Fetching by full URL: $fetchUrl")
                    response = RetrofitInstance.api.getRatesByFullUrl(fetchUrl)
                } else {
                    Log.d(TAG, "Fetching by default endpoint (initial load)")
                    response = RetrofitInstance.api.getStandardUnitRates(params = emptyMap())
                }

                if (response.isSuccessful) {
                    response.body()?.let { ratesResponse ->
                        Log.d(
                            TAG,
                            "Fetched ${ratesResponse.results.size} rates. Next: ${ratesResponse.next}, Prev: ${ratesResponse.previous}"
                        )
                        val newCount = allFetchedRates.size + ratesResponse.results.count {
                            allFetchedRates.add(it)
                        }
                        Log.d(
                            TAG,
                            "Added ${newCount - (allFetchedRates.size - ratesResponse.results.size)} new unique rates. Total unique: ${allFetchedRates.size}"
                        )


                        if (isInitialLoad) {
                            urlToFetchOlder = ratesResponse.next
                            urlToFetchNewer = ratesResponse.previous
                        } else if (isLoadingOlderData) {
                            // Only update urlToFetchOlder if the response for older data provides a 'next' for even older data
                            urlToFetchOlder = ratesResponse.next
                        } else if (isLoadingNewerData) {
                            // Only update urlToFetchNewer if the response for newer data provides a 'previous' for even newer data
                            urlToFetchNewer = ratesResponse.previous
                        }

                        _uiState.value =
                            UiState.Success(allFetchedRates.toList()) // TreeSet ensures it's sorted

                    } ?: run {
                        Log.e(TAG, "Empty response body for URL: $fetchUrl")
                        if (isInitialLoad) _uiState.value =
                            UiState.Error("Empty response from server.")
                        // For paginated loads, you might want a less disruptive error, or just log it.
                    }
                } else {
                    Log.e(
                        TAG,
                        "API Error: ${response.code()} ${response.message()} for URL: $fetchUrl"
                    )
                    if (isInitialLoad) _uiState.value =
                        UiState.Error("API Error: ${response.code()}")
                    // Handle errors for paginated loads (e.g., show a toast, log, or ignore if minor)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error for URL: $fetchUrl", e)
                if (isInitialLoad) _uiState.value =
                    UiState.Error("Network error. Please check connection.")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error for URL: $fetchUrl", e)
                if (isInitialLoad) _uiState.value =
                    UiState.Error("An unexpected error occurred: ${e.message}")
            } finally {
                if (isLoadingOlderData) isLoadingOlder = false
                if (isLoadingNewerData) isLoadingNewer = false
                Log.d(
                    TAG,
                    "Fetch finished. Total rates: ${allFetchedRates.size}, NextOlderURL: $urlToFetchOlder, NextNewerURL: $urlToFetchNewer"
                )
            }
        }
    }
}