package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Payments
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CashEntry
import com.example.data.model.CashEntryType
import com.example.data.model.PaymentMode
import com.example.ui.components.CashDenominationCalculatorSheet
import com.example.ui.components.CompactCalculatorKeypad
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.KhataDashboardData
import com.example.util.formatDateShort
import com.example.util.formatDateTimeShort
import com.example.util.formatInr
import com.example.util.formatRupeeInputFromPaise
import com.example.util.isSameDay
import com.example.util.parseExpressionToPaise
import com.example.util.startOfDaysAgoMillis
import com.example.util.startOfTodayMillis
import kotlin.math.abs

private enum class CashPeriodFilter(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    WEEK("Last 7 Days"),
    ALL("All Time")
}

private val CASH_CATEGORIES = listOf(
    "Daily Sales",
    "Supplier",
    "Rent",
    "Salary",
    "Electricity",
    "Expense",
    "Cash Tally",
    "General"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashbookScreen(
    data: KhataDashboardData,
    isSaving: Boolean,
    onAddCashEntry: (
        type: CashEntryType,
        amountPaise: Long,
        category: String,
        paymentMode: PaymentMode,
        note: String,
        dateMillis: Long
    ) -> Unit,
    onUpdateCashEntry: (
        cashEntryId: String,
        type: CashEntryType,
        amountPaise: Long,
        category: String,
        paymentMode: PaymentMode,
        note: String,
        dateMillis: Long
    ) -> Unit,
    onDeleteCashEntry: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPeriod by remember { mutableStateOf(CashPeriodFilter.TODAY) }
    var activeSheetType by remember { mutableStateOf<CashEntryType?>(null) }
    var editingEntry by remember { mutableStateOf<CashEntry?>(null) }
    var showDenominationCalc by remember { mutableStateOf(false) }

    val now = remember { System.currentTimeMillis() }
    val todayStart = remember(now) { startOfTodayMillis(now) }
    val yesterdayStart = remember(now) { startOfDaysAgoMillis(1, now) }
    val weekStart = remember(now) { startOfDaysAgoMillis(6, now) }

    val filteredEntries = remember(data.allCashEntries, selectedPeriod) {
        data.allCashEntries.filter { entry ->
            when (selectedPeriod) {
                CashPeriodFilter.TODAY -> isSameDay(entry.entryDateMillis, todayStart)
                CashPeriodFilter.YESTERDAY -> isSameDay(entry.entryDateMillis, yesterdayStart)
                CashPeriodFilter.WEEK -> entry.entryDateMillis >= weekStart
                CashPeriodFilter.ALL -> true
            }
        }
    }

    val periodInPaise = remember(filteredEntries) {
        filteredEntries.filter { it.cashType == CashEntryType.IN }.sumOf { it.amountPaise }
    }
    val periodOutPaise = remember(filteredEntries) {
        filteredEntries.filter { it.cashType == CashEntryType.OUT }.sumOf { it.amountPaise }
    }

    val overallCashInHandPaise = remember(data.allCashEntries) {
        val cashIn = data.allCashEntries
            .filter { it.cashType == CashEntryType.IN && it.mode == PaymentMode.CASH }
            .sumOf { it.amountPaise }
        val cashOut = data.allCashEntries
            .filter { it.cashType == CashEntryType.OUT && it.mode == PaymentMode.CASH }
            .sumOf { it.amountPaise }
        cashIn - cashOut
    }

    val overallOnlineBalancePaise = remember(data.allCashEntries) {
        val onlineIn = data.allCashEntries
            .filter { it.cashType == CashEntryType.IN && it.mode == PaymentMode.ONLINE }
            .sumOf { it.amountPaise }
        val onlineOut = data.allCashEntries
            .filter { it.cashType == CashEntryType.OUT && it.mode == PaymentMode.ONLINE }
            .sumOf { it.amountPaise }
        onlineIn - onlineOut
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Daily Cashbook (Galla)", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Track Cash In, Expenses & Note Tally",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDenominationCalc = true },
                        modifier = Modifier.testTag("top_cash_tally_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Calculate,
                            contentDescription = "Cash Tally Calculator"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            editingEntry = null
                            activeSheetType = CashEntryType.IN
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("cash_in_button")
                    ) {
                        Text("+ Cash In", style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = {
                            editingEntry = null
                            activeSheetType = CashEntryType.OUT
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("cash_out_button")
                    ) {
                        Text("- Cash Out", style = MaterialTheme.typography.labelLarge)
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
                CashbookHeroCard(
                    periodLabel = selectedPeriod.label,
                    periodInPaise = periodInPaise,
                    periodOutPaise = periodOutPaise,
                    overallCashInHandPaise = overallCashInHandPaise,
                    overallOnlineBalancePaise = overallOnlineBalancePaise
                )
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { showDenominationCalc = true }
                        .testTag("open_cash_tally_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Calculate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cash Tally Calculator (Note Ginti)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Count ₹2000–₹1 notes & match Galla (${formatInr(overallCashInHandPaise)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CashPeriodFilter.entries.forEach { period ->
                        FilterChip(
                            selected = selectedPeriod == period,
                            onClick = { selectedPeriod = period },
                            label = { Text(period.label) }
                        )
                    }
                }
            }

            if (filteredEntries.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "No cash entries for ${selectedPeriod.label}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Record daily counter sales (+ Cash In) and shop expenses (- Cash Out) for automatic cash calculation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredEntries,
                    key = { it.id }
                ) { entry ->
                    CashEntryRowCard(
                        entry = entry,
                        onClick = {
                            editingEntry = entry
                            activeSheetType = entry.cashType
                        }
                    )
                }
            }
        }
    }

    if (activeSheetType != null) {
        CashEntrySheet(
            initialType = activeSheetType!!,
            existingEntry = editingEntry,
            isSaving = isSaving,
            onSave = { type, amountPaise, category, mode, note, dateMillis ->
                val current = editingEntry
                if (current == null) {
                    onAddCashEntry(type, amountPaise, category, mode, note, dateMillis)
                } else {
                    onUpdateCashEntry(current.id, type, amountPaise, category, mode, note, dateMillis)
                }
                activeSheetType = null
                editingEntry = null
            },
            onDelete = editingEntry?.let { entry ->
                {
                    onDeleteCashEntry(entry.id)
                    activeSheetType = null
                    editingEntry = null
                }
            },
            onDismiss = {
                activeSheetType = null
                editingEntry = null
            }
        )
    }

    if (showDenominationCalc) {
        CashDenominationCalculatorSheet(
            bookCashInHandPaise = overallCashInHandPaise,
            onDismiss = { showDenominationCalc = false },
            onSaveDifferenceAsCashEntry = { diffPaise, note ->
                val type = if (diffPaise > 0) CashEntryType.IN else CashEntryType.OUT
                onAddCashEntry(
                    type,
                    abs(diffPaise),
                    "Cash Tally",
                    PaymentMode.CASH,
                    note,
                    System.currentTimeMillis()
                )
                showDenominationCalc = false
            }
        )
    }
}

