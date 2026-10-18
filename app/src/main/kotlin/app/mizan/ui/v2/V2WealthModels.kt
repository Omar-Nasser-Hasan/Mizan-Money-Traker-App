// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import app.mizan.data.db.AccountBalanceRow
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.AssetEntity
import app.mizan.data.db.AssetTransactionEntity
import app.mizan.data.db.CurrencyEntity
import app.mizan.data.db.FxRateEntity
import app.mizan.data.db.PriceSnapshotEntity
import app.mizan.data.db.StatsEntryRow
import app.mizan.domain.assets.AssetTransaction
import app.mizan.domain.assets.AssetTransactionKind
import app.mizan.domain.assets.AssetType
import app.mizan.domain.assets.AverageCost
import app.mizan.domain.assets.WealthCalculator
import app.mizan.domain.assets.WealthCashBucket
import app.mizan.domain.assets.WealthHoldingInput
import app.mizan.domain.assets.WealthHoldingValue
import app.mizan.domain.assets.WealthSummary
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class V2WealthPosition(
    val asset: AssetEntity,
    val quantity: BigDecimal,
    val pricePerUnit: BigDecimal?,
    val priceCurrency: String,
    val sourceLabel: String,
    val ageHours: Long?,
    val valueEgpMinor: Long,
    val costEgpMinor: Long,
    val unrealizedEgpMinor: Long,
    val realizedEgpMinor: Long,
    val weightBasisPoints: Long,
) {
    val stale: Boolean get() = ageHours != null && ageHours >= 24L
}

data class V2WealthSnapshot(
    val summary: WealthSummary,
    val positions: List<V2WealthPosition>,
    val accountTypeTotals: List<Pair<String, Long>>,
)

object V2WealthModels {
    private val cairo = ZoneId.of("Africa/Cairo")

    fun build(
        accounts: List<AccountEntity>,
        balances: List<AccountBalanceRow>,
        currencies: List<CurrencyEntity>,
        assets: List<AssetEntity>,
        transactions: List<AssetTransactionEntity>,
        prices: List<PriceSnapshotEntity>,
        fxHistory: List<FxRateEntity>,
        statsEntries: List<StatsEntryRow>,
        now: Instant,
    ): V2WealthSnapshot {
        val activeAccounts = accounts.filter { it.includeInTotals }.associateBy { it.id }
        val includedBalances = balances.filter { it.accountId in activeAccounts }
        val today = now.atZone(cairo).toLocalDate()
        val monthAgo = today.minusDays(30)
        val cash =
            includedBalances.map { row ->
                WealthCashBucket(row.openingMinor + row.entryMinor, row.decimals, BigDecimal(row.egpPerUnit))
            }
        val currentInputs =
            assets.mapNotNull { asset ->
                holdingInput(asset, transactions, prices, fxHistory, currencies, today, now)
            }
        val currentCashEgp =
            cash.fold(
                0L,
            ) { total, bucket ->
                Math.addExact(
                    total,
                    app.mizan.domain.money.MoneyMath
                        .baseMinor(bucket.amountMinor, bucket.currencyDecimals, bucket.egpPerUnit),
                )
            }
        val recentCashDelta =
            statsEntries
                .asSequence()
                .filter { it.accountId in activeAccounts && LocalDate.parse(it.bookedDate) > monthAgo }
                .fold(0L) { total, row -> Math.addExact(total, row.baseMinor) }
        val previousCashEgp = Math.subtractExact(currentCashEgp, recentCashDelta)
        val previousCash = listOf(WealthCashBucket(previousCashEgp, 2, BigDecimal.ONE))
        val previousInputs =
            assets.mapNotNull { asset ->
                holdingInput(asset, transactions, prices, fxHistory, currencies, monthAgo, now, beforeOrOn = monthAgo)
            }
        val previousNetWorth = WealthCalculator.calculate(previousCash, previousInputs).netWorthEgpMinor
        val summary = WealthCalculator.calculate(cash, currentInputs, previousNetWorth)
        val valuesById = summary.holdings.associateBy { it.assetId }
        val positions =
            assets.map { asset ->
                val trades = transactionsFor(asset.id, transactions)
                val price = resolvedPrice(asset, trades, prices, now)
                val rate = price?.let { rateOnOrBefore(it.currency, today, fxHistory) } ?: BigDecimal.ONE
                val currency = price?.currency ?: asset.quoteCurrency
                val decimals = currencyDecimals(currency, currencies)
                val position = AverageCost.position(trades)
                val value: WealthHoldingValue? = valuesById[asset.id]
                V2WealthPosition(
                    asset = asset,
                    quantity = position.quantity,
                    pricePerUnit = price?.price,
                    priceCurrency = currency,
                    sourceLabel = price?.sourceLabel ?: "No saved price",
                    ageHours = price?.asOf?.let { ageHours(now, it) },
                    valueEgpMinor = value?.valueEgpMinor ?: 0L,
                    costEgpMinor = value?.costEgpMinor ?: convert(position.costMinor, decimals, rate),
                    unrealizedEgpMinor = value?.unrealizedProfitEgpMinor ?: -convert(position.costMinor, decimals, rate),
                    realizedEgpMinor = value?.realizedProfitEgpMinor ?: convert(position.realizedProfitMinor, decimals, rate),
                    weightBasisPoints = value?.weightBasisPoints ?: 0L,
                )
            }
        val accountTotals =
            includedBalances
                .groupBy { activeAccounts.getValue(it.accountId).type }
                .map { (type, rows) ->
                    type to
                        rows.fold(0L) { total, row ->
                            Math.addExact(
                                total,
                                app.mizan.domain.money.MoneyMath.baseMinor(
                                    row.openingMinor + row.entryMinor,
                                    row.decimals,
                                    BigDecimal(row.egpPerUnit),
                                ),
                            )
                        }
                }.sortedBy { it.first }
        return V2WealthSnapshot(summary, positions, accountTotals)
    }

