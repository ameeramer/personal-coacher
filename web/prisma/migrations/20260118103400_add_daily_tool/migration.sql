-- CreateTable
CREATE TABLE "DailyTool" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "date" TIMESTAMP(3) NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "htmlCode" TEXT NOT NULL,
    "journalContext" TEXT,
    "status" TEXT NOT NULL DEFAULT 'PENDING',
    "usedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "DailyTool_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "DailyTool_userId_date_idx" ON "DailyTool"("userId", "date");

-- CreateIndex
CREATE INDEX "DailyTool_userId_status_idx" ON "DailyTool"("userId", "status");

-- AddForeignKey
ALTER TABLE "DailyTool" ADD CONSTRAINT "DailyTool_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
