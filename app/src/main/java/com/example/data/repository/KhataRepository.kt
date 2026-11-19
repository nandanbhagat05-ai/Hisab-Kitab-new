package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.CashEntry
import com.example.data.model.CashEntryType
import com.example.data.model.Customer
import com.example.data.model.LedgerEntry
import com.example.data.model.LedgerEntryType
import com.example.data.model.PaymentMode
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class KhataRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google to continue.")
        check(uid == expectedUserId) { "Signed-in account changed; please retry." }
        return uid
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        requireUserId(userId)
        val path = "users/$userId"
        userDoc(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                logAndRethrowIfNeeded(error, OperationType.GET, path, userId)
            }
            .collect { emit(it) }
    }

    fun observeCustomers(userId: String): Flow<List<Customer>> = flow {
        requireUserId(userId)
        val path = "users/$userId/customers"
        userDoc(userId).collection("customers")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Customer::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logAndRethrowIfNeeded(error, OperationType.LIST, path, userId)
            }
            .collect { emit(it) }
    }

    fun observeLedgerEntries(userId: String): Flow<List<LedgerEntry>> = flow {
        requireUserId(userId)
        val path = "users/$userId/ledgerEntries"
        userDoc(userId).collection("ledgerEntries")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    LedgerEntry::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logAndRethrowIfNeeded(error, OperationType.LIST, path, userId)
            }
            .collect { emit(it) }
    }

    fun observeCashEntries(userId: String): Flow<List<CashEntry>> = flow {
        requireUserId(userId)
        val path = "users/$userId/cashEntries"
        userDoc(userId).collection("cashEntries")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CashEntry::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logAndRethrowIfNeeded(error, OperationType.LIST, path, userId)
            }
            .collect { emit(it) }
    }

    private fun logAndRethrowIfNeeded(
        error: Throwable,
        op: OperationType,
        path: String,
        expectedUid: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    suspend fun saveUserProfile(
        userId: String,
        businessName: String,
        ownerName: String,
        phone: String
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val cleanBusiness = businessName.trim().take(60)
            require(cleanBusiness.isNotEmpty()) { "Business name cannot be empty." }
            val cleanOwner = ownerName.trim().take(60)
            val cleanPhone = phone.trim().take(20)

            val docRef = userDoc(uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "businessName" to cleanBusiness,
                        "ownerName" to cleanOwner,
                        "phone" to cleanPhone,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "businessName" to cleanBusiness,
                        "ownerName" to cleanOwner,
                        "phone" to cleanPhone,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addCustomer(
        userId: String,
        name: String,
        phone: String,
        notes: String
    ): Result<String> {
        var docPath = "users/$userId/customers"
        return try {
            val uid = requireUserId(userId)
            val cleanName = name.trim().take(60)
            require(cleanName.isNotEmpty()) { "Customer name is required." }
            val cleanPhone = phone.trim().take(20)
            val cleanNotes = notes.trim().take(150)

            val docRef = userDoc(uid).collection("customers").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "phone" to cleanPhone,
                "notes" to cleanNotes,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateCustomer(
        userId: String,
        customerId: String,
        name: String,
        phone: String,
        notes: String
    ): Result<Unit> {
        val docPath = "users/$userId/customers/$customerId"
        return try {
            val uid = requireUserId(userId)
            require(customerId.isNotBlank()) { "Customer ID is required." }
            val cleanName = name.trim().take(60)
            require(cleanName.isNotEmpty()) { "Customer name is required." }
            val cleanPhone = phone.trim().take(20)
            val cleanNotes = notes.trim().take(150)

            userDoc(uid).collection("customers").document(customerId).update(
                mapOf(
                    "name" to cleanName,
                    "phone" to cleanPhone,
                    "notes" to cleanNotes,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomerWithEntries(userId: String, customerId: String): Result<Unit> {
        val docPath = "users/$userId/customers/$customerId"
        return try {
            val uid = requireUserId(userId)
            require(customerId.isNotBlank()) { "Customer ID is required." }

            val entriesSnapshot = userDoc(uid).collection("ledgerEntries")
                .whereEqualTo("customerId", customerId)
                .get()
                .await()

            val refsToDelete = entriesSnapshot.documents.map { it.reference } +
                listOf(userDoc(uid).collection("customers").document(customerId))

            refsToDelete.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addLedgerEntry(
        userId: String,
        customerId: String,
        type: LedgerEntryType,
        amountPaise: Long,
        note: String,
        entryDateMillis: Long
    ): Result<String> {
        var docPath = "users/$userId/ledgerEntries"
        return try {
            val uid = requireUserId(userId)
            require(customerId.isNotBlank()) { "Customer ID is required." }
            require(amountPaise in 1L..MAX_AMOUNT_PAISE) { "Amount must be greater than ₹0." }
            require(entryDateMillis > 0L) { "Valid entry date is required." }
            val cleanNote = note.trim().take(200)

            val docRef = userDoc(uid).collection("ledgerEntries").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "customerId" to customerId,
                    "type" to type.name,
                    "amountPaise" to amountPaise,
                    "note" to cleanNote,
                    "entryDateMillis" to entryDateMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateLedgerEntry(
        userId: String,
        entryId: String,
        type: LedgerEntryType,
        amountPaise: Long,
        note: String,
        entryDateMillis: Long
    ): Result<Unit> {
        val docPath = "users/$userId/ledgerEntries/$entryId"
        return try {
            val uid = requireUserId(userId)
            require(entryId.isNotBlank()) { "Entry ID is required." }
            require(amountPaise in 1L..MAX_AMOUNT_PAISE) { "Amount must be greater than ₹0." }
            require(entryDateMillis > 0L) { "Valid entry date is required." }
            val cleanNote = note.trim().take(200)

            userDoc(uid).collection("ledgerEntries").document(entryId).update(
                mapOf(
                    "type" to type.name,
                    "amountPaise" to amountPaise,
                    "note" to cleanNote,
                    "entryDateMillis" to entryDateMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteLedgerEntry(userId: String, entryId: String): Result<Unit> {
        val docPath = "users/$userId/ledgerEntries/$entryId"
        return try {
            val uid = requireUserId(userId)
            require(entryId.isNotBlank()) { "Entry ID is required." }
            userDoc(uid).collection("ledgerEntries").document(entryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addCashEntry(
        userId: String,
        type: CashEntryType,
        amountPaise: Long,
        category: String,
        paymentMode: PaymentMode,
        note: String,
        entryDateMillis: Long
    ): Result<String> {
        var docPath = "users/$userId/cashEntries"
        return try {
            val uid = requireUserId(userId)
            require(amountPaise in 1L..MAX_AMOUNT_PAISE) { "Amount must be greater than ₹0." }
            val cleanCategory = category.trim().ifEmpty { "General" }.take(40)
            val cleanNote = note.trim().take(200)
            require(entryDateMillis > 0L) { "Valid entry date is required." }

            val docRef = userDoc(uid).collection("cashEntries").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "type" to type.name,
                    "amountPaise" to amountPaise,
                    "category" to cleanCategory,
                    "paymentMode" to paymentMode.name,
                    "note" to cleanNote,
                    "entryDateMillis" to entryDateMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateCashEntry(
        userId: String,
        cashEntryId: String,
        type: CashEntryType,
        amountPaise: Long,
        category: String,
        paymentMode: PaymentMode,
        note: String,
        entryDateMillis: Long
    ): Result<Unit> {
        val docPath = "users/$userId/cashEntries/$cashEntryId"
        return try {
            val uid = requireUserId(userId)
            require(cashEntryId.isNotBlank()) { "Cash entry ID is required." }
            require(amountPaise in 1L..MAX_AMOUNT_PAISE) { "Amount must be greater than ₹0." }
            val cleanCategory = category.trim().ifEmpty { "General" }.take(40)
            val cleanNote = note.trim().take(200)
            require(entryDateMillis > 0L) { "Valid entry date is required." }

            userDoc(uid).collection("cashEntries").document(cashEntryId).update(
                mapOf(
                    "type" to type.name,
                    "amountPaise" to amountPaise,
                    "category" to cleanCategory,
                    "paymentMode" to paymentMode.name,
                    "note" to cleanNote,
                    "entryDateMillis" to entryDateMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCashEntry(userId: String, cashEntryId: String): Result<Unit> {
        val docPath = "users/$userId/cashEntries/$cashEntryId"
        return try {
            val uid = requireUserId(userId)
            require(cashEntryId.isNotBlank()) { "Cash entry ID is required." }
            userDoc(uid).collection("cashEntries").document(cashEntryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        const val MAX_AMOUNT_PAISE = 100_000_000_000L
    }
}
