package com.manish.vahakvigil.data.export

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import com.manish.vahakvigil.domain.export.SensorCsvDocumentExport
import com.manish.vahakvigil.domain.export.SensorCsvExportProgress
import com.manish.vahakvigil.domain.export.SensorCsvExportResult
import com.manish.vahakvigil.domain.export.SensorCsvExporter

/** SAF adapter; the domain exporter owns and closes the opened document stream. */
class AndroidSensorCsvDocumentExporter(
    private val contentResolver: ContentResolver,
    private val exporter: SensorCsvExporter
) : SensorCsvDocumentExport {
    override suspend fun countEvents(sessionId: String): Long = exporter.countEvents(sessionId)

    override suspend fun export(
        sessionId: String,
        destination: String,
        onProgress: suspend (SensorCsvExportProgress) -> Unit
    ): SensorCsvExportResult = exporter.export(
        sessionId = sessionId,
        openOutputStream = {
            contentResolver.openOutputStream(Uri.parse(destination), "w")
                ?: throw IOException("The selected document could not be opened for writing")
        },
        onProgress = onProgress
    )
}
