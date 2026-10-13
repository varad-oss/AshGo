package com.varad.unstoppableash.data.sos

import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.domain.model.LocationFix
import com.varad.unstoppableash.domain.model.SosRecordState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SosRepositoryTest {
    private val db = FakeRtdb(nowMs = 500_000L)
    private val sos = SosRepository(db)
    private val p = "p1"

    @Test fun raiseWritesActiveSosWithKnownLocation() = runTest {
        val id = sos.newSosId(p)
        assertTrue(sos.raise(p, "alice", id, LocationFix(18.5, 73.8, 12f, 480_000L), battery = 41) is Outcome.Success)
        assertEquals(
            mapOf("from" to "alice", "state" to "active", "at" to 500_000L, "lat" to 18.5, "lng" to 73.8, "locAt" to 480_000L, "acc" to 12.0, "battery" to 41L),
            db.read("pairs/p1/sos/$id"),
        )
    }

    @Test fun raiseWithoutLocationOmitsIt() = runTest {
        sos.raise(p, "alice", "s1", location = null, battery = null)
        assertEquals(mapOf("from" to "alice", "state" to "active", "at" to 500_000L), db.read("pairs/p1/sos/s1"))
    }

    @Test fun cancelUpdatesStateAndTimeTogether() = runTest {
        sos.raise(p, "alice", "s1", null, null)
        db.writeLog.clear()
        sos.cancel(p, "s1")
        assertEquals(listOf("update pairs/p1/sos/s1 [cancelledAt, state]"), db.writeLog)
        assertEquals("cancelled", db.read("pairs/p1/sos/s1/state")); assertEquals(500_000L, db.read("pairs/p1/sos/s1/cancelledAt"))
    }

    @Test fun senderUpdatesSmsCountsAndFreshLocation() = runTest {
        sos.raise(p, "alice", "s1", null, null)
        sos.updateSmsCounts(p, "s1", sent = 2, failed = 1)
        sos.updateLocation(p, "s1", LocationFix(1.0, 2.0, null, 499_000L))
        val r = sos.records(p).first().single()
        assertEquals(2, r.smsSent); assertEquals(1, r.smsFailed); assertEquals(1.0, r.lat); assertEquals(499_000L, r.locAt); assertNull(r.accuracyM)
    }

    @Test fun ackAndDeliveredOnlyForPartnerSos() = runTest {
        sos.raise(p, "alice", "s1", null, null)
        val rec = sos.records(p).first().single()
        sos.ack(p, rec, "alice")
        assertNull(db.read("pairs/p1/sos/s1/ackBy"))
        sos.markDelivered(p, rec, "bob"); sos.ack(p, rec, "bob")
        val after = sos.records(p).first().single()
        assertEquals(mapOf("bob" to 500_000L), after.deliveredTo); assertEquals(mapOf("bob" to 500_000L), after.ackBy)
        db.writeLog.clear()
        sos.ack(p, after, "bob")
        assertTrue(db.writeLog.isEmpty())
    }

    @Test fun partnerActiveSosIsTheLatestActiveFromPartner() = runTest {
        db.set("pairs/p1/sos", mapOf(
            "s1" to mapOf("from" to "bob", "state" to "cancelled", "at" to 100L, "cancelledAt" to 200L),
            "s2" to mapOf("from" to "alice", "state" to "active", "at" to 300L),
            "s3" to mapOf("from" to "bob", "state" to "active", "at" to 400L),
        ))
        val active = sos.partnerActiveSos(p, "alice").first()
        assertEquals("s3", active?.id); assertEquals(SosRecordState.ACTIVE, active?.state)
        db.set("pairs/p1/sos/s3/state", "cancelled")
        assertNull(sos.partnerActiveSos(p, "alice").first())
    }
}
