package com.personalcoacher.domain.model

import java.time.Instant

data class User(
    val id: String,
    val email: String,
    val name: String?,
    val image: String?,
    val timezone: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
