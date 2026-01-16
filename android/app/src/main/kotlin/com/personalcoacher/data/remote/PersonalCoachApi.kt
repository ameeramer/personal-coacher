package com.personalcoacher.data.remote

import com.personalcoacher.data.remote.dto.AgendaItemDto
import com.personalcoacher.data.remote.dto.AnalyzeJournalRequest
import com.personalcoacher.data.remote.dto.AnalyzeJournalResponse
import com.personalcoacher.data.remote.dto.ConversationDto
import com.personalcoacher.data.remote.dto.CreateAgendaItemRequest
import com.personalcoacher.data.remote.dto.CreateConversationRequest
import com.personalcoacher.data.remote.dto.CreateJournalEntryRequest
import com.personalcoacher.data.remote.dto.CreateSummaryRequest
import com.personalcoacher.data.remote.dto.CsrfResponse
import com.personalcoacher.data.remote.dto.JournalEntryDto
import com.personalcoacher.data.remote.dto.LocalChatRequest
import com.personalcoacher.data.remote.dto.LocalChatResponse
import com.personalcoacher.data.remote.dto.LocalSummaryResponse
import com.personalcoacher.data.remote.dto.MessageStatusResponse
import com.personalcoacher.data.remote.dto.SendMessageRequest
import com.personalcoacher.data.remote.dto.SendMessageResponse
import com.personalcoacher.data.remote.dto.SessionResponse
import com.personalcoacher.data.remote.dto.SummaryDto
import com.personalcoacher.data.remote.dto.UpdateAgendaItemRequest
import com.personalcoacher.data.remote.dto.UpdateJournalEntryRequest
import okhttp3.ResponseBody
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
        @Field("csrfToken") csrfToken: String
    ): Response<ResponseBody>

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

    // Local-only chat (AI processed, no DB persistence on server)
    @POST("api/chat/local")
    suspend fun sendMessageLocal(@Body request: LocalChatRequest): Response<LocalChatResponse>

    // ==================== Summaries ====================

    @GET("api/summary")
    suspend fun getSummaries(@Query("type") type: String? = null): Response<List<SummaryDto>>

    @POST("api/summary")
    suspend fun createSummary(@Body request: CreateSummaryRequest): Response<SummaryDto>

    // Local-only summary (AI generated, no DB persistence on server)
    @POST("api/summary/local")
    suspend fun createSummaryLocal(@Body request: CreateSummaryRequest): Response<LocalSummaryResponse>

    // ==================== Agenda ====================

    @GET("api/agenda")
    suspend fun getAgendaItems(
        @Query("startTime") startTime: String? = null,
        @Query("endTime") endTime: String? = null
    ): Response<List<AgendaItemDto>>

    @POST("api/agenda")
    suspend fun createAgendaItem(@Body request: CreateAgendaItemRequest): Response<AgendaItemDto>

    @GET("api/agenda/{id}")
    suspend fun getAgendaItem(@Path("id") id: String): Response<AgendaItemDto>

    @PUT("api/agenda/{id}")
    suspend fun updateAgendaItem(
        @Path("id") id: String,
        @Body request: UpdateAgendaItemRequest
    ): Response<AgendaItemDto>

    @DELETE("api/agenda/{id}")
    suspend fun deleteAgendaItem(@Path("id") id: String): Response<Unit>

    // Analyze journal entry for events (AI-powered)
    @POST("api/agenda/analyze")
    suspend fun analyzeJournalForEvents(@Body request: AnalyzeJournalRequest): Response<AnalyzeJournalResponse>
}
