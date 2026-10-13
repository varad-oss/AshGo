package com.varad.unstoppableash.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Test

class DbPathsTest {
    @Test fun pathsMatchArchitectureSection5() {
        assertEquals("userPairs/u1", DbPaths.userPair("u1"))
        assertEquals("invites/bob@x,com", DbPaths.invites("bob@x,com"))
        assertEquals("invites/bob@x,com/p1", DbPaths.invite("bob@x,com", "p1"))
        assertEquals("pairs/p1", DbPaths.pair("p1"))
        assertEquals("pairs/p1/meta", DbPaths.meta("p1"))
        assertEquals("pairs/p1/profiles/u1", DbPaths.profile("p1", "u1"))
        assertEquals("pairs/p1/presence/u1", DbPaths.presence("p1", "u1"))
        assertEquals("pairs/p1/location/u1", DbPaths.location("p1", "u1"))
        assertEquals("pairs/p1/trips/u1", DbPaths.trip("p1", "u1"))
        assertEquals("pairs/p1/chat", DbPaths.chat("p1"))
        assertEquals("pairs/p1/chat/m1", DbPaths.chatMessage("p1", "m1"))
        assertEquals("pairs/p1/media/x1", DbPaths.media("p1", "x1"))
        assertEquals("pairs/p1/events", DbPaths.events("p1"))
        assertEquals("pairs/p1/events/e1/seenBy/u2", DbPaths.eventReceipt("p1", "e1", Receipt.SEEN, "u2"))
        assertEquals("pairs/p1/events/e1/deliveredTo/u2", DbPaths.eventReceipt("p1", "e1", Receipt.DELIVERED, "u2"))
        assertEquals("pairs/p1/sos/s1", DbPaths.sos("p1", "s1"))
        assertEquals("pairs/p1/sos/s1/ackBy/u2", DbPaths.sosReceipt("p1", "s1", Receipt.ACK, "u2"))
        assertEquals(".info/connected", DbPaths.CONNECTED)
        assertEquals(".info/serverTimeOffset", DbPaths.SERVER_TIME_OFFSET)
    }

    @Test fun emailKeyMatchesTheRulesNormalisation() {
        assertEquals("bob,smith@example,com", emailKey("  Bob.Smith@Example.com "))
        assertEquals("a,b,c@d,e", emailKey("a.b.c@d.e"))
    }
}
