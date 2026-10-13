package com.varad.unstoppableash.domain.model

enum class TripState { ACTIVE, ARRIVED, ENDED, OVERDUE }

data class TripSnapshot(val state: TripState, val expectBy: Long?, val extendedTo: Long?, val endedAt: Long?)
