import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

// GET /api/recorder/sessions - List all recording sessions for the user
export async function GET() {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  try {
    const sessions = await prisma.recordingSession.findMany({
      where: { userId: session.user.id },
      include: {
        transcriptions: {
          orderBy: { chunkIndex: 'asc' }
        }
      },
      orderBy: { createdAt: 'desc' }
    })

    return NextResponse.json(sessions)
  } catch (error) {
    console.error('Failed to fetch recording sessions:', error)
    return NextResponse.json({ error: 'Failed to fetch sessions' }, { status: 500 })
  }
}

// POST /api/recorder/sessions - Create a new recording session
export async function POST(request: NextRequest) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  let body
  try {
    body = await request.json()
  } catch {
    return NextResponse.json({ error: 'Invalid JSON' }, { status: 400 })
  }

  const { chunkDuration, title } = body

  if (!chunkDuration || typeof chunkDuration !== 'number') {
    return NextResponse.json({ error: 'chunkDuration is required' }, { status: 400 })
  }

  try {
    const recordingSession = await prisma.recordingSession.create({
      data: {
        userId: session.user.id,
        title: title || null,
        chunkDuration,
        status: 'recording'
      }
    })

    return NextResponse.json(recordingSession)
  } catch (error) {
    console.error('Failed to create recording session:', error)
    return NextResponse.json({ error: 'Failed to create session' }, { status: 500 })
  }
}
