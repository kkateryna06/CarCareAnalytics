package com.carcare.analytics.feature.forecast

import com.carcare.analytics.core.network.VehicleDto
import java.io.File

data class ForecastMonth(val month: String,val amountPln: String)
data class ForecastMethod(val method: String,val forecastPln: String?,val maePln: String?)
data class ForecastEvaluation(val targetMonth: String,val trainingFrom: String,val trainingTo: String,val excludedMonth: String,
    val actualPln: String,val simplePln: String,val weightedPln: String,val simpleAbsoluteErrorPln: String,val weightedAbsoluteErrorPln: String)
data class ForecastDto(val vehicleId: Long,val asOfDate: String,val timeZone: String,val targetMonth: String,val excludedMonth: String,
    val windowMonths: Int,val horizonMonths: Int,val availableMonths: Int,val requestedHistoryMonths: Int,val sufficientData: Boolean,
    val evaluationCount: Int,val bestMethod: String?,val history: List<ForecastMonth>,val methods: List<ForecastMethod>,
    val evaluations: List<ForecastEvaluation>,val warnings: List<String>)
data class CsvArtifact(val file: File)

interface ForecastDataSource {
    suspend fun vehicle(vehicle: Long): VehicleDto
    suspend fun forecast(vehicle: Long,historyMonths: Int): ForecastDto
    suspend fun export(vehicle: Long,from: String,to: String): CsvArtifact
}
