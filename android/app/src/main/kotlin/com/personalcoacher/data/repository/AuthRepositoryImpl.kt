package com.personalcoacher.data.repository

import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.dao.UserDao
import com.personalcoacher.data.local.entity.UserEntity
import com.personalcoacher.data.remote.PersonalCoachApi
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
            // Step 1: Get CSRF token from NextAuth
            val csrfResponse = api.getCsrfToken()
            if (!csrfResponse.isSuccessful) {
                return Resource.error("Login failed: Unable to initialize session")
            }
            val csrfToken = csrfResponse.body()?.csrfToken
                ?: return Resource.error("Login failed: Invalid CSRF response")

            // Step 2: Login with credentials and CSRF token
            val response = api.login(
                email = email,
                password = password,
                csrfToken = csrfToken
            )

            if (response.isSuccessful) {
                // Extract token from cookies (get all Set-Cookie headers)
                val cookieHeaders = response.headers().values("Set-Cookie")
                val token = extractTokenFromCookies(cookieHeaders)

                if (token != null) {
                    // Save token first
                    tokenManager.saveToken(token)
                    tokenManager.saveUserEmail(email)

                    // Step 3: Fetch the session to get user details
                    val sessionResponse = api.getSession()
                    val sessionUser = sessionResponse.body()?.user

                    if (sessionUser != null) {
                        tokenManager.saveUserId(sessionUser.id)

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
                        Resource.error("Login failed: Unable to fetch user session")
                    }
                } else {
                    Resource.error("Login failed: No session token received")
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

    private fun extractTokenFromCookies(cookieHeaders: List<String>): String? {
        if (cookieHeaders.isEmpty()) return null
        // Try to extract next-auth.session-token from cookies
        // Each Set-Cookie header contains one cookie, format: "name=value; attributes..."
        for (cookie in cookieHeaders) {
            val cookiePart = cookie.split(";").firstOrNull()?.trim() ?: continue
            if (cookiePart.startsWith("next-auth.session-token=")) {
                return cookiePart.substringAfter("next-auth.session-token=")
            }
            // Also check for __Secure- prefix variant used in production
            if (cookiePart.startsWith("__Secure-next-auth.session-token=")) {
                return cookiePart.substringAfter("__Secure-next-auth.session-token=")
            }
        }
        return null
    }
}
