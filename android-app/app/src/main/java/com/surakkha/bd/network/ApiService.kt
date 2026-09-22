package com.surakkha.bd.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

// =========================================================================
// Sealed Class for Result Wrapper (Success / Error / Loading)
// =========================================================================
sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val code: Int? = null) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()
}

// =========================================================================
// Data Transfer Objects (DTOs)
// =========================================================================

/**
 * User information returned by the backend.
 */
data class UserDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

/**
 * Request body for POST /register
 */
data class RegisterRequest(
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("password") val password: String
)

/**
 * Response body for POST /register (201 Created)
 */
data class RegisterResponse(
    @SerializedName("message") val message: String,
    @SerializedName("user") val user: UserDto
)

/**
 * Request body for POST /login
 */
data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

/**
 * Response body for POST /login (200 OK)
 */
data class LoginResponse(
    @SerializedName("message") val message: String,
    @SerializedName("token") val token: String,
    @SerializedName("user") val user: UserDto? = null
)

/**
 * Request body for POST /sos
 */
data class SosRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("sos_type") val sosType: String = "general"
)

/**
 * Detailed Incident Object
 */
data class IncidentDto(
    @SerializedName("id") val id: Int,
    @SerializedName("status") val status: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("sos_type") val sosType: String? = null,
    @SerializedName("severity") val severity: Int? = null
)

/**
 * Response body for POST /sos (201 Created)
 */
data class SosResponse(
    @SerializedName("message") val message: String? = null,
    @SerializedName("id") val id: Int? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("incident") val incident: IncidentDto? = null
) {
    val incidentId: Int
        get() = id ?: incident?.id ?: 0

    val incidentStatus: String
        get() = status ?: incident?.status ?: "pending"

    val incidentCreatedAt: String
        get() = createdAt ?: incident?.createdAt ?: ""
}

/**
 * Request body for POST /location/update (Geo Service on Port 3003)
 */
data class LocationUpdateRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

/**
 * Standard backend error response format.
 */
data class ErrorResponse(
    @SerializedName("message") val message: String? = null
)

// =========================================================================
// Retrofit ApiService Interface
// =========================================================================
interface ApiService {

    /**
     * Registers a new user.
     * Endpoint: POST /register (Auth Service on Port 3001)
     */
    @POST("register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<RegisterResponse>

    /**
     * Authenticates an existing user and returns a JWT token.
     * Endpoint: POST /login (Auth Service on Port 3001)
     */
    @POST("login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    /**
     * Health check endpoint to verify backend connectivity.
     * Endpoint: GET /health
     */
    @GET("health")
    suspend fun healthCheck(): Response<Map<String, String>>

    /**
     * Triggers an emergency SOS incident.
     * Endpoint: POST /sos (SOS Service on Port 3002)
     */
    @POST("sos")
    suspend fun triggerSos(
        @Header("Authorization") token: String,
        @Body request: SosRequest
    ): Response<SosResponse>

    /**
     * Updates user's live GPS location.
     * Endpoint: POST /location/update (Geo Service on Port 3003)
     */
    @POST("location/update")
    suspend fun updateLocation(
        @Header("Authorization") token: String,
        @Body request: LocationUpdateRequest
    ): Response<Unit>
}
