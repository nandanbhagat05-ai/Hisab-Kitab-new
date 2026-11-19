package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.CashEntry
import com.example.data.model.CashEntryType
import com.example.data.model.Customer
import com.example.data.model.CustomerWithBalance
import com.example.data.model.LedgerEntry
import com.example.data.model.LedgerEntryType
import com.example.data.model.LedgerEntryWithBalance
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object PdfAndShareHelper {

    private const val PAGE_WIDTH = 595 // A4 points
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    fun generateBusinessReportPdf(
        context: Context,
        businessName: String,
        periodLabel: String,
        customersWithBalance: List<CustomerWithBalance>,
        cashEntries: List<CashEntry>,
        ledgerEntries: List<LedgerEntry>
    ): Result<File> = runCatching {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        var pageNumber = 1
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas
        var y = drawHeader(canvas, businessName, "Business Hisab-Kitab & Cash Report ($periodLabel)")

        val totalLena = customersWithBalance.filter { it.netBalancePaise > 0 }.sumOf { it.netBalancePaise }
        val totalDena = customersWithBalance.filter { it.netBalancePaise < 0 }.sumOf { abs(it.netBalancePaise) }
        val totalCashIn = cashEntries.filter { it.cashType == CashEntryType.IN }.sumOf { it.amountPaise }
        val totalCashOut = cashEntries.filter { it.cashType == CashEntryType.OUT }.sumOf { it.amountPaise }
        val totalGavePeriod = ledgerEntries.filter { it.entryType == LedgerEntryType.GAVE }.sumOf { it.amountPaise }
        val totalGotPeriod = ledgerEntries.filter { it.entryType == LedgerEntryType.GOT }.sumOf { it.amountPaise }

        y = drawSummaryGrid(
            canvas,
            y,
            listOf(
                "Total Lena (To Collect)" to formatInr(totalLena),
                "Total Dena (To Pay)" to formatInr(totalDena),
                "Cash In ($periodLabel)" to formatInr(totalCashIn),
                "Cash Out ($periodLabel)" to formatInr(totalCashOut),
                "Udhar Given ($periodLabel)" to formatInr(totalGavePeriod),
                "Payment Got ($periodLabel)" to formatInr(totalGotPeriod)
            )
        )

        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 47, 77)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 10f
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9.5f
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }
        val greenPaint = Paint(rowPaint).apply {
            color = Color.rgb(15, 118, 110)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val redPaint = Paint(rowPaint).apply {
            color = Color.rgb(185, 28, 28)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        fun nextPageIfNeeded(neededHeight: Float) {
            if (y + neededHeight > PAGE_HEIGHT - MARGIN - 24f) {
                drawFooter(canvas, pageNumber)
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = drawHeader(canvas, businessName, "Business Report (Continued)")
            }
        }

        y += 14f
        canvas.drawText("Customer Khata Balances (${customersWithBalance.size})", MARGIN, y, sectionPaint)
        y += 16f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 14f

        canvas.drawText("Customer", MARGIN, y, mutedPaint)
        canvas.drawText("Phone", MARGIN + 185f, y, mutedPaint)
        canvas.drawText("Gave / Got", MARGIN + 300f, y, mutedPaint)
        canvas.drawText("Net Balance", PAGE_WIDTH - MARGIN - 85f, y, mutedPaint)
        y += 8f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 14f

        if (customersWithBalance.isEmpty()) {
            canvas.drawText("No customer records yet.", MARGIN, y, mutedPaint)
            y += 20f
        } else {
            customersWithBalance.forEach { item ->
                nextPageIfNeeded(24f)
                canvas.drawText(item.customer.name.take(28), MARGIN, y, rowPaint)
                canvas.drawText(item.customer.phone.ifBlank { "-" }.take(15), MARGIN + 185f, y, mutedPaint)
                canvas.drawText(
                    "${formatInr(item.totalGavePaise)} / ${formatInr(item.totalGotPaise)}",
                    MARGIN + 300f,
                    y,
                    mutedPaint
                )
                val (balLabel, balPaint) = when {
                    item.netBalancePaise > 0 -> "${formatInr(item.netBalancePaise)} Lena" to greenPaint
                    item.netBalancePaise < 0 -> "${formatInr(abs(item.netBalancePaise))} Dena" to redPaint
                    else -> "Settled" to mutedPaint
                }
                canvas.drawText(balLabel, PAGE_WIDTH - MARGIN - 95f, y, balPaint)
                y += 10f
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
                y += 14f
            }
        }

        nextPageIfNeeded(56f)
        y += 14f
        canvas.drawText("Cashbook Summary (${cashEntries.size} entries)", MARGIN, y, sectionPaint)
        y += 16f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 14f

        if (cashEntries.isEmpty()) {
            canvas.drawText("No cashbook entries for $periodLabel.", MARGIN, y, mutedPaint)
            y += 18f
        } else {
            cashEntries.take(80).forEach { entry ->
                nextPageIfNeeded(22f)
                canvas.drawText(formatDateShort(entry.entryDateMillis), MARGIN, y, mutedPaint)
                canvas.drawText("${entry.category} (${entry.paymentMode})", MARGIN + 85f, y, rowPaint)
                canvas.drawText(entry.note.take(26), MARGIN + 245f, y, mutedPaint)
                val isIn = entry.cashType == CashEntryType.IN
                val amtStr = (if (isIn) "+ " else "- ") + formatInr(entry.amountPaise)
                canvas.drawText(amtStr, PAGE_WIDTH - MARGIN - 85f, y, if (isIn) greenPaint else redPaint)
                y += 10f
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
                y += 14f
            }
        }

        drawFooter(canvas, pageNumber)
        doc.finishPage(page)

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "AtharvNandan_Report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out -> doc.writeTo(out) }
        doc.close()
        file
    }

    fun generateCustomerStatementPdf(
        context: Context,
        businessName: String,
        customer: Customer,
        entries: List<LedgerEntryWithBalance>,
        netBalancePaise: Long
    ): Result<File> = runCatching {
        val doc = PdfDocument()
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = drawHeader(canvas, businessName, "Customer Ledger Statement: ${customer.name}")

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            textSize = 10.5f
        }
        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 10f
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9.5f
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }
        val greenPaint = Paint(rowPaint).apply {
            color = Color.rgb(15, 118, 110)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val redPaint = Paint(rowPaint).apply {
            color = Color.rgb(185, 28, 28)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        if (customer.phone.isNotBlank()) {
            canvas.drawText("Phone: ${customer.phone}", MARGIN, y, subPaint)
            y += 14f
        }

        val balanceLabel = when {
            netBalancePaise > 0 -> "Net Pending (You will get): ${formatInr(netBalancePaise)}"
            netBalancePaise < 0 -> "Net Advance (You will give): ${formatInr(abs(netBalancePaise))}"
            else -> "Account Status: All Settled (₹0)"
        }
        canvas.drawText(
            balanceLabel,
            MARGIN,
            y + 4f,
            when {
                netBalancePaise > 0 -> greenPaint
                netBalancePaise < 0 -> redPaint
                else -> rowPaint
            }
        )
        y += 22f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 14f

        canvas.drawText("Date", MARGIN, y, mutedPaint)
        canvas.drawText("Note", MARGIN + 90f, y, mutedPaint)
        canvas.drawText("Gave / Got", MARGIN + 290f, y, mutedPaint)
        canvas.drawText("Running Bal", PAGE_WIDTH - MARGIN - 85f, y, mutedPaint)
        y += 8f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 14f

        if (entries.isEmpty()) {
            canvas.drawText("No transactions recorded yet.", MARGIN, y, mutedPaint)
        } else {
            entries.forEach { item ->
                if (y > PAGE_HEIGHT - MARGIN - 28f) {
                    drawFooter(canvas, pageNumber)
                    doc.finishPage(page)
                    pageNumber++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                    canvas = page.canvas
                    y = drawHeader(canvas, businessName, "Customer Statement: ${customer.name} (Cont.)")
                }
                val entry = item.entry
                canvas.drawText(formatDateShort(entry.entryDateMillis), MARGIN, y, mutedPaint)
                canvas.drawText(entry.note.ifBlank { "-" }.take(34), MARGIN + 90f, y, rowPaint)
                val isGave = entry.entryType == LedgerEntryType.GAVE
                val typeStr = (if (isGave) "Gave " else "Got ") + formatInr(entry.amountPaise)
                canvas.drawText(typeStr, MARGIN + 290f, y, if (isGave) redPaint else greenPaint)
                val balText = when {
                    item.runningBalancePaise > 0 -> "${formatInr(item.runningBalancePaise)} Dr"
                    item.runningBalancePaise < 0 -> "${formatInr(abs(item.runningBalancePaise))} Cr"
                    else -> "₹0"
                }
                canvas.drawText(balText, PAGE_WIDTH - MARGIN - 85f, y, rowPaint)
                y += 10f
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
                y += 14f
            }
        }

        drawFooter(canvas, pageNumber)
        doc.finishPage(page)

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safeName = customer.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
        val file = File(dir, "Khata_${safeName}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out -> doc.writeTo(out) }
        doc.close()
        file
    }

    private fun drawHeader(canvas: Canvas, businessName: String, subtitle: String): Float {
        val bgPaint = Paint().apply { color = Color.rgb(30, 47, 77) }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 84f, bgPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240)
            textSize = 11f
        }
        val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(251, 191, 36)
            textSize = 9.5f
        }

        val generatedAt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        canvas.drawText(businessName.ifBlank { "AtharvNandan" }, MARGIN, 34f, titlePaint)
        canvas.drawText(subtitle, MARGIN, 54f, subPaint)
        canvas.drawText("Generated: $generatedAt", MARGIN, 70f, stampPaint)
        return 104f
    }

    private fun drawSummaryGrid(
        canvas: Canvas,
        startY: Float,
        items: List<Pair<String, String>>
    ): Float {
        val boxPaint = Paint().apply {
            color = Color.rgb(248, 250, 252)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val colWidth = (PAGE_WIDTH - MARGIN * 2 - 16f) / 2f
        val boxHeight = 44f
        var y = startY
        items.chunked(2).forEach { row ->
            row.forEachIndexed { idx, (label, value) ->
                val x = MARGIN + idx * (colWidth + 16f)
                canvas.drawRoundRect(x, y, x + colWidth, y + boxHeight, 6f, 6f, boxPaint)
                canvas.drawRoundRect(x, y, x + colWidth, y + boxHeight, 6f, 6f, borderPaint)
                canvas.drawText(label, x + 10f, y + 16f, labelPaint)
                canvas.drawText(value, x + 10f, y + 34f, valuePaint)
            }
            y += boxHeight + 10f
        }
        return y
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            textSize = 9f
        }
        canvas.drawText(
            "AtharvNandan Digital Hisab-Kitab • Page $pageNumber",
            MARGIN,
            PAGE_HEIGHT - 16f,
            footerPaint
        )
    }

    fun sharePdfFile(context: Context, file: File, shareTitle: String): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, shareTitle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, shareTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun buildWhatsAppReminderMessage(
        customerName: String,
        netBalancePaise: Long,
        businessName: String,
        businessPhone: String
    ): String {
        val shop = businessName.ifBlank { "our shop" }
        val amountText = formatInr(abs(netBalancePaise))
        val contactLine = if (businessPhone.isNotBlank()) "\nContact: $businessPhone" else ""
        return if (netBalancePaise > 0) {
            "Namaste $customerName ji,\n\n" +
                "This is a gentle payment reminder from *$shop*.\n" +
                "Your pending Khata balance is *$amountText*.\n" +
                "Please settle at your convenience. Thank you!$contactLine"
        } else {
            "Namaste $customerName ji,\n\n" +
                "Your current Khata balance summary with *$shop*: " +
                if (netBalancePaise == 0L) "All settled (₹0)." else "Advance credit of *$amountText*." +
                contactLine
        }
    }

    fun shareWhatsAppReminder(
        context: Context,
        customerName: String,
        customerPhone: String,
        netBalancePaise: Long,
        businessName: String,
        businessPhone: String
    ) {
        val message = buildWhatsAppReminderMessage(customerName, netBalancePaise, businessName, businessPhone)
        val digits = customerPhone.filter { it.isDigit() }
        val formattedPhone = when {
            digits.length == 10 -> "91$digits"
            digits.length > 10 -> digits
            else -> ""
        }

        if (formattedPhone.isNotEmpty()) {
            val encoded = URLEncoder.encode(message, "UTF-8")
            val waUri = Uri.parse("https://wa.me/$formattedPhone?text=$encoded")
            val waIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(waIntent)
                return
            } catch (_: Exception) {
                // Fallback to share chooser below
            }
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        // Prefer WhatsApp if installed, otherwise general share sheet
        val pm = context.packageManager
        val whatsAppPkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
        if (whatsAppPkg != null) {
            shareIntent.setPackage(whatsAppPkg)
            try {
                context.startActivity(shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
                shareIntent.setPackage(null)
            }
        }
        context.startActivity(
            Intent.createChooser(shareIntent, "Send Payment Reminder")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun shareTextSummary(context: Context, title: String, message: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
