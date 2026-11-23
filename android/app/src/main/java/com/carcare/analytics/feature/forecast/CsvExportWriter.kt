package com.carcare.analytics.feature.forecast

import java.io.*

class InvalidCsvExport: Exception("Invalid or oversized CSV response")

/** Bounded streaming copy; a failed or cancelled transfer must never leave a shareable partial file. */
fun writeCsvExport(input: InputStream,destination: File,maxBytes: Long = 10L*1024*1024,checkActive: ()->Unit = {}) {
    try {
        input.use { source ->
            val bom=ByteArray(3)
            try { DataInputStream(source).readFully(bom) } catch(ex: EOFException) { throw InvalidCsvExport() }
            if(!bom.contentEquals(byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte()))) throw InvalidCsvExport()
            destination.outputStream().use { target ->
                target.write(bom); var size=3L; val buffer=ByteArray(16*1024)
                while(true) {
                    checkActive(); val count=source.read(buffer); if(count<0) break
                    size+=count; if(size>maxBytes) throw InvalidCsvExport()
                    target.write(buffer,0,count)
                }
            }
        }
    } catch(ex: Exception) { destination.delete(); throw ex }
}
