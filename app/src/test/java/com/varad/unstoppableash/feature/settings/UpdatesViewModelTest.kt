package com.varad.unstoppableash.feature.settings

import com.varad.unstoppableash.MainDispatcherRule
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.data.update.UpdateCheck
import com.varad.unstoppableash.data.update.UpdateFailure
import com.varad.unstoppableash.data.update.UpdateInfo
import com.varad.unstoppableash.data.update.UpdateSource
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class UpdatesViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val info = UpdateInfo(12, "2.0.1", "https://github.com/varad-oss/AshGo-releases/x.apk", "a".repeat(64), 8_000_000, "Fixes")
    private val file = File("ashgo-12.apk")

    class FakeSource(var check: UpdateCheck, var download: Outcome<File>) : UpdateSource {
        var installed: File? = null
        override suspend fun check() = check
        override suspend fun download(info: UpdateInfo, onProgress: (Float?) -> Unit): Outcome<File> { onProgress(0.5f); return download }
        override fun install(file: File) { installed = file }
    }

    private fun TestScope.settle() { testScheduler.advanceUntilIdle() }

    @Test fun foundThenDownloadedThenVerifiedThenHandedToTheInstaller() = runTest(main.dispatcher) {
        val src = FakeSource(UpdateCheck.Available(info), Outcome.Success(file))
        val vm = UpdatesViewModel(src)
        vm.check(); settle()
        assertEquals(UpdatesState.Found(info), vm.state.value)
        vm.download(); settle()
        assertEquals(file, src.installed)
    }

    @Test fun aChecksumMismatchIsNeverInstalled() = runTest(main.dispatcher) {
        val src = FakeSource(UpdateCheck.Available(info), Outcome.Failure(FailureReason.INVALID, UpdateFailure.CHECKSUM.name))
        val vm = UpdatesViewModel(src)
        vm.check(); settle(); vm.download(); settle()
        assertEquals(UpdatesState.Failed(UpdateFailure.CHECKSUM), vm.state.value)
        assertEquals(null, src.installed)
    }

    @Test fun upToDateAndCannotCheckAreDifferent() = runTest(main.dispatcher) {
        val vm = UpdatesViewModel(FakeSource(UpdateCheck.UpToDate, Outcome.Success(file)))
        vm.check(); settle()
        assertEquals(UpdatesState.UpToDate, vm.state.value)
        val vm2 = UpdatesViewModel(FakeSource(UpdateCheck.Unavailable, Outcome.Success(file)))
        vm2.check(); settle()
        assertTrue(vm2.state.value is UpdatesState.CannotCheck)
    }
}
