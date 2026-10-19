// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.io

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletCsvImporterTest {
    @Test
    fun `maps headers and produces dry-run account totals without inferring kind from category`() {
        val preview =
            WalletCsvImporter.preview(
                "Date,Account,Category,Amount,Currency,Type,Note\n" +
                    "2026-10-01,Wallet,Food,-12.50,EGP,Expense,\"Coffee, morning\"\n" +
                    "2026-10-02,Wallet,Investments,100.00,EGP,Income,Gold",
            )
        assertEquals(emptyList<String>(), preview.errors)
        assertEquals(-1_250L, preview.records[0].amountMinor)
        assertEquals(WalletImportKind.INCOME, preview.records[1].kind)
        assertEquals(8_750L, preview.accountTotalsMinor["Wallet"])
    }

    @Test fun `Wallet Expenses with Financial investments category and transfer false stay expenses`() {
        val rows =
            listOf(
                "Investment,Financial investments,EGP,-6300,Expenses,FALSE,2026-10-07",
                "Investment,Financial investments,EGP,-5350,Expenses,FALSE,2026-09-23",
                "Investment,Financial investments,EGP,-5750,Expenses,FALSE,2026-09-10",
                "Investment,Financial investments,EGP,-7800,Expenses,FALSE,2026-08-31",
                "Investment,Financial investments,EGP,-59600,Expenses,FALSE,2026-02-27",
                "Investment,Financial investments,EGP,-9300,Expenses,FALSE,2026-01-02",
                "Investment,Financial investments,EGP,-13500,Expenses,FALSE,2025-12-02",
            )
        val preview =
            WalletCsvImporter.preview(
                "account,category,currency,amount,type,transfer,date\n" +
                    rows.joinToString("\n"),
            )

        assertEquals(emptyList<String>(), preview.errors)
        assertEquals(7, preview.records.size)
        assertEquals(setOf(WalletImportKind.EXPENSE), preview.records.map { it.kind }.toSet())
        assertEquals(setOf(null), preview.records.map { it.destination }.toSet())
        assertEquals(-10_760_000L, preview.accountTotalsMinor["Investment"])
    }

    @Test fun `mapping quoted multiline BOM KWD JPY and transfers retain exact bucket deltas`() {
        val csv =
            "\uFEFFWhen,Wallet,Amount,Currency,Type,To account,Note\n" +
                "2026-10-01,Cash,-1.234,KWD,Expense,,\"Arabic عشاء\nwith friends\"\n" +
                "2026-10-01,Cash,120,JPY,Income,,\n" +
                "2026-10-02,Cash,-10,EGP,Transfer,Bank,"
        val result = WalletCsvImporter.preview(csv, mapOf("date" to "When", "account" to "Wallet"))
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(-1234L, result.bucketTotalsMinor[WalletBucket("Cash", "KWD")])
        assertEquals(120L, result.bucketTotalsMinor[WalletBucket("Cash", "JPY")])
        assertEquals(-1000L, result.bucketTotalsMinor[WalletBucket("Cash", "EGP")])
        assertEquals(1000L, result.bucketTotalsMinor[WalletBucket("Bank", "EGP")])
        assertEquals("Arabic عشاء\nwith friends", result.records.first().note)
    }

    @Test fun `paired transfer creates one transaction and unmatched transfer blocks commit`() {
        val header = "Date,Account,Amount,Type,Transfer\n"
        val result =
            WalletCsvImporter.preview(
                header +
                    "2026-10-01,Cash,-10,,TRUE\n2026-10-01,Bank,10,,TRUE",
            )
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(1, result.records.size)
        assertEquals("Bank", result.records.single().destination)
        org.junit.Assert.assertTrue(WalletCsvImporter.preview(header + "2026-10-01,Cash,-10,,TRUE").errors.isNotEmpty())
    }
}
