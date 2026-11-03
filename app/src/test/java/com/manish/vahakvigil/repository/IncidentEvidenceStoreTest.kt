package com.manish.vahakvigil.repository

import com.manish.vahakvigil.data.repository.IncidentEvidenceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncidentEvidenceStoreTest {
    @Test fun `evidence requires a valid incident timestamp`() {
        assertNull(IncidentEvidenceStore.incidentId(null))
        assertNull(IncidentEvidenceStore.incidentId(0L))
        assertNull(IncidentEvidenceStore.incidentId(-1L))
        assertEquals(1234L, IncidentEvidenceStore.incidentId(1234L))
    }
}
