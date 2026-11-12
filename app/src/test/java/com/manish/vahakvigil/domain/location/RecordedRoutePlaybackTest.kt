package com.manish.vahakvigil.domain.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordedRoutePlaybackTest {
    @Test fun progressSelectsOnlyRecordedPointIndices() {
        assertEquals(0, RecordedRoutePlayback.indexForProgress(0f, 5))
        assertEquals(2, RecordedRoutePlayback.indexForProgress(.5f, 5))
        assertEquals(4, RecordedRoutePlayback.indexForProgress(1f, 5))
        assertEquals(0, RecordedRoutePlayback.indexForProgress(-1f, 5))
        assertEquals(4, RecordedRoutePlayback.indexForProgress(2f, 5))
    }

    @Test fun emptyAndSinglePointRoutesAreHandled() {
        assertNull(RecordedRoutePlayback.indexForProgress(.7f, 0))
        assertEquals(0, RecordedRoutePlayback.indexForProgress(.7f, 1))
        assertEquals(0f, RecordedRoutePlayback.progressForIndex(0, 1), 0f)
        assertNull(RecordedRoutePlayback.nextIndex(0, 0))
    }

    @Test fun playbackNeverAdvancesPastTheEnd() {
        assertEquals(1, RecordedRoutePlayback.nextIndex(0, 3))
        assertEquals(2, RecordedRoutePlayback.nextIndex(1, 3))
        assertNull(RecordedRoutePlayback.nextIndex(2, 3))
        assertEquals(1f, RecordedRoutePlayback.progressForIndex(99, 3), 0f)
    }
}
