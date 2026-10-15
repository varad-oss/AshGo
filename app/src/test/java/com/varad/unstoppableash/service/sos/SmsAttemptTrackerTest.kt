package com.varad.unstoppableash.service.sos

import com.varad.unstoppableash.domain.model.SmsStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsAttemptTrackerTest {
    private val t = SmsAttemptTracker()

    @Test fun reportsOnlyWhenTheCombinedStatusChanges() {
        t.start("a", parts = 2)
        assertNull("one part sent, the other unknown: still queued", t.record("a", 0, SmsPart.SENT))
        assertEquals(SmsStatus.SENT, t.record("a", 1, SmsPart.SENT))
        assertNull(t.record("a", 0, SmsPart.DELIVERED))
        assertEquals(SmsStatus.DELIVERED, t.record("a", 1, SmsPart.DELIVERED))
    }

    @Test fun anyFailedPartFailsTheMessageOnce() {
        t.start("a", parts = 3)
        assertEquals(SmsStatus.FAILED, t.record("a", 1, SmsPart.FAILED))
        assertNull("already failed", t.record("a", 0, SmsPart.SENT))
    }

    @Test fun aDeliveryReportNeverDowngradesAFailure() {
        t.start("a", parts = 1)
        assertEquals(SmsStatus.FAILED, t.record("a", 0, SmsPart.FAILED))
        assertNull(t.record("a", 0, SmsPart.DELIVERED))
    }

    @Test fun unknownAttemptsAreIgnored() {
        assertNull("callback from before a process restart", t.record("ghost", 0, SmsPart.SENT))
    }

    @Test fun attemptsAreIndependent() {
        t.start("a", 1); t.start("b", 1)
        assertEquals(SmsStatus.FAILED, t.record("a", 0, SmsPart.FAILED))
        assertEquals(SmsStatus.SENT, t.record("b", 0, SmsPart.SENT))
    }

    @Test fun aDeliveryReportCountsOnlyWhenTheNetworkSaysComplete() {
        // TP-Status (3GPP TS 23.040): 0x00–0x1F complete, 0x20–0x3F still trying, 0x40+ failed.
        assertEquals(SmsPart.DELIVERED, SmsPart.fromDeliveryStatus(0x00))
        assertNull("still trying is not delivered", SmsPart.fromDeliveryStatus(0x20))
        assertNull(SmsPart.fromDeliveryStatus(0x3F))
        assertEquals(SmsPart.FAILED, SmsPart.fromDeliveryStatus(0x40))
        assertEquals(SmsPart.FAILED, SmsPart.fromDeliveryStatus(0x65))
        assertNull("an unreadable report proves nothing", SmsPart.fromDeliveryStatus(null))
    }
}
