# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Personal Coacher is a native Android application combining journaling with AI-powered personal coaching. Users can log daily entries, receive smart reminders, interact with an AI coach (powered by Claude API), record voice memos with transcription, and manage their agenda with event suggestions.

### Core Features
- Daily journal entries with rich text editing, mood tracking, and tags
- AI-generated summaries (daily/weekly/monthly)
- Conversational AI coach for personal growth guidance
- Voice recording with AI transcription (Gemini)
- Smart agenda with AI-powered event suggestions
- Push notifications with AI-driven dynamic reminders
- Offline-first local storage

---

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Local Database**: Room (SQLite)
- **Networking**: Retrofit + OkHttp
- **AI Services**: Claude API (chat), Gemini API (transcription)
- **Dependency Injection**: Hilt
- **Architecture**: MVVM with Repository pattern
- **Background Work**: WorkManager
- **Async**: Kotlin Coroutines + Flow

---

## Build & Development

1. Open `/android` folder in Android Studio
2. Sync Gradle files
3. Configure `android/local.properties` with SDK path and API keys
4. Run on emulator or device

```bash
cd android
./gradlew assembleDebug    # Build debug APK
./gradlew assembleRelease  # Build release APK
./gradlew test             # Run unit tests
```

---

## Project Structure

```
android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/personalcoacher/
│   │   │   │   ├── MainActivity.kt           # Entry point
│   │   │   │   ├── PersonalCoachApp.kt       # Application class
│   │   │   │   │
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/
│   │   │   │   │   │   ├── PersonalCoachDatabase.kt  # Room database
│   │   │   │   │   │   ├── TokenManager.kt           # Auth token storage
│   │   │   │   │   │   ├── dao/                      # Data Access Objects
│   │   │   │   │   │   │   ├── JournalEntryDao.kt
│   │   │   │   │   │   │   ├── ConversationDao.kt
│   │   │   │   │   │   │   ├── MessageDao.kt
│   │   │   │   │   │   │   ├── SummaryDao.kt
│   │   │   │   │   │   │   ├── AgendaItemDao.kt
│   │   │   │   │   │   │   ├── ScheduleRuleDao.kt
│   │   │   │   │   │   │   ├── RecordingSessionDao.kt
│   │   │   │   │   │   │   ├── TranscriptionDao.kt
│   │   │   │   │   │   │   ├── EventNotificationDao.kt
│   │   │   │   │   │   │   ├── EventSuggestionDao.kt
│   │   │   │   │   │   │   ├── SentNotificationDao.kt
│   │   │   │   │   │   │   └── UserDao.kt
│   │   │   │   │   │   └── entity/                   # Room entities
│   │   │   │   │   │       ├── JournalEntryEntity.kt
│   │   │   │   │   │       ├── ConversationEntity.kt
│   │   │   │   │   │       ├── MessageEntity.kt
│   │   │   │   │   │       ├── SummaryEntity.kt
│   │   │   │   │   │       ├── AgendaItemEntity.kt
│   │   │   │   │   │       ├── ScheduleRuleEntity.kt
│   │   │   │   │   │       ├── RecordingSessionEntity.kt
│   │   │   │   │   │       ├── TranscriptionEntity.kt
│   │   │   │   │   │       ├── EventNotificationEntity.kt
│   │   │   │   │   │       ├── EventSuggestionEntity.kt
│   │   │   │   │   │       ├── SentNotificationEntity.kt
│   │   │   │   │   │       └── UserEntity.kt
│   │   │   │   │   │
│   │   │   │   │   ├── remote/
│   │   │   │   │   │   ├── PersonalCoachApi.kt       # Retrofit API interface
│   │   │   │   │   │   ├── AuthInterceptor.kt        # Auth token interceptor
│   │   │   │   │   │   ├── SessionCookieJar.kt       # Cookie handling
│   │   │   │   │   │   ├── ClaudeApiService.kt       # Claude API service
│   │   │   │   │   │   ├── ClaudeStreamingClient.kt  # Streaming chat client
│   │   │   │   │   │   ├── GeminiTranscriptionService.kt  # Voice transcription
│   │   │   │   │   │   ├── EventAnalysisService.kt   # AI event analysis
│   │   │   │   │   │   └── dto/                      # Data Transfer Objects
│   │   │   │   │   │       ├── AuthDto.kt
│   │   │   │   │   │       ├── JournalDto.kt
│   │   │   │   │   │       ├── ChatDto.kt
│   │   │   │   │   │       ├── SummaryDto.kt
│   │   │   │   │   │       └── AgendaDto.kt
│   │   │   │   │   │
│   │   │   │   │   └── repository/                   # Repository implementations
│   │   │   │   │       ├── AuthRepositoryImpl.kt
│   │   │   │   │       ├── JournalRepositoryImpl.kt
│   │   │   │   │       ├── ChatRepositoryImpl.kt
│   │   │   │   │       ├── SummaryRepositoryImpl.kt
│   │   │   │   │       ├── AgendaRepositoryImpl.kt
│   │   │   │   │       ├── RecorderRepositoryImpl.kt
│   │   │   │   │       ├── ScheduleRuleRepositoryImpl.kt
│   │   │   │   │       ├── EventNotificationRepositoryImpl.kt
│   │   │   │   │       └── DynamicNotificationRepositoryImpl.kt
│   │   │   │   │
│   │   │   │   ├── di/                               # Hilt modules
│   │   │   │   │   ├── AppModule.kt
│   │   │   │   │   ├── DatabaseModule.kt
│   │   │   │   │   ├── NetworkModule.kt
│   │   │   │   │   └── RepositoryModule.kt
│   │   │   │   │
│   │   │   │   ├── domain/
│   │   │   │   │   ├── model/                        # Domain models
│   │   │   │   │   │   ├── JournalEntry.kt
│   │   │   │   │   │   ├── Conversation.kt
│   │   │   │   │   │   ├── Message.kt
│   │   │   │   │   │   ├── Summary.kt
│   │   │   │   │   │   ├── User.kt
│   │   │   │   │   │   ├── AgendaItem.kt
│   │   │   │   │   │   ├── ScheduleRule.kt
│   │   │   │   │   │   ├── RecordingSession.kt
│   │   │   │   │   │   └── Transcription.kt
│   │   │   │   │   └── repository/                   # Repository interfaces
│   │   │   │   │       ├── AuthRepository.kt
│   │   │   │   │       ├── JournalRepository.kt
│   │   │   │   │       ├── ChatRepository.kt
│   │   │   │   │       ├── SummaryRepository.kt
│   │   │   │   │       ├── AgendaRepository.kt
│   │   │   │   │       ├── RecorderRepository.kt
│   │   │   │   │       ├── ScheduleRuleRepository.kt
│   │   │   │   │       ├── EventNotificationRepository.kt
│   │   │   │   │       └── DynamicNotificationRepository.kt
│   │   │   │   │
│   │   │   │   ├── notification/                     # Background workers
│   │   │   │   │   ├── NotificationHelper.kt         # Notification creation
│   │   │   │   │   ├── NotificationScheduler.kt      # Scheduling logic
│   │   │   │   │   ├── NotificationPromptBuilder.kt  # AI prompt building
│   │   │   │   │   ├── NotificationAlarmReceiver.kt  # Alarm broadcast receiver
│   │   │   │   │   ├── JournalReminderWorker.kt      # Daily reminder worker
│   │   │   │   │   ├── DynamicNotificationWorker.kt  # AI-driven notifications
│   │   │   │   │   ├── EventNotificationWorker.kt    # Event reminders
│   │   │   │   │   ├── EventAnalysisWorker.kt        # AI event analysis
│   │   │   │   │   └── BackgroundChatWorker.kt       # Background AI chat
│   │   │   │   │
│   │   │   │   ├── recorder/
│   │   │   │   │   └── AudioRecorderService.kt       # Foreground recording service
│   │   │   │   │
│   │   │   │   ├── ui/
│   │   │   │   │   ├── PersonalCoachApp.kt           # Main app composable
│   │   │   │   │   ├── theme/                        # Material theme
│   │   │   │   │   │   ├── Theme.kt
│   │   │   │   │   │   ├── Color.kt
│   │   │   │   │   │   └── Type.kt
│   │   │   │   │   ├── navigation/
│   │   │   │   │   │   └── Screen.kt                 # Navigation routes
│   │   │   │   │   ├── components/                   # Reusable components
│   │   │   │   │   │   ├── IOSComponents.kt          # iOS-style UI elements
│   │   │   │   │   │   ├── AddScheduleRuleDialog.kt  # Schedule rule dialog
│   │   │   │   │   │   └── journal/
│   │   │   │   │   │       ├── WysiwygEditor.kt      # Rich text editor
│   │   │   │   │   │       ├── RichTextToolbar.kt    # Formatting toolbar
│   │   │   │   │   │       └── LinedPaperBackground.kt
│   │   │   │   │   └── screens/
│   │   │   │   │       ├── home/
│   │   │   │   │       │   ├── HomeScreen.kt
│   │   │   │   │       │   └── HomeViewModel.kt
│   │   │   │   │       ├── login/
│   │   │   │   │       │   ├── LoginScreen.kt
│   │   │   │   │       │   └── LoginViewModel.kt
│   │   │   │   │       ├── journal/
│   │   │   │   │       │   ├── JournalScreen.kt
│   │   │   │   │       │   ├── JournalViewModel.kt
│   │   │   │   │       │   ├── JournalEditorScreen.kt
│   │   │   │   │       │   └── JournalEditorViewModel.kt
│   │   │   │   │       ├── coach/
│   │   │   │   │       │   ├── CoachScreen.kt
│   │   │   │   │       │   └── CoachViewModel.kt
│   │   │   │   │       ├── summaries/
│   │   │   │   │       │   ├── SummariesScreen.kt
│   │   │   │   │       │   └── SummariesViewModel.kt
│   │   │   │   │       ├── agenda/
│   │   │   │   │       │   ├── AgendaScreen.kt
│   │   │   │   │       │   └── AgendaViewModel.kt
│   │   │   │   │       ├── recorder/
│   │   │   │   │       │   ├── RecorderScreen.kt
│   │   │   │   │       │   ├── RecorderViewModel.kt
│   │   │   │   │       │   ├── RecorderSettingsSheet.kt
│   │   │   │   │       │   ├── RecorderControlsSection.kt
│   │   │   │   │       │   ├── SessionsComponents.kt
│   │   │   │   │       │   ├── TranscriptionsComponents.kt
│   │   │   │   │       │   └── RecorderUtils.kt
│   │   │   │   │       └── settings/
│   │   │   │   │           ├── SettingsScreen.kt
│   │   │   │   │           └── SettingsViewModel.kt
│   │   │   │   │
│   │   │   │   └── util/
│   │   │   │       ├── CoachPrompts.kt               # AI system prompts
│   │   │   │       ├── DateUtils.kt                  # Date formatting
│   │   │   │       ├── DebugLogHelper.kt             # Debug logging
│   │   │   │       ├── Resource.kt                   # Resource wrapper
│   │   │   │       └── Result.kt                     # Result wrapper
│   │   │   │
│   │   │   └── res/                                  # Android resources
│   │   │
│   │   └── androidTest/                              # Instrumented tests
│   │       └── kotlin/com/personalcoacher/
│   │           └── data/local/MigrationTest.kt
│   │
│   └── build.gradle.kts
│
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## Key Patterns

### Offline-First Architecture
- All data is stored locally in Room SQLite database
- Network sync happens in background when connectivity available
- Conflict resolution: Server wins for same-timestamp conflicts

### Data Flow
```
UI (Compose) → ViewModel → Repository → Room (local) + Retrofit (remote)
                                              ↓
                                      Flow<List<Entity>>
