package com.personalcoacher.domain.repository

import com.personalcoacher.notification.NotificationPromptBuilder.GeneratedNotification
import com.personalcoacher.util.Resource

interface DynamicNotificationRepository {
    suspend fun generateAndShowDynamicNotification(userId: String): Resource<GeneratedNotification>
}
