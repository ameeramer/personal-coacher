package com.personalcoacher.data.remote.dto

import com.google.gson.annotations.SerializedName

// Request DTOs
data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

// Response DTOs
data class AuthResponse(
    @SerializedName("user") val user: UserDto?,
    @SerializedName("token") val token: String?,
    @SerializedName("error") val error: String?
)

data class UserDto(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("name") val name: String?,
    @SerializedName("image") val image: String?,
    @SerializedName("timezone") val timezone: String?,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("updatedAt") val updatedAt: String?
)

data class SessionResponse(
    @SerializedName("user") val user: SessionUser?
)

data class SessionUser(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String?,
    @SerializedName("name") val name: String?
)

data class CsrfResponse(
    @SerializedName("csrfToken") val csrfToken: String
)
