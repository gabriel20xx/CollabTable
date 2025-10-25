package com.collabtable.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.collabtable.app.data.dao.*
import com.collabtable.app.data.model.*

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE fields ADD COLUMN fieldType TEXT NOT NULL DEFAULT 'STRING'")
            database.execSQL("ALTER TABLE fields ADD COLUMN fieldOptions TEXT NOT NULL DEFAULT ''")
        }
    }

@Database(
    entities = [
        CollabList::class,
        Field::class,
        Item::class,
        ItemValue::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class CollabTableDatabase : RoomDatabase() {
    abstract fun listDao(): ListDao

    abstract fun fieldDao(): FieldDao

    abstract fun itemDao(): ItemDao

    abstract fun itemValueDao(): ItemValueDao

    companion object {
        @Volatile
        private var INSTANCE: CollabTableDatabase? = null

        fun getDatabase(context: Context): CollabTableDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance =
                    Room.databaseBuilder(
                        context.applicationContext,
                        CollabTableDatabase::class.java,
                        "collab_table_database",
                    )
                        .addMigrations(MIGRATION_1_2)
                        .build()
                INSTANCE = instance
                instance
            }
        }

        fun clearDatabase(context: Context) {
            synchronized(this) {
                INSTANCE?.close()
                context.deleteDatabase("collab_table_database")
                INSTANCE = null
            }
        }
    }
}
