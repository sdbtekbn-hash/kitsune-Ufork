package io.github.huskydg.magisk.data.database

import androidx.room.*
import io.github.huskydg.magisk.core.model.module.Blacklist

@Dao
interface BlacklistDao {
    
    @Query("SELECT * FROM blacklist WHERE id = :id")
    suspend fun getBlacklist(id: String): Blacklist?
    
    @Query("SELECT * FROM blacklist")
    suspend fun getAllBlacklist(): List<Blacklist>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlacklist(blacklist: Blacklist)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlacklistList(blacklist: List<Blacklist>)
    
    @Delete
    suspend fun deleteBlacklist(blacklist: Blacklist)
    
    @Query("DELETE FROM blacklist WHERE id = :id")
    suspend fun deleteBlacklistById(id: String)
    
    @Query("DELETE FROM blacklist")
    suspend fun clearBlacklist()
    
    // Helper methods
    suspend fun isModuleBlacklisted(moduleId: String): Boolean {
        val blacklist = getBlacklist(moduleId) ?: return false
        return Blacklist.isBlacklisted(true, blacklist)
    }
}
