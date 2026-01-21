-- CreateTable
CREATE TABLE "DailyToolJob" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "qstashMessageId" TEXT,
    "status" TEXT NOT NULL DEFAULT 'PENDING',
    "error" TEXT,
    "dailyToolId" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "DailyToolJob_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "DailyToolJob_userId_status_idx" ON "DailyToolJob"("userId", "status");

-- CreateIndex
CREATE INDEX "DailyToolJob_qstashMessageId_idx" ON "DailyToolJob"("qstashMessageId");
