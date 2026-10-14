package com.varad.unstoppableash.feature.settings

import com.varad.unstoppableash.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ReliabilityViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val allGood = ReliabilityChecks(true, true, true, true, true, true, connected = false)

    @Test fun connectionIsOnlyOkWhenTheBackgroundServiceRunsToo() = runTest(main.dispatcher) {
        val online = MutableStateFlow(true)
        val running = MutableStateFlow(false)
        val vm = ReliabilityViewModel({ allGood }, online, running)
        backgroundScope.launch { vm.items.collect {} }
        vm.items.first { items -> items.any { it.check == Check.CONNECTION && !it.ok } }
        running.value = true
        vm.items.first { items -> items.single { it.check == Check.CONNECTION }.ok }
    }
}
