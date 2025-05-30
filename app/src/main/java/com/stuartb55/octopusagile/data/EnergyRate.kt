package com.stuartb55.octopusagile.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EnergyRate(
    @Json(name = "value_exc_vat")
    val valueExcVat: Double,
    @Json(name = "value_inc_vat")
    val valueIncVat: Double,
    @Json(name = "valid_from")
    val validFrom: String,
    @Json(name = "valid_to")
    val validTo: String,
    @Json(name = "payment_method")
    val paymentMethod: String?
)