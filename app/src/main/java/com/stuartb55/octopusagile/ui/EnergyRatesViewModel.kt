package com.stuartb55.octopusagile.ui // Ensure this is the first line

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stuartb55.octopusagile.data.EnergyRate // Make sure this import is correct for your EnergyRate data class
import com.stuartb55.octopusagile.network.RetrofitInstance // Make sure this import is correct for your Retrofit setup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

// Sealed class for UI State
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class EnergyRatesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<EnergyRate>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<EnergyRate>>> = _uiState

    private val TAG = "EnergyRatesViewModel"

    init {
        fetchEnergyRates()
    }

    fun fetchEnergyRates() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val response = RetrofitInstance.api.getStandardUnitRates() // Ensure this path is correct
                if (response.isSuccessful) {
                    response.body()?.results?.let { apiRates ->
                        if (apiRates.isEmpty()) {
                            _uiState.value = UiState.Success(emptyList())
                            return@let
                        }

                        val now = OffsetDateTime.now(ZoneOffset.UTC)

                        fun parseDateTimeSafe(dateTimeString: String): OffsetDateTime? {
                            return try {
                                if (dateTimeString.isBlank()) null else OffsetDateTime.parse(dateTimeString)
                            } catch (e: DateTimeParseException) {
                                Log.e(TAG, "Failed to parse date: $dateTimeString", e)
                                null
                            }
                        }

                        val processableRates = apiRates.mapNotNull { rate ->
                            val validFrom = parseDateTimeSafe(rate.validFrom)
                            val validTo = parseDateTimeSafe(rate.validTo)
                            if (validFrom != null && validTo != null) {
                                Triple(rate, validFrom, validTo)
                            } else {
                                null
                            }
                        }

                        val currentRateTriple = processableRates.find { (_, from, to) ->
                            !from.isAfter(now) && now.isBefore(to) // from <= now && now < to
                        }

                        val finalSortedRates = mutableListOf<EnergyRate>()

                        if (currentRateTriple != null) {
                            finalSortedRates.add(currentRateTriple.first)
                            val futureRates = processableRates
                                .filter { (_, from, _) -> !from.isBefore(currentRateTriple.third) } // from >= currentRate.validTo
                                .filter { it.first != currentRateTriple.first }
                                .sortedBy { it.second } // sort by parsed 'validFrom'
                                .map { it.first }
                            finalSortedRates.addAll(futureRates)
                        } else {
                            val upcomingRates = processableRates
                                .filter { (_, from, _) -> from.isAfter(now) } // from > now
                                .sortedBy { it.second }
                                .map { it.first }
                            finalSortedRates.addAll(upcomingRates)
                        }

                        _uiState.value = UiState.Success(finalSortedRates)

                    } ?: run {
                        _uiState.value = UiState.Error("Empty response body or no results found")
                    }
                } else {
                    _uiState.value = UiState.Error("API Error: ${response.code()} ${response.message()}")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error", e)
                _uiState.value = UiState.Error("Network error: Please check your connection.")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                _uiState.value = UiState.Error("An unexpected error occurred: ${e.message}")
            }
        }
    }
}