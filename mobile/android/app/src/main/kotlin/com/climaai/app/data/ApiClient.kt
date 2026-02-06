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

interface ClimaAIApi {
    
    // Weather endpoints
    @GET("/api/weather")
    suspend fun getWeather(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double
    ): Response<WeatherResponse>
    
    @GET("/api/insights")
    suspend fun getAIInsights(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double
    ): Response<AIInsightsResponse>
    
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
