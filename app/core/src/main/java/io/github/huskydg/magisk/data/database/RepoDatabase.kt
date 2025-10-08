package io.github.huskydg.magisk.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.huskydg.magisk.core.AppContext
import io.github.huskydg.magisk.core.model.module.Blacklist
import io.github.huskydg.magisk.core.model.module.OnlineModule

@Database(version = 2, entities = [OnlineModule::class, Blacklist::class], exportSchema = false)
@TypeConverters(Converters::class)
abstract class RepoDatabase : RoomDatabase() {
    abstract fun repoDao(): RepoDao
    abstract fun blacklistDao(): BlacklistDao

    companion object {
        @Volatile
        private var INSTANCE: RepoDatabase? = null

        fun getInstance(): RepoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    AppContext,
                    RepoDatabase::class.java,
                    "repo.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

