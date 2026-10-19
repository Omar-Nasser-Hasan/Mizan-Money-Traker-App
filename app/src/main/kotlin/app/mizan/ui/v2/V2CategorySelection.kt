// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import app.mizan.data.db.CategoryEntity
import app.mizan.data.repository.MizanRepository

object V2CategorySelection {
    fun all(categories: List<CategoryEntity>): Set<String> = categories.map { it.id }.toSet()

    fun clear(): Set<String> = setOf(MizanRepository.UNCATEGORIZED_ID)

    fun branch(
        id: String,
        categories: List<CategoryEntity>,
    ): Set<String> {
        val result = mutableSetOf(id)
        var previousSize: Int
        do {
            previousSize = result.size
            categories.filter { it.parentId in result }.forEach { result += it.id }
        } while (result.size != previousSize)
        return result
    }

    fun toggleBranch(
        id: String,
        selected: Set<String>,
        categories: List<CategoryEntity>,
    ): Set<String> {
        val branch = branch(id, categories)
        return (if (selected.containsAll(branch)) selected - branch else selected + branch) + clear()
    }

    fun toggleLeaf(
        id: String,
        selected: Set<String>,
        categories: List<CategoryEntity>,
    ): Set<String> {
        val next = (if (id in selected) selected - id else selected + id).toMutableSet()
        var parent = categories.firstOrNull { it.id == id }?.parentId
        while (parent != null) {
            if (categories.any { it.parentId == parent && it.id in next }) next += parent else next -= parent
            parent = categories.firstOrNull { it.id == parent }?.parentId
        }
        return next + clear()
    }
}
