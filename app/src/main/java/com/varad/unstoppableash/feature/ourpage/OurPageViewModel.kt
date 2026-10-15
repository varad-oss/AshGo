package com.varad.unstoppableash.feature.ourpage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varad.unstoppableash.data.media.EncodedPhoto
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.wall.WallEntry
import com.varad.unstoppableash.data.wall.WallRepository
import com.varad.unstoppableash.domain.model.PairState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** [file] is null until the photo is on this phone (downloaded once, then kept in the cache). */
data class WallPhoto(val id: String, val from: String, val file: String?)

data class OurPageState(
    val photos: List<WallPhoto> = emptyList(),
    val adding: Boolean = false,
    val skipped: Int = 0,
    val paired: Boolean = true,
) {
    val full: Boolean get() = photos.size >= WallRepository.MAX
}

/** "Our page": one wall both phones share (owner's ask). */
@OptIn(ExperimentalCoroutinesApi::class)
class OurPageViewModel(
    private val pairs: PairRepository,
    private val wall: WallRepository,
    private val encode: suspend (uri: String) -> EncodedPhoto?,
    private val cacheDir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private data class Local(val adding: Boolean = false, val skipped: Int = 0)
    private val local = MutableStateFlow(Local())
    private val cached = MutableStateFlow<Set<String>>(emptySet())
    private val fetching = mutableSetOf<String>()

    private val paired: Flow<PairState.Paired?> = pairs.pairState().map { it as? PairState.Paired }
        .distinctUntilChanged { a, b -> a?.meta?.pairId == b?.meta?.pairId && a?.myUid == b?.myUid }

    val state: StateFlow<OurPageState> = paired.flatMapLatest { p ->
        if (p == null) flowOf(OurPageState(paired = false))
        else combine(wall.entries(p.meta.pairId).onStart { emit(emptyList()) }, cached, local) { entries, have, l ->
            entries.forEach { fetchIfMissing(p.meta.pairId, it, have) }
            OurPageState(entries.map { WallPhoto(it.id, it.from, fileOf(it.id).takeIf { _ -> it.id in have }?.path) }, l.adding, l.skipped)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), OurPageState())

    /** Shrinks each photo on this phone first (D-5); nothing past the wall's limit is sent. */
    fun add(uris: List<String>) {
        if (uris.isEmpty()) return
        local.update { it.copy(adding = true, skipped = 0) }
        viewModelScope.launch {
            val p = paired.first() ?: return@launch local.update { it.copy(adding = false) }
            var room = WallRepository.MAX - wall.entries(p.meta.pairId).first().size
            var skipped = 0
            for (uri in uris) {
                if (room <= 0) { skipped++; continue }
                val photo = withContext(io) { encode(uri) }
                if (photo == null) { skipped++; continue }
                when (val r = wall.add(p.meta.pairId, p.myUid, photo.jpeg, photo.width, photo.height)) {
                    is com.varad.unstoppableash.core.result.Outcome.Success -> {
                        // Mine is already here: keep it so this phone never downloads its own photo.
                        withContext(io) { cacheDir.mkdirs(); fileOf(r.value).writeBytes(photo.jpeg) }
                        cached.update { it + r.value }
                        room--
                    }
                    is com.varad.unstoppableash.core.result.Outcome.Failure -> skipped++
                }
            }
            local.update { it.copy(adding = false, skipped = skipped) }
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            val p = paired.first() ?: return@launch
            wall.remove(p.meta.pairId, id)
            withContext(io) { fileOf(id).delete() }
            cached.update { it - id }
        }
    }

    private fun fetchIfMissing(pairId: String, e: WallEntry, have: Set<String>) {
        if (e.id in have) return
        synchronized(fetching) { if (!fetching.add(e.id)) return }
        viewModelScope.launch {
            val ok = withContext(io) {
                val f = fileOf(e.id)
                if (f.exists()) return@withContext true
                val bytes = wall.photo(pairId, e.id) ?: return@withContext false
                cacheDir.mkdirs()
                val tmp = File(cacheDir, "${e.id}.part").apply { writeBytes(bytes) }
                tmp.renameTo(f)
            }
            synchronized(fetching) { fetching.remove(e.id) }
            if (ok) cached.update { it + e.id }
        }
    }

    private fun fileOf(id: String) = File(cacheDir, "${id.filter(Char::isLetterOrDigit)}.jpg")
}
