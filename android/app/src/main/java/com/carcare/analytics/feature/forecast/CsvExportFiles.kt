package com.carcare.analytics.feature.forecast

import android.content.*
import androidx.core.content.FileProvider
import com.carcare.analytics.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import javax.inject.*

@Singleton
class CsvExportFiles @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val directory get()=File(context.cacheDir,"exports")
    fun create(vehicle: Long,from: String,to: String): File {
        val folder=directory; check(folder.mkdirs() || folder.isDirectory)
        folder.listFiles()?.filter { System.currentTimeMillis()-it.lastModified()>24*60*60*1000L }?.forEach { it.delete() }
        return File(folder,"carcare-$vehicle-$from-$to-${UUID.randomUUID()}.csv")
    }
    suspend fun clear() { withContext(Dispatchers.IO) { directory.listFiles()?.forEach { it.delete() } } }
}

fun csvShareIntent(context: Context,artifact: CsvArtifact): Intent {
    val uri=FileProvider.getUriForFile(context,"${context.packageName}.exports",artifact.file)
    return Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type="text/csv"; putExtra(Intent.EXTRA_STREAM,uri)
        clipData=ClipData.newRawUri("CSV",uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    },context.getString(R.string.share_csv))
}
