import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const { id } = await params

  const entry = await prisma.journalEntry.findFirst({
    where: {
      id,
      userId: session.user.id
    }
  })

  if (!entry) {
    return NextResponse.json({ error: 'Entry not found' }, { status: 404 })
  }

  return NextResponse.json(entry)
}

export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const { id } = await params
  const body = await request.json()
  const { content, mood, tags } = body

  const entry = await prisma.journalEntry.updateMany({
    where: {
      id,
      userId: session.user.id
    },
    data: {
      ...(content && { content }),
      ...(mood !== undefined && { mood }),
      ...(tags && { tags })
    }
  })

  if (entry.count === 0) {
    return NextResponse.json({ error: 'Entry not found' }, { status: 404 })
  }

  const updated = await prisma.journalEntry.findUnique({ where: { id } })
  return NextResponse.json(updated)
}

export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const { id } = await params

  const entry = await prisma.journalEntry.deleteMany({
    where: {
      id,
      userId: session.user.id
    }
  })

  if (entry.count === 0) {
    return NextResponse.json({ error: 'Entry not found' }, { status: 404 })
  }

  return NextResponse.json({ success: true })
}
