package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

fun formatInr(paise: Long, includeSign: Boolean = false): String {
    val negative = paise < 0
    val absPaise = abs(paise)
    val rupees = absPaise / 100
    val remPaise = absPaise % 100

    val formattedRupees = formatIndianGrouping(rupees)
    val decimalPart = if (remPaise == 0L) "" else ".${remPaise.toString().padStart(2, '0')}"
    val prefix = when {
        negative -> "-"
        includeSign && paise > 0 -> "+"
        else -> ""
    }
    return "${prefix}₹$formattedRupees$decimalPart"
}

private fun formatIndianGrouping(value: Long): String {
    val s = value.toString()
    if (s.length <= 3) return s
    val lastThree = s.takeLast(3)
    val rest = s.dropLast(3)
    val groupedRest = buildString {
        var count = 0
        for (i in rest.length - 1 downTo 0) {
            append(rest[i])
            count++
            if (count % 2 == 0 && i != 0) {
                append(',')
            }
        }
    }.reversed()
    return "$groupedRest,$lastThree"
}

fun formatRupeeInputFromPaise(paise: Long): String {
    if (paise <= 0L) return ""
    val rupees = paise / 100
    val rem = paise % 100
    return if (rem == 0L) rupees.toString() else "$rupees.${rem.toString().padStart(2, '0')}"
}

fun parseExpressionToPaise(expression: String): Long? {
    val value = evaluateExpression(expression) ?: return null
    if (value.isNaN() || value.isInfinite() || value <= 0.0) return null
    val paise = (value * 100.0).roundToLong()
    return if (paise > 0L) paise else null
}

/**
 * Evaluates arithmetic expressions supporting +, -, *, /, %, parentheses, and decimals.
 */
fun evaluateExpression(rawInput: String): Double? {
    val sanitized = rawInput
        .replace("×", "*")
        .replace("÷", "/")
        .replace(",", "")
        .replace(" ", "")
        .trim()
    if (sanitized.isEmpty()) return null

    return try {
        val parser = ExpressionParser(sanitized)
        val result = parser.parse()
        if (parser.pos == sanitized.length && !result.isNaN() && !result.isInfinite()) {
            result
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

private class ExpressionParser(private val input: String) {
    var pos: Int = 0

    fun parse(): Double = parseExpression()

    private fun parseExpression(): Double {
        var value = parseTerm()
        while (pos < input.length) {
            when (input[pos]) {
                '+' -> {
                    pos++
                    value += parseTerm()
                }
                '-' -> {
                    pos++
                    value -= parseTerm()
                }
                else -> break
            }
        }
        return value
    }

    private fun parseTerm(): Double {
        var value = parseFactor()
        while (pos < input.length) {
            when (input[pos]) {
                '*' -> {
                    pos++
                    value *= parseFactor()
                }
                '/' -> {
                    pos++
                    val divisor = parseFactor()
                    if (divisor == 0.0) throw IllegalArgumentException("Division by zero")
                    value /= divisor
                }
                else -> break
            }
        }
        return value
    }

    private fun parseFactor(): Double {
        if (pos >= input.length) throw IllegalArgumentException("Unexpected end")
        if (input[pos] == '+') {
            pos++
            return parseFactor()
        }
        if (input[pos] == '-') {
            pos++
            return -parseFactor()
        }
        if (input[pos] == '(') {
            pos++
            val inside = parseExpression()
            if (pos < input.length && input[pos] == ')') {
                pos++
            }
            return inside
        }
        val start = pos
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
            pos++
        }
        if (start == pos) throw IllegalArgumentException("Expected number")
        var num = input.substring(start, pos).toDouble()
        if (pos < input.length && input[pos] == '%') {
            pos++
            num /= 100.0
        }
        return num
    }
}

fun formatDateShort(millis: Long): String {
    if (millis <= 0L) return ""
    val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return fmt.format(Date(millis))
}

fun formatDateTimeShort(millis: Long): String {
    if (millis <= 0L) return ""
    val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return fmt.format(Date(millis))
}

fun formatDayMonth(millis: Long): String {
    if (millis <= 0L) return ""
    val fmt = SimpleDateFormat("dd MMM", Locale.getDefault())
    return fmt.format(Date(millis))
}

fun startOfTodayMillis(nowMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

fun startOfDaysAgoMillis(daysAgo: Int, nowMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = startOfTodayMillis(nowMillis)
        add(Calendar.DAY_OF_YEAR, -daysAgo)
    }
    return cal.timeInMillis
}

fun startOfMonthMillis(nowMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

fun isSameDay(millisA: Long, millisB: Long): Boolean {
    val calA = Calendar.getInstance().apply { timeInMillis = millisA }
    val calB = Calendar.getInstance().apply { timeInMillis = millisB }
    return calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
        calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR)
}
