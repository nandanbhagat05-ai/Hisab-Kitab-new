package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class LedgerEntryType {
    GAVE,
    GOT;

    companion object {
        fun fromRaw(raw: String): LedgerEntryType =
            entries.firstOrNull { it.name == raw } ?: GAVE
    }
}

enum class CashEntryType {
    IN,
    OUT;

    companion object {
        fun fromRaw(raw: String): CashEntryType =
            entries.firstOrNull { it.name == raw } ?: IN
    }
}

enum class PaymentMode {
    CASH,
    ONLINE;

    companion object {
        fun fromRaw(raw: String): PaymentMode =
            entries.firstOrNull { it.name == raw } ?: CASH
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val businessName: String = "AtharvNandan",
    val ownerName: String = "",
    val phone: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Customer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LedgerEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val customerId: String = "",
    val type: String = LedgerEntryType.GAVE.name,
    val amountPaise: Long = 0L,
    val note: String = "",
    val entryDateMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val entryType: LedgerEntryType
        get() = LedgerEntryType.fromRaw(type)
}

@Keep
@IgnoreExtraProperties
data class CashEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = CashEntryType.IN.name,
    val amountPaise: Long = 0L,
    val category: String = "Sales",
    val paymentMode: String = PaymentMode.CASH.name,
    val note: String = "",
    val entryDateMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val cashType: CashEntryType
        get() = CashEntryType.fromRaw(type)

    val mode: PaymentMode
        get() = PaymentMode.fromRaw(paymentMode)
}

data class CustomerWithBalance(
    val customer: Customer,
    val totalGavePaise: Long,
    val totalGotPaise: Long,
    /** Positive => Customer owes merchant (You will get / Lena Hai). Negative => Merchant owes customer (You will give / Dena Hai). */
    val netBalancePaise: Long,
    val entryCount: Int,
    val lastActivityMillis: Long,
    val lastNote: String
)

data class LedgerEntryWithBalance(
    val entry: LedgerEntry,
    /** Running net balance right after this entry (positive = customer owes, negative = advance). */
    val runningBalancePaise: Long
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
