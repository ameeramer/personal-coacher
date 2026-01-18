import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

// GET - Retrieve all daily tools for the current user (for sync/download)
export async function GET(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions)

    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
    }

    const searchParams = request.nextUrl.searchParams
    const status = searchParams.get('status')

    const dailyTools = await prisma.dailyTool.findMany({
      where: {
        userId: session.user.id,
        ...(status ? { status } : {})
      },
      orderBy: { date: 'desc' },
      take: 100
    })

    return NextResponse.json(dailyTools)
  } catch (error) {
    console.error('Error fetching daily tools:', error)
    return NextResponse.json({
      error: 'Failed to fetch daily tools',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}

// POST - Create/sync a daily tool from the mobile app (for upload/backup)
export async function POST(request: NextRequest) {
  try {
    const session = await getServerSession(authOptions)

    if (!session?.user?.id) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
    }

    const body = await request.json()
    const {
      id,
      date,
      title,
      description,
      htmlCode,
      journalContext,
      status,
      usedAt,
      createdAt,
      updatedAt
    } = body

    // Validate required fields
    if (!date || !title || !description || !htmlCode) {
      return NextResponse.json(
        { error: 'date, title, description, and htmlCode are required' },
        { status: 400 }
      )
    }

    // Validate status if provided
    const validStatuses = ['PENDING', 'LIKED', 'DISLIKED']
    if (status && !validStatuses.includes(status)) {
      return NextResponse.json(
        { error: `status must be one of: ${validStatuses.join(', ')}` },
        { status: 400 }
      )
    }

    // Upsert - create if doesn't exist, update if it does (for sync)
    const dailyTool = await prisma.dailyTool.upsert({
      where: { id: id || '' },
      update: {
        date: new Date(date),
        title,
        description,
        htmlCode,
        journalContext,
        status: status || 'PENDING',
        usedAt: usedAt ? new Date(usedAt) : null,
        updatedAt: updatedAt ? new Date(updatedAt) : new Date()
      },
      create: {
        id,
        userId: session.user.id,
        date: new Date(date),
        title,
        description,
        htmlCode,
        journalContext,
        status: status || 'PENDING',
        usedAt: usedAt ? new Date(usedAt) : null,
        createdAt: createdAt ? new Date(createdAt) : new Date(),
        updatedAt: updatedAt ? new Date(updatedAt) : new Date()
      }
    })

    return NextResponse.json(dailyTool, { status: 201 })
  } catch (error) {
    console.error('Error creating daily tool:', error)
    return NextResponse.json({
      error: 'Failed to create daily tool',
      details: error instanceof Error ? error.message : 'Unknown error'
    }, { status: 500 })
  }
}
