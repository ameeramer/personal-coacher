import { NextRequest, NextResponse } from 'next/server'
import { getServerSession } from 'next-auth'
import { authOptions } from '@/lib/auth'
import { prisma } from '@/lib/prisma'
import { getGeminiModel, DEFAULT_GEMINI_MODEL } from '@/lib/gemini'

// POST /api/recorder/transcribe - Upload audio chunk and transcribe
export async function POST(request: NextRequest) {
  const session = await getServerSession(authOptions)

  if (!session?.user?.id) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  try {
    const formData = await request.formData()
    const audioFile = formData.get('audio') as File | null
    const sessionId = formData.get('sessionId') as string | null
    const chunkIndex = formData.get('chunkIndex') as string | null
    const startTime = formData.get('startTime') as string | null
    const endTime = formData.get('endTime') as string | null
    const duration = formData.get('duration') as string | null
    const modelId = (formData.get('model') as string | null) || DEFAULT_GEMINI_MODEL

    if (!audioFile || !sessionId || !chunkIndex || !startTime || !endTime || !duration) {
      return NextResponse.json({ error: 'Missing required fields' }, { status: 400 })
    }

    // Verify session ownership
    const recordingSession = await prisma.recordingSession.findFirst({
      where: {
        id: sessionId,
        userId: session.user.id
      }
    })

    if (!recordingSession) {
      return NextResponse.json({ error: 'Session not found' }, { status: 404 })
    }

    // Create transcription record with pending status
    const transcription = await prisma.transcription.create({
      data: {
        sessionId,
        chunkIndex: parseInt(chunkIndex),
        content: '',
        startTime: new Date(startTime),
        endTime: new Date(endTime),
        duration: parseInt(duration),
        status: 'pending'
      }
    })

    // Process transcription asynchronously
    processTranscription(transcription.id, audioFile, modelId).catch(err => {
      console.error('Transcription processing error:', err)
    })

    return NextResponse.json({
      transcriptionId: transcription.id,
      status: 'pending'
    })
  } catch (error) {
    console.error('Failed to handle transcription upload:', error)
    return NextResponse.json({ error: 'Failed to process upload' }, { status: 500 })
  }
}

async function processTranscription(transcriptionId: string, audioFile: File, modelId: string) {
  try {
    // Update status to processing
    await prisma.transcription.update({
      where: { id: transcriptionId },
      data: { status: 'processing' }
    })

    // Convert file to base64
    const arrayBuffer = await audioFile.arrayBuffer()
    const base64Audio = Buffer.from(arrayBuffer).toString('base64')

    // Get the Gemini model with user-specified model ID
    const model = getGeminiModel(modelId)

    // Create the transcription request
    const result = await model.generateContent([
      {
        inlineData: {
          mimeType: audioFile.type || 'audio/webm',
          data: base64Audio
        }
      },
      {
        text: 'Please transcribe this audio recording. Detect the language automatically and transcribe in the original language. Only output the transcription text, nothing else. If there is no speech or the audio is silent, respond with "[No speech detected]".'
      }
    ])

    const response = await result.response
    const transcribedText = response.text().trim()

    // Update transcription with result
    await prisma.transcription.update({
      where: { id: transcriptionId },
      data: {
        content: transcribedText,
        status: 'completed'
      }
    })
  } catch (error) {
    console.error('Transcription error:', error)

    // Update transcription with error
    await prisma.transcription.update({
      where: { id: transcriptionId },
      data: {
        status: 'failed',
        errorMessage: error instanceof Error ? error.message : 'Unknown error occurred'
      }
    })
  }
}
