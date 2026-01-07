package com.personalcoacher.domain.repository

import com.personalcoacher.domain.model.User
import com.personalcoacher.util.Resource
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val isLoggedIn: Flow<Boolean>
    val currentUserId: Flow<String?>

    suspend fun login(email: String, password: String): Resource<User>
    suspend fun logout()
    suspend fun getCurrentUser(): User?
}
