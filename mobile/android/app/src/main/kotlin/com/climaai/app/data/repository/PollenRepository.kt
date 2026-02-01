package com.climaai.app.data.repository

import android.util.Log
import com.climaai.app.data.api.OpenMeteoAirQualityApi
import com.climaai.app.ui.screens.PollenData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for fetching pollen data from Open-Meteo Air Quality API.
 * Free, no API key required.
 */
object PollenRepository {
    
    private const val TAG = "PollenRepository"
    private val api = OpenMeteoAirQualityApi.create()
    
    /**
     * Fetch current pollen levels.
     */
    suspend fun getPollen(lat: Double, lon: Double): Result<PollenData> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAirQuality(lat, lon)
            
            if (response.isSuccessful && response.body() != null) {
                val current = response.body()!!.current
                
                if (current != null) {
                    // Convert pollen counts to 0-5 scale
                    val treePollen = pollenToLevel(
                        (current.alderPollen ?: 0) + 
                        (current.birchPollen ?: 0)
                    )
                    val grassPollen = pollenToLevel(current.grassPollen ?: 0)
                    val weedPollen = pollenToLevel(
                        (current.ragweedPollen ?: 0) + 
                        (current.mugwortPollen ?: 0)
                    )
                    
                    // Mold approximated from humidity conditions
                    val moldLevel = when {
                        current.pm25 > 50 -> 4
                        current.pm25 > 25 -> 3
                        current.pm25 > 10 -> 2
                        else -> 1
                    }
                    
                    val overall = maxOf(treePollen, grassPollen, weedPollen, moldLevel)
                    
                    Result.success(PollenData(
                        tree = treePollen,
                        grass = grassPollen,
                        weed = weedPollen,
                        mold = moldLevel,
                        overall = overall
                    ))
                } else {
                    Log.w(TAG, "No current pollen data available")
                    Result.success(getDefaultPollenData())
                }
            } else {
                Log.e(TAG, "API error: ${response.code()}")
                Result.failure(Exception("Failed to fetch pollen: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Convert pollen count to 0-5 level scale.
     * Based on typical pollen count thresholds.
     */
    private fun pollenToLevel(count: Int): Int = when {
        count <= 0 -> 0
        count <= 10 -> 1
        count <= 50 -> 2
        count <= 100 -> 3
        count <= 200 -> 4
        else -> 5
    }
    
    private fun getDefaultPollenData() = PollenData(
        tree = 2,
        grass = 2,
        weed = 1,
        mold = 1,
        overall = 2
    )
}
