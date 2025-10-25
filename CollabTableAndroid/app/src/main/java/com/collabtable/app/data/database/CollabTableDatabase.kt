package com.collabtable.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.collabtable.app.data.dao.FieldDao
import com.collabtable.app.data.dao.ItemDao
import com.collabtable.app.data.dao.ItemValueDao
import com.collabtable.app.data.dao.ListDao
import com.collabtable.app.data.model.CollabList
import com.collabtable.app.data.model.Field
import com.collabtable.app.data.model.Item
import com.collabtable.app.data.model.ItemValue

val migration1To2 =
    object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE fields ADD COLUMN fieldType TEXT NOT NULL DEFAULT 'STRING'")
            database.execSQL("ALTER TABLE fields ADD COLUMN fieldOptions TEXT NOT NULL DEFAULT ''")
        }
    }

val migration2To3 =
    object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Local-only column for manual reordering; nullable so existing rows remain unaffected
            database.execSQL("ALTER TABLE lists ADD COLUMN orderIndex INTEGER")
        }
    }

@Database(
    entities = [
        CollabList::class,
        Field::class,
        Item::class,
        ItemValue::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class CollabTableDatabase : RoomDatabase() {
    abstract fun listDao(): ListDao

    abstract fun fieldDao(): FieldDao

    abstract fun itemDao(): ItemDao

    abstract fun itemValueDao(): ItemValueDao

    companion object {
        @Volatile
        private var dbInstance: CollabTableDatabase? = null

        fun getDatabase(context: Context): CollabTableDatabase {
            return dbInstance ?: synchronized(this) {
                val instance =
                    Room.databaseBuilder(
                        context.applicationContext,
                        CollabTableDatabase::class.java,
                        "collab_table_database",
                    )
                        .addMigrations(migration1To2, migration2To3)
                        .build()
                dbInstance = instance
                instance
            }
        }

        fun clearDatabase(context: Context) {
            synchronized(this) {
                dbInstance?.close()
                context.deleteDatabase("collab_table_database")
                dbInstance = null
            }
        }
    }
}
