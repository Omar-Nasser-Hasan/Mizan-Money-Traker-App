// Copyright (c) 2026 Mizan contributors
package app.mizan.data.db

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** M0 has only a schema marker; no product data is persisted. */
@Entity(tableName = "schema_marker")
data class SchemaMarker(
    @PrimaryKey val id: Int,
)

/** Schema migrations are explicit from the first version. */
@Database(entities = [SchemaMarker::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) = Unit
            }
    }
}
