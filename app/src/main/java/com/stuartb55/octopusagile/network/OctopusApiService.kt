package com.stuartb55.octopusagile.network

import com.stuartb55.octopusagile.data.EnergyRatesResponse
import retrofit2.Response
import retrofit2.http.GET

interface OctopusApiService {
    @GET("products/AGILE-24-10-01/electricity-tariffs/E-1R-AGILE-24-10-01-G/standard-unit-rates/")
    suspend fun getStandardUnitRates(): Response<EnergyRatesResponse>
}