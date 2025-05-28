package com.stuartb55.octopusagile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed // Changed to itemsIndexed for logging/debugging
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stuartb55.octopusagile.data.EnergyRate
import com.stuartb55.octopusagile.ui.EnergyRatesViewModel
import com.stuartb55.octopusagile.ui.UiState
import com.stuartb55.octopusagile.ui.theme.OctopusEnergyRatesTheme
import com.stuartb55.octopusagile.utils.formatDateAndTimeForDisplay
import com.stuartb55.octopusagile.utils.formatTimeForDisplay
import java.util.Locale
import java.time.OffsetDateTime
import java.time.ZoneOffset


// Color definitions and getRateStyling function remain the same as your previous version
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnergyRatesScreen(viewModel: EnergyRatesViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Octopus Energy Rates") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when (val state = uiState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                        Text("Loading initial rates...", modifier = Modifier.padding(top = 60.dp))
                    }
                }

                is UiState.Success -> {
                    val rates = state.data
                    if (rates.isNotEmpty()) {
                        // Find index of current rate to scroll to it initially (optional)
                        LaunchedEffect(rates) {
                            val now = OffsetDateTime.now(ZoneOffset.UTC)
                            val currentIndex = rates.indexOfFirst { rate ->
                                val from = OffsetDateTime.parse(rate.validFrom)
                                val to = OffsetDateTime.parse(rate.validTo)
                                !from.isAfter(now) && now.isBefore(to)
                            }
                            if (currentIndex != -1) {
                                listState.scrollToItem(currentIndex)
                            } else {
                                // If no current, scroll to nearest future or don't scroll
                                val futureIndex = rates.indexOfFirst {
                                    OffsetDateTime.parse(it.validFrom).isAfter(now)
                                }
                                if (futureIndex != -1) listState.scrollToItem(futureIndex)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                items = rates,
                                key = { _, rate -> rate.validFrom } // Unique and stable key
                            ) { index, rate ->
                                EnergyRateItem(rate = rate)
                                // Log.d("LazyColumn", "Displaying item $index: ${rate.validFrom}")
                            }
                        }

                        // Logic to trigger loading more items
                        val buffer = 7 // Number of items from the end/start to trigger load
                        val loadOlderTrigger by remember {
                            derivedStateOf {
                                val firstVisible =
                                    listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: -1
                                firstVisible != -1 && firstVisible <= buffer
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
                            modifier = Modifier.fillMaxSize(),
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

// CurrentRateDisplay is no longer used as a separate fixed item.
// The EnergyRateItem will be used for all items in the LazyColumn.

@Composable
fun EnergyRateItem(rate: EnergyRate) { // This is now the standard item for all rates
    val (containerColor, contentColor) = getRateStyling(rate.valueIncVat)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
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
        }
    }
}