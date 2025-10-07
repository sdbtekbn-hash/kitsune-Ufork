package io.github.huskydg.magisk.core.model.module

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import io.github.huskydg.magisk.data.database.Converters
import kotlinx.parcelize.Parcelize
import android.os.Parcelable

@Entity(tableName = "blacklist")
@TypeConverters(Converters::class)
@Parcelize
data class Blacklist(
    @PrimaryKey val id: String,
    val source: String,
    val notes: String? = null,
    val antifeatures: List<String>? = null
) : Parcelable {
    
    val isValid get() = id.isNotBlank() && source.isNotBlank()
    
    companion object {
        val EMPTY = Blacklist(
            id = "",
            source = "",
            notes = null,
            antifeatures = null
        )
        
        fun isBlacklisted(enabled: Boolean, blacklist: Blacklist?): Boolean {
            return enabled && 
                   blacklist != null && 
                   blacklist.isValid &&
                   !(blacklist.antifeatures != null && blacklist.antifeatures.size == 1 && blacklist.antifeatures.contains("NoSourceSince"))
        }
    }
}

