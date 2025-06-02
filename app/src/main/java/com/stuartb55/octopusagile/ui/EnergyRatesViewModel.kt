package com.stuartb55.octopusagile.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stuartb55.octopusagile.data.EnergyRate
import com.stuartb55.octopusagile.network.EnergyRatesResponse
import com.stuartb55.octopusagile.network.RetrofitInstance
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.TreeSet

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class EnergyRatesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<EnergyRate>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<EnergyRate>>> = _uiState.asStateFlow()

    private val TAG = "EnergyRatesViewModel"

    // Comparator ensures rates are sorted by validFrom, then by valueIncVat as a tie-breaker
    private val allFetchedRates = TreeSet<EnergyRate>(
        compareBy<EnergyRate> {
            try {
                OffsetDateTime.parse(it.validFrom)
            } catch (e: DateTimeParseException) {
                Log.w(
                    TAG,
                    "Could not parse validFrom for sorting: ${it.validFrom}. Treating as MAX.",
                    e
                )
                OffsetDateTime.MAX // Consistently place unparseable items at the end
            }
        }.thenBy { it.valueIncVat }
    )

    private var urlToFetchOlder: String? = null
    private var urlToFetchNewer: String? = null

    @Volatile
    private var isLoadingOlder = false

    @Volatile
    private var isLoadingNewer = false

    init {
        initialLoad()
    }

    fun initialLoad() {
        // Avoid multiple initial loads if already loading or data present
        if ((_uiState.value is UiState.Loading && allFetchedRates.isEmpty()) || _uiState.value is UiState.Error) {
            Log.d(TAG, "Initial load triggered or retrying from error.")
        } else if (_uiState.value is UiState.Loading) {
            Log.d(TAG, "Initial load skipped: already loading.")
            return
        } else if (allFetchedRates.isNotEmpty()) {
            Log.d(TAG, "Initial load skipped: data already present.")
            _uiState.value = UiState.Success(allFetchedRates.toList()) // Ensure UI has current data
            return
        }

        fetchRates(
            fetchUrl = null,
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
            if (isInitialLoad && _uiState.value !is UiState.Loading) { // Set loading only if not already
                _uiState.value = UiState.Loading
            }
            if (isLoadingOlderData) isLoadingOlder = true
            if (isLoadingNewerData) isLoadingNewer = true

            val maxRetries = 3
            var currentRetry = 0
            var lastException: Exception? = null

            while (currentRetry < maxRetries) {
                try {
                    val response: Response<EnergyRatesResponse>
                    val urlBeingFetched = fetchUrl ?: "default_standard_rates_endpoint"
                    Log.d(TAG, "Attempting fetch for $urlBeingFetched, try ${currentRetry + 1}")

                    if (fetchUrl != null) {
                        response = RetrofitInstance.api.getRatesByFullUrl(fetchUrl)
                    } else {
                        response = RetrofitInstance.api.getStandardUnitRates(params = emptyMap())
                    }

                    if (response.isSuccessful) {
                        response.body()?.let { ratesResponse ->
                            Log.d(
                                TAG,
                                "Fetched ${ratesResponse.results.size} rates from $urlBeingFetched. Next: ${ratesResponse.next}, Prev: ${ratesResponse.previous}"
                            )

                            val previousSize = allFetchedRates.size
                            ratesResponse.results.forEach { allFetchedRates.add(it) }
                            val newRatesAdded = allFetchedRates.size - previousSize
                            Log.d(
                                TAG,
                                "Added $newRatesAdded new unique rates. Total unique: ${allFetchedRates.size}"
                            )

                            if (isInitialLoad || (fetchUrl == null && !isLoadingOlderData && !isLoadingNewerData)) { // Covers initial load and manual refresh like pull-to-refresh
                                urlToFetchOlder = ratesResponse.next
                                urlToFetchNewer = ratesResponse.previous
                            } else if (isLoadingOlderData) {
                                urlToFetchOlder =
                                    ratesResponse.next // API's "next" is older for pagination
                            } else if (isLoadingNewerData) {
                                urlToFetchNewer =
                                    ratesResponse.previous // API's "previous" is newer
                            }

                            if (allFetchedRates.isEmpty()) {
                                _uiState.value = UiState.Error("No rates data found.")
                            } else {
                                _uiState.value = UiState.Success(allFetchedRates.toList())
                            }
                            lastException = null
                        } ?: run {
                            Log.e(TAG, "Empty response body for URL: $urlBeingFetched")
                            lastException = IOException("Empty response body from server")
                            if (isInitialLoad && allFetchedRates.isEmpty()) _uiState.value =
                                UiState.Error(
                                    lastException!!.message ?: "Empty response from server."
                                )
                        }
                    } else {
                        Log.e(
                            TAG,
                            "API Error: ${response.code()} ${response.message()} for URL: $urlBeingFetched"
                        )
                        lastException = IOException("API Error: ${response.code()}")
                        if (response.code() < 500 && response.code() != 408 && response.code() != 429) { // Don't retry for most 4xx client errors
                            if (isInitialLoad && allFetchedRates.isEmpty()) _uiState.value =
                                UiState.Error(lastException!!.message ?: "API Error")
                            break // Exit retry loop for non-retryable HTTP errors
                        }
                    }
                } catch (e: UnknownHostException) {
                    lastException = e
                    Log.w(
                        TAG,
                        "Retry ${currentRetry + 1}/$maxRetries: Failed to resolve host for URL: $fetchUrl",
                        e
                    )
                } catch (e: ConnectException) {
                    lastException = e
                    Log.w(
                        TAG,
                        "Retry ${currentRetry + 1}/$maxRetries: Connection exception for URL: $fetchUrl",
                        e
                    )
                } catch (e: SocketTimeoutException) {
                    lastException = e
                    Log.w(
                        TAG,
                        "Retry ${currentRetry + 1}/$maxRetries: Socket timeout for URL: $fetchUrl",
                        e
                    )
                } catch (e: IOException) {
                    lastException = e
                    Log.e(
                        TAG,
                        "Retry ${currentRetry + 1}/$maxRetries: Network IO error for URL: $fetchUrl",
                        e
                    )
                } catch (e: Exception) {
                    lastException = e
                    Log.e(
                        TAG,
                        "Retry ${currentRetry + 1}/$maxRetries: Unexpected error for URL: $fetchUrl",
                        e
                    )
                    if (isInitialLoad && allFetchedRates.isEmpty()) _uiState.value =
                        UiState.Error("An unexpected error occurred: ${e.message}")
                    break // Don't retry for other generic exceptions
                }

                currentRetry++
                if (currentRetry < maxRetries) {
                    delay(2000L * currentRetry) // Exponential backoff: 2s, 4s
                }
            }

            if (lastException != null && (isInitialLoad || allFetchedRates.isEmpty())) {
                // If all retries failed and it's an initial load or we still have no data, set error state
                val errorMessage =
                    "Failed to fetch data: ${lastException!!.message ?: "Unknown error after retries"}"
                Log.e(TAG, "All retries failed for $fetchUrl. Setting error state: $errorMessage")
                _uiState.value = UiState.Error(errorMessage)
            } else lastException?.let {
                Log.e(
                    TAG,
                    "All retries failed for $fetchUrl, but existing data is present. Last error: ${it.message}"
                )
            }


            if (isLoadingOlderData) isLoadingOlder = false
            if (isLoadingNewerData) isLoadingNewer = false

            val fetchType = when {
                isInitialLoad -> "Initial"
                isLoadingOlderData -> "Older"
                isLoadingNewerData -> "Newer"
                else -> "Default/Refresh"
            }
            Log.d(
                TAG,
                "$fetchType fetch finished. Total rates: ${allFetchedRates.size}, NextOlderURL: $urlToFetchOlder, NextNewerURL: $urlToFetchNewer, isLoadingOlder: $isLoadingOlder, isLoadingNewer: $isLoadingNewer"
            )
        }
    }
}