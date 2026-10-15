package com.varad.unstoppableash.service.connect

import com.varad.unstoppableash.domain.model.SharingMode
import com.varad.unstoppableash.domain.model.Trip
import com.varad.unstoppableash.domain.model.TripState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundPolicyTest {
    private fun trip(state: TripState) = Trip(state, null, null, null, null, true, 1L, null)
    private val idle = BackgroundInputs(watcher = true, partnerTrip = null, partnerSharing = SharingMode.OFF, followSharing = true, myTrip = null, locationServiceRunning = false)

    @Test fun theWatcherStaysOnlyDuringTheirTripOrFollowedSharing() {
        assertFalse("nothing going on: dies when cleared", BackgroundPolicy.keepConnected(idle))
        assertTrue(BackgroundPolicy.keepConnected(idle.copy(partnerTrip = trip(TripState.ACTIVE))))
        assertTrue("overdue matters most", BackgroundPolicy.keepConnected(idle.copy(partnerTrip = trip(TripState.OVERDUE))))
        assertFalse("a finished trip doesn't count", BackgroundPolicy.keepConnected(idle.copy(partnerTrip = trip(TripState.ARRIVED))))
        assertTrue(BackgroundPolicy.keepConnected(idle.copy(partnerSharing = SharingMode.LIVE)))
        assertFalse("not following their sharing", BackgroundPolicy.keepConnected(idle.copy(partnerSharing = SharingMode.LIVE, followSharing = false)))
        assertFalse("paused sharing isn't live", BackgroundPolicy.keepConnected(idle.copy(partnerSharing = SharingMode.PAUSED)))
    }

    @Test fun theOneWatchedOverStaysDuringTheirTripUnlessLocationSharingAlreadyCarriesIt() {
        val me = idle.copy(watcher = false)
        assertFalse(BackgroundPolicy.keepConnected(me))
        assertTrue("a trip without sharing", BackgroundPolicy.keepConnected(me.copy(myTrip = trip(TripState.ACTIVE))))
        assertFalse("location sharing's own service carries the alerts: no second notification",
            BackgroundPolicy.keepConnected(me.copy(myTrip = trip(TripState.ACTIVE), locationServiceRunning = true)))
        assertFalse("their partner's state never keeps them on", BackgroundPolicy.keepConnected(me.copy(partnerTrip = trip(TripState.ACTIVE), partnerSharing = SharingMode.LIVE)))
    }
}
