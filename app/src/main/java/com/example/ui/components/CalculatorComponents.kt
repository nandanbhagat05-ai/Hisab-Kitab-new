package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.PdfAndShareHelper
import com.example.util.evaluateExpression
import com.example.util.formatInr
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
fun CompactCalculatorKeypad(
    expression: String,
    onExpressionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val evaluated = remember(expression) { evaluateExpression(expression) }
    val evaluatedPaise = evaluated?.let { (it * 100.0).roundToLong() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Calculate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Quick Hisab Calculator",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (evaluatedPaise != null && evaluatedPaise > 0) {
                Text(
                    text = "= ${formatInr(evaluatedPaise)}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        val rows = listOf(
            listOf("7", "8", "9", "÷"),
            listOf("4", "5", "6", "×"),
            listOf("1", "2", "3", "-"),
            listOf("C", "0", ".", "+"),
            listOf("00", "⌫", "=")
        )
        rows.forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowKeys.forEach { key ->
                    val isOp = key in setOf("÷", "×", "-", "+", "=")
                    val isClear = key == "C" || key == "⌫"
                    val containerColor = when {
                        key == "=" -> MaterialTheme.colorScheme.primary
                        isOp -> MaterialTheme.colorScheme.secondaryContainer
                        isClear -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                    val contentColor = when {
                        key == "=" -> MaterialTheme.colorScheme.onPrimary
                        isOp -> MaterialTheme.colorScheme.onSecondaryContainer
                        isClear -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Surface(
                        color = containerColor,
                        contentColor = contentColor,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(if (key == "=") 2f else 1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                when (key) {
                                    "C" -> onExpressionChange("")
                                    "⌫" -> if (expression.isNotEmpty()) {
                                        onExpressionChange(expression.dropLast(1))
                                    }
                                    "=" -> {
                                        val res = evaluateExpression(expression)
                                        if (res != null && res > 0) {
                                            val rounded = (res * 100.0).roundToLong()
                                            val rupees = rounded / 100
                                            val rem = rounded % 100
                                            onExpressionChange(
                                                if (rem == 0L) rupees.toString()
                                                else "$rupees.${rem.toString().padStart(2, '0')}"
                                            )
                                        }
                                    }
                                    else -> onExpressionChange(expression + key)
                                }
                            }
                            .testTag("calc_key_$key")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (key == "⌫") {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                    contentDescription = "Backspace",
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val DENOMINATIONS = listOf(2000, 500, 200, 100, 50, 20, 10, 5, 2, 1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashDenominationCalculatorSheet(
    bookCashInHandPaise: Long,
    onDismiss: () -> Unit,
    onSaveDifferenceAsCashEntry: (diffPaise: Long, summaryNote: String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val counts = remember { mutableStateMapOf<Int, Int>().apply { DENOMINATIONS.forEach { put(it, 0) } } }

    val totalPhysicalRupees = DENOMINATIONS.sumOf { denom -> denom.toLong() * (counts[denom] ?: 0) }
    val totalPhysicalPaise = totalPhysicalRupees * 100L
    val differencePaise = totalPhysicalPaise - bookCashInHandPaise

    fun buildSummaryText(): String {
        val lines = DENOMINATIONS.mapNotNull { denom ->
            val c = counts[denom] ?: 0
            if (c > 0) "₹$denom × $c = ${formatInr(denom.toLong() * c * 100L)}" else null
        }
        return buildString {
            appendLine("*Daily Cash Tally (Galla Hisab)*")
            if (lines.isEmpty()) {
                appendLine("No notes counted.")
            } else {
                lines.forEach { appendLine(it) }
            }
            appendLine("---")
            appendLine("Physical Cash: ${formatInr(totalPhysicalPaise)}")
            appendLine("Book Cash in Hand: ${formatInr(bookCashInHandPaise)}")
            val diffLabel = when {
                differencePaise > 0 -> "Surplus: +${formatInr(differencePaise)}"
                differencePaise < 0 -> "Shortfall: -${formatInr(abs(differencePaise))}"
                else -> "Exact Match!"
            }
            appendLine("Status: $diffLabel")
        }
    }

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
                Column {
                    Text(
                        text = "Cash Tally Calculator",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Count notes & coins to match your Galla",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { DENOMINATIONS.forEach { counts[it] = 0 } },
                    modifier = Modifier.testTag("reset_cash_tally_button")
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset counts")
                }
            }

            // Tally Summary Card
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Physical Cash Count",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatInr(totalPhysicalPaise),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = JetBrainsMonoFamily
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Book Cash (Galla)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatInr(bookCashInHandPaise),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = JetBrainsMonoFamily
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                    val statusColor = when {
                        differencePaise == 0L -> MaterialTheme.colorScheme.tertiary
                        differencePaise > 0L -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    }
                    val statusText = when {
                        differencePaise == 0L -> "Exact Match with Cashbook"
                        differencePaise > 0L -> "Surplus by ${formatInr(differencePaise)}"
                        else -> "Shortfall by ${formatInr(abs(differencePaise))}"
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelLarge,
                            color = statusColor
                        )
                    }
                }
            }

            // Denomination Rows
            DENOMINATIONS.forEach { denom ->
                val count = counts[denom] ?: 0
                val rowTotalPaise = denom.toLong() * count * 100L
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "₹$denom",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(68.dp)
                    )
                    IconButton(
                        onClick = { if (count > 0) counts[denom] = count - 1 },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Decrease ₹$denom count")
                    }
                    OutlinedTextField(
                        value = if (count == 0) "" else count.toString(),
                        onValueChange = { raw ->
                            val parsed = raw.filter { it.isDigit() }.take(5).toIntOrNull() ?: 0
                            counts[denom] = parsed
                        },
                        placeholder = { Text("0", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = JetBrainsMonoFamily,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.width(74.dp)
                    )
                    IconButton(
                        onClick = { counts[denom] = count + 1 },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Increase ₹$denom count")
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatInr(rowTotalPaise),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (count > 0) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        PdfAndShareHelper.shareTextSummary(
                            context = context,
                            title = "Share Cash Tally",
                            message = buildSummaryText()
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("share_cash_tally_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share Tally")
                }

                if (totalPhysicalPaise > 0L && differencePaise != 0L) {
                    Button(
                        onClick = {
                            val note = "Cash tally adjustment (${formatInr(totalPhysicalPaise)} counted)"
                            onSaveDifferenceAsCashEntry(differencePaise, note)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("adjust_cashbook_button")
                    ) {
                        Text(
                            if (differencePaise > 0) "Add +${formatInr(differencePaise)}"
                            else "Deduct ${formatInr(abs(differencePaise))}"
                        )
                    }
                }
            }
        }
    }
}
