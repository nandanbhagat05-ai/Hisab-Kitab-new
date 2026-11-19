package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CustomerWithBalance
import com.example.data.model.LedgerEntry
import com.example.data.model.LedgerEntryType
import com.example.data.model.LedgerEntryWithBalance
import com.example.ui.components.CompactCalculatorKeypad
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.PdfAndShareHelper
import com.example.util.formatDateShort
import com.example.util.formatDateTimeShort
import com.example.util.formatInr
import com.example.util.formatRupeeInputFromPaise
import com.example.util.parseExpressionToPaise
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerWithBalance: CustomerWithBalance?,
    timeline: List<LedgerEntryWithBalance>,
    businessName: String,
    businessPhone: String,
    isSaving: Boolean,
    onBack: () -> Unit,
    onUpdateCustomer: (name: String, phone: String, notes: String) -> Unit,
    onDeleteCustomer: () -> Unit,
    onAddEntry: (
        type: LedgerEntryType,
        amountPaise: Long,
        note: String,
        dateMillis: Long,
        alsoCashbook: Boolean
    ) -> Unit,
    onUpdateEntry: (
        entryId: String,
        type: LedgerEntryType,
        amountPaise: Long,
        note: String,
        dateMillis: Long
    ) -> Unit,
    onDeleteEntry: (String) -> Unit
) {
    val context = LocalContext.current

    if (customerWithBalance == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Customer Khata") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Customer not found.", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    val customer = customerWithBalance.customer
    var activeEntrySheetType by remember { mutableStateOf<LedgerEntryType?>(null) }
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var showEditCustomerSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = customer.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (customer.phone.isNotBlank()) {
                            Text(
                                text = customer.phone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showEditCustomerSheet = true },
                        modifier = Modifier.testTag("edit_customer_button")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit Customer")
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("delete_customer_button")
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete Customer")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            editingEntry = null
                            activeEntrySheetType = LedgerEntryType.GAVE
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("you_gave_button")
                    ) {
                        Text("You Gave ₹", style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = {
                            editingEntry = null
                            activeEntrySheetType = LedgerEntryType.GOT
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("you_got_button")
                    ) {
                        Text("You Got ₹", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CustomerBalanceCard(customerWithBalance = customerWithBalance)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            PdfAndShareHelper.shareWhatsAppReminder(
                                context = context,
                                customerName = customer.name,
                                customerPhone = customer.phone,
                                netBalancePaise = customerWithBalance.netBalancePaise,
                                businessName = businessName,
                                businessPhone = businessPhone
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("whatsapp_reminder_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Remind", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            val pdfResult = PdfAndShareHelper.generateCustomerStatementPdf(
                                context = context,
                                businessName = businessName,
                                customer = customer,
                                entries = timeline.reversed(), // chronological in PDF
                                netBalancePaise = customerWithBalance.netBalancePaise
                            )
                            pdfResult.fold(
                                onSuccess = { file ->
                                    PdfAndShareHelper.sharePdfFile(
                                        context = context,
                                        file = file,
                                        shareTitle = "${customer.name} Khata Statement"
                                    )
                                },
                                onFailure = { err ->
                                    Toast.makeText(
                                        context,
                                        err.localizedMessage ?: "Failed to create PDF",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("customer_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("PDF", maxLines = 1)
                    }

                    if (customer.phone.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                val dialIntent = Intent(
                                    Intent.ACTION_DIAL,
                                    Uri.parse("tel:${customer.phone.trim()}")
                                )
                                context.startActivity(dialIntent)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.9f)
                                .height(48.dp)
                                .testTag("call_customer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = "Call ${customer.name}",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (timeline.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("No transactions yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Tap 'You Gave ₹' when you give credit (Udhar) or 'You Got ₹' when ${customer.name} pays you.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = timeline,
                    key = { it.entry.id }
                ) { row ->
                    LedgerTimelineRow(
                        item = row,
                        onClick = {
                            editingEntry = row.entry
                            activeEntrySheetType = row.entry.entryType
                        }
                    )
                }
            }
        }
    }

    if (activeEntrySheetType != null) {
        LedgerEntrySheet(
            initialType = activeEntrySheetType!!,
            existingEntry = editingEntry,
            isSaving = isSaving,
            onSave = { type, amountPaise, note, dateMillis, alsoCashbook ->
                val currentEditing = editingEntry
                if (currentEditing == null) {
                    onAddEntry(type, amountPaise, note, dateMillis, alsoCashbook)
                } else {
                    onUpdateEntry(currentEditing.id, type, amountPaise, note, dateMillis)
                }
                activeEntrySheetType = null
                editingEntry = null
            },
            onDelete = editingEntry?.let { entry ->
                {
                    onDeleteEntry(entry.id)
                    activeEntrySheetType = null
                    editingEntry = null
                }
            },
            onDismiss = {
                activeEntrySheetType = null
                editingEntry = null
            }
        )
    }

    if (showEditCustomerSheet) {
        CustomerFormSheet(
            title = "Edit Customer",
            initialName = customer.name,
            initialPhone = customer.phone,
            initialNotes = customer.notes,
            isSaving = isSaving,
            onSave = { name, phone, notes ->
                onUpdateCustomer(name, phone, notes)
                showEditCustomerSheet = false
            },
            onDismiss = { showEditCustomerSheet = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${customer.name}?") },
            text = {
                Text("This will permanently remove this customer and all ${timeline.size} ledger entries from your Khata.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteCustomer()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CustomerBalanceCard(customerWithBalance: CustomerWithBalance) {
    val net = customerWithBalance.netBalancePaise
    val (bgColor, accentColor, label) = when {
        net > 0L -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.tertiary,
            "You will get (Lena Hai)"
        )
        net < 0L -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            "You will give (Dena Hai)"
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Account Settled"
        )
    }

    Surface(
        color = bgColor,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor
                    )
                    Text(
                        text = formatInr(abs(net)),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = JetBrainsMonoFamily
                        ),
                        color = accentColor
                    )
                }
            }

            HorizontalDivider(color = accentColor.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Gave: ${formatInr(customerWithBalance.totalGavePaise)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Total Got: ${formatInr(customerWithBalance.totalGotPaise)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun LedgerTimelineRow(
    item: LedgerEntryWithBalance,
    onClick: () -> Unit
) {
    val entry = item.entry
    val isGave = entry.entryType == LedgerEntryType.GAVE
    val amountColor = if (isGave) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    val badgeBg = if (isGave) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("ledger_entry_${entry.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDateTimeShort(entry.entryDateMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = entry.note.ifBlank {
                        if (isGave) "Credit given (Udhar)" else "Payment received (Jama)"
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                val balText = when {
                    item.runningBalancePaise > 0L -> "Bal: ${formatInr(item.runningBalancePaise)} Lena"
                    item.runningBalancePaise < 0L -> "Bal: ${formatInr(abs(item.runningBalancePaise))} Dena"
                    else -> "Bal: Settled"
                }
                Text(
                    text = balText,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isGave) "GAVE" else "GOT",
                        style = MaterialTheme.typography.labelSmall,
                        color = amountColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatInr(entry.amountPaise),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = amountColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerEntrySheet(
    initialType: LedgerEntryType,
    existingEntry: LedgerEntry?,
    isSaving: Boolean,
    onSave: (
        type: LedgerEntryType,
        amountPaise: Long,
        note: String,
        dateMillis: Long,
        alsoCashbook: Boolean
    ) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember(existingEntry, initialType) { mutableStateOf(existingEntry?.entryType ?: initialType) }
    var amountExpr by remember(existingEntry) {
        mutableStateOf(
            if (existingEntry != null) formatRupeeInputFromPaise(existingEntry.amountPaise) else ""
        )
    }
    var note by remember(existingEntry) { mutableStateOf(existingEntry?.note ?: "") }
    var dateMillis by remember(existingEntry) {
        mutableLongStateOf(existingEntry?.entryDateMillis ?: System.currentTimeMillis())
    }
    var alsoCashbook by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val parsedPaise = remember(amountExpr) { parseExpressionToPaise(amountExpr) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingEntry == null) "Record Khata Entry" else "Edit Khata Entry",
                    style = MaterialTheme.typography.headlineSmall
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_entry_button")) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete entry",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == LedgerEntryType.GAVE,
                    onClick = { type = LedgerEntryType.GAVE },
                    label = { Text("You Gave (Udhar)") }
                )
                FilterChip(
                    selected = type == LedgerEntryType.GOT,
                    onClick = { type = LedgerEntryType.GOT },
                    label = { Text("You Got (Jama)") }
                )
            }

            OutlinedTextField(
                value = amountExpr,
                onValueChange = { amountExpr = it },
                label = { Text("Amount (supports 250+120*2) *") },
                prefix = { Text("₹ ") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ledger_amount_input")
            )

            CompactCalculatorKeypad(
                expression = amountExpr,
                onExpressionChange = { amountExpr = it }
            )

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 200) note = it },
                label = { Text("Item / Note (optional)") },
                placeholder = { Text("e.g., Ration bill, UPI payment") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ledger_note_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(formatDateShort(dateMillis))
                }
            }

            if (existingEntry == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Also sync to Daily Cashbook",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (type == LedgerEntryType.GOT) "Adds Cash In entry automatically"
                            else "Adds Cash Out entry automatically",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = alsoCashbook,
                        onCheckedChange = { alsoCashbook = it }
                    )
                }
            }

            Button(
                onClick = {
                    val amt = parsedPaise ?: return@Button
                    onSave(type, amt, note, dateMillis, alsoCashbook)
                },
                enabled = !isSaving && parsedPaise != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (type == LedgerEntryType.GAVE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_ledger_entry_button")
            ) {
                val amountLabel = parsedPaise?.let { " ${formatInr(it)}" } ?: ""
                Text(if (isSaving) "Saving..." else "Save$amountLabel")
            }
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { dateMillis = it }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }
}
