package com.manish.vahakvigil.domain.export

/** Platform-neutral document export contract; destination is the system document URI string. */
interface SensorCsvDocumentExport {
    suspend fun countEvents(sessionId: String): Long

    suspend fun export(
        sessionId: String,
        destination: String,
        onProgress: suspend (SensorCsvExportProgress) -> Unit = {}
    ): SensorCsvExportResult
}
