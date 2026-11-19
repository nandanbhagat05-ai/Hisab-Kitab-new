package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CashEntry
import com.example.data.model.CashEntryType
import com.example.data.model.Customer
import com.example.data.model.CustomerWithBalance
import com.example.data.model.LedgerEntry
import com.example.data.model.LedgerEntryType
import com.example.data.model.LedgerEntryWithBalance
import com.example.data.model.PaymentMode
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.KhataRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class KhataDashboardData(
    val profile: UserProfile?,
    val customersWithBalance: List<CustomerWithBalance>,
    val allLedgerEntries: List<LedgerEntry>,
    val allCashEntries: List<CashEntry>,
    val totalToCollectPaise: Long,
    val totalToPayPaise: Long
)

class KhataViewModel(
    private val repository: KhataRepository,
    val currentUserId: String
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private fun <T> Flow<T>.toUiStateFlow(label: String): Flow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load $label"))
            }

    private val profileStateFlow: Flow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId).toUiStateFlow("profile")

    private val customersStateFlow: Flow<UiState<List<Customer>>> =
        repository.observeCustomers(currentUserId).toUiStateFlow("customers")

    private val ledgerStateFlow: Flow<UiState<List<LedgerEntry>>> =
        repository.observeLedgerEntries(currentUserId).toUiStateFlow("ledger entries")

    private val cashStateFlow: Flow<UiState<List<CashEntry>>> =
        repository.observeCashEntries(currentUserId).toUiStateFlow("cash entries")

    val dashboardState: StateFlow<UiState<KhataDashboardData>> = combine(
        profileStateFlow,
        customersStateFlow,
        ledgerStateFlow,
        cashStateFlow
    ) { profileState, customersState, ledgerState, cashState ->
        val firstError = listOf(profileState, customersState, ledgerState, cashState)
            .filterIsInstance<UiState.Error>()
            .firstOrNull()
        if (firstError != null) {
            return@combine firstError
        }

        if (profileState is UiState.Loading ||
            customersState is UiState.Loading ||
            ledgerState is UiState.Loading ||
            cashState is UiState.Loading
        ) {
            return@combine UiState.Loading
        }

        val profile = (profileState as UiState.Success).data
        val customers = (customersState as UiState.Success).data
        val ledgerEntries = (ledgerState as UiState.Success).data
            .sortedByDescending { it.entryDateMillis }
        val cashEntries = (cashState as UiState.Success).data
            .sortedWith(
                compareByDescending<CashEntry> { it.entryDateMillis }
                    .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
            )

        val entriesByCustomer = ledgerEntries.groupBy { it.customerId }
        val customersWithBalance = customers.map { customer ->
            val entries = entriesByCustomer[customer.id].orEmpty()
            var gaveTotal = 0L
            var gotTotal = 0L
            entries.forEach { entry ->
                when (entry.entryType) {
                    LedgerEntryType.GAVE -> gaveTotal += entry.amountPaise
                    LedgerEntryType.GOT -> gotTotal += entry.amountPaise
                }
            }
            val latestEntry = entries.maxWithOrNull(
                compareBy<LedgerEntry> { it.entryDateMillis }
                    .thenBy { it.createdAt ?: Timestamp(0, 0) }
            )
            val fallbackTime = customer.updatedAt?.toDate()?.time
                ?: customer.createdAt?.toDate()?.time
                ?: 0L
            CustomerWithBalance(
                customer = customer,
                totalGavePaise = gaveTotal,
                totalGotPaise = gotTotal,
                netBalancePaise = gaveTotal - gotTotal,
                entryCount = entries.size,
                lastActivityMillis = latestEntry?.entryDateMillis ?: fallbackTime,
                lastNote = latestEntry?.note.orEmpty()
            )
        }.sortedByDescending { it.lastActivityMillis }

        val totalToCollect = customersWithBalance
            .filter { it.netBalancePaise > 0L }
            .sumOf { it.netBalancePaise }
        val totalToPay = customersWithBalance
            .filter { it.netBalancePaise < 0L }
            .sumOf { -it.netBalancePaise }

        UiState.Success(
            KhataDashboardData(
                profile = profile,
                customersWithBalance = customersWithBalance,
                allLedgerEntries = ledgerEntries,
                allCashEntries = cashEntries,
                totalToCollectPaise = totalToCollect,
                totalToPayPaise = totalToPay
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    fun computeCustomerLedgerTimeline(
        entries: List<LedgerEntry>,
        customerId: String
    ): List<LedgerEntryWithBalance> {
        val chronological = entries
            .filter { it.customerId == customerId }
            .sortedWith(
                compareBy<LedgerEntry> { it.entryDateMillis }
                    .thenBy { it.createdAt ?: Timestamp(0, 0) }
            )
        var running = 0L
        val withBalances = chronological.map { entry ->
            when (entry.entryType) {
                LedgerEntryType.GAVE -> running += entry.amountPaise
                LedgerEntryType.GOT -> running -= entry.amountPaise
            }
            LedgerEntryWithBalance(entry = entry, runningBalancePaise = running)
        }
        return withBalances.reversed()
    }

    fun saveUserProfile(businessName: String, ownerName: String, phone: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveUserProfile(currentUserId, businessName, ownerName, phone)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _messages.tryEmit("Business profile updated")
                    onDone()
                },
                onFailure = { e ->
                    _messages.tryEmit(e.localizedMessage ?: "Failed to update profile")
                }
            )
        }
    }

    fun addCustomer(
        name: String,
        phone: String,
        notes: String,
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.addCustomer(currentUserId, name, phone, notes)
            _isSaving.value = false
            result.fold(
                onSuccess = { newId ->
                    _messages.tryEmit("Customer added")
                    onCreated(newId)
                },
                onFailure = { e ->
                    _messages.tryEmit(e.localizedMessage ?: "Failed to add customer")
                }
            )
        }
    }

    fun updateCustomer(
        customerId: String,
        name: String,
        phone: String,
        notes: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateCustomer(currentUserId, customerId, name, phone, notes)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _messages.tryEmit("Customer updated")
                    onDone()
                },
                onFailure = { e ->
                    _messages.tryEmit(e.localizedMessage ?: "Failed to update customer")
                }
            )
        }
    }

    fun deleteCustomer(customerId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteCustomerWithEntries(currentUserId, customerId)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _messages.tryEmit("Customer and entries deleted")
                    onDeleted()
                },
                onFailure = { e ->
                    _messages.tryEmit(e.localizedMessage ?: "Failed to delete customer")
                }
            )
        }
    }

    fun addLedgerEntry(
        customerId: String,
        type: LedgerEntryType,
        amountPaise: Long,
        note: String,
        entryDateMillis: Long,
        alsoRecordInCashbook: Boolean = false,
        customerName: String = "",
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.addLedgerEntry(
                userId = currentUserId,
                customerId = customerId,
                type = type,
                amountPaise = amountPaise,
                note = note,
                entryDateMillis = entryDateMillis
            )
            if (result.isSuccess && alsoRecordInCashbook) {
                val cashType = if (type == LedgerEntryType.GOT) CashEntryType.IN else CashEntryType.OUT
                val label = if (customerName.isNotBlank()) {
                    "${if (cashType == CashEntryType.IN) "From" else "To"} $customerName"
                } else {
                    "Khata Entry"
                }
                repository.addCashEntry(
                    userId = currentUserId,
                    type = cashType,
                    amountPaise = amountPaise,
                    category = "Khata Sync",
                    paymentMode = PaymentMode.CASH,
                    note = listOf(label, note.trim()).filter { it.isNotEmpty() }.joinToString(" • "),
                    entryDateMillis = entryDateMillis
                )
            }
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _messages.tryEmit(if (type == LedgerEntryType.GAVE) "Udhar recorded" else "Payment recorded")
                    onDone()
                },
                onFailure = { e ->
                    _messages.tryEmit(e.localizedMessage ?: "Failed to save entry")
                }
            )
        }
    }

    fun updateLedgerEntry(
        entryId: String,
        type: LedgerEntryType,
        amountPaise: Long,
        note: String,
        entryDateMillis: Long,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateLedgerEntry(
                userId = currentUserId,
                entryId = entryId,
                type = type,
                amountPaise = amountPaise,
                note = note,
                entryDateMillis = entryDateMillis
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _messages.tryEmit("Entry updated")
                    onDone()
                },
                onFailure = { e ->
                    _messages.tryEmit(e.localizedMessage ?: "Failed to update entry")
                }
            )
        }
    }

    fun deleteLedgerEntry(entryId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteLedgerEntry(currentUserId, entryId)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _messages.tryEmit("Entry deleted")
                    onDone()
                },
                onFailure = { e ->
                    _messages.tryEmit(e.localizedMessage ?: "Failed to delete entry")
                }
            )
        }
    }

    fun addCashEntry(
        type: CashEntryType,
        amountPaise: Long,
        category: String,
        paymentMode: PaymentMode,
        note: String,
        entryDateMillis: Long,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.addCashEntry(
                userId = currentUserId,
                type = type,
                amountPaise = amountPaise,
                category = category,
                paymentMode = paymentMode,
                note = note,
                entryDateMillis = entryDateMillis
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _messages.tryEmit(if (type == CashEntryType.IN) "Cash In saved" else "Cash Out saved")
                    onDone()
                },
                onFailure = { e ->
                    _messages.tryEmit(e.localizedMessage ?: "Failed to save cash entry")
                }
            )
        }
    }

    fun updateCashEntry(
        cashEntryId: String,
        type: CashEntryType,
        amountPaise: Long,
        category: String,
        paymentMode: PaymentMode,
        note: String,
        entryDateMillis: Long,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateCashEntry(
                userId = currentUserId,
                cashEntryId = cashEntryId,
                type = type,
                amountPaise = amountPaise,
                category = category,
                paymentMode = paymentMode,
                note = note,
                entryDateMillis = entryDateMillis
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _messages.tryEmit("Cash entry updated")
                    onDone()
                },
                onFailure = { e ->
                    _messages.tryEmit(e.localizedMessage ?: "Failed to update cash entry")
                }
            )
        }
    }

    fun deleteCashEntry(cashEntryId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteCashEntry(currentUserId, cashEntryId)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _messages.tryEmit("Cash entry deleted")
                    onDone()
                },
                onFailure = { e ->
                    _messages.tryEmit(e.localizedMessage ?: "Failed to delete cash entry")
                }
            )
        }
    }

    private companion object {
        const val TAG = "KhataViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
