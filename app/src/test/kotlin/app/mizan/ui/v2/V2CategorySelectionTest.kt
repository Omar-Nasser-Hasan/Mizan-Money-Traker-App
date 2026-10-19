// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import app.mizan.data.db.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class V2CategorySelectionTest {
    @Test fun `parent toggles all children while leaf changes keep correct parent state`() {
        val all = V2CategorySelection.all(categories)
        val clearedBranch = V2CategorySelection.toggleBranch("parent", all, categories)
        assertEquals(setOf("system-uncategorized"), clearedBranch)
        val oneChild = V2CategorySelection.toggleLeaf("child", clearedBranch, categories)
        assertEquals(setOf("parent", "child", "system-uncategorized"), oneChild)
        assertFalse("parent" in V2CategorySelection.toggleLeaf("child", oneChild, categories))
        assertEquals(all, V2CategorySelection.toggleBranch("parent", oneChild, categories))
    }

    private val categories =
        listOf(
            CategoryEntity("parent", name = "Food", icon = "restaurant", color = "#0785DB", kind = "EXPENSE"),
            CategoryEntity("child", "parent", "Groceries", "shopping_cart", "#0785DB", "EXPENSE"),
            CategoryEntity("other", "parent", "Coffee", "coffee", "#0785DB", "EXPENSE"),
            CategoryEntity("system-uncategorized", name = "Uncategorized", icon = "inbox", color = "#0785DB", kind = "EXPENSE"),
        )
}
