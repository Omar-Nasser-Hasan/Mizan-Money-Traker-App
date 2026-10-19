// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.io

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletCsvImporterTest {
    @Test
    fun `maps headers produces dry-run account totals and upgrades investments`() {
        val preview =
            WalletCsvImporter.preview(
                "Date,Account,Category,Amount,Currency,Note\n" +
                    "2026-10-01,Wallet,Food,-12.50,EGP,\"Coffee, morning\"\n" +
                    "2026-10-02,Wallet,Investments,100.00,EGP,Gold",
            )
        assertEquals(emptyList<String>(), preview.errors)
        assertEquals(-1_250L, preview.records[0].amountMinor)
        assertEquals(WalletImportKind.TRANSFER, preview.records[1].kind)
        assertEquals(-11_250L, preview.accountTotalsMinor["Wallet"])
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
        val header = "Date,Account,Amount,Type\n"
        val result =
            WalletCsvImporter.preview(
                header +
                    "2026-10-01,Cash,-10,Transfer\n2026-10-01,Bank,10,Transfer",
            )
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(1, result.records.size)
        assertEquals("Bank", result.records.single().destination)
        org.junit.Assert.assertTrue(WalletCsvImporter.preview(header + "2026-10-01,Cash,-10,Transfer").errors.isNotEmpty())
    }
}
