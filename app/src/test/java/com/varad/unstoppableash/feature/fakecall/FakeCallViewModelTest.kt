package com.varad.unstoppableash.feature.fakecall

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.varad.unstoppableash.MainDispatcherRule
import com.varad.unstoppableash.domain.model.FakeCallVoice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FakeCallViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private class RecordingAudio : FakeCallAudio {
        val log = mutableListOf<String>()
        override fun ring() { log += "ring" }
        override fun play(voice: FakeCallVoice?) { log += "play:${voice?.id}" }
        override fun stop() { log += "stop" }
    }

    private val audio = RecordingAudio()

    private fun TestScope.store() = FakeCallVoiceStore(
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmp.root.resolve("v.preferences_pb") }),
        copyIn = { src, id -> if (src == "bad") null else "file:///voices/$id" },
        deleteFile = {},
    )

    private fun TestScope.vm(store: FakeCallVoiceStore = store()) = FakeCallViewModel(store, audio).also { v -> backgroundScope.launch { v.state.collect {} } }

    @Test fun ringsAfterTheChosenDelayNotBefore() = runTest(main.dispatcher) {
        val vm = vm()
        vm.setCaller("Mum")
        vm.schedule(CallDelay.TEN_SECONDS)
        testScheduler.advanceTimeBy(9_000); testScheduler.runCurrent()
        assertTrue(vm.state.value.phase is CallPhase.Waiting)
        assertTrue(audio.log.isEmpty())
        testScheduler.advanceTimeBy(1_001); testScheduler.runCurrent()
        assertEquals(CallPhase.Ringing, vm.state.value.phase)
        assertEquals(listOf("ring"), audio.log)
    }

    @Test fun cancellingWhileWaitingNeverRings() = runTest(main.dispatcher) {
        val vm = vm()
        vm.schedule(CallDelay.ONE_MINUTE)
        vm.cancel(); testScheduler.runCurrent()
        testScheduler.advanceTimeBy(120_000); testScheduler.runCurrent()
        assertEquals(CallPhase.Setup, vm.state.value.phase)
        assertTrue(audio.log.none { it == "ring" })
    }

    @Test fun declineStopsRingingAndGoesBack() = runTest(main.dispatcher) {
        val vm = vm()
        vm.schedule(CallDelay.NOW); testScheduler.runCurrent()
        vm.decline(); testScheduler.runCurrent()
        assertEquals(CallPhase.Setup, vm.state.value.phase)
        assertEquals(listOf("ring", "stop"), audio.log)
    }

    @Test fun acceptPlaysTheChosenVoice() = runTest(main.dispatcher) {
        val store = store()
        store.add("Brother", "content://a")
        val vm = vm(store)
        val v = vm.state.first { it.voices.isNotEmpty() }.voices.first()
        vm.selectVoice(v.id)
        vm.schedule(CallDelay.NOW); testScheduler.runCurrent()
        vm.accept(); testScheduler.runCurrent()
        assertTrue(vm.state.value.phase is CallPhase.InCall)
        assertEquals(listOf("ring", "stop", "play:${v.id}"), audio.log)
        vm.end(); testScheduler.runCurrent()
        assertEquals(CallPhase.Setup, vm.state.value.phase)
    }

    @Test fun voicesAreCopiedInAndUnreadableOnesRefused() = runTest(main.dispatcher) {
        val store = store()
        assertTrue(store.add("Dad", "content://x"))
        assertEquals(false, store.add("Broken", "bad"))
        val list = store.voices.first()
        assertEquals(listOf("Dad"), list.map { it.name })
        assertTrue(list.single().uri.startsWith("file:///voices/"))
        store.remove(list.single().id)
        assertTrue(store.voices.first().isEmpty())
    }

    @Test fun v1VoicesImportOnce() = runTest(main.dispatcher) {
        val store = store()
        store.importLegacyOnce { listOf(FakeCallVoice("old", "Old voice", "content://v1")) }
        store.importLegacyOnce { listOf(FakeCallVoice("old2", "Again", "content://v1b")) }
        assertEquals(listOf("Old voice"), store.voices.first().map { it.name })
    }
}
