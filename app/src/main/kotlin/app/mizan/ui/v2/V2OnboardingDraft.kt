// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.runtime.saveable.Saver
import app.mizan.domain.accounts.OnboardingSetup
import app.mizan.domain.accounts.SetupAccount
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class SetupAccountDraft(
    val id: String,
    val name: String,
    val type: String,
    val color: String,
    val icon: String,
    val balances: Map<String, String> = mapOf("EGP" to "0"),
)

data class V2OnboardingDraft(
    val accounts: List<SetupAccountDraft> = defaults,
    val percentages: Map<String, String> = defaultPercentages(accounts),
    val singleAccountId: String? = null,
    val selectedCategoryIds: Set<String>? = null,
) {
    fun plan(decimals: Map<String, Int>): OnboardingSetup =
        OnboardingSetup(
            accounts.map { account ->
                SetupAccount(
                    account.id,
                    account.name,
                    account.type,
                    account.color,
                    account.icon,
                    account.balances.mapValues { (currency, value) -> OnboardingSetup.openingMinor(value, decimals.getValue(currency)) },
                )
            },
            singleAccountId?.let { mapOf(it to 10_000L) }
                ?: percentages.mapValues { OnboardingSetup.percentBasisPoints(it.value) },
            selectedCategoryIds,
        )

    companion object {
        val defaults =
            listOf(
                SetupAccountDraft("setup-essential", "Essential", "CASH", "#0785DB", "account_balance_wallet"),
                SetupAccountDraft("setup-fun", "Fun", "CASH", "#717BCB", "sentiment_satisfied"),
                SetupAccountDraft("setup-investment", "Investment", "INVESTMENT", "#9A6EC2", "trending_up"),
            )

        fun defaultPercentages(accounts: List<SetupAccountDraft>): Map<String, String> =
            OnboardingSetup.defaultSplit(accounts.map { it.id }).mapValues {
                java.math.BigDecimal
                    .valueOf(it.value, 2)
                    .toPlainString()
            }

        val StateSaver = Saver<V2OnboardingDraft, String>(save = { it.encode() }, restore = { decode(it) })

        fun decode(value: String): V2OnboardingDraft {
            val root = Json.parseToJsonElement(value).jsonObject
            val accounts =
                root.getValue("accounts").jsonArray.map { item ->
                    val account = item.jsonObject

                    fun text(key: String) = account.getValue(key).jsonPrimitive.content
                    SetupAccountDraft(
                        text("id"),
                        text("name"),
                        text("type"),
                        text("color"),
                        text("icon"),
                        account.getValue("balances").jsonObject.mapValues { it.value.jsonPrimitive.content },
                    )
                }
            return V2OnboardingDraft(
                accounts,
                root.getValue("percentages").jsonObject.mapValues { it.value.jsonPrimitive.content },
                root["single"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content,
                root["categories"]
                    ?.takeUnless { it == JsonNull }
                    ?.jsonArray
                    ?.map { it.jsonPrimitive.content }
                    ?.toSet(),
            )
        }
    }
}

fun V2OnboardingDraft.encode(): String =
    buildJsonObject {
        put(
            "accounts",
            JsonArray(
                accounts.map { account ->
                    buildJsonObject {
                        put("id", account.id)
                        put("name", account.name)
                        put("type", account.type)
                        put("color", account.color)
                        put("icon", account.icon)
                        put("balances", buildJsonObject { account.balances.forEach { (key, value) -> put(key, value) } })
                    }
                },
            ),
        )
        put("percentages", buildJsonObject { percentages.forEach { (key, value) -> put(key, value) } })
        put("single", singleAccountId?.let(::JsonPrimitive) ?: JsonNull)
        put("categories", selectedCategoryIds?.let { JsonArray(it.sorted().map(::JsonPrimitive)) } ?: JsonNull)
    }.toString()
