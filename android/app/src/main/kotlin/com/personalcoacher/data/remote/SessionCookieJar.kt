package com.personalcoacher.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A CookieJar implementation that stores cookies in memory and allows
 * querying for specific cookies like the NextAuth session token.
 */
@Singleton
class SessionCookieJar @Inject constructor() : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Merge cookies instead of replacing
        val existingCookies = cookieStore.getOrPut(url.host) { mutableListOf() }
        for (cookie in cookies) {
            // Remove any existing cookie with the same name
            existingCookies.removeAll { it.name == cookie.name }
            existingCookies.add(cookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore[url.host]?.filter { !it.expiresAt.let { exp -> exp < System.currentTimeMillis() } } ?: emptyList()
    }

    /**
     * Extracts the NextAuth session token from stored cookies.
     * Checks both development (next-auth.session-token) and
     * production (__Secure-next-auth.session-token) cookie names.
     */
    fun getSessionToken(host: String): String? {
        val cookies = cookieStore[host] ?: return null

        // Try production cookie name first
        cookies.find { it.name == "__Secure-next-auth.session-token" }?.let {
            return it.value
        }

        // Fall back to development cookie name
        cookies.find { it.name == "next-auth.session-token" }?.let {
            return it.value
        }

        return null
    }

    /**
     * Clears all stored cookies.
     */
    fun clear() {
        cookieStore.clear()
    }
}
