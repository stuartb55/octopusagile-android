package com.stuartb55.octopusagile.network

import com.google.gson.annotations.SerializedName
import com.stuartb55.octopusagile.data.EnergyRate

data class EnergyRatesResponse(
    @SerializedName("count") val count: Int,
    @SerializedName("next") val next: String?,
    @SerializedName("previous") val previous: String?,
    @SerializedName("results") val results: List<EnergyRate>
)