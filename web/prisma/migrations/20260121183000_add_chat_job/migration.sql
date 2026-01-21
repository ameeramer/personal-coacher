-- CreateTable
CREATE TABLE "ChatJob" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "conversationId" TEXT NOT NULL,
    "messageId" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'PENDING',
    "buffer" TEXT NOT NULL DEFAULT '',
    "error" TEXT,
    "fcmToken" TEXT,
    "clientConnected" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ChatJob_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "ChatJob_userId_status_idx" ON "ChatJob"("userId", "status");

-- CreateIndex
CREATE INDEX "ChatJob_conversationId_idx" ON "ChatJob"("conversationId");

-- CreateIndex
CREATE INDEX "ChatJob_messageId_idx" ON "ChatJob"("messageId");
