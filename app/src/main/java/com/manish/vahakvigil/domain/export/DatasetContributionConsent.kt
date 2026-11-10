package com.manish.vahakvigil.domain.export

/** Local-only consent state. Granting consent never selects or exports existing rides. */
interface DatasetContributionConsentStore {
    fun isGranted(): Boolean
    fun grantedAtEpochMillis(): Long?
    fun grant(nowEpochMillis: Long)
    fun withdraw()
}

class DatasetContributionConsent(private val store: DatasetContributionConsentStore) {
    fun mayExport(): Boolean = store.isGranted()
    fun grantedAtEpochMillis(): Long? = store.grantedAtEpochMillis()

    fun requireConsent() {
        check(mayExport()) { "Dataset contribution consent is required before export" }
    }

    fun grant(nowEpochMillis: Long) {
        require(nowEpochMillis > 0L)
        store.grant(nowEpochMillis)
    }

    fun withdraw() = store.withdraw()
}

data class DatasetPackageProgress(val writtenRows: Long, val totalRows: Long)

sealed interface DatasetPackageExportResult {
    data object Empty : DatasetPackageExportResult
    data class Complete(val datasetSessionId: String, val exportedRows: Long) : DatasetPackageExportResult
}

interface SensorDatasetPackageExport {
    suspend fun export(
        internalSessionId: String,
        destination: String,
        recordingProvenance: String,
        onProgress: suspend (DatasetPackageProgress) -> Unit = {}
    ): DatasetPackageExportResult
}
