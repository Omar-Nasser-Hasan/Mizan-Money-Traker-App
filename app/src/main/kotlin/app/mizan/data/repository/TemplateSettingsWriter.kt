// Copyright (c) 2026 Mizan contributors
package app.mizan.data.repository

import app.mizan.data.db.AppDatabase
import app.mizan.data.db.IncomeSourceEntity
import app.mizan.data.db.RecordTemplateEntity
import app.mizan.data.db.SplitLineEntity
import app.mizan.data.db.SplitTemplateEntity
import app.mizan.domain.ledger.IncomeSplitCalculator
import app.mizan.domain.ledger.SplitLine
import app.mizan.domain.ledger.SplitMode

class TemplateSettingsWriter(
    private val db: AppDatabase,
) {
    suspend fun save(
        template: SplitTemplateEntity,
        lines: List<SplitLineEntity>,
    ) {
        require(template.name.isNotBlank())
        if (!template.archived) {
            val accounts =
                db
                    .accountDao()
                    .active()
                    .map { it.id }
                    .toSet()
            require(lines.all { it.templateId == template.id && it.accountId in accounts })
            require(template.remainderAccountId == null || template.remainderAccountId in accounts)
            IncomeSplitCalculator.allocate(
                100_000L,
                lines.map {
                    SplitLine(it.id, it.templateId, it.accountId, SplitMode.valueOf(it.mode), it.value)
                },
                template.remainderAccountId,
            )
        }
        db.incomeSetupDao().replaceTemplate(template.copy(name = template.name.trim()), lines)
    }

    suspend fun save(source: IncomeSourceEntity) {
        require(source.name.isNotBlank())
        if (!source.archived) {
            source.defaultCategoryId?.let { id ->
                require(db.referenceDataDao().allCategories().any { it.id == id && it.kind == "INCOME" })
            }
            source.defaultTemplateId?.let { id -> require(db.incomeSetupDao().template(id) != null) }
        }
        db.incomeSetupDao().upsertSource(source.copy(name = source.name.trim()))
    }

    suspend fun save(template: RecordTemplateEntity) {
        require(template.name.isNotBlank() && template.kind in setOf("EXPENSE", "INCOME", "TRANSFER"))
        require(template.amountMinor == null || template.amountMinor > 0)
        db.incomeSetupDao().upsertRecordTemplate(template.copy(name = template.name.trim()))
    }
}
