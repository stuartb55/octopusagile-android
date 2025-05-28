package com.stuartb55.octopusagile.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EnergyRatesResponse(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<EnergyRate>
)

@JsonClass(generateAdapter = true)
data class EnergyRate(
    @Json(name = "value_exc_vat")
    val valueExcVat: Double,
    @Json(name = "value_inc_vat")
    val valueIncVat: Double,
    @Json(name = "valid_from")
    val validFrom: String, // You might want to parse this to a Date/Time object later
    @Json(name = "valid_to")
    val validTo: String,   // Same here
    @Json(name = "payment_method")
    val paymentMethod: String? // Or a more specific type if known
)