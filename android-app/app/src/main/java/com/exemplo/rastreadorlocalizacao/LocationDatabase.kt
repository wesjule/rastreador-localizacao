package com.exemplo.rastreadorlocalizacao

import android.content.Context
import androidx.room.*
import java.util.*

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double,
    val speed: Float,
    val timestamp: Date
)

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY timestamp DESC")
    suspend fun getAllLocations(): List<LocationEntity>
    
    @Query("SELECT COUNT(*) FROM locations")
    suspend fun getLocationCount(): Int
    
    @Insert
    suspend fun insert(location: LocationEntity)

    @Query("DELETE FROM locations")
    suspend fun deleteAll()

    // Lote mais ANTIGO primeiro (ordem cronológica p/ desenhar o trajeto certo no painel).
    @Query("SELECT * FROM locations ORDER BY id ASC LIMIT :limit")
    suspend fun getOldestBatch(limit: Int): List<LocationEntity>

    // Apaga SÓ os pontos já confirmados pelo servidor (por id) — preserva os capturados durante o envio.
    @Query("DELETE FROM locations WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Database(entities = [LocationEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class LocationDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    
    companion object {
        @Volatile
        private var INSTANCE: LocationDatabase? = null
        
        fun getDatabase(context: Context): LocationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LocationDatabase::class.java,
                    "location_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

