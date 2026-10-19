// Copyright (c) 2026 Mizan contributors
package app.mizan.data.repository

import androidx.room.withTransaction
import app.mizan.data.db.AppDatabase
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.LabelEntity

/** Keeps referenced ids and category meaning stable while changing presentation and visibility. */
class ReferenceSettingsWriter(
    private val db: AppDatabase,
) {
    suspend fun save(value: CategoryEntity) {
        require(value.name.isNotBlank())
        val rows = db.referenceDataDao().allCategories()
        val existing = rows.firstOrNull { it.id == value.id }
        if (existing != null && locked(existing)) {
            require(value.copy(sortOrder = existing.sortOrder, archived = existing.archived) == existing)
            require(!value.archived || existing.archived) { "This linked category is locked" }
        }
        if (value.parentId != null) {
            val parent = requireNotNull(rows.firstOrNull { it.id == value.parentId })
            require(parent.kind == value.kind && parent.id != value.id)
            require(value.parentId !in branch(value.id, rows)) { "A category cannot be its own descendant" }
        }
        db.referenceDataDao().upsertCategory(value.copy(name = value.name.trim()))
    }

    suspend fun archive(
        id: String,
        archived: Boolean,
    ) = db.withTransaction {
        val rows = db.referenceDataDao().allCategories()
        val ids = branch(id, rows)
        if (archived) require(rows.filter { it.id in ids }.none(::locked)) { "This branch contains a locked category" }
        ids.forEach { db.referenceDataDao().archiveCategory(it, archived) }
        if (!archived) {
            var parent = rows.firstOrNull { it.id == id }?.parentId
            while (parent != null) {
                db.referenceDataDao().archiveCategory(parent, false)
                parent = rows.firstOrNull { it.id == parent }?.parentId
            }
        }
    }

    suspend fun restoreVisibility(values: List<CategoryEntity>) =
        db.withTransaction {
            values.forEach { value -> db.referenceDataDao().archiveCategory(value.id, value.archived) }
        }

    suspend fun reorder(ids: List<String>) =
        db.withTransaction {
            require(ids.distinct().size == ids.size)
            val rows = db.referenceDataDao().allCategories().filter { it.id in ids }
            require(rows.size == ids.size && rows.map { it.parentId to it.kind }.distinct().size <= 1)
            ids.forEachIndexed { index, id -> db.referenceDataDao().reorderCategory(id, index) }
        }

    suspend fun save(value: LabelEntity) {
        require(value.name.isNotBlank())
        db.referenceDataDao().upsertLabel(value.copy(name = value.name.trim()))
    }

    companion object {
        fun locked(value: CategoryEntity): Boolean = value.id == MizanRepository.UNCATEGORIZED_ID || value.isDebtService

        fun branch(
            id: String,
            rows: List<CategoryEntity>,
        ): Set<String> {
            val selected = mutableSetOf(id)
            do {
                val before = selected.size
                rows.filter { it.parentId in selected }.forEach { selected += it.id }
            } while (selected.size != before)
            return selected
        }
    }
}
