package com.climaai.app.data

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit
import com.climaai.app.BuildConfig
import com.climaai.app.data.api.*

interface ClimaAIApi {
    
    // Weather endpoints
    // The backend has no /api/weather route and names its parameters
    // latitude/longitude, not lat/lon — this returned 404 on every call.
    // getCurrentWeather below declares the same endpoint correctly but nothing
    // used it; WeatherRepository calls this one.
    @GET("/api/weather/current")
    suspend fun getWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<WeatherResponse>
    
    @GET("/api/weather/current")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<WeatherResponse>
    
    @GET("/api/weather/hourly")
    suspend fun getHourlyForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hours") hours: Int = 24
    ): Response<WeatherResponse>
    
    @GET("/api/weather/daily")
    suspend fun getDailyForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("days") days: Int = 7
    ): Response<WeatherResponse>
    
    @GET("/api/weather/air-quality")
    suspend fun getAirQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<Map<String, AirQuality>>
    
    @GET("/api/weather/nowcast")
    suspend fun getNowcast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<Map<String, Any>>
    
    @GET("/api/weather/radar")
    suspend fun getRadarFrames(): Response<Map<String, Any>>
    
    @GET("/api/weather/alerts")
    suspend fun getWeatherAlertsNWS(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<NWSAlertsResponse>
    
    @GET("/api/weather/alerts/{state}")
    suspend fun getStateWeatherAlerts(
        @Path("state") state: String
    ): Response<NWSStateAlertsResponse>
    
    // AI endpoints (Premium)
    @GET("/api/insights")
    suspend fun getAIInsights(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double
    ): Response<AIInsightsResponse>
    
    @GET("/api/summary")
    suspend fun getDailySummary(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("location_name") locationName: String = "your location"
    ): Response<Map<String, Any>>
    
    @GET("/api/outfit")
    suspend fun getOutfitRecommendation(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<OutfitRecommendation>
    
    @GET("/api/activities")
    suspend fun getActivityRecommendations(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<List<ActivityRecommendation>>
    
    @GET("/api/health")
    suspend fun getHealthInsights(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<HealthInsight>
    
    @GET("/api/travel-risk")
    suspend fun getTravelRisk(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("destination") destination: String = "your destination"
    ): Response<TravelRiskAnalysis>
    
    // Auth endpoints
    @POST("/api/auth/register")
    suspend fun register(@Body user: UserRegister): Response<TokenResponse>
    
    @POST("/api/auth/login")
    @FormUrlEncoded
    suspend fun login(
        @Field("username") email: String,
        @Field("password") password: String
    ): Response<TokenResponse>

    @POST("/api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<Map<String, String>>
    
    @GET("/api/auth/me")
    suspend fun getCurrentUser(): Response<User>
    
    @PUT("/api/users/preferences")
    suspend fun updatePreferences(@Body preferences: UserPreferences): Response<User>
    
    // Subscription endpoints
    @GET("/api/subscriptions/status")
    suspend fun getSubscriptionStatus(): Response<SubscriptionStatus>
    
    @POST("/api/subscriptions/validate")
    suspend fun validateReceipt(
        @Body data: ReceiptValidationRequest
    ): Response<ReceiptValidationResponse>
    
    @POST("/api/subscriptions/trial")
    suspend fun startTrial(@Body data: TrialRequest): Response<Subscription>
    
    @GET("/api/subscriptions/plans")
    suspend fun getPlans(): Response<PlansResponse>
    
    // ============================================================
    // Alerts endpoints
    // ============================================================
    
    @GET("/alerts")
    suspend fun getAlerts(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("location_name") locationName: String = "your location"
    ): Response<AlertsResponse>
    
    @GET("/alerts/history")
    suspend fun getAlertHistory(): Response<AlertHistoryResponse>
    
    @POST("/alerts/{alertId}/dismiss")
    suspend fun dismissAlert(
        @Path("alertId") alertId: Int
    ): Response<Map<String, String>>
    
    // ============================================================
    // Health endpoints (pollen, flu, migraine, activities)
    // ============================================================
    
    @GET("/health/pollen")
    suspend fun getPollenForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("days") days: Int = 5
    ): Response<PollenForecastResponse>
    
    @GET("/health/pollen/today")
    suspend fun getTodaysPollen(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<PollenTodayResponse>
    
    @GET("/health/flu-risk")
    suspend fun getFluRisk(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<FluRiskResponse>
    
    @GET("/health/migraine-risk")
    suspend fun getMigraineRisk(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<MigraineRiskResponse>
    
    @GET("/health/activities")
    suspend fun getAllActivities(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<AllActivitiesResponse>
    
    @GET("/health/activities/{activity}")
    suspend fun getActivityForecast(
        @Path("activity") activity: String,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<ActivityDetailResponse>
    
    // ============================================================
    // Location endpoints
    // ============================================================
    
    @GET("/api/locations/search")
    suspend fun searchLocations(
        @Query("query") query: String,
        @Query("limit") limit: Int = 10
    ): Response<LocationSearchResponse>
    
    @GET("/api/locations/favorites")
    suspend fun getFavoriteLocations(): Response<FavoriteLocationsResponse>
    
    @POST("/api/locations/favorites")
    suspend fun addFavoriteLocation(
        @Query("name") name: String,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("is_default") isDefault: Boolean = false
    ): Response<AddFavoriteResponse>
    
    @DELETE("/api/locations/favorites/{locationId}")
    suspend fun deleteFavoriteLocation(
        @Path("locationId") locationId: Int
    ): Response<Map<String, String>>
    
    @PATCH("/api/locations/favorites/{locationId}/default")
    suspend fun setDefaultLocation(
        @Path("locationId") locationId: Int
    ): Response<Map<String, String>>
    
    // ============================================================
    // Notification endpoints
    // ============================================================
    
    @POST("/notifications/register-device")
    suspend fun registerDevice(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<DeviceRegisterResponse>
    
    @POST("/notifications/unregister-device")
    suspend fun unregisterDevice(
        @Body body: Map<String, String>
    ): Response<Map<String, String>>
    
    @POST("/notifications/test")
    suspend fun sendTestNotification(
        @Body body: Map<String, String>
    ): Response<TestNotificationResponse>
    
    @GET("/notifications/preferences")
    suspend fun getNotificationPreferences(): Response<NotificationPreferencesResponse>
    
    @PUT("/notifications/preferences")
    suspend fun updateNotificationPreferences(
        @Body body: Map<String, Boolean>
    ): Response<NotificationPreferencesUpdateResponse>
    
    // ============================================================
    // Personalization endpoints
    // ============================================================
    
    @POST("/personalization/track")
    suspend fun trackEvent(
        @Body request: TrackEventRequest
    ): Response<Map<String, String>>
    
    @GET("/personalization/profile")
    suspend fun getPersonalizationProfile(
        @Query("user_id") userId: String = "demo_user"
    ): Response<UserPreferenceProfile>
    
    @GET("/personalization/recommendations")
    suspend fun getPersonalizedRecommendations(
        @Query("temperature") temperature: Double,
        @Query("humidity") humidity: Int,
        @Query("uv_index") uvIndex: Double,
        @Query("precipitation_probability") precipProbability: Int,
        @Query("user_id") userId: String = "demo_user"
    ): Response<PersonalizedContentResponse>
    
    @GET("/personalization/should-notify")
    suspend fun shouldNotify(
        @Query("notification_type") notificationType: String,
        @Query("user_id") userId: String = "demo_user"
    ): Response<ShouldNotifyResponse>
    
    @POST("/personalization/profile/update")
    suspend fun updatePersonalizationProfile(
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<UserPreferenceProfile>
    
    // ============================================================
    // Precipitation Nowcast endpoint
    // ============================================================
    
    @GET("/api/v1/weather/nowcast")
    suspend fun getPrecipitationNowcast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<PrecipitationNowcast>
    
    // ============================================================
    // Multi-Source Weather endpoints
    // ============================================================
    
    @GET("/api/weather/multi-source")
    suspend fun getMultiSourceWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("sources") sources: String? = null
    ): Response<MultiSourceWeatherResponse>
    
    @GET("/api/weather/uv")
    suspend fun getUVIndex(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<UVIndexData>
    
    @GET("/api/weather/marine")
    suspend fun getMarineWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Response<MarineWeatherData>
    
    @GET("/api/weather/historical")
    suspend fun getHistoricalWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String
    ): Response<HistoricalWeatherData>
}

// Request/Response classes for subscriptions
data class ReceiptValidationRequest(
    val platform: String,
    val receipt_data: String,
    val product_id: String? = null
)

data class ReceiptValidationResponse(
    val valid: Boolean,
    val platform: String,
    val is_active: Boolean?,
    val product_id: String?,
    val expires_at: String?,
    val error: String?
)

data class TrialRequest(val platform: String = "google")

data class PlansResponse(
    val plans: List<SubscriptionPlan>,
    val trial: TrialInfo
)

data class TrialInfo(
    val duration_days: Int,
    val features: String
)

object ApiClient {
    private var authToken: String? = null
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
            authToken?.let {
                request.addHeader("Authorization", "Bearer $it")
            }
            chain.proceed(request.build())
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        .create()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
    
    val api: ClimaAIApi = retrofit.create(ClimaAIApi::class.java)
    
    fun setAuthToken(token: String?) {
        authToken = token
    }
    
    fun getAuthToken(): String? = authToken
}
