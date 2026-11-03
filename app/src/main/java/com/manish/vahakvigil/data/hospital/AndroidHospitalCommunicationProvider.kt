package com.manish.vahakvigil.data.hospital

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.manish.vahakvigil.domain.hospital.EmergencyHospitalCommunicationProvider
import com.manish.vahakvigil.domain.hospital.HospitalCallResult
import com.manish.vahakvigil.domain.hospital.HospitalCallStatus
import com.manish.vahakvigil.domain.model.EmergencyHospital

/**
 * Android platform implementation of [EmergencyHospitalCommunicationProvider].
 *
 * Safety guarantees:
 * - Uses the safest calling behavior: launches [Intent.ACTION_DIAL] with the hospital phone.
 * - Accurately reports [HospitalCallStatus.DIAL_REQUESTED].
 * - NEVER claims "Hospital answered" or "Call connected" without actual carrier/line feedback.
 * - If hospital phone is missing or blank, returns [HospitalCallStatus.NOT_AVAILABLE] without crashing.
 * - Catches any [Exception] and returns [HospitalCallStatus.FAILED].
 */
class AndroidHospitalCommunicationProvider(
    private val context: Context
) : EmergencyHospitalCommunicationProvider {

    private var currentStatus: HospitalCallStatus = HospitalCallStatus.NOT_CONFIGURED

    override suspend fun initiateHospitalCall(hospital: EmergencyHospital): HospitalCallResult {
        val phone = hospital.phoneNumber?.trim()
        if (phone.isNullOrBlank()) {
            currentStatus = HospitalCallStatus.NOT_AVAILABLE
            return HospitalCallResult(
                status = HospitalCallStatus.NOT_AVAILABLE,
                details = "Hospital phone unavailable."
            )
        }

        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phone")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            currentStatus = HospitalCallStatus.DIAL_REQUESTED
            HospitalCallResult(
                status = HospitalCallStatus.DIAL_REQUESTED,
                details = "Dialer requested for ${hospital.name}"
            )
        } catch (e: Exception) {
            currentStatus = HospitalCallStatus.FAILED
            HospitalCallResult(
                status = HospitalCallStatus.FAILED,
                details = "Failed to launch dialer: ${e.message}"
            )
        }
    }

    override fun getCommunicationStatus(): HospitalCallStatus = currentStatus
}