    private fun holdingInput(
        asset: AssetEntity,
        transactions: List<AssetTransactionEntity>,
        prices: List<PriceSnapshotEntity>,
        fxHistory: List<FxRateEntity>,
        currencies: List<CurrencyEntity>,
        rateDate: LocalDate,
        now: Instant,
        beforeOrOn: LocalDate? = null,
    ): WealthHoldingInput? {
        val trades = transactionsFor(asset.id, transactions).filter { beforeOrOn == null || it.date <= beforeOrOn }
        val position = AverageCost.position(trades)
        if (position.quantity <= BigDecimal.ZERO) return null
        val price = resolvedPrice(asset, trades, prices, now, beforeOrOn) ?: return null
        val rate = rateOnOrBefore(price.currency, rateDate, fxHistory) ?: return null
        val decimals = currencyDecimals(price.currency, currencies)
        return WealthHoldingInput(
            assetId = asset.id,
            type = assetType(asset.type),
            name = asset.name,
            quantity = position.quantity,
            pricePerUnit = price.price,
            currency = price.currency,
            currencyDecimals = decimals,
            egpPerUnit = rate,
            resaleDeductionPct = BigDecimal(asset.resaleDeductionPct),
            costMinor = position.costMinor,
            realizedProfitMinor = position.realizedProfitMinor,
            source = price.sourceLabel,
            staleHours = ageHours(now, price.asOf),
        )
    }

    private data class ResolvedAssetPrice(
        val price: BigDecimal,
        val currency: String,
        val asOf: Instant,
        val sourceLabel: String,
    )

    private fun resolvedPrice(
        asset: AssetEntity,
        trades: List<AssetTransaction>,
        prices: List<PriceSnapshotEntity>,
        now: Instant,
        beforeOrOn: LocalDate? = null,
    ): ResolvedAssetPrice? {
        val key = priceKey(asset)
        val limit =
            beforeOrOn
                ?.atTime(23, 59, 59)
                ?.atZone(cairo)
                ?.toInstant()
                ?.toEpochMilli() ?: now.toEpochMilli()
        val snapshot = prices.filter { it.key == key && it.asOf <= limit }.maxByOrNull { it.asOf }
        if (snapshot != null) {
            val source =
                when {
                    asset.type == "GOLD" && asset.symbolOrKarats.uppercase() != "21K" &&
                        snapshot.source.contains(
                            "21K",
                            ignoreCase = true,
                        )
                    -> "Derived"

                    snapshot.isManual -> "Manual"

                    else -> "Auto"
                }
            return ResolvedAssetPrice(snapshot.price.toBigDecimal(), snapshot.currency, Instant.ofEpochMilli(snapshot.asOf), source)
        }
        val lastTrade = trades.filter { beforeOrOn == null || it.date <= beforeOrOn }.maxByOrNull { it.date }
        return lastTrade?.let {
            val asOf = it.date.atStartOfDay(cairo).toInstant()
            ResolvedAssetPrice(it.unitPrice, asset.quoteCurrency, asOf, "Last trade")
        }
    }

    private fun transactionsFor(
        id: String,
        values: List<AssetTransactionEntity>,
    ): List<AssetTransaction> =
        values
            .asSequence()
            .filter { it.assetId == id && it.deletedAt == null }
            .map {
                AssetTransaction(
                    id = it.id,
                    assetId = it.assetId,
                    kind = AssetTransactionKind.valueOf(it.kind),
                    date = LocalDate.parse(it.date),
                    quantity = BigDecimal(it.quantity),
                    unitPrice = BigDecimal(it.unitPrice),
                    feeMinor = it.feeMinor,
                    totalMinor = it.totalMinor,
                    txnId = it.txnId,
                    note = it.note,
                )
            }.toList()

    private fun priceKey(asset: AssetEntity): String =
        when {
            asset.type != "GOLD" -> "MANUAL:${asset.id}"
            asset.goldForm == "COIN" -> "GOLD_POUND"
            else -> "GOLD_${asset.symbolOrKarats.uppercase()}"
        }

    private fun assetType(value: String): AssetType = runCatching { AssetType.valueOf(value) }.getOrDefault(AssetType.OTHER)

    private fun rateOnOrBefore(
        currency: String,
        date: LocalDate,
        history: List<FxRateEntity>,
    ): BigDecimal? =
        if (currency == "EGP") {
            BigDecimal.ONE
        } else {
            history
                .filter { it.currency == currency && LocalDate.parse(it.date) <= date }
                .maxByOrNull { it.date }
                ?.let { BigDecimal(it.egpPerUnit) }
        }

    private fun currencyDecimals(
        currency: String,
        currencies: List<CurrencyEntity>,
    ): Int = currencies.firstOrNull { it.code == currency }?.decimals ?: 2

    private fun convert(
        amountMinor: Long,
        decimals: Int,
        rate: BigDecimal,
    ): Long =
        app.mizan.domain.money.MoneyMath
            .baseMinor(amountMinor, decimals, rate)

    private fun ageHours(
        now: Instant,
        then: Instant,
    ): Long = (now.toEpochMilli() - then.toEpochMilli()).coerceAtLeast(0L) / 3_600_000L
}
