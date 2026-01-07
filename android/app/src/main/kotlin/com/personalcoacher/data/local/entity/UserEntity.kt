package com.personalcoacher.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.personalcoacher.domain.model.User
import java.time.Instant

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val email: String,
    val name: String?,
    val image: String?,
    val timezone: String?,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomainModel(): User {
        return User(
            id = id,
            email = email,
            name = name,
            image = image,
            timezone = timezone,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt)
        )
    }

    companion object {
        fun fromDomainModel(user: User): UserEntity {
            return UserEntity(
                id = user.id,
                email = user.email,
                name = user.name,
                image = user.image,
                timezone = user.timezone,
                createdAt = user.createdAt.toEpochMilli(),
                updatedAt = user.updatedAt.toEpochMilli()
            )
        }
    }
}
