// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.ports

import app.mizan.domain.ledger.LedgerPosting
import app.mizan.domain.ledger.TxnKind
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class RecordQuery(
    val accountId: String? = null,
    val categoryId: String? = null,
    val labelId: String? = null,
    val kind: TxnKind? = null,
    val status: String? = null,
    val currency: String? = null,
    val dateStart: LocalDate? = null,
    val dateEnd: LocalDate? = null,
    val amountMin: Long? = null,
    val amountMax: Long? = null,
    val search: String? = null,
)

data class PageRequest(
    val offset: Int,
    val limit: Int,
)

data class Page<T>(
    val values: List<T>,
    val total: Int,
)

interface LedgerRepository {
    suspend fun post(posting: LedgerPosting)

    suspend fun softDelete(transactionId: String)

    suspend fun restore(transactionId: String)

    fun observeRecordCount(query: RecordQuery): Flow<Int>
}
