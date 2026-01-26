import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'

export async function GET() {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const notes = await prisma.note.findMany({
    where: {
      userId: session.user.id
    },
    orderBy: { createdAt: 'desc' },
    take: 100
  })

  return NextResponse.json(notes)
}

export async function POST(request: NextRequest) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const body = await request.json()
  const { title, content } = body

  if (!title || !content) {
    return NextResponse.json({ error: 'Title and content are required' }, { status: 400 })
  }

  const note = await prisma.note.create({
    data: {
      userId: session.user.id,
      title,
      content
    }
  })

  return NextResponse.json(note, { status: 201 })
}
