// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.ledger

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Four-function calculator used by Entry; it never converts an amount through floating point. */
object EntryCalculator {
    private val operators = setOf('+', '-', '×', '÷')

    fun evaluate(expression: String): BigDecimal {
        require(expression.isNotBlank()) { "Amount is empty" }
        val values = ArrayDeque<BigDecimal>()
        val ops = ArrayDeque<Char>()
        var index = 0
        while (index < expression.length) {
            val char = expression[index]
            when {
                char.isWhitespace() -> {
                    index++
                }

                char.isDigit() || char == '.' -> {
                    val start = index
                    while (index < expression.length && (expression[index].isDigit() || expression[index] == '.')) index++
                    values.addLast(expression.substring(start, index).toBigDecimal())
                }

                char in operators -> {
                    while (ops.isNotEmpty() && precedence(ops.last()) >= precedence(char)) apply(values, ops.removeLast())
                    ops.addLast(char)
                    index++
                }

                else -> {
                    throw IllegalArgumentException("Unsupported calculator token")
                }
            }
        }
        while (ops.isNotEmpty()) apply(values, ops.removeLast())
        require(values.size == 1) { "Incomplete expression" }
        return values.last()
    }

    /** Evaluates an entry expression and rounds it once to the chosen currency's minor unit. */
    fun minorUnits(
        expression: String,
        decimals: Int,
    ): Long {
        require(decimals >= 0) { "Currency decimals cannot be negative" }
        return evaluate(expression)
            .movePointRight(decimals)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    private fun precedence(operator: Char): Int = if (operator == '×' || operator == '÷') 2 else 1

    private fun apply(
        values: ArrayDeque<BigDecimal>,
        operator: Char,
    ) {
        require(values.size >= 2) { "Incomplete expression" }
        val right = values.removeLast()
        val left = values.removeLast()
        val result =
            when (operator) {
                '+' -> {
                    left + right
                }

                '-' -> {
                    left - right
                }

                '×' -> {
                    left * right
                }

                '÷' -> {
                    require(right.compareTo(BigDecimal.ZERO) != 0) { "Cannot divide by zero" }
                    left.divide(right, MathContext(16, RoundingMode.HALF_UP))
                }

                else -> {
                    error("Unknown operator")
                }
            }
        values.addLast(result)
    }
}
