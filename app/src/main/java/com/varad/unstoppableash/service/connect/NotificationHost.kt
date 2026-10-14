package com.varad.unstoppableash.service.connect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * One notification engine per process, running while anyone needs it: the app on screen, the "stay connected"
 * service, or location sharing's service. Two of them at once must never double-post.
 */
class NotificationHost(private val scope: CoroutineScope, private val run: suspend () -> Unit) {
    private var users = 0
    private var job: Job? = null

    @Synchronized fun acquire() {
        users++
        if (job?.isActive != true) job = scope.launch { run() }
    }

    @Synchronized fun release() {
        if (users == 0) return
        users--
        if (users == 0) { job?.cancel(); job = null }
    }
}
