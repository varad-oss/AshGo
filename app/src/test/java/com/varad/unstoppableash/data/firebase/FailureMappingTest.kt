package com.varad.unstoppableash.data.firebase

import com.varad.unstoppableash.core.result.FailureReason
import org.junit.Assert.assertEquals
import org.junit.Test

class FailureMappingTest {
    @Test fun permissionDeniedFromAnyWrapperOrCause() {
        assertEquals(FailureReason.PERMISSION_DENIED, failureReasonOf(RuntimeException("Firebase Database error: Permission denied")))
        assertEquals(FailureReason.PERMISSION_DENIED, failureReasonOf(IllegalStateException("task failed", RuntimeException("PERMISSION_DENIED: Permission denied"))))
    }

    @Test fun networkProblemsAreOffline() {
        assertEquals(FailureReason.OFFLINE, failureReasonOf(RuntimeException("The write was canceled because of a network error")))
        assertEquals(FailureReason.OFFLINE, failureReasonOf(RuntimeException("Client is offline")))
        assertEquals(FailureReason.OFFLINE, failureReasonOf(RuntimeException("disconnected")))
    }

    @Test fun anythingElseIsUnknown() {
        assertEquals(FailureReason.UNKNOWN, failureReasonOf(RuntimeException("boom")))
        assertEquals(FailureReason.UNKNOWN, failureReasonOf(RuntimeException()))
    }
}
