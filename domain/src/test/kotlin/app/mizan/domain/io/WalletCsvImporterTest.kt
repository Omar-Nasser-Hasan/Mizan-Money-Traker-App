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
        assertEquals(WalletImportKind.ASSET_BUY, preview.records[1].kind)
        assertEquals(8_750L, preview.accountTotalsMinor["Wallet"])
    }
}