@Composable
private fun CashbookHeroCard(
    periodLabel: String,
    periodInPaise: Long,
    periodOutPaise: Long,
    overallCashInHandPaise: Long,
    overallOnlineBalancePaise: Long
) {
    val totalBalance = overallCashInHandPaise + overallOnlineBalancePaise
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Cash + Online Balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatInr(totalBalance),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = JetBrainsMonoFamily
                        ),
                        color = if (totalBalance >= 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Payments,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Cash in Hand (Galla)", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = formatInr(overallCashInHandPaise),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Online / UPI", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = formatInr(overallOnlineBalancePaise),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "$periodLabel In",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "+ ${formatInr(periodInPaise)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$periodLabel Out",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "- ${formatInr(periodOutPaise)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun CashEntryRowCard(
    entry: CashEntry,
    onClick: () -> Unit
) {
    val isIn = entry.cashType == CashEntryType.IN
    val color = if (isIn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("cash_entry_${entry.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = entry.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = entry.paymentMode,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (entry.note.isNotBlank()) {
                    Text(
                        text = entry.note,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formatDateTimeShort(entry.entryDateMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = (if (isIn) "+ " else "- ") + formatInr(entry.amountPaise),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = color
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CashEntrySheet(
    initialType: CashEntryType,
    existingEntry: CashEntry?,
    isSaving: Boolean,
    onSave: (
        type: CashEntryType,
        amountPaise: Long,
        category: String,
        paymentMode: PaymentMode,
        note: String,
        dateMillis: Long
    ) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember(existingEntry, initialType) { mutableStateOf(existingEntry?.cashType ?: initialType) }
    var amountExpr by remember(existingEntry) {
        mutableStateOf(
            if (existingEntry != null) formatRupeeInputFromPaise(existingEntry.amountPaise) else ""
        )
    }
    var category by remember(existingEntry) {
        mutableStateOf(
            existingEntry?.category ?: if (initialType == CashEntryType.IN) "Daily Sales" else "Supplier"
        )
    }
    var paymentMode by remember(existingEntry) {
        mutableStateOf(existingEntry?.mode ?: PaymentMode.CASH)
    }
    var note by remember(existingEntry) { mutableStateOf(existingEntry?.note ?: "") }
    var dateMillis by remember(existingEntry) {
        mutableLongStateOf(existingEntry?.entryDateMillis ?: System.currentTimeMillis())
    }
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
                    text = if (existingEntry == null) "Record Cash Entry" else "Edit Cash Entry",
                    style = MaterialTheme.typography.headlineSmall
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete cash entry",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == CashEntryType.IN,
                    onClick = { type = CashEntryType.IN },
                    label = { Text("+ Cash In (Jama)") }
                )
                FilterChip(
                    selected = type == CashEntryType.OUT,
                    onClick = { type = CashEntryType.OUT },
                    label = { Text("- Cash Out (Kharch)") }
                )
            }

            OutlinedTextField(
                value = amountExpr,
                onValueChange = { amountExpr = it },
                label = { Text("Amount (supports 500+250) *") },
                prefix = { Text("₹ ") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cash_amount_input")
            )

            CompactCalculatorKeypad(
                expression = amountExpr,
                onExpressionChange = { amountExpr = it }
            )

            Text("Category", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CASH_CATEGORIES.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = paymentMode == PaymentMode.CASH,
                    onClick = { paymentMode = PaymentMode.CASH },
                    label = { Text("Cash") }
                )
                FilterChip(
                    selected = paymentMode == PaymentMode.ONLINE,
                    onClick = { paymentMode = PaymentMode.ONLINE },
                    label = { Text("Online / UPI") }
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(formatDateShort(dateMillis), style = MaterialTheme.typography.labelMedium)
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 200) note = it },
                label = { Text("Note / Remark (optional)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cash_note_input")
            )

            Button(
                onClick = {
                    val amt = parsedPaise ?: return@Button
                    onSave(type, amt, category, paymentMode, note, dateMillis)
                },
                enabled = !isSaving && parsedPaise != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (type == CashEntryType.IN) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_cash_entry_button")
            ) {
                val amtStr = parsedPaise?.let { " ${formatInr(it)}" } ?: ""
                Text(if (isSaving) "Saving..." else "Save$amtStr")
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
