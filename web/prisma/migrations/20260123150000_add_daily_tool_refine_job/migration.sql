-- CreateTable
CREATE TABLE "DailyToolRefineJob" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "dailyToolId" TEXT NOT NULL,
    "feedback" TEXT NOT NULL,
    "qstashMessageId" TEXT,
    "status" TEXT NOT NULL DEFAULT 'PENDING',
    "error" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "DailyToolRefineJob_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "DailyToolRefineJob_userId_status_idx" ON "DailyToolRefineJob"("userId", "status");

-- CreateIndex
CREATE INDEX "DailyToolRefineJob_qstashMessageId_idx" ON "DailyToolRefineJob"("qstashMessageId");

-- CreateIndex
CREATE INDEX "DailyToolRefineJob_dailyToolId_idx" ON "DailyToolRefineJob"("dailyToolId");
