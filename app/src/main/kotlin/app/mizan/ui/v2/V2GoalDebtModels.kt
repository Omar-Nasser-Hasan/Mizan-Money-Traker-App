// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import app.mizan.data.db.AccountBalanceRow
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.AssetEntity
import app.mizan.data.db.AssetTransactionEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.FxRateEntity
import app.mizan.data.db.GoalContributionEntity
import app.mizan.data.db.GoalEntity
import app.mizan.data.db.GoalLinkEntity
import app.mizan.data.db.PriceSnapshotEntity
import app.mizan.domain.assets.AssetTransaction
import app.mizan.domain.assets.AssetTransactionKind
import app.mizan.domain.assets.AssetValuation
import app.mizan.domain.assets.AverageCost
import app.mizan.domain.money.MoneyMath
import app.mizan.domain.plan.DebtBalance
import app.mizan.domain.plan.GoalProgress
import app.mizan.domain.plan.GoalStatus
import java.math.BigDecimal
import java.time.LocalDate

internal data class GoalSourceValue(
    val key: String,
    val name: String,
    val valueMinor: Long,
    val allocationPct: Long,
)

internal data class GoalCardData(
    val goal: GoalEntity,
    val progress: GoalProgress,
    val sources: List<GoalSourceValue>,
    val monthsBehind: Long,
)

internal data class DebtCardData(
    val debt: app.mizan.data.db.DebtEntity,
    val balance: DebtBalance,
    val overdue: Boolean,
)

internal object GoalDebtCalculations {
    fun goals(
        goals: List<GoalEntity>,
        links: List<GoalLinkEntity>,
        contributions: List<GoalContributionEntity>,
        balances: List<AccountBalanceRow>,
        accounts: List<AccountEntity>,
        assets: List<AssetEntity>,
        trades: List<AssetTransactionEntity>,
        prices: List<PriceSnapshotEntity>,
        currencies: List<CurrencyEntity>,
        rates: List<FxRateEntity>,
        today: LocalDate,
    ): List<GoalCardData> {
        val accountValues =
            accounts.associate {
                it.id to
                    balances.filter { row -> row.accountId == it.id }.sumOf { row ->
                        convert(Math.addExact(row.openingMinor, row.entryMinor), row.currency, row.decimals, row.egpPerUnit)
                    }
            }
        val assetValues =
            assets.associate { asset ->
                val position =
                    AverageCost.position(
                        trades.filter { it.assetId == asset.id && it.deletedAt == null }.map { trade ->
                            AssetTransaction(
                                trade.id,
                                trade.assetId,
                                AssetTransactionKind.valueOf(trade.kind),
                                LocalDate.parse(trade.date),
                                BigDecimal(trade.quantity),
                                BigDecimal(trade.unitPrice),
                                trade.feeMinor,
                                trade.totalMinor,
                                trade.txnId,
                                trade.note,
                            )
                        },
                    )
                val key = if (asset.type == "GOLD") "GOLD_${asset.symbolOrKarats.uppercase()}" else "MANUAL:${asset.id}"
                val price = prices.firstOrNull { it.key == key }
                val value =
                    if (price == null) {
                        0L
                    } else {
                        val decimals = currencies.firstOrNull { it.code == price.currency }?.decimals ?: 2
                        val quote =
                            AssetValuation.valueMinor(
                                position.quantity.coerceAtLeast(BigDecimal.ZERO),
                                BigDecimal(price.price),
                                decimals,
                                BigDecimal(asset.resaleDeductionPct),
                            )
                        convert(
                            quote,
                            price.currency,
                            decimals,
                            if (price.currency ==
                                "EGP"
                            ) {
                                "1"
                            } else {
                                rates.firstOrNull { it.currency == price.currency }?.egpPerUnit ?: "0"
                            },
                        )
                    }
                asset.id to value
            }
        val accountNames = accounts.associate { it.id to it.name }
        val assetNames = assets.associate { it.id to it.name }
        return goals.map { goal ->
            val sourceLinks = links.filter { it.goalId == goal.id }
            val sources =
                sourceLinks.mapNotNull { link ->
                    val (name, value) =
                        if (link.assetId.isNotBlank()) {
                            (assetNames[link.assetId] ?: return@mapNotNull null) to (assetValues[link.assetId] ?: 0L)
                        } else {
                            (accountNames[link.accountId] ?: return@mapNotNull null) to (accountValues[link.accountId] ?: 0L)
                        }
                    GoalSourceValue(
                        if (link.assetId.isNotBlank()) "asset:${link.assetId}" else "account:${link.accountId}",
                        name,
                        value,
                        link.allocationPct,
                    )
                }
            val linked = sources.sumOf { it.valueMinor * it.allocationPct / 100L }
            val manual = contributions.filter { it.goalId == goal.id }.sumOf { it.amountMinor }
            val progress =
                GoalProgress(goal.targetMinor, linked, manual, goal.targetDate?.let(LocalDate::parse), GoalStatus.valueOf(goal.status))
            val monthsBehind =
                if (progress.isOverdue(today)) {
                    val target = progress.targetDate!!
                    java.time.temporal.ChronoUnit.MONTHS
                        .between(target.withDayOfMonth(1), today.withDayOfMonth(1))
                        .coerceAtLeast(1)
                } else {
                    0L
                }
            GoalCardData(goal, progress, sources, monthsBehind)
        }
    }

    fun debt(
        entity: app.mizan.data.db.DebtEntity,
        repaymentsMinor: Long,
        today: LocalDate,
    ) = DebtCardData(
        entity,
        DebtBalance(entity.principalMinor, repaymentsMinor),
        entity.dueDate?.let { LocalDate.parse(it) < today } == true,
    )

    private fun convert(
        amountMinor: Long,
        currency: String,
        decimals: Int,
        rate: String,
    ): Long = MoneyMath.baseMinor(amountMinor, decimals, BigDecimal(rate))
}
