package com.personalcoacher.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.personalcoacher.BuildConfig
import com.personalcoacher.data.remote.AuthInterceptor
import com.personalcoacher.data.remote.ClaudeApiService
import com.personalcoacher.data.remote.PersonalCoachApi
import com.personalcoacher.data.remote.SessionCookieJar
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder().create()
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    fun provideSessionCookieJar(): SessionCookieJar {
        return SessionCookieJar()
    }

    @Provides
    @Singleton
    fun provideCookieJar(sessionCookieJar: SessionCookieJar): CookieJar {
        return sessionCookieJar
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
        cookieJar: CookieJar
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS) // Longer for AI responses
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun providePersonalCoachApi(retrofit: Retrofit): PersonalCoachApi {
        return retrofit.create(PersonalCoachApi::class.java)
    }

    // Claude API direct access (api.anthropic.com) - for regular chat/streaming
    // Uses HTTP/2 (default) for efficient multiplexing on short-lived requests
    @Provides
    @Singleton
    @Named("claudeOkHttp")
    fun provideClaudeOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // 2 min timeout for regular chat
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // Claude API for long-running tool generation (8192 tokens, 2-5 minutes)
    // Uses HTTP/1.1 to avoid "stream was reset: CANCEL" errors
    // HTTP/2 streams can be cancelled by proxies/networks on long-running requests
    @Provides
    @Singleton
    @Named("claudeToolGenerationOkHttp")
    fun provideClaudeToolGenerationOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            // Force HTTP/1.1 to avoid "stream was reset: CANCEL" errors
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(60, TimeUnit.SECONDS) // Longer connect timeout for slow networks
            .readTimeout(1200, TimeUnit.SECONDS) // 20 min timeout for large AI responses
            .writeTimeout(60, TimeUnit.SECONDS) // Longer write timeout
            .retryOnConnectionFailure(true)
            // Ping interval to keep connection alive (30 seconds)
            // Note: This only works for HTTP/2, but won't hurt HTTP/1.1
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("claudeRetrofit")
    fun provideClaudeRetrofit(
        @Named("claudeOkHttp") okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.anthropic.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("claudeToolGenerationRetrofit")
    fun provideClaudeToolGenerationRetrofit(
        @Named("claudeToolGenerationOkHttp") okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.anthropic.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideClaudeApiService(
        @Named("claudeRetrofit") retrofit: Retrofit
    ): ClaudeApiService {
        return retrofit.create(ClaudeApiService::class.java)
    }

    // Separate ClaudeApiService for tool generation with HTTP/1.1 and longer timeout
    @Provides
    @Singleton
    @Named("claudeToolGenerationApiService")
    fun provideClaudeToolGenerationApiService(
        @Named("claudeToolGenerationRetrofit") retrofit: Retrofit
    ): ClaudeApiService {
        return retrofit.create(ClaudeApiService::class.java)
    }

    // Voyage AI API for embeddings (api.voyageai.com)
    // User has opted out of data retention - zero-day retention policy
    @Provides
    @Singleton
    @Named("voyageOkHttp")
    fun provideVoyageOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS) // Embeddings are quick
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
