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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CustomerWithBalance
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.KhataDashboardData
import com.example.util.PdfAndShareHelper
import com.example.util.formatDateShort
import com.example.util.formatInr
import kotlin.math.abs

private enum class CustomerFilter(val label: String) {
    ALL("All"),
    TO_COLLECT("Lena Hai (Due)"),
    TO_PAY("Dena Hai"),
    SETTLED("Settled")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhataScreen(
    data: KhataDashboardData,
    isSaving: Boolean,
    onCustomerClick: (String) -> Unit,
    onAddCustomer: (name: String, phone: String, notes: String) -> Unit,
    onOpenBusinessProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(CustomerFilter.ALL) }
    var showAddCustomerSheet by remember { mutableStateOf(false) }

    val filteredCustomers = remember(data.customersWithBalance, searchQuery, selectedFilter) {
        data.customersWithBalance.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                item.customer.name.contains(searchQuery, ignoreCase = true) ||
                item.customer.phone.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                CustomerFilter.ALL -> true
                CustomerFilter.TO_COLLECT -> item.netBalancePaise > 0L
                CustomerFilter.TO_PAY -> item.netBalancePaise < 0L
                CustomerFilter.SETTLED -> item.netBalancePaise == 0L
            }
            matchesQuery && matchesFilter
        }
    }

    val businessName = data.profile?.businessName?.ifBlank { "AtharvNandan" } ?: "AtharvNandan"
    val businessPhone = data.profile?.phone.orEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = businessName,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${data.customersWithBalance.size} Customers • Auto Hisab",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenBusinessProfile,
                        modifier = Modifier.testTag("business_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Storefront,
                            contentDescription = "Business Profile"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddCustomerSheet = true },
                icon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                text = { Text("Add Customer") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_customer_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                KhataSummaryHeroCard(
                    totalToCollectPaise = data.totalToCollectPaise,
                    totalToPayPaise = data.totalToPayPaise
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search customer name or phone...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_search_input")
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomerFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }

            if (filteredCustomers.isEmpty()) {
                item {
                    EmptyKhataCard(
                        hasAnyCustomers = data.customersWithBalance.isNotEmpty(),
                        onAddCustomer = { showAddCustomerSheet = true }
                    )
                }
            } else {
                items(
                    items = filteredCustomers,
                    key = { it.customer.id }
                ) { item ->
                    CustomerRowCard(
                        item = item,
                        onClick = { onCustomerClick(item.customer.id) },
                        onSendReminder = {
                            PdfAndShareHelper.shareWhatsAppReminder(
                                context = context,
                                customerName = item.customer.name,
                                customerPhone = item.customer.phone,
                                netBalancePaise = item.netBalancePaise,
                                businessName = businessName,
                                businessPhone = businessPhone
                            )
                        }
                    )
                }
            }
        }
    }

    if (showAddCustomerSheet) {
        CustomerFormSheet(
            title = "Add New Customer",
            initialName = "",
            initialPhone = "",
            initialNotes = "",
            isSaving = isSaving,
            onSave = { name, phone, notes ->
                onAddCustomer(name, phone, notes)
                showAddCustomerSheet = false
            },
            onDismiss = { showAddCustomerSheet = false }
        )
    }
}

@Composable
private fun KhataSummaryHeroCard(
    totalToCollectPaise: Long,
    totalToPayPaise: Long
) {
    val netPaise = totalToCollectPaise - totalToPayPaise
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                        )
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (netPaise >= 0) "Net Khata (You will get)" else "Net Khata (You will give)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatInr(abs(netPaise)),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = JetBrainsMonoFamily
                        ),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Text(
                        text = "AUTO HISAB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "You will get (Lena)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = formatInr(totalToCollectPaise),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "You will give (Dena)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = formatInr(totalToPayPaise),
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
}

@Composable
private fun CustomerRowCard(
    item: CustomerWithBalance,
    onClick: () -> Unit,
    onSendReminder: () -> Unit
) {
    val customer = item.customer
    val initials = customer.name
        .trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "K" }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("customer_card_${customer.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildString {
                    if (item.lastNote.isNotBlank()) {
                        append(item.lastNote)
                    } else if (customer.phone.isNotBlank()) {
                        append(customer.phone)
                    } else {
                        append("${item.entryCount} entries")
                    }
                    if (item.lastActivityMillis > 0L) {
                        append(" • ")
                        append(formatDateShort(item.lastActivityMillis))
                    }
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                val (amountText, subLabel, color) = when {
                    item.netBalancePaise > 0L -> Triple(
                        formatInr(item.netBalancePaise),
                        "Lena Hai",
                        MaterialTheme.colorScheme.tertiary
                    )
                    item.netBalancePaise < 0L -> Triple(
                        formatInr(abs(item.netBalancePaise)),
                        "Dena Hai",
                        MaterialTheme.colorScheme.error
                    )
                    else -> Triple(
                        "₹0",
                        "Settled",
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = color
                )
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = color
                )
            }

            if (item.netBalancePaise > 0L) {
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onSendReminder,
                    modifier = Modifier.testTag("quick_remind_${customer.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send WhatsApp reminder to ${customer.name}",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyKhataCard(
    hasAnyCustomers: Boolean,
    onAddCustomer: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = if (hasAnyCustomers) "No matching customers" else "Start your Digital Khata",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = if (hasAnyCustomers) {
                    "Try clearing your search or changing the filter chip."
                } else {
                    "Add your first customer to track Udhar (credit) and Jama (payments) with automatic hisab."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasAnyCustomers) {
                Spacer(Modifier.height(4.dp))
                Button(onClick = onAddCustomer) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add First Customer")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerFormSheet(
    title: String,
    initialName: String,
    initialPhone: String,
    initialNotes: String,
    isSaving: Boolean,
    onSave: (name: String, phone: String, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var notes by remember { mutableStateOf(initialNotes) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 60) name = it },
                label = { Text("Customer Name *") },
                placeholder = { Text("e.g., Ramesh Traders") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("customer_name_input")
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { if (it.length <= 20) phone = it },
                label = { Text("WhatsApp / Mobile Number") },
                placeholder = { Text("e.g., 9876543210") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("customer_phone_input")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 150) notes = it },
                label = { Text("Address / Notes (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { onSave(name, phone, notes) },
                enabled = !isSaving && name.trim().isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_customer_button")
            ) {
                Text(if (isSaving) "Saving..." else "Save Customer")
            }
        }
    }
}
