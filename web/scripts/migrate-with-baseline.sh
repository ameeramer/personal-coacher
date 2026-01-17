#!/bin/bash
# migrate-with-baseline.sh
# This script handles Prisma migrations on an existing database by:
# 1. Creating the _prisma_migrations table if it doesn't exist
# 2. Marking existing migrations as already applied (baseline)
# 3. Running migrate deploy for any new migrations

set -e

echo "Starting migration with baseline..."

# Try to run migrate deploy first - if it works, we're done
if npx prisma migrate deploy 2>&1; then
    echo "Migrations applied successfully!"
    exit 0
fi

echo "Migrate deploy failed. Attempting to baseline existing migrations..."

# If migrate deploy failed, we need to baseline
# First, resolve (mark as applied) the existing migrations that already exist in the DB
# These are the migrations for tables that were created before we started using migrate

MIGRATIONS=(
    "20260102145232_init"
    "20260104201500_add_push_subscription"
)

for migration in "${MIGRATIONS[@]}"; do
    echo "Marking migration $migration as applied..."
    npx prisma migrate resolve --applied "$migration" 2>/dev/null || true
done

# Now run migrate deploy again to apply any new migrations
echo "Running migrate deploy for remaining migrations..."
npx prisma migrate deploy

echo "Migration baseline complete!"
