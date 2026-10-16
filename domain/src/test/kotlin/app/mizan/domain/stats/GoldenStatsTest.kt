// Copyright (c) 2026 Mizan contributors
package app.mizan.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GoldenStatsTest {
    private val march = Period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 20))

    @Test fun hand_computed_golden_dataset_matches_every_statistic_total() {
        val rows =
            requireNotNull(javaClass.getResourceAsStream("/golden/stats-v1.csv"))
                .bufferedReader()
                .readLines()
                .drop(1)
                .filter(String::isNotBlank)
                .map { line ->
                    val values = line.split(',')
                    StatRecord(
                        values[0],
                        LocalDate.parse(values[1]),
                        StatKind.valueOf(values[2]),
                        values[3].toLong(),
                        values[4],
                        values[6].ifBlank { null },
                        values[7].ifBlank { null },
                        values[8].trim().takeIf { it.isNotEmpty() }?.split(';')?.toSet().orEmpty(),
                    )
                }
        val cashFlow = StatsEngine.cashFlow(rows, march)

        assertEquals(60, rows.size)
        assertEquals(200_000L, cashFlow.incomeMinor)
        assertEquals(60_000L, cashFlow.expenseMinor)
        assertEquals(140_000L, cashFlow.netMinor)
        assertEquals(30_000L, StatsEngine.byCategory(rows, march)["groceries"])
        assertEquals(30_000L, StatsEngine.byCategory(rows, march)["restaurants"])
        assertEquals(60_000L, StatsEngine.byLabel(rows, march)["food"])
        assertEquals(30_000L, StatsEngine.topPayees(rows, march).first { it.first == "market" }.second)
        assertEquals(30_000L, StatsEngine.topPayees(rows, march).first { it.first == "cafe" }.second)
    }
}
