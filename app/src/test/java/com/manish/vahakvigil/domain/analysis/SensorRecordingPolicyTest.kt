package com.manish.vahakvigil.domain.analysis

import com.manish.vahakvigil.domain.model.RideRecordingCompleteness
import com.manish.vahakvigil.domain.model.RideRecordingCompletenessPolicy
import com.manish.vahakvigil.domain.model.RideSessionStatus
import com.manish.vahakvigil.domain.model.SensorGpsTimelineAlignment
import com.manish.vahakvigil.domain.model.SensorGpsTimelinePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class SensorRecordingPolicyTest {
    @Test fun completenessRequiresCompletedSessionBothStreamsAndMultipleGpsPoints() {
        assertEquals(
            RideRecordingCompleteness.COMPLETE,
            RideRecordingCompletenessPolicy.evaluate(RideSessionStatus.COMPLETED, 2, 1, 1)
        )
        assertEquals(
            RideRecordingCompleteness.PARTIAL,
            RideRecordingCompletenessPolicy.evaluate(RideSessionStatus.ACTIVE, 2, 1, 1)
        )
        assertEquals(
            RideRecordingCompleteness.PARTIAL,
            RideRecordingCompletenessPolicy.evaluate(RideSessionStatus.COMPLETED, 1, 1, 1)
        )
        assertEquals(
            RideRecordingCompleteness.PARTIAL,
            RideRecordingCompletenessPolicy.evaluate(RideSessionStatus.COMPLETED, 2, 0, 1)
        )
        assertEquals(
            RideRecordingCompleteness.NO_SENSOR_DATA,
            RideRecordingCompletenessPolicy.evaluate(RideSessionStatus.COMPLETED, 2, 0, 0)
        )
    }

    @Test fun sensorAndGpsClocksAreNeverClaimedToBeAligned() {
        assertEquals(
            SensorGpsTimelineAlignment.SEPARATE_TIMELINES,
            SensorGpsTimelinePolicy.alignment(sensorEventCount = 10, gpsPointCount = 2)
        )
        assertEquals(
            SensorGpsTimelineAlignment.SENSOR_OR_GPS_DATA_MISSING,
            SensorGpsTimelinePolicy.alignment(sensorEventCount = 0, gpsPointCount = 2)
        )
        assertEquals(
            SensorGpsTimelineAlignment.SENSOR_OR_GPS_DATA_MISSING,
            SensorGpsTimelinePolicy.alignment(sensorEventCount = 10, gpsPointCount = 0)
        )
    }
}
