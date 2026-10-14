package com.varad.unstoppableash.feature.safety

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.varad.unstoppableash.MainDispatcherRule
import com.varad.unstoppableash.data.local.EmergencyContactsStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ContactsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.vm(): ContactsViewModel {
        val store = EmergencyContactsStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.root.resolve("c.preferences_pb") })
        return ContactsViewModel(store).also { v -> backgroundScope.launch { v.state.collect {} } }
    }

    private fun TestScope.settle() { testScheduler.advanceTimeBy(1_000); testScheduler.runCurrent() }

    @Test fun addsAndRemoves() = runTest(main.dispatcher) {
        val vm = vm()
        vm.add("Mum", "+91 98765 43210"); settle()
        assertEquals(listOf("Mum"), vm.state.value.contacts.map { it.name })
        vm.remove(vm.state.value.contacts.single().id); settle()
        assertEquals(0, vm.state.value.contacts.size)
    }

    @Test fun theSameNumberTwiceIsRefusedWithAReason() = runTest(main.dispatcher) {
        val vm = vm()
        vm.add("Mum", "+91 98765 43210"); settle()
        vm.add("Mother", "+919876543210"); settle()
        assertEquals(1, vm.state.value.contacts.size); assertEquals(ContactsNotice.DUPLICATE, vm.state.value.notice)
    }

    @Test fun atMostFive() = runTest(main.dispatcher) {
        val vm = vm()
        (1..6).forEach { vm.add("C$it", "90000000$it"); settle() }
        assertEquals(5, vm.state.value.contacts.size); assertEquals(ContactsNotice.FULL, vm.state.value.notice)
    }

    @Test fun somethingThatIsNotAPhoneNumberIsRefused() = runTest(main.dispatcher) {
        val vm = vm()
        vm.add("Mum", "call me"); settle()
        assertEquals(0, vm.state.value.contacts.size); assertEquals(ContactsNotice.INVALID, vm.state.value.notice)
        vm.clearNotice(); settle()
        assertNull(vm.state.value.notice)
    }
}
