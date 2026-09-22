package com.surakkha.bd.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton object providing Retrofit instances for:
 * - Auth Service (port 3001)
 * - SOS Service (port 3002)
 * - Geo Service (port 3003)
 */
object RetrofitClient {

    // OkHttp Logging Interceptor for HTTP request and response inspection in Logcat
    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    // Shared OkHttpClient configuration
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // Retrofit instance for Auth Service (Port 3001)
    private val authRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.AUTH_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Retrofit instance for SOS Service (Port 3002)
    private val sosRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.SOS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Retrofit instance for Geo Service (Port 3003)
    private val geoRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.GEO_SERVICE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * API Service for Authentication (Login / Register / Health) on Port 3001.
     */
    val authApiService: ApiService by lazy {
        authRetrofit.create(ApiService::class.java)
    }

    // Default apiService alias for backward compatibility with LoginActivity & RegisterActivity
    val apiService: ApiService
        get() = authApiService

    /**
     * API Service for Emergency SOS Incidents on Port 3002.
     */
    val sosApiService: ApiService by lazy {
        sosRetrofit.create(ApiService::class.java)
    }

    /**
     * API Service for Geo Location & Proximity on Port 3003.
     */
    val geoApiService: ApiService by lazy {
        geoRetrofit.create(ApiService::class.java)
    }

    /**
     * Executes an API call safely and maps the response to a sealed [NetworkResult].
     */
    suspend fun <T> safeApiCall(apiCall: suspend () -> retrofit2.Response<T>): NetworkResult<T> {
        return try {
            val response = apiCall()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    NetworkResult.Success(body)
                } else {
                    // For endpoints returning Response<Unit> (e.g. 200/204 with empty body)
                    @Suppress("UNCHECKED_CAST")
                    NetworkResult.Success(Unit as T)
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    val errorObj = com.google.gson.Gson().fromJson(errorBody, ErrorResponse::class.java)
                    errorObj.message ?: "Request failed with status code ${response.code()}"
                } catch (e: Exception) {
                    "Request failed with status code ${response.code()}"
                }
                NetworkResult.Error(errorMessage, response.code())
            }
        } catch (e: java.net.SocketTimeoutException) {
            NetworkResult.Error("Connection timed out. Please check your network connection.")
        } catch (e: java.net.ConnectException) {
            NetworkResult.Error("Could not connect to server. Ensure your backend service is running.")
        } catch (e: java.io.IOException) {
            NetworkResult.Error("Network error: ${e.localizedMessage ?: "Unknown I/O error"}")
        } catch (e: Exception) {
            NetworkResult.Error("Unexpected error: ${e.localizedMessage ?: "An unexpected error occurred"}")
        }
    }
}
