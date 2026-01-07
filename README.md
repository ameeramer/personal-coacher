# Personal Coacher

A personal journaling and AI coaching application combining daily journaling with Claude-powered personal growth guidance.

## Project Structure

This is a monorepo containing both web and native Android applications.

### `/web` (Legacy - Maintenance Only)
The original Next.js web application.
- **Status**: Maintenance mode - no new features planned
- **Stack**: Next.js 16, TypeScript, Tailwind CSS, PostgreSQL/Prisma, NextAuth.js
- **Deployment**: Vercel

### `/android` (Active Development)
The native Android app built with Kotlin and Jetpack Compose.
- **Status**: Active development
- **Stack**: Kotlin, Jetpack Compose, Room, Retrofit, Hilt
- **Features**: Offline-first architecture, local SQLite storage, sync with web API

## Features

- Daily journal entries with mood tracking and tags
- AI-powered personal coaching conversations (Claude API)
- AI-generated summaries (daily/weekly/monthly)
- Push notifications for daily reminders
- Offline-first data storage (Android)
- Cross-device sync

## Getting Started

### Web Application
```bash
cd web
npm install
npm run dev
```

### Android Application
1. Open the `android/` folder in Android Studio
2. Sync Gradle files
3. Configure `local.properties` with your SDK path
4. Run on emulator or device

## Environment Variables

See `CLAUDE.md` for detailed environment variable documentation.

## License

MIT
