# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Personal Coacher is a web application combining journaling with AI-powered personal coaching. Users can log daily entries, receive reminders, and interact with an AI coach (powered by Claude API) that provides personalized growth suggestions and conversation.

### Core Features
- Daily journal entries with mood tracking and tags
- AI-generated summaries (daily/weekly/monthly)
- Conversational AI coach for personal growth guidance
- Chat interface with conversation history
- Push notifications for daily journal reminders (PWA)

## Tech Stack

- **Frontend**: Next.js 16 with TypeScript (App Router)
- **Backend**: Next.js API routes
- **Database**: PostgreSQL with Prisma 7
- **AI**: Anthropic Claude API (claude-sonnet-4-20250514)
- **Authentication**: NextAuth.js (credentials provider, demo mode)
- **Styling**: Tailwind CSS

## Build & Development Commands

```bash
npm install          # Install dependencies
npm run dev          # Run development server (http://localhost:3000)
npm run build        # Build for production
npm run lint         # Run ESLint
npx prisma generate  # Generate Prisma client (required after schema changes)
npx prisma migrate dev  # Run database migrations
npx prisma studio    # Open database GUI
```

## Architecture

```
src/
├── app/                    # Next.js App Router
│   ├── api/
│   │   ├── auth/[...nextauth]/  # NextAuth handler
│   │   ├── chat/           # AI chat endpoint
│   │   ├── conversations/  # Conversation CRUD
│   │   ├── journal/        # Journal CRUD
│   │   ├── notifications/  # Push notification endpoints
│   │   └── summary/        # AI summary generation
│   ├── coach/              # Chat interface page
│   ├── journal/            # Journal page
│   ├── login/              # Login page
│   └── summaries/          # Summaries page
├── components/
│   ├── coach/              # ChatInterface, ConversationList
│   ├── journal/            # JournalEditor, JournalEntryCard
│   ├── notifications/      # NotificationSettings, ServiceWorkerRegistration
│   ├── providers/          # SessionProvider
│   └── ui/                 # Navigation
├── generated/prisma/       # Generated Prisma client (gitignored)
├── lib/
│   ├── anthropic.ts        # Claude API client
│   ├── auth.ts             # NextAuth configuration
│   ├── prisma.ts           # Prisma client with pg adapter
│   └── prompts/coach.ts    # AI system prompts
└── types/                  # TypeScript declarations

public/
├── manifest.json           # PWA manifest
├── sw.js                   # Service Worker for push notifications
└── icons/                  # PWA icons

scripts/
└── generate-vapid-keys.mjs # VAPID key generation script

prisma/
├── schema.prisma           # Database models
└── prisma.config.ts        # Prisma 7 configuration
```

## Key Patterns

### Prisma 7 Configuration
- Uses `prisma.config.ts` for datasource URL (not in schema.prisma)
- Requires `@prisma/adapter-pg` for PostgreSQL connections
- Generated client imported from `@/generated/prisma/client`

### AI Integration
- Claude API calls made server-side only in `/api/chat`, `/api/summary`, and `/api/notifications/send-dynamic`
- Coach system prompt in `src/lib/prompts/coach.ts` defines persona
- Notification prompts in `src/lib/prompts/coach.ts` for dynamic AI notifications
- Recent journal entries are included as context for coaching conversations and notifications

### Authentication
- Demo mode: any email/password creates or logs into an account
- Session stored in JWT, user ID available via `session.user.id`
- Protected routes redirect to `/login` when unauthenticated

## Database Models

- **User**: Account holder with email auth
- **JournalEntry**: Daily entries with content, mood, tags
- **Conversation**: Chat sessions with the AI coach
- **Message**: Individual messages in conversations
- **Summary**: AI-generated summaries (daily/weekly/monthly)
- **PushSubscription**: Web push notification subscriptions
- **SentNotification**: Tracks sent AI-generated notifications (for history/deduplication)

## Environment Variables

Required in `.env`:
```
DATABASE_URL=postgresql://user:password@localhost:5432/personal_coacher
ANTHROPIC_API_KEY=your-api-key
NEXTAUTH_SECRET=generate-with-openssl-rand-base64-32
NEXTAUTH_URL=http://localhost:3000

# Push Notifications (optional - for daily reminders)
NEXT_PUBLIC_VAPID_PUBLIC_KEY=your-vapid-public-key
VAPID_PRIVATE_KEY=your-vapid-private-key
VAPID_SUBJECT=mailto:your-email@example.com
CRON_SECRET=generate-a-random-secret-for-cron-jobs
```

### Generating VAPID Keys
Run this script to generate VAPID keys for push notifications:
```bash
node scripts/generate-vapid-keys.mjs
```

### Setting Up Notifications

#### Static Journal Reminders
To trigger simple journal reminders, set up a cron service to call:
```
POST https://your-domain.com/api/notifications/send
Authorization: Bearer YOUR_CRON_SECRET
```

#### Dynamic AI-Generated Notifications
For personalized, context-aware notifications that reference user journal entries:
```
POST https://your-domain.com/api/notifications/send-dynamic
Authorization: Bearer YOUR_CRON_SECRET
```

Features of dynamic notifications:
- **Time-aware**: Adjusts message tone based on time of day (morning/afternoon/evening/night)
- **Personalized**: References specific topics from user's recent journal entries
- **No repetition**: Tracks sent notifications to avoid repeating the same topics
- **AI-powered**: Uses Claude to generate caring, contextual check-in messages

Recommended cron schedule for dynamic notifications:
- 9:00 AM - Morning check-in
- 2:00 PM - Afternoon encouragement
- 7:00 PM - Evening reflection

Recommended services:
- **cron-job.org** (free): Set up multiple jobs to hit your API at different times
- **GitHub Actions**: Create a scheduled workflow (note: may have timing variance)

# Automation Rules

- Always open a Pull Request immediately after pushing changes to a new branch.
- Use the GitHub CLI (`gh pr create`) to do this.