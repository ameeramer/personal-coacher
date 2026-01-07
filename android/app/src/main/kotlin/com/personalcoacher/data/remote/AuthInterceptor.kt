package com.personalcoacher.data.remote

import com.personalcoacher.data.local.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Get token synchronously (this runs on OkHttp's thread)
        val token = runBlocking { tokenManager.getToken() }

        return if (token != null) {
            val authenticatedRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Cookie", "next-auth.session-token=$token")
                .build()
            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
