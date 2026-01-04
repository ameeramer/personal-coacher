# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Personal Coacher is a web application combining journaling with AI-powered personal coaching. Users can log daily entries, receive reminders, and interact with an AI coach (powered by Claude API) that provides personalized growth suggestions and conversation.

### Core Features
- Daily journal entries with mood tracking and tags
- AI-generated summaries (daily/weekly/monthly)
- Conversational AI coach for personal growth guidance
- Chat interface with conversation history

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
│   │   └── summary/        # AI summary generation
│   ├── coach/              # Chat interface page
│   ├── journal/            # Journal page
│   ├── login/              # Login page
│   └── summaries/          # Summaries page
├── components/
│   ├── coach/              # ChatInterface, ConversationList
│   ├── journal/            # JournalEditor, JournalEntryCard
│   ├── providers/          # SessionProvider
│   └── ui/                 # Navigation
├── generated/prisma/       # Generated Prisma client (gitignored)
├── lib/
│   ├── anthropic.ts        # Claude API client
│   ├── auth.ts             # NextAuth configuration
│   ├── prisma.ts           # Prisma client with pg adapter
│   └── prompts/coach.ts    # AI system prompts
└── types/                  # TypeScript declarations

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
- Claude API calls made server-side only in `/api/chat` and `/api/summary`
- Coach system prompt in `src/lib/prompts/coach.ts` defines persona
- Recent journal entries are included as context for coaching conversations

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

## Environment Variables

Required in `.env`:
```
DATABASE_URL=postgresql://user:password@localhost:5432/personal_coacher
ANTHROPIC_API_KEY=your-api-key
NEXTAUTH_SECRET=generate-with-openssl-rand-base64-32
NEXTAUTH_URL=http://localhost:3000
```

# Automation Rules

- Always open a Pull Request immediately after pushing changes to a new branch.
- Use the GitHub CLI (`gh pr create`) to do this.