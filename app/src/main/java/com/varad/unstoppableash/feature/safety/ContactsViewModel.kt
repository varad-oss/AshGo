package com.varad.unstoppableash.feature.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varad.unstoppableash.data.local.EmergencyContactsStore
import com.varad.unstoppableash.domain.model.EmergencyContact
import com.varad.unstoppableash.domain.model.normalizedNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ContactsNotice { DUPLICATE, FULL, INVALID }

data class ContactsUiState(val contacts: List<EmergencyContact> = emptyList(), val notice: ContactsNotice? = null)

/** Emergency contacts (REDESIGN_SPEC §EmergencyContacts). They live on this phone only and never reach Firebase. */
class ContactsViewModel(private val store: EmergencyContactsStore) : ViewModel() {
    private val notice = MutableStateFlow<ContactsNotice?>(null)

    val state: StateFlow<ContactsUiState> = combine(store.contacts, notice, ::ContactsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    fun add(name: String, number: String) {
        viewModelScope.launch {
            val digits = normalizedNumber(number).count { it.isDigit() }
            notice.value = when {
                digits !in 3..15 -> ContactsNotice.INVALID
                store.contacts.first().size >= MAX_CONTACTS -> ContactsNotice.FULL
                !store.add(name, number) -> ContactsNotice.DUPLICATE
                else -> null
            }
        }
    }

    fun remove(id: String) {
        viewModelScope.launch { store.remove(id) }
    }

    fun clearNotice() { notice.value = null }

    companion object {
        /** Each SOS texts every contact; five keeps the cost and the time to send sensible. */
        const val MAX_CONTACTS = 5
    }
}
