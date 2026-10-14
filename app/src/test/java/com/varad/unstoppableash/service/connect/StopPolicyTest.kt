package com.varad.unstoppableash.service.connect

import com.varad.unstoppableash.domain.model.PairMeta
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.model.Relationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StopPolicyTest {
    private val meta = PairMeta("p1", "alice", "bob", Relationship.PARTNER, 1L, 2L)

    @Test fun signedOutStopsSoon() = assertEquals(StopPolicy.SIGNED_OUT_STOP_MS, StopPolicy.stopAfterMs(PairState.NotSignedIn))

    @Test fun pairedKeepsRunning() = assertNull(StopPolicy.stopAfterMs(PairState.Paired(meta, "alice", "bob", null, null)))

    @Test fun aBriefUnpairedReadingIsWaitedOut() = assertEquals(StopPolicy.UNPAIRED_GRACE_MS, StopPolicy.stopAfterMs(PairState.Unpaired))
}
