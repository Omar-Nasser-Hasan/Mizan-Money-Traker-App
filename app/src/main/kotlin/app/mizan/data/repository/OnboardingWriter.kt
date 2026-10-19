// Copyright (c) 2026 Mizan contributors
package app.mizan.data.repository

import androidx.room.withTransaction
import app.mizan.data.db.AccountBucketEntity
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.AppDatabase
import app.mizan.data.db.IncomeSourceEntity
import app.mizan.data.db.SplitLineEntity
import app.mizan.data.db.SplitTemplateEntity
import app.mizan.domain.accounts.OnboardingSetup
import java.time.LocalDate

/** One transaction with stable draft ids: retrying a completed write cannot duplicate setup rows. */
class OnboardingWriter(
    private val database: AppDatabase,
) {
    suspend fun save(
        setup: OnboardingSetup,
        openingDate: LocalDate,
    ) {
        database.withTransaction {
            setup.accounts.forEachIndexed { index, account ->
                database.accountDao().upsert(
                    AccountEntity(
                        account.id,
                        account.name.trim(),
                        account.type,
                        account.color,
                        account.icon,
                        account.buckets.keys.first(),
                        openingDate.toString(),
                        true,
                        false,
                        index,
                    ),
                )
                database.accountDao().removeSetupBucketsNotIn(account.id, account.buckets.keys.toList())
                database.accountDao().upsertBuckets(
                    account.buckets.map { (currency, minor) ->
                        AccountBucketEntity(account.id, currency, minor)
                    },
                )
            }
            val lines =
                setup.splitBasisPoints
                    .filterValues { it > 0 }
                    .entries
                    .toList()
            database.incomeSetupDao().replaceTemplate(
                SplitTemplateEntity(TEMPLATE_ID, "Default income split", lines.first().key),
                lines.mapIndexed { index, (accountId, basisPoints) ->
                    SplitLineEntity("$TEMPLATE_ID-$accountId", TEMPLATE_ID, accountId, "PERCENT", basisPoints, index)
                },
            )
            database.incomeSetupDao().upsertSource(IncomeSourceEntity(SOURCE_ID, "Default income", TEMPLATE_ID, "salary"))
            val categories = database.referenceDataDao().allCategories()
            val active = setup.activeCategoryIds(categories.associate { it.id to it.parentId }, setOf(MizanRepository.UNCATEGORIZED_ID))
            categories.forEach { database.referenceDataDao().archiveCategory(it.id, it.id !in active) }
        }
    }

    companion object {
        const val TEMPLATE_ID = "onboarding-default-split"
        const val SOURCE_ID = "onboarding-default-income"
    }
}
