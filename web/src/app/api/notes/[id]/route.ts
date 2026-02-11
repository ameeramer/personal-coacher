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

  const note = await prisma.note.findFirst({
    where: {
      id,
      userId: session.user.id
    }
  })

  if (!note) {
    return NextResponse.json({ error: 'Note not found' }, { status: 404 })
  }

  return NextResponse.json(note)
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
  const { title, content } = body

  const note = await prisma.note.updateMany({
    where: {
      id,
      userId: session.user.id
    },
    data: {
      ...(title && { title }),
      ...(content && { content })
    }
  })

  if (note.count === 0) {
    return NextResponse.json({ error: 'Note not found' }, { status: 404 })
  }

  const updated = await prisma.note.findUnique({ where: { id } })
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

  const note = await prisma.note.deleteMany({
    where: {
      id,
      userId: session.user.id
    }
  })

  if (note.count === 0) {
    return NextResponse.json({ error: 'Note not found' }, { status: 404 })
  }

  return NextResponse.json({ success: true })
}
