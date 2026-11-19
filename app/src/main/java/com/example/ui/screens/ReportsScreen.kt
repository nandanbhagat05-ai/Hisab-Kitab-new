package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CashEntryType
import com.example.data.model.LedgerEntryType
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.KhataDashboardData
import com.example.util.PdfAndShareHelper
import com.example.util.formatDayMonth
import com.example.util.formatInr
import com.example.util.isSameDay
import com.example.util.startOfDaysAgoMillis
import com.example.util.startOfMonthMillis
import com.example.util.startOfTodayMillis

private enum class ReportPeriod(val label: String) {
    TODAY("Today"),
    WEEK("Last 7 Days"),
    MONTH("This Month"),
    ALL("All Time")
}

private data class DailyCashBar(
    val label: String,
    val cashInPaise: Long,
    val cashOutPaise: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    data: KhataDashboardData,
    onCustomerClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.WEEK) }

    val now = remember { System.currentTimeMillis() }
    val todayStart = remember(now) { startOfTodayMillis(now) }
    val weekStart = remember(now) { startOfDaysAgoMillis(6, now) }
    val monthStart = remember(now) { startOfMonthMillis(now) }

    val periodLedger = remember(data.allLedgerEntries, selectedPeriod) {
        data.allLedgerEntries.filter { entry ->
            when (selectedPeriod) {
                ReportPeriod.TODAY -> entry.entryDateMillis >= todayStart
                ReportPeriod.WEEK -> entry.entryDateMillis >= weekStart
                ReportPeriod.MONTH -> entry.entryDateMillis >= monthStart
                ReportPeriod.ALL -> true
            }
        }
    }

    val periodCash = remember(data.allCashEntries, selectedPeriod) {
        data.allCashEntries.filter { entry ->
            when (selectedPeriod) {
                ReportPeriod.TODAY -> entry.entryDateMillis >= todayStart
                ReportPeriod.WEEK -> entry.entryDateMillis >= weekStart
                ReportPeriod.MONTH -> entry.entryDateMillis >= monthStart
                ReportPeriod.ALL -> true
            }
        }
    }

    val totalGave = remember(periodLedger) {
        periodLedger.filter { it.entryType == LedgerEntryType.GAVE }.sumOf { it.amountPaise }
    }
    val totalGot = remember(periodLedger) {
        periodLedger.filter { it.entryType == LedgerEntryType.GOT }.sumOf { it.amountPaise }
    }
    val totalCashIn = remember(periodCash) {
        periodCash.filter { it.cashType == CashEntryType.IN }.sumOf { it.amountPaise }
    }
    val totalCashOut = remember(periodCash) {
        periodCash.filter { it.cashType == CashEntryType.OUT }.sumOf { it.amountPaise }
    }

    val last7DaysBars = remember(data.allCashEntries) {
        (6 downTo 0).map { daysAgo ->
            val dayMillis = startOfDaysAgoMillis(daysAgo, now)
            val entries = data.allCashEntries.filter { isSameDay(it.entryDateMillis, dayMillis) }
            DailyCashBar(
                label = formatDayMonth(dayMillis),
                cashInPaise = entries.filter { it.cashType == CashEntryType.IN }.sumOf { it.amountPaise },
                cashOutPaise = entries.filter { it.cashType == CashEntryType.OUT }.sumOf { it.amountPaise }
            )
        }
    }

    val topPendingCustomers = remember(data.customersWithBalance) {
        data.customersWithBalance
            .filter { it.netBalancePaise > 0L }
            .sortedByDescending { it.netBalancePaise }
            .take(10)
    }

    val businessName = data.profile?.businessName?.ifBlank { "AtharvNandan" } ?: "AtharvNandan"
    val businessPhone = data.profile?.phone.orEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Hisab Reports & PDF", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Analytics, PDF Export & WhatsApp Reminders",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReportPeriod.entries.forEach { period ->
                        FilterChip(
                            selected = selectedPeriod == period,
                            onClick = { selectedPeriod = period },
                            label = { Text(period.label) }
                        )
                    }
                }
            }

            // Export Actions Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Share Reports",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(
                            onClick = {
                                val res = PdfAndShareHelper.generateBusinessReportPdf(
                                    context = context,
                                    businessName = businessName,
                                    periodLabel = selectedPeriod.label,
                                    customersWithBalance = data.customersWithBalance,
                                    cashEntries = periodCash,
                                    ledgerEntries = periodLedger
                                )
                                res.fold(
                                    onSuccess = { file ->
                                        PdfAndShareHelper.sharePdfFile(
                                            context = context,
                                            file = file,
                                            shareTitle = "$businessName - ${selectedPeriod.label} PDF Report"
                                        )
                                    },
                                    onFailure = { err ->
                                        Toast.makeText(
                                            context,
                                            err.localizedMessage ?: "Failed to generate PDF",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("share_business_pdf_button")
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Share Full Business PDF (${selectedPeriod.label})")
                        }

                        OutlinedButton(
                            onClick = {
                                val summaryText = buildString {
                                    appendLine("*$businessName — Hisab Summary (${selectedPeriod.label})*")
                                    appendLine("Total Lena (To Collect): ${formatInr(data.totalToCollectPaise)}")
                                    appendLine("Total Dena (To Pay): ${formatInr(data.totalToPayPaise)}")
                                    appendLine("Udhar Given: ${formatInr(totalGave)}")
                                    appendLine("Payment Got: ${formatInr(totalGot)}")
                                    appendLine("Cash In: ${formatInr(totalCashIn)}")
                                    appendLine("Cash Out: ${formatInr(totalCashOut)}")
                                    appendLine("Net Cashflow: ${formatInr(totalCashIn - totalCashOut)}")
                                }
                                PdfAndShareHelper.shareTextSummary(
                                    context = context,
                                    title = "Share Business Summary",
                                    message = summaryText
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("share_text_report_button")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Share Text Summary on WhatsApp")
                        }
                    }
                }
            }

            // Summary Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ReportMetricCard(
                            title = "Udhar Given",
                            value = formatInr(totalGave),
                            valueColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        ReportMetricCard(
                            title = "Payment Received",
                            value = formatInr(totalGot),
                            valueColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ReportMetricCard(
                            title = "Cash In (${selectedPeriod.label})",
                            value = formatInr(totalCashIn),
                            valueColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                        ReportMetricCard(
                            title = "Cash Out (${selectedPeriod.label})",
                            value = formatInr(totalCashOut),
                            valueColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 7-Day Cashflow Chart
            item {
                CashflowBarChartCard(bars = last7DaysBars)
            }

            // Pending Collections with WhatsApp Reminder buttons
            item {
                Text(
                    text = "Pending Collections (${topPendingCustomers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (topPendingCustomers.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "All customers are settled! No pending dues right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(
                    items = topPendingCustomers,
                    key = { it.customer.id }
                ) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium,
                        onClick = { onCustomerClick(item.customer.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.customer.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.customer.phone.ifBlank { "No phone saved" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = formatInr(item.netBalancePaise),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    PdfAndShareHelper.shareWhatsAppReminder(
                                        context = context,
                                        customerName = item.customer.name,
                                        customerPhone = item.customer.phone,
                                        netBalancePaise = item.netBalancePaise,
                                        businessName = businessName,
                                        businessPhone = businessPhone
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send WhatsApp reminder",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportMetricCard(
    title: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CashflowBarChartCard(bars: List<DailyCashBar>) {
    val maxVal = remember(bars) {
        bars.maxOfOrNull { maxOf(it.cashInPaise, it.cashOutPaise) }?.coerceAtLeast(100L) ?: 100L
    }
    val hasData = bars.any { it.cashInPaise > 0L || it.cashOutPaise > 0L }

    val inColor = MaterialTheme.colorScheme.tertiary
    val outColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("7-Day Cashflow", style = MaterialTheme.typography.titleMedium)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(inColor)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("In", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(outColor)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Out", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (!hasData) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Add Cashbook entries to visualize your 7-day Cash In vs Cash Out trend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                ) {
                    val slotWidth = size.width / bars.size.coerceAtLeast(1)
                    val barWidth = (slotWidth * 0.28f).coerceAtMost(24.dp.toPx())
                    val gap = 4.dp.toPx()
                    val chartHeight = size.height - 8.dp.toPx()

                    // Baseline and midpoint grid
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, chartHeight),
                        end = Offset(size.width, chartHeight),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = gridColor.copy(alpha = 0.5f),
                        start = Offset(0f, chartHeight / 2f),
                        end = Offset(size.width, chartHeight / 2f),
                        strokeWidth = 1.dp.toPx()
                    )

                    bars.forEachIndexed { idx, bar ->
                        val centerX = slotWidth * idx + slotWidth / 2f
                        val inHeight = (bar.cashInPaise.toFloat() / maxVal.toFloat()) * (chartHeight - 6.dp.toPx())
                        val outHeight = (bar.cashOutPaise.toFloat() / maxVal.toFloat()) * (chartHeight - 6.dp.toPx())

                        if (inHeight > 0f) {
                            drawRoundRect(
                                color = inColor,
                                topLeft = Offset(centerX - barWidth - gap / 2f, chartHeight - inHeight),
                                size = Size(barWidth, inHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                        if (outHeight > 0f) {
                            drawRoundRect(
                                color = outColor,
                                topLeft = Offset(centerX + gap / 2f, chartHeight - outHeight),
                                size = Size(barWidth, outHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    bars.forEach { bar ->
                        Text(
                            text = bar.label.take(6),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
