package com.personalcoacher.data.remote

import com.personalcoacher.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor that adds authentication headers to requests.
 *
 * Note: Cookie-based authentication for NextAuth is handled by SessionCookieJar.
 * This interceptor only adds the Authorization header for APIs that accept Bearer tokens.
 * The CookieJar automatically includes session cookies in requests.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Get token synchronously using the non-suspend method
        val token = tokenManager.getTokenSync()

        return if (token != null) {
            // Only add Authorization header - CookieJar handles cookies
            val authenticatedRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
