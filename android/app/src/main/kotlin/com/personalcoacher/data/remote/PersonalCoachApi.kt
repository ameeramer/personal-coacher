package com.personalcoacher.data.remote

import com.personalcoacher.data.remote.dto.ConversationDto
import com.personalcoacher.data.remote.dto.CreateConversationRequest
import com.personalcoacher.data.remote.dto.CreateJournalEntryRequest
import com.personalcoacher.data.remote.dto.CreateSummaryRequest
import com.personalcoacher.data.remote.dto.CsrfResponse
import com.personalcoacher.data.remote.dto.JournalEntryDto
import com.personalcoacher.data.remote.dto.LoginRequest
import com.personalcoacher.data.remote.dto.MessageStatusResponse
import com.personalcoacher.data.remote.dto.SendMessageRequest
import com.personalcoacher.data.remote.dto.SendMessageResponse
import com.personalcoacher.data.remote.dto.SessionResponse
import com.personalcoacher.data.remote.dto.SummaryDto
import com.personalcoacher.data.remote.dto.UpdateJournalEntryRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface PersonalCoachApi {

    // ==================== Auth ====================

    @GET("api/auth/csrf")
    suspend fun getCsrfToken(): Response<CsrfResponse>

    @FormUrlEncoded
    @POST("api/auth/callback/credentials")
    suspend fun login(
        @Field("email") email: String,
        @Field("password") password: String,
        @Field("csrfToken") csrfToken: String,
        @Field("json") json: Boolean = true
    ): Response<SessionResponse>

    @GET("api/auth/session")
    suspend fun getSession(): Response<SessionResponse>

    // ==================== Journal ====================

    @GET("api/journal")
    suspend fun getJournalEntries(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): Response<List<JournalEntryDto>>

    @POST("api/journal")
    suspend fun createJournalEntry(@Body request: CreateJournalEntryRequest): Response<JournalEntryDto>

    @GET("api/journal/{id}")
    suspend fun getJournalEntry(@Path("id") id: String): Response<JournalEntryDto>

    @PUT("api/journal/{id}")
    suspend fun updateJournalEntry(
        @Path("id") id: String,
        @Body request: UpdateJournalEntryRequest
    ): Response<JournalEntryDto>

    @DELETE("api/journal/{id}")
    suspend fun deleteJournalEntry(@Path("id") id: String): Response<Unit>

    // ==================== Conversations ====================

    @GET("api/conversations")
    suspend fun getConversations(): Response<List<ConversationDto>>

    @POST("api/conversations")
    suspend fun createConversation(@Body request: CreateConversationRequest): Response<ConversationDto>

    @GET("api/conversations/{id}")
    suspend fun getConversation(@Path("id") id: String): Response<ConversationDto>

    @DELETE("api/conversations/{id}")
    suspend fun deleteConversation(@Path("id") id: String): Response<Unit>

    // ==================== Chat ====================

    @POST("api/chat")
    suspend fun sendMessage(@Body request: SendMessageRequest): Response<SendMessageResponse>

    @GET("api/chat/status")
    suspend fun getMessageStatus(@Query("messageId") messageId: String): Response<MessageStatusResponse>

    @POST("api/chat/mark-seen")
    suspend fun markMessageSeen(@Body messageId: Map<String, String>): Response<Unit>

    // ==================== Summaries ====================

    @GET("api/summary")
    suspend fun getSummaries(@Query("type") type: String? = null): Response<List<SummaryDto>>

    @POST("api/summary")
    suspend fun createSummary(@Body request: CreateSummaryRequest): Response<SummaryDto>
}
