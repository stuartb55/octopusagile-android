package com.stuartb55.octopusagile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke // Added for Card border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

import com.stuartb55.octopusagile.data.EnergyRate
import com.stuartb55.octopusagile.ui.EnergyRatesViewModel
import com.stuartb55.octopusagile.ui.UiState
import com.stuartb55.octopusagile.ui.theme.OctopusEnergyRatesTheme
import com.stuartb55.octopusagile.utils.formatDateAndTimeForDisplay
import com.stuartb55.octopusagile.utils.formatTimeForDisplay


// Color definitions (remains the same)
private val RateColorBlue = Color(0xFF1976D2)
private val RateColorGreen = Color(0xFF388E3C)
private val RateColorAmber = Color(0xFFFFA000)
private val RateColorRed = Color(0xFFD32F2F)

private fun getRateStyling(rateValue: Double): Pair<Color, Color> {
    return when {
        rateValue <= 0 -> RateColorBlue to Color.White
        rateValue <= 15 -> RateColorGreen to Color.White
        rateValue <= 26 -> RateColorAmber to Color.Black
        else -> RateColorRed to Color.White
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel: EnergyRatesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OctopusEnergyRatesTheme {
                EnergyRatesScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun LivePriceSummaryCard(
    currentRate: EnergyRate?,
    nextRate: EnergyRate?,
    lowestRateNext24h: EnergyRate?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Live Price Information",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text("Current Price:", style = MaterialTheme.typography.titleMedium)
            if (currentRate != null) {
                Text(
                    "${String.format(Locale.UK, "%.2f", currentRate.valueIncVat)} p/kWh (until ${
                        formatTimeForDisplay(
                            currentRate.validTo
                        )
                    })",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Text("Data unavailable", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(8.dp))

            Text("Next Price:", style = MaterialTheme.typography.titleMedium)
            if (nextRate != null) {
                Text(
                    "${String.format(Locale.UK, "%.2f", nextRate.valueIncVat)} p/kWh (from ${
                        formatTimeForDisplay(
                            nextRate.validFrom
                        )
                    })",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Text("Data unavailable", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(8.dp))

            Text("Lowest (next 24h):", style = MaterialTheme.typography.titleMedium)
            if (lowestRateNext24h != null) {
                Text(
                    "${
                        String.format(
                            Locale.UK,
                            "%.2f",
                            lowestRateNext24h.valueIncVat
                        )
                    } p/kWh (starts ${
                        formatTimeForDisplay(lowestRateNext24h.validFrom)
                    })",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Text(
                    "Data unavailable or no future rates loaded",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnergyRatesScreen(viewModel: EnergyRatesViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // State for current time, updates periodically to refresh current rate identification
    var currentTime by remember { mutableStateOf(OffsetDateTime.now(ZoneOffset.UTC)) }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(30_000L) // Update every 30 seconds
            currentTime = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Octopus Agile Pricing",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            when (val state = uiState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                        Text("Loading initial rates...", modifier = Modifier.padding(top = 60.dp))
                    }
                }

                is UiState.Success -> {
                    val rates = state.data

                    val (currentActualRate, nextActualRate, lowestRateInNext24h) = remember(
                        rates,
                        currentTime
                    ) {
                        val now = currentTime
                        var current: EnergyRate? = null
                        var next: EnergyRate? = null
                        var lowest24h: EnergyRate? = null

                        if (rates.isNotEmpty()) {
                            val currentRateIndex = rates.indexOfFirst { rate ->
                                try {
                                    val from = OffsetDateTime.parse(rate.validFrom)
                                    val to = OffsetDateTime.parse(rate.validTo)
                                    !now.isBefore(from) && now.isBefore(to)
                                } catch (e: Exception) {
                                    Log.e(
                                        "RateParsing",
                                        "Error parsing for current: ${rate.validFrom}",
                                        e
                                    )
                                    false
                                }
                            }

                            if (currentRateIndex != -1) {
                                current = rates[currentRateIndex]
                                if (currentRateIndex + 1 < rates.size) {
                                    next = rates[currentRateIndex + 1]
                                }
                            }

                            val twentyFourHoursLater = now.plusHours(24)
                            val futureRatesInNext24h = rates.filter { rate ->
                                try {
                                    val from = OffsetDateTime.parse(rate.validFrom)
                                    !from.isBefore(now) && from.isBefore(twentyFourHoursLater)
                                } catch (e: Exception) {
                                    Log.e(
                                        "RateParsing",
                                        "Error parsing for 24h lowest: ${rate.validFrom}",
                                        e
                                    )
                                    false
                                }
                            }
                            lowest24h = futureRatesInNext24h.minByOrNull { it.valueIncVat }
                        }
                        Triple(current, next, lowest24h)
                    }

                    LivePriceSummaryCard(
                        currentRate = currentActualRate,
                        nextRate = nextActualRate,
                        lowestRateNext24h = lowestRateInNext24h
                    )

                    if (rates.isNotEmpty()) {
                        LaunchedEffect(rates, listState) {
                            val nowForScroll = OffsetDateTime.now(ZoneOffset.UTC)
                            val currentIndex = rates.indexOfFirst { rate ->
                                try {
                                    val from = OffsetDateTime.parse(rate.validFrom)
                                    val to = OffsetDateTime.parse(rate.validTo)
                                    !from.isAfter(nowForScroll) && nowForScroll.isBefore(to)
                                } catch (e: Exception) {
                                    false
                                }
                            }
                            if (currentIndex != -1) {
                                listState.scrollToItem(currentIndex)
                            } else {
                                val futureIndex = rates.indexOfFirst {
                                    try {
                                        OffsetDateTime.parse(it.validFrom).isAfter(nowForScroll)
                                    } catch (e: Exception) {
                                        false
                                    }
                                }
                                if (futureIndex != -1) listState.scrollToItem(futureIndex)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                items = rates,
                                key = { _, rate -> rate.validFrom }
                            ) { _, itemRate ->
                                EnergyRateItem(
                                    rate = itemRate,
                                    isCurrent = itemRate == currentActualRate
                                )
                            }
                        }

                        // Pagination logic remains the same
                        val buffer = 7
                        val loadOlderTrigger by remember {
                            derivedStateOf {
                                val firstVisible =
                                    listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: -1
                                firstVisible != -1 && firstVisible <= buffer && listState.layoutInfo.totalItemsCount > 0
                            }
                        }
                        val loadNewerTrigger by remember {
                            derivedStateOf {
                                val lastVisible =
                                    listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                                val totalItems = listState.layoutInfo.totalItemsCount
                                lastVisible != -1 && totalItems > 0 && lastVisible >= totalItems - 1 - buffer
                            }
                        }
                        LaunchedEffect(loadOlderTrigger) {
                            if (loadOlderTrigger) {
                                Log.d("Pagination", "Attempting to load older rates.")
                                viewModel.loadOlderRates()
                            }
                        }
                        LaunchedEffect(loadNewerTrigger) {
                            if (loadNewerTrigger) {
                                Log.d("Pagination", "Attempting to load newer rates.")
                                viewModel.loadNewerRates()
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No energy rates available at the moment. Pull to refresh or check connection.",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EnergyRateItem(rate: EnergyRate, isCurrent: Boolean) {
    val (containerColor, contentColor) = getRateStyling(rate.valueIncVat)

    // Define dynamic styling based on whether the item is current
    val cardElevation = if (isCurrent) 8.dp else 2.dp // Slightly higher elevation for current item
    val borderStroke = if (isCurrent) {
        BorderStroke(4.dp, MaterialTheme.colorScheme.primary) // Highlight border for current item
    } else {
        null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = borderStroke // Apply the conditional border
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Rate: ${String.format(Locale.UK, "%.2f", rate.valueIncVat)} p/kWh",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Valid: ${formatDateAndTimeForDisplay(rate.validFrom)} - ${
                    formatTimeForDisplay(
                        rate.validTo
                    )
                }",
                style = MaterialTheme.typography.bodySmall
            )
            if (isCurrent) {
                Text(
                    "Current Slot",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}