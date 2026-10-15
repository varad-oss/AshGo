package com.varad.unstoppableash.domain.sos

import com.varad.unstoppableash.domain.model.LocationFix
import com.varad.unstoppableash.domain.model.SmsStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsComposerTest {
    private val now = 1_000_000_000L

    @Test fun includesAnOpenStreetMapLinkWithAccuracyAndAge() {
        val text = SmsComposer.sos("Asha", LocationFix(18.520431, 73.856743, 12f, now - 120_000), battery = 41, nowMs = now)
        assertTrue(text, text.startsWith("AshGo SOS: Asha needs help."))
        assertTrue(text, "https://www.openstreetmap.org/?mlat=18.52043&mlon=73.85674#map=17/18.52043/73.85674" in text)
        assertTrue(text, "(+/-12 m, 2 min ago)" in text)
        assertTrue(text, "Battery 41%." in text)
    }

    @Test fun noFixSaysLocationUnavailable() {
        val text = SmsComposer.sos("Asha", null, battery = null, nowMs = now)
        assertTrue(text, "Location unavailable." in text)
        assertFalse(text, "openstreetmap" in text); assertFalse(text, "Battery" in text)
    }

    @Test fun zeroZeroIsNeverSentAsALocation() {
        val text = SmsComposer.sos("Asha", LocationFix(0.0, 0.0, 5f, now), battery = 80, nowMs = now)
        assertTrue(text, "Location unavailable." in text)
    }

    @Test fun fixedTextIsGsm7SoTheMessageStaysShort() {
        val text = SmsComposer.sos("A", LocationFix(-33.86785, 151.20732, 1500f, now - 3 * 3_600_000), battery = 5, nowMs = now)
        assertTrue(text, text.all { it in GSM7 })
        assertTrue(text, "(+/-1500 m, 3 h ago)" in text)
        assertTrue(SmsComposer.falseAlarm("A").all { it in GSM7 })
    }

    @Test fun falseAlarmNamesTheSender() {
        assertEquals("AshGo: Asha is safe. The earlier SOS was a false alarm. Sorry for the worry.", SmsComposer.falseAlarm("Asha"))
    }

    @Test fun aggregatorIsWorstOfParts() {
        assertEquals(SmsStatus.FAILED, SmsAggregator.combine(listOf(SmsStatus.SENT, SmsStatus.FAILED)))
        assertEquals(SmsStatus.SENT, SmsAggregator.combine(listOf(SmsStatus.SENT, SmsStatus.SENT)))
        assertEquals(SmsStatus.SENT, SmsAggregator.combine(listOf(SmsStatus.DELIVERED, SmsStatus.SENT)))
        assertEquals(SmsStatus.DELIVERED, SmsAggregator.combine(listOf(SmsStatus.DELIVERED, SmsStatus.DELIVERED)))
        assertEquals(SmsStatus.QUEUED, SmsAggregator.combine(listOf(SmsStatus.SENT, SmsStatus.QUEUED)))
        assertEquals(SmsStatus.QUEUED, SmsAggregator.combine(emptyList()))
    }

    private companion object {
        // GSM 03.38 basic set (enough for this check).
        val GSM7 = ("@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà").toSet()
    }
}
