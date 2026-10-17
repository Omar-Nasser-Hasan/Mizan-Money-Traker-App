// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.mizan.domain.zakat.ZakatCalculator
import app.mizan.domain.zakat.ZakatInput
import java.math.RoundingMode

@Composable
fun zakatScreen(modifier: Modifier) {
    var cash by remember { mutableStateOf("0") }
    var gold by remember { mutableStateOf("0") }
    var investments by remember { mutableStateOf("0") }
    var receivables by remember { mutableStateOf("0") }
    var debts by remember { mutableStateOf("0") }
    var creditCard by remember { mutableStateOf("0") }
    var goldPrice by remember { mutableStateOf("0") }
    var result by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Zakat calculator") }
        item { Text("Enter EGP values. This is a calculator, not a ruling; confirm treatment of jewelry and shares with your scholar.") }
        item { zakatField("Cash", cash) { cash = it } }
        item { zakatField("Gold value included", gold) { gold = it } }
        item { zakatField("Stocks and funds included", investments) { investments = it } }
        item { zakatField("Receivables included", receivables) { receivables = it } }
        item { zakatField("Debts due within one year", debts) { debts = it } }
        item { zakatField("Credit-card debt", creditCard) { creditCard = it } }
        item { zakatField("24K gold price per gram", goldPrice) { goldPrice = it } }
        item {
            Button(
                onClick = {
                    result =
                        runCatching {
                            val calculation =
                                ZakatCalculator.calculate(
                                    ZakatInput(
                                        cashMinor = egpMinor(cash),
                                        goldMinor = egpMinor(gold),
                                        investmentsMinor = egpMinor(investments),
                                        receivablesMinor = egpMinor(receivables),
                                        debtsDueWithinYearMinor = egpMinor(debts),
                                        creditCardDebtMinor = egpMinor(creditCard),
                                        gold24kPricePerGramMinor = egpMinor(goldPrice),
                                    ),
                                )
                            "Eligible ${egp(
                                calculation.eligibleMinor,
                            )} · Nisab ${egp(
                                calculation.nisabMinor,
                            )} · ${if (calculation.isDue) "Due ${egp(calculation.dueMinor)}" else "Not due"}"
                        }.getOrElse { "Enter valid non-negative amounts." }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Calculate") }
        }
        result?.let { value -> item { Text(value) } }
    }
}

@Composable
private fun zakatField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(value, onValueChange, label = { Text("$label (EGP)") }, modifier = Modifier.fillMaxWidth())
}

private fun egpMinor(value: String): Long =
    value
        .trim()
        .replace(",", "")
        .toBigDecimal()
        .movePointRight(2)
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()

private fun egp(minor: Long): String =
    java.math.BigDecimal
        .valueOf(minor, 2)
        .toPlainString() + " EGP"
