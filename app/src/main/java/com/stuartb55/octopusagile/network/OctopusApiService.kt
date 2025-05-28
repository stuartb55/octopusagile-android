// network/OctopusApiService.kt
package com.stuartb55.octopusagile.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.QueryMap
import retrofit2.http.Url

interface OctopusApiService {
    @GET("v1/products/AGILE-24-10-01/electricity-tariffs/E-1R-AGILE-24-10-01-G/standard-unit-rates/")
    suspend fun getStandardUnitRates(@QueryMap params: Map<String, String>? = null): Response<EnergyRatesResponse>

    @GET
    suspend fun getRatesByFullUrl(@Url url: String): Response<EnergyRatesResponse>
}