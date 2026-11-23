package com.carcare.analytics.feature.vehicles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.carcare.analytics.R
import com.carcare.analytics.core.network.CarCareApi
import com.carcare.analytics.core.security.Session
import com.carcare.analytics.core.security.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val MaxEdge=1280
private const val MaxBytes=384*1024

@Singleton
class VehiclePhotoImages @Inject constructor(@ApplicationContext private val context: Context,private val api: CarCareApi,private val tokens: TokenStore) {
    private data class PhotoKey(val vehicle: Long,val version: String)
    private var owner: Session?=null
    private val lock=Any()
    private val images=object: LruCache<PhotoKey,Bitmap>(12*1024*1024) {
        override fun sizeOf(key: PhotoKey,value: Bitmap)=value.allocationByteCount
    }

    private fun owner(): Session? {
        val current=tokens.session.value
        if(current!==owner) {
            owner=current
            images.evictAll()
        }
        return current
    }

    fun cached(id: Long,version: String): ImageBitmap?=synchronized(lock) {
        owner() ?: return@synchronized null
        images.get(PhotoKey(id,version))?.asImageBitmap()
    }

    suspend fun load(id: Long,version: String): ImageBitmap?=withContext(Dispatchers.IO) {
        val account=synchronized(lock) { owner() } ?: return@withContext null
        cached(id,version)?.let { return@withContext it }
        val photo=api.vehiclePhoto(id,version).use { body ->
            if(body.contentType()?.subtype!="jpeg" || body.contentLength()>MaxBytes) return@withContext null
            BitmapFactory.decodeStream(body.byteStream())
        } ?: return@withContext null
        ensureActive()
        synchronized(lock) {
            if(owner()!==account) return@synchronized null
            images.put(PhotoKey(id,version),photo)
            photo.asImageBitmap()
        }
    }

    suspend fun importPhoto(uri: Uri): String=withContext(Dispatchers.IO) {
        val resolver=context.contentResolver
        val bitmap=if(Build.VERSION.SDK_INT>=28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver,uri)) { decoder,info,_ ->
                decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
                val longest=maxOf(info.size.width,info.size.height)
                if(longest>MaxEdge) decoder.setTargetSize(
                    (info.size.width.toLong()*MaxEdge/longest).toInt().coerceAtLeast(1),
                    (info.size.height.toLong()*MaxEdge/longest).toInt().coerceAtLeast(1)
                )
            }
        } else {
            val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,options) }
            val edge=maxOf(options.outHeight,options.outWidth)
            if(edge<=0) throw IOException("Unreadable photo")
            options.inJustDecodeBounds=false
            options.inSampleSize=1
            while(edge/options.inSampleSize>MaxEdge) options.inSampleSize*=2
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,options) }
                ?: throw IOException("Unreadable photo")
        }
        val output=ByteArrayOutputStream()
        try {
            // Keep the payload bounded without retaining the original or its metadata.
            for(quality in listOf(88,76,64,52,40,28,16)) {
                output.reset()
                if(!bitmap.compress(Bitmap.CompressFormat.JPEG,quality,output)) throw IOException("Unreadable photo")
                if(output.size() in 1..MaxBytes) {
                    ensureActive()
                    val draft=File.createTempFile("vehicle-photo-",".jpg",context.cacheDir)
                    try { draft.writeBytes(output.toByteArray()) }
                    catch(error: Exception) { draft.delete(); throw error }
                    return@withContext draft.name
                }
            }
            throw IOException("Photo too large")
        } finally { bitmap.recycle() }
    }

    private fun draftFile(name: String)=File(context.cacheDir,name)
    suspend fun draftImage(name: String): ImageBitmap?=withContext(Dispatchers.IO) {
        BitmapFactory.decodeFile(draftFile(name).path)?.asImageBitmap()
    }
    suspend fun encodedDraft(name: String): String=withContext(Dispatchers.IO) {
        val file=draftFile(name)
        if(file.length() !in 1L..MaxBytes.toLong()) throw IOException("Photo unavailable")
        Base64.encodeToString(file.readBytes(),Base64.NO_WRAP)
    }
    fun discardDraft(name: String?) { name?.let { draftFile(it).delete() } }
}

internal val LocalVehiclePhotos=staticCompositionLocalOf<VehiclePhotoImages?> { null }

@Composable
internal fun VehiclePhoto(id: Long?,version: String?,modifier: Modifier,draft: String? = null,fallback: @Composable () -> Unit = {}) {
    val images=LocalVehiclePhotos.current
    key(id,version,draft) {
        val bitmap by produceState<ImageBitmap?>(if(id!=null && version!=null && draft==null) images?.cached(id,version) else null,images) {
            if(value==null && images!=null) try {
                value=when {
                    draft!=null -> images.draftImage(draft)
                    id!=null && version!=null -> images.load(id,version)
                    else -> null
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { /* Leave the car icon visible. */ }
        }
        val photo=bitmap
        if(photo!=null) Image(photo,stringResource(R.string.vehicle_photo),modifier,contentScale=ContentScale.Crop) else fallback()
    }
}
