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

    // Claude API direct access (api.anthropic.com)
    @Provides
    @Singleton
    @Named("claudeOkHttp")
    fun provideClaudeOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // Longer timeout for AI responses
            .writeTimeout(30, TimeUnit.SECONDS)
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
    fun provideClaudeApiService(
        @Named("claudeRetrofit") retrofit: Retrofit
    ): ClaudeApiService {
        return retrofit.create(ClaudeApiService::class.java)
    }
}
