package com.stuartb55.octopusagile

import android.os.Bundle
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

// Define colours for rate levels
private val RateColorBlue = Color(0xFF1976D2) // Material Blue 700
private val RateColorGreen = Color(0xFF388E3C) // Material Green 700
private val RateColorAmber = Color(0xFFFFA000) // Material Amber 700
private val RateColorRed = Color(0xFFD32F2F)   // Material Red 700

// Helper function to determine card container and content colors based on rate
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
                    }
                }

                is UiState.Success -> {
                    val rates = state.data
                    if (rates.isNotEmpty()) {
                        val currentRate = rates.first()
                        val futureRates = rates.drop(1).take(48)

                        Column(modifier = Modifier.fillMaxSize()) {
                            CurrentRateDisplay(
                                rate = currentRate,
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                            )
                            if (futureRates.isNotEmpty()) {
                                EnergyRateList(rates = futureRates)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No further upcoming rates available for the next 24 hours.")
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No energy rates available at the moment.",
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
fun CurrentRateDisplay(rate: EnergyRate, modifier: Modifier = Modifier) {
    val (containerColor, contentColor) = getRateStyling(rate.valueIncVat) // Get dynamic colors

    Card(
        modifier = modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Current Rate: ${
                    String.format(
                        Locale.UK,
                        "%.2f",
                        rate.valueIncVat
                    )
                } p/kWh",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Valid until: ${formatTimeForDisplay(rate.validTo)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun EnergyRateList(rates: List<EnergyRate>) {
    if (rates.isEmpty()) {
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rates) { rate ->
            EnergyRateItem(rate = rate)
        }
    }
}

@Composable
fun EnergyRateItem(rate: EnergyRate) {
    val (containerColor, contentColor) = getRateStyling(rate.valueIncVat) // Get dynamic colors

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors( // Apply dynamic colors
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Rate: ${String.format(Locale.UK, "%.2f", rate.valueIncVat)} p/kWh",
                style = MaterialTheme.typography.titleMedium
                // Text color will be inherited from Card's contentColor
            )
            Text(
                text = "Valid: ${formatDateAndTimeForDisplay(rate.validFrom)} - ${
                    formatTimeForDisplay(
                        rate.validTo
                    )
                }",
                style = MaterialTheme.typography.bodySmall
                // Text color will be inherited from Card's contentColor
            )
        }
    }
}