package com.climaai.app.data.cache

import android.content.Context
import androidx.room.*

/**
 * Room entity for cached weather data.
 * Keyed by location (lat/lon rounded to 2 decimals for grouping nearby locations).
 */
@Entity(tableName = "weather_cache")
data class CachedWeather(
    @PrimaryKey
    val locationKey: String,  // Format: "lat_lon" with 2 decimal precision
    
    val latitude: Double,
    val longitude: Double,
    val locationName: String?,
    
    /** Raw JSON response from API */
    val weatherJson: String,
    
    /** Timestamp when cached (millis) */
    val cachedAt: Long,
    
    /** Last successful network fetch timestamp */
    val lastFetchedAt: Long
)

/**
 * Room entity for cached AI insights.
 */
@Entity(tableName = "ai_insights_cache")
data class CachedAIInsights(
    @PrimaryKey
    val locationKey: String,
    
    val latitude: Double,
    val longitude: Double,
    
    /** Raw JSON response from API */
    val insightsJson: String,
    
    val cachedAt: Long
)

/**
 * DAO for weather cache operations.
 */
@Dao
interface WeatherCacheDao {
    
    @Query("SELECT * FROM weather_cache WHERE locationKey = :key")
    suspend fun getWeather(key: String): CachedWeather?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeather(cache: CachedWeather)
    
    @Query("DELETE FROM weather_cache WHERE locationKey = :key")
    suspend fun deleteWeather(key: String)
    
    @Query("DELETE FROM weather_cache")
    suspend fun clearAllWeather()
    
    @Query("DELETE FROM weather_cache WHERE cachedAt < :cutoffTime")
    suspend fun deleteStaleWeather(cutoffTime: Long)
    
    @Query("SELECT * FROM ai_insights_cache WHERE locationKey = :key")
    suspend fun getAIInsights(key: String): CachedAIInsights?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAIInsights(cache: CachedAIInsights)
    
    @Query("DELETE FROM ai_insights_cache")
    suspend fun clearAllInsights()
}

/**
 * Room database for weather caching.
 */
@Database(
    entities = [CachedWeather::class, CachedAIInsights::class],
    version = 1,
    exportSchema = false
)
abstract class WeatherCacheDatabase : RoomDatabase() {
    abstract fun weatherCacheDao(): WeatherCacheDao
    
    companion object {
        @Volatile
        private var INSTANCE: WeatherCacheDatabase? = null
        
        fun getInstance(context: Context): WeatherCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WeatherCacheDatabase::class.java,
                    "weather_cache.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Helper to generate location key from coordinates.
 * Rounds to 2 decimal places (~1.1km precision) to group nearby locations.
 */
fun generateLocationKey(lat: Double, lon: Double): String {
    val roundedLat = String.format("%.2f", lat)
    val roundedLon = String.format("%.2f", lon)
    return "${roundedLat}_${roundedLon}"
}
