# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Personal Coacher is an application combining journaling with AI-powered personal coaching. Users can log daily entries, receive reminders, and interact with an AI coach (powered by Claude API) that provides personalized growth suggestions and conversation.

This is a **monorepo** containing two applications:
- **`/web`** - Legacy Next.js web application (maintenance mode)
- **`/android`** - Native Android app with Kotlin/Jetpack Compose (active development)

### Core Features
- Daily journal entries with mood tracking and tags
- AI-generated summaries (daily/weekly/monthly)
- Conversational AI coach for personal growth guidance
- Chat interface with conversation history
- Push notifications for daily journal reminders
- Offline-first local storage (Android)

---

## Web Application (`/web`)

**Status**: Legacy - Maintenance Only

### Tech Stack
- **Frontend**: Next.js 16 with TypeScript (App Router)
- **Backend**: Next.js API routes
- **Database**: PostgreSQL with Prisma 7
- **AI**: Anthropic Claude API (claude-sonnet-4-20250514)
- **Authentication**: NextAuth.js (credentials provider, demo mode)
- **Styling**: Tailwind CSS

### Build & Development Commands

```bash
cd web
npm install          # Install dependencies
npm run dev          # Run development server (http://localhost:3000)
npm run build        # Build for production
npm run lint         # Run ESLint
npx prisma generate  # Generate Prisma client (required after schema changes)
npx prisma migrate dev  # Run database migrations
npx prisma studio    # Open database GUI
```

### Web Architecture

```
web/
├── src/
│   ├── app/                    # Next.js App Router
│   │   ├── api/
│   │   │   ├── auth/[...nextauth]/  # NextAuth handler
│   │   │   ├── chat/           # AI chat endpoint
│   │   │   ├── conversations/  # Conversation CRUD
│   │   │   ├── journal/        # Journal CRUD
│   │   │   ├── notifications/  # Push notification endpoints
│   │   │   └── summary/        # AI summary generation
│   │   ├── coach/              # Chat interface page
│   │   ├── journal/            # Journal page
│   │   ├── login/              # Login page
│   │   └── summaries/          # Summaries page
│   ├── components/
│   │   ├── coach/              # ChatInterface, ConversationList
│   │   ├── journal/            # JournalEditor, JournalEntryCard
│   │   ├── notifications/      # NotificationSettings, ServiceWorkerRegistration
│   │   ├── providers/          # SessionProvider
│   │   └── ui/                 # Navigation
│   ├── generated/prisma/       # Generated Prisma client (gitignored)
│   ├── lib/
│   │   ├── anthropic.ts        # Claude API client
│   │   ├── auth.ts             # NextAuth configuration
│   │   ├── prisma.ts           # Prisma client with pg adapter
│   │   └── prompts/coach.ts    # AI system prompts
│   └── types/                  # TypeScript declarations
├── public/
│   ├── manifest.json           # PWA manifest
│   ├── sw.js                   # Service Worker for push notifications
│   └── icons/                  # PWA icons
├── scripts/
│   └── generate-vapid-keys.mjs # VAPID key generation script
└── prisma/
    └── schema.prisma           # Database models
```

---

## Android Application (`/android`)

**Status**: Active Development

### Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Local Database**: Room (SQLite)
- **Networking**: Retrofit + OkHttp
- **Dependency Injection**: Hilt
- **Architecture**: MVVM with Repository pattern
- **Async**: Kotlin Coroutines + Flow

### Build & Development

1. Open `/android` folder in Android Studio
2. Sync Gradle files
3. Configure `android/local.properties` with SDK path
4. Run on emulator or device

```bash
cd android
./gradlew assembleDebug    # Build debug APK
./gradlew assembleRelease  # Build release APK
./gradlew test             # Run unit tests
```

### Android Architecture

```
android/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/personalcoacher/
│   │   │   ├── data/
│   │   │   │   ├── local/          # Room database, DAOs, entities
│   │   │   │   ├── remote/         # Retrofit API service
│   │   │   │   └── repository/     # Repository implementations
│   │   │   ├── di/                 # Hilt dependency injection modules
│   │   │   ├── domain/
│   │   │   │   ├── model/          # Domain models
│   │   │   │   └── repository/     # Repository interfaces
│   │   │   ├── ui/
│   │   │   │   ├── components/     # Reusable Compose components
│   │   │   │   ├── navigation/     # Navigation graph
│   │   │   │   ├── screens/        # Screen composables
│   │   │   │   │   ├── journal/
│   │   │   │   │   ├── coach/
│   │   │   │   │   ├── summaries/
│   │   │   │   │   └── login/
│   │   │   │   └── theme/          # Material theme
│   │   │   └── util/               # Utilities
│   │   └── res/                    # Resources
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

### Key Android Patterns

#### Offline-First Architecture
- All data is stored locally in Room SQLite database
- Network sync happens in background when connectivity available
- `SyncStatus` enum tracks: LOCAL_ONLY, SYNCING, SYNCED
- Conflict resolution: Server wins for same-timestamp conflicts

#### Data Flow
```
UI (Compose) → ViewModel → Repository → Room (local) + Retrofit (remote)
                                              ↓
                                      Flow<List<Entity>>
```

#### Authentication
- JWT token stored in EncryptedSharedPreferences
- Auto-refresh on 401 responses
- Login creates account if doesn't exist (same as web)

---

## Database Models

Shared between web (Prisma) and Android (Room):

| Model | Description |
|-------|-------------|
| **User** | Account with email auth |
| **JournalEntry** | Daily entries with content, mood, tags |
| **Conversation** | Chat sessions with AI coach |
| **Message** | Individual messages in conversations |
| **Summary** | AI-generated summaries (daily/weekly/monthly) |

---

## API Endpoints

The Android app communicates with the web API:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/auth/signin` | POST | Login/create account |
| `/api/journal` | GET | List journal entries |
| `/api/journal` | POST | Create journal entry |
| `/api/journal/[id]` | PUT | Update journal entry |
| `/api/journal/[id]` | DELETE | Delete journal entry |
| `/api/conversations` | GET | List conversations |
| `/api/conversations` | POST | Create conversation |
| `/api/conversations/[id]` | GET | Get conversation with messages |
| `/api/chat` | POST | Send message to AI coach |
| `/api/summary` | GET | List summaries |
| `/api/summary` | POST | Generate new summary |

---

## Environment Variables

### Web (`/web/.env`)
```
DATABASE_URL=postgresql://user:password@localhost:5432/personal_coacher
ANTHROPIC_API_KEY=your-api-key
NEXTAUTH_SECRET=generate-with-openssl-rand-base64-32
NEXTAUTH_URL=http://localhost:3000

# Push Notifications (optional)
NEXT_PUBLIC_VAPID_PUBLIC_KEY=your-vapid-public-key
VAPID_PRIVATE_KEY=your-vapid-private-key
VAPID_SUBJECT=mailto:your-email@example.com
CRON_SECRET=generate-a-random-secret-for-cron-jobs
```

### Android (`/android/local.properties`)
```
sdk.dir=/path/to/android/sdk
API_BASE_URL=https://your-deployed-app.vercel.app
```

---

## Automation Rules

- Always open a Pull Request immediately after pushing changes to a new branch.
- Use the GitHub CLI (`gh pr create`) to do this.
