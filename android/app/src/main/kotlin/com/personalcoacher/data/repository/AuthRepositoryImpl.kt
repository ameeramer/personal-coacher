package com.personalcoacher.data.repository

import com.personalcoacher.BuildConfig
import com.personalcoacher.data.local.TokenManager
import com.personalcoacher.data.local.dao.UserDao
import com.personalcoacher.data.local.entity.UserEntity
import com.personalcoacher.data.remote.PersonalCoachApi
import com.personalcoacher.data.remote.SessionCookieJar
import com.personalcoacher.domain.model.User
import com.personalcoacher.domain.repository.AuthRepository
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: PersonalCoachApi,
    private val userDao: UserDao,
    private val tokenManager: TokenManager,
    private val sessionCookieJar: SessionCookieJar
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
            // Note: NextAuth returns a redirect (302) with cookies set on the redirect response.
            // OkHttp follows redirects automatically, so we can't get cookies from response.headers().
            // Instead, the SessionCookieJar captures all cookies including from redirects.
            val response = api.login(
                email = email,
                password = password,
                csrfToken = csrfToken
            )

            // Extract the API host from the base URL
            val apiHost = try {
                java.net.URL(BuildConfig.API_BASE_URL).host
            } catch (e: Exception) {
                return Resource.error("Login failed: Invalid API URL configuration")
            }

            // Get the session token from CookieJar (captured during the redirect chain)
            val token = sessionCookieJar.getSessionToken(apiHost)

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
                // Provide more diagnostic info
                Resource.error("Login failed: No session token in cookies. Response code: ${response.code()}")
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
}
