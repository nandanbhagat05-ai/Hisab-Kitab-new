package com.example

import com.example.util.evaluateExpression
import com.example.util.formatInr
import com.example.util.parseExpressionToPaise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattersAndCalculatorTest {

    @Test
    fun formatInr_formatsIndianNumberingSystem() {
        assertEquals("₹0", formatInr(0L))
        assertEquals("₹950", formatInr(95000L))
        assertEquals("₹1,25,450", formatInr(12545000L))
        assertEquals("₹1,25,450.50", formatInr(12545050L))
        assertEquals("-₹500", formatInr(-50000L))
    }

    @Test
    fun evaluateExpression_computesOrderOfOperations() {
        assertEquals(690.0, evaluateExpression("450+120*2")!!, 0.001)
        assertEquals(1140.0, evaluateExpression("(450+120)×2")!!, 0.001)
        assertEquals(250.5, evaluateExpression("501÷2")!!, 0.001)
        assertNull(evaluateExpression("10/0"))
        assertNull(evaluateExpression(""))
    }

    @Test
    fun parseExpressionToPaise_convertsToPaiseSafely() {
        assertEquals(69000L, parseExpressionToPaise("450+120*2"))
        assertEquals(12550L, parseExpressionToPaise("125.5"))
        assertNull(parseExpressionToPaise("0"))
        assertNull(parseExpressionToPaise("-50"))
    }
}