```

### Authentication
- Credentials stored in TokenManager (EncryptedSharedPreferences)
- AuthInterceptor adds token to requests
- Auto-refresh on 401 responses

### AI Integration
- **Claude API**: Used for coaching conversations and dynamic notifications
- **Gemini API**: Used for voice transcription
- Both support streaming responses for real-time UI updates

### Background Work
- WorkManager handles scheduled tasks (notifications, sync)
- Foreground service for audio recording
- Alarm receivers for precise notification timing

---

## Database Models

| Model | Description |
|-------|-------------|
| **User** | Account with email auth |
| **JournalEntry** | Daily entries with content, mood, tags |
| **Conversation** | Chat sessions with AI coach |
| **Message** | Individual messages in conversations |
| **Summary** | AI-generated summaries (daily/weekly/monthly) |
| **AgendaItem** | Calendar events and tasks |
| **ScheduleRule** | Recurring schedule definitions |
| **RecordingSession** | Voice recording metadata |
| **Transcription** | AI-transcribed text from recordings |
| **EventNotification** | Scheduled event reminders |
| **EventSuggestion** | AI-suggested events |

---

## Environment Configuration

### `android/local.properties`
```
sdk.dir=/path/to/android/sdk
ANTHROPIC_API_KEY=your-claude-api-key
GEMINI_API_KEY=your-gemini-api-key
```

---

## Automation Rules

- Always open a Pull Request immediately after pushing changes to a new branch.
- Use the GitHub CLI (`gh pr create`) to do this.
