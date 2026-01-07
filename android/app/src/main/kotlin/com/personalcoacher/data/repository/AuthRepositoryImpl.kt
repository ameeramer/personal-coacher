package com.personalcoacher.data.repository

import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.dao.UserDao
import com.personalcoacher.data.local.entity.UserEntity
import com.personalcoacher.data.remote.PersonalCoachApi
import com.personalcoacher.data.remote.dto.LoginRequest
import com.personalcoacher.domain.model.User
import com.personalcoacher.domain.repository.AuthRepository
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: PersonalCoachApi,
    private val userDao: UserDao,
    private val tokenManager: TokenManager
) : AuthRepository {

    override val isLoggedIn: Flow<Boolean> = tokenManager.isLoggedIn
    override val currentUserId: Flow<String?> = tokenManager.currentUserId

    override suspend fun login(email: String, password: String): Resource<User> {
        return try {
            val response = api.login(LoginRequest(email, password))

            if (response.isSuccessful) {
                val sessionResponse = response.body()
                val sessionUser = sessionResponse?.user

                if (sessionUser != null) {
                    // Extract token from cookies or create a session token
                    val cookies = response.headers()["Set-Cookie"]
                    val token = extractTokenFromCookies(cookies) ?: UUID.randomUUID().toString()

                    // Save credentials
                    tokenManager.saveToken(token)
                    tokenManager.saveUserId(sessionUser.id)
                    tokenManager.saveUserEmail(email)

                    // Create and save user
                    val now = Instant.now()
                    val user = User(
                        id = sessionUser.id,
                        email = sessionUser.email ?: email,
                        name = sessionUser.name,
                        image = null,
                        timezone = null,
                        createdAt = now,
                        updatedAt = now
                    )
                    userDao.insertUser(UserEntity.fromDomainModel(user))

                    Resource.success(user)
                } else {
                    Resource.error("Login failed: Invalid response")
                }
            } else {
                Resource.error("Login failed: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.error("Login failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    override suspend fun logout() {
        tokenManager.clearAll()
        userDao.deleteAllUsers()
    }

    override suspend fun getCurrentUser(): User? {
        return userDao.getCurrentUser().firstOrNull()?.toDomainModel()
    }

    private fun extractTokenFromCookies(cookies: String?): String? {
        if (cookies == null) return null
        // Try to extract next-auth.session-token from cookies
        return cookies.split(";")
            .map { it.trim() }
            .find { it.startsWith("next-auth.session-token=") }
            ?.substringAfter("=")
            ?.substringBefore(";")
    }
}
