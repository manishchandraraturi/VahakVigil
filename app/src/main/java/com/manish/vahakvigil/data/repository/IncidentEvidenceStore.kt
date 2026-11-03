package com.manish.vahakvigil.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Private, device-local evidence store. Keys are the qualified incident window-end timestamps. */
class IncidentEvidenceStore(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "incident-evidence").apply { mkdirs() }

    fun read(incidentId: Long): Evidence {
        val prefs = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val photo = prefs.getString(photoKey(incidentId), null)?.let(::File)?.takeIf(File::exists)
        return Evidence(prefs.getString(noteKey(incidentId), null), photo)
    }

    fun saveNote(incidentId: Long, note: String) {
        require(incidentId > 0L && note.isNotBlank())
        appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(noteKey(incidentId), note.trim()).apply()
    }

    fun createCaptureUri(incidentId: Long): Uri {
        require(incidentId > 0L)
        val pending = File(root, "$incidentId.pending.jpg")
        pending.delete()
        return FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", pending)
    }

    fun commitCapture(incidentId: Long): Boolean {
        val pending = File(root, "$incidentId.pending.jpg")
        if (!pending.exists() || pending.length() == 0L) {
            pending.delete()
            return false
        }
        val destination = File(root, "$incidentId.jpg")
        val backup = File(root, "$incidentId.previous.jpg")
        backup.delete()
        val hadPrevious = destination.exists()
        if (hadPrevious && !destination.renameTo(backup)) return false
        if (!pending.renameTo(destination)) {
            if (hadPrevious) backup.renameTo(destination)
            return false
        }
        backup.delete()
        appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(photoKey(incidentId), destination.absolutePath).apply()
        return true
    }

    fun deleteNote(incidentId: Long) {
        appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().remove(noteKey(incidentId)).apply()
    }

    fun deletePhoto(incidentId: Long) {
        val prefs = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        prefs.getString(photoKey(incidentId), null)?.let(::File)?.delete()
        File(root, "$incidentId.pending.jpg").delete()
        prefs.edit().remove(photoKey(incidentId)).apply()
    }

    fun cancelCapture(incidentId: Long) {
        File(root, "$incidentId.pending.jpg").delete()
    }

    fun deleteIncident(incidentId: Long) {
        deleteNote(incidentId)
        deletePhoto(incidentId)
        File(root, "$incidentId.previous.jpg").delete()
        File(root, "$incidentId.pending.jpg").delete()
    }

    data class Evidence(val note: String?, val photo: File?)

    private fun noteKey(id: Long) = "note_$id"
    private fun photoKey(id: Long) = "photo_$id"

    companion object {
        private const val PREFERENCES = "local_incident_evidence"
        fun incidentId(windowEndTimestampMs: Long?): Long? = windowEndTimestampMs?.takeIf { it > 0L }
    }
}
