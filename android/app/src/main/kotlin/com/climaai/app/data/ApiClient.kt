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
    
    // The router mounts the frame list at /radar/frames. /radar on its own is
    // only the shared prefix of that and the tile route, so it 404s.
    @GET("/api/weather/radar/frames")
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
    // The router names these latitude/longitude like every other endpoint here.
    // Sending lat/lon made FastAPI reject the call with a 422 for a missing
    // required query parameter — the error the AI Insights card led to, and one
    // no amount of retrying could clear.
    @GET("/api/insights")
    suspend fun getAIInsights(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
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
    
    // The router reads a JSON UserLogin body. This was declared as an OAuth2
    // style form post with a "username" field, so FastAPI rejected it with a 422
    // before it ever looked at the credentials — every sign-in failed, and the
    // screen reported it as bad credentials.
    @POST("/api/auth/login")
    suspend fun loginRequest(@Body credentials: UserLogin): Response<TokenResponse>

    @POST("/api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<Map<String, String>>
    
    @GET("/api/auth/me")
    suspend fun getCurrentUser(): Response<User>
    
    // There is no /api/users router at all; preferences are saved through the
    // auth router's user update, which expects them nested under "preferences".
    @PUT("/api/auth/me")
    suspend fun updatePreferences(@Body update: UserUpdateRequest): Response<User>

    // Account deletion (Play requires an in-app path now that registration is
    // in-app). Same /me as the profile — the users router mounts at /api/auth.
    // Replies 204 No Content.
    @DELETE("/api/auth/me")
    suspend fun deleteAccount(): Response<Unit>

    // Subscription endpoints
    @GET("/api/subscriptions/status")
    suspend fun getSubscriptionStatus(): Response<SubscriptionStatus>
    
    @POST("/api/subscriptions/validate")
    suspend fun validateReceipt(
        @Body data: ReceiptValidationRequest
    ): Response<ReceiptValidationResponse>
    
    @POST("/api/subscriptions/trial")
    suspend fun startTrial(@Body data: TrialRequest): Response<Subscription>

    // Server-side entitlement for a completed Play purchase: the backend
    // validates the token with Google and opens the subscription row that
    // /status reads. Without this call a purchase exists only on the device.
    @POST("/api/subscriptions/activate")
    suspend fun activateSubscription(
        @Body data: SubscriptionActivateRequest
    ): Response<Subscription>

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
    
    // Every personalization route resolves the user from the bearer token. The
    // user_id query these used to send is not a parameter any of them declares,
    // so it was ignored — a caller passing one was not choosing a profile, it
    // just read its own.
    @GET("/personalization/profile")
    suspend fun getPersonalizationProfile(): Response<UserPreferenceProfile>

    @GET("/personalization/recommendations")
    suspend fun getPersonalizedRecommendations(
        @Query("temperature") temperature: Double,
        @Query("humidity") humidity: Int,
        @Query("uv_index") uvIndex: Double,
        @Query("precipitation_probability") precipProbability: Int
    ): Response<PersonalizedContentResponse>

    @GET("/personalization/should-notify")
    suspend fun shouldNotify(
        @Query("notification_type") notificationType: String
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

/**
 * Sign in with the two fields the caller actually holds.
 *
 * An extension rather than a member so that fixing the wire format above did not
 * ripple out into every call site: Retrofit needs the body as a single object,
 * but nobody calling this has one.
 */
suspend fun ClimaAIApi.login(email: String, password: String): Response<TokenResponse> =
    loginRequest(UserLogin(email = email, password = password))

// Body of PUT /api/auth/me. Every field on the router's UserUpdate is optional,
// so this only carries the part the app actually changes.
data class UserUpdateRequest(
    val preferences: UserPreferences
)

// Body of POST /api/subscriptions/activate — the router's SubscriptionCreate.
// platform and plan must be values of the backend's SubscriptionPlatform
// ("google"/"apple") and SubscriptionPlan ("monthly"/"annual") enums;
// receipt_data carries the Play purchase token.
data class SubscriptionActivateRequest(
    val platform: String,
    val plan: String,
    val receipt_data: String
)

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
    
    // In an unconfigured release build API_BASE_URL is the RFC 2606 sentinel
    // https://unconfigured.invalid/ (see build.gradle). That is safe to build
    // against: baseUrl() only parses the URL — no DNS, no connection — and
    // create() returns a lazy proxy, so this object never touches the network
    // until a call executes. Those calls fail fast with UnknownHostException,
    // which every repository already treats as offline. Keep it that way: no
    // eager health checks or warm-up pings against the base URL at init.
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
