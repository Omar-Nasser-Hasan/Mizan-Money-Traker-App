// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.io

import app.mizan.domain.ledger.AccountType
import app.mizan.domain.ledger.PaymentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MizanImportTest {
    private val headers =
        listOf(
            "event_id",
            "source_ref",
            "event_kind",
            "date",
            "account",
            "account_type",
            "currency",
            "amount",
            "category",
            "payee",
            "payment_type",
            "note",
            "destination",
            "asset_id",
            "asset_name",
            "asset_type",
            "asset_symbol",
            "asset_unit",
            "asset_gold_form",
            "quantity",
            "unit_price",
            "fee",
            "debt_id",
            "debt_person",
            "debt_direction",
            "debt_principal",
            "debt_opened_at",
            "debt_due_date",
        )

    private fun row(vararg values: Pair<String, String>): String {
        val byName = values.toMap()
        return headers.joinToString(",") { name ->
            val value = byName[name].orEmpty()
            "\"${value.replace("\"", "\"\"")}\""
        }
    }

    @Test fun golden_events_keep_asset_and_debt_kinds_out_of_income_and_expense() {
        val csv =
            headers.joinToString(",") + "\n" +
                row(
                    "event_id" to "salary-1",
                    "source_ref" to "source:3",
                    "event_kind" to "INCOME",
                    "date" to "2026-10-19",
                    "account" to "Investment",
                    "account_type" to "INVESTMENT",
                    "currency" to "EGP",
                    "amount" to "6500",
                    "category" to "Salary",
                ) +
                "\n" +
                row(
                    "event_id" to "gold-buy",
                    "source_ref" to "manual:gold",
                    "event_kind" to "ASSET_BUY",
                    "date" to "2025-12-05",
                    "account" to "Investment",
                    "account_type" to "INVESTMENT",
                    "currency" to "EGP",
                    "amount" to "22840",
                    "payment_type" to "TRANSFER",
                    "asset_id" to "gold-21k",
                    "asset_name" to "21K gold",
                    "asset_type" to "GOLD",
                    "asset_symbol" to "21K",
                    "asset_unit" to "g",
                    "asset_gold_form" to "COIN",
                    "quantity" to "4",
                    "unit_price" to "5710",
                ) +
                "\n" +
                row(
                    "event_id" to "dad-loan",
                    "source_ref" to "manual:dad",
                    "event_kind" to "DEBT_BORROW",
                    "date" to "2025-12-05",
                    "account" to "Investment",
                    "account_type" to "INVESTMENT",
                    "currency" to "EGP",
                    "amount" to "22840",
                    "debt_id" to "dad-gold",
                    "debt_person" to "Dad",
                    "debt_direction" to "BORROWED",
                    "debt_principal" to "22840",
                    "debt_opened_at" to "2025-12-05",
                ) +
                "\n" +
                row(
                    "event_id" to "repay",
                    "source_ref" to "source:562",
                    "event_kind" to "DEBT_REPAY_OUT",
                    "date" to "2025-12-05",
                    "account" to "Investment",
                    "account_type" to "INVESTMENT",
                    "currency" to "EGP",
                    "amount" to "13500",
                    "debt_id" to "dad-gold",
                ) +
                "\n" +
                row(
                    "event_id" to "unmatched",
                    "source_ref" to "source:80",
                    "event_kind" to "REVIEW",
                    "note" to "Salary-funded row; asset identity is not known.",
                )

        val preview = MizanImportParser.preview(csv)

        assertTrue(preview.errors.toString(), preview.errors.isEmpty())
        assertEquals(4, preview.events.size)
        assertEquals(
            listOf(
                MizanImportKind.INCOME,
                MizanImportKind.ASSET_BUY,
                MizanImportKind.DEBT_BORROW,
                MizanImportKind.DEBT_REPAY_OUT,
            ),
            preview.events.map {
                it.kind
            },
        )
        assertEquals(AccountType.INVESTMENT, preview.events[0].accountType)
        assertEquals(PaymentType.BANK_TRANSFER, preview.events[1].paymentType)
        assertEquals(1, preview.reviews.size)
        assertEquals("source:80", preview.reviews.single().sourceRef)
        assertEquals("Salary-funded row; asset identity is not known.", preview.reviews.single().note)
        assertEquals(-700_000L, preview.bucketTotalsMinor[WalletBucket("Investment", "EGP")])
    }

    @Test fun transfer_totals_have_two_opposite_legs_and_currency_minor_units() {
        val csv =
            headers.joinToString(",") + "\n" +
                row(
                    "event_id" to "transfer-1",
                    "source_ref" to "source:1",
                    "event_kind" to "TRANSFER",
                    "date" to "2026-10-01",
                    "account" to "Cash",
                    "account_type" to "CASH",
                    "currency" to "EGP",
                    "amount" to "12.50",
                    "payment_type" to "TRANSFER",
                    "note" to "Move",
                    "destination" to "Wallet",
                ) +
                "\n" +
                row(
                    "event_id" to "expense-1",
                    "source_ref" to "source:2",
                    "event_kind" to "EXPENSE",
                    "date" to "2026-10-02",
                    "account" to "Cash",
                    "account_type" to "CASH",
                    "currency" to "EGP",
                    "amount" to "5.25",
                    "category" to "Food",
                    "note" to "Tea",
                )

        val preview = MizanImportParser.preview(csv)

        assertTrue(preview.errors.toString(), preview.errors.isEmpty())
        assertEquals(-1775L, preview.bucketTotalsMinor[WalletBucket("Cash", "EGP")])
        assertEquals(1250L, preview.bucketTotalsMinor[WalletBucket("Wallet", "EGP")])
    }

    @Test fun historic_debt_opening_does_not_change_the_account_balance() {
        val csv =
            headers.joinToString(",") + "\n" +
                row(
                    "event_id" to "historic-debt",
                    "source_ref" to "source-debt:6",
                    "event_kind" to "DEBT_OPENING",
                    "date" to "2026-05-18",
                    "account" to "Essential",
                    "account_type" to "BANK",
                    "currency" to "EGP",
                    "amount" to "270",
                    "debt_id" to "essam-hakim",
                    "debt_person" to "Essam",
                    "debt_direction" to "BORROWED",
                    "debt_principal" to "270",
                    "debt_opened_at" to "2026-05-18",
                )

        val preview = MizanImportParser.preview(csv)

        assertTrue(preview.errors.toString(), preview.errors.isEmpty())
        assertEquals(1, preview.events.size)
        assertEquals(MizanImportKind.DEBT_OPENING, preview.events.single().kind)
        assertTrue(preview.bucketTotalsMinor.isEmpty())
    }

    @Test fun malformed_asset_and_debt_rows_are_reported_not_guessed() {
        val csv =
            headers.joinToString(",") + "\n" +
                row(
                    "event_id" to "bad-asset",
                    "source_ref" to "manual:1",
                    "event_kind" to "ASSET_BUY",
                    "date" to "2026-10-01",
                    "account" to "Cash",
                    "currency" to "EGP",
                    "amount" to "1000",
                    "asset_name" to "Gold",
                    "asset_type" to "GOLD",
                    "asset_unit" to "g",
                    "quantity" to "1",
                    "unit_price" to "1000",
                ) +
                "\n" +
                row(
                    "event_id" to "bad-debt",
                    "source_ref" to "manual:2",
                    "event_kind" to "DEBT_BORROW",
                    "date" to "2026-10-01",
                    "account" to "Cash",
                    "currency" to "EGP",
                    "amount" to "500",
                    "debt_id" to "debt-1",
                    "debt_person" to "Dad",
                    "debt_direction" to "UNKNOWN",
                    "debt_principal" to "500",
                )

        val preview = MizanImportParser.preview(csv)

        assertEquals(0, preview.events.size)
        assertEquals(2, preview.errors.size)
        assertTrue(preview.errors.any { it.contains("Missing asset_id") })
        assertTrue(preview.errors.any { it.contains("Debt direction") })
    }
}
