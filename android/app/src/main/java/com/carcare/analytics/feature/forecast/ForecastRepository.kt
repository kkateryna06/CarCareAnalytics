package com.carcare.analytics.feature.forecast

import com.carcare.analytics.core.security.TokenStore
import com.carcare.analytics.feature.vehicles.VehicleRepository
import kotlinx.coroutines.*
import java.io.IOException
import java.time.ZoneId
import javax.inject.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Singleton
class ForecastRepository @Inject constructor(private val api: ForecastApi,private val tokens: TokenStore,private val files: CsvExportFiles,private val vehicles: VehicleRepository): ForecastDataSource {
    override suspend fun vehicle(vehicle: Long)=vehicles.get(vehicle)
    override suspend fun forecast(vehicle: Long,historyMonths: Int)=api.forecast(vehicle,historyMonths,ZoneId.systemDefault().id)
    override suspend fun export(vehicle: Long,from: String,to: String): CsvArtifact = withContext(Dispatchers.IO) {
        val session=tokens.session.value?:throw IOException("Session expired")
        var file: java.io.File?=null
        try {
            api.export(vehicle,from,to,ZoneId.systemDefault().id).use { body ->
                if(body.contentType()?.type!="text" || body.contentType()?.subtype!="csv" || body.contentLength()>10L*1024*1024) throw InvalidCsvExport()
                val target=files.create(vehicle,from,to); file=target
                val job=currentCoroutineContext()
                writeCsvExport(body.byteStream(),target) { job.ensureActive() }
                if(tokens.session.value?.accessToken!=session.accessToken) throw CancellationException("Session changed")
                CsvArtifact(target)
            }
        } catch(ex: Exception) { file?.delete(); throw ex }
    }
}
@Module @InstallIn(SingletonComponent::class)
abstract class ForecastModule { @Binds abstract fun source(repository: ForecastRepository): ForecastDataSource }
