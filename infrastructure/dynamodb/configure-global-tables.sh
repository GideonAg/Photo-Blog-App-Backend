#!/bin/bash

# Script to configure DynamoDB global tables based on global-table-config.json
# Requires AWS CLI and jq

set -e

CONFIG_FILE="infrastructure/dynamodb/global-table-config.json"
PRIMARY_REGION="eu-west-1"
BACKUP_REGION="eu-central-1"

if [ ! -f "$CONFIG_FILE" ]; then
  echo "Error: $CONFIG_FILE not found"
  exit 1
fi

# Check if jq is installed
if ! command -v jq &> /dev/null; then
  echo "Error: jq is required but not installed"
  exit 1
fi

# Read global tables from config
TABLES=$(jq -r '.GlobalTables[].TableName' "$CONFIG_FILE")

for TABLE_NAME in $TABLES; do
  echo "Configuring global table for $TABLE_NAME..."

  # Verify table exists in primary region
  aws dynamodb describe-table \
    --table-name "$TABLE_NAME" \
    --region "$PRIMARY_REGION" >/dev/null 2>&1 || {
    echo "Error: Table $TABLE_NAME does not exist in $PRIMARY_REGION"
    exit 1
  }

  # Verify table exists in backup region
  aws dynamodb describe-table \
    --table-name "$TABLE_NAME" \
    --region "$BACKUP_REGION" >/dev/null 2>&1 || {
    echo "Error: Table $TABLE_NAME does not exist in $BACKUP_REGION"
    exit 1
  }

  # Check if global table already exists
  GLOBAL_TABLE_STATUS=$(aws dynamodb describe-global-table \
    --global-table-name "$TABLE_NAME" \
    --region "$PRIMARY_REGION" 2>/dev/null | jq -r '.GlobalTableDescription.GlobalTableStatus // "NONE"')

  if [ "$GLOBAL_TABLE_STATUS" = "NONE" ]; then
    echo "Creating global table $TABLE_NAME..."
    aws dynamodb create-global-table \
      --global-table-name "$TABLE_NAME" \
      --replication-group "[{\"RegionName\": \"$PRIMARY_REGION\"}, {\"RegionName\": \"$BACKUP_REGION\"}]" \
      --region "$PRIMARY_REGION"
  else
    echo "Global table $TABLE_NAME already exists, checking replicas..."
    REPLICAS=$(aws dynamodb describe-global-table \
      --global-table-name "$TABLE_NAME" \
      --region "$PRIMARY_REGION" | jq -r '.GlobalTableDescription.ReplicationGroup[].RegionName')
    if ! echo "$REPLICAS" | grep -q "$BACKUP_REGION"; then
      echo "Adding replica in $BACKUP_REGION..."
      aws dynamodb update-global-table \
        --global-table-name "$TABLE_NAME" \
        --replica-updates "[{\"Create\": {\"RegionName\": \"$BACKUP_REGION\"}}]" \
        --region "$PRIMARY_REGION"
    fi
  fi

  # Enable PITR for PhotosTable in both regions
  if [ "$TABLE_NAME" = "photo-blog-application-group1-dev-photos" ]; then
    for REGION in "$PRIMARY_REGION" "$BACKUP_REGION"; do
      echo "Enabling PITR for $TABLE_NAME in $REGION..."
      aws dynamodb update-continuous-backups \
        --table-name "$TABLE_NAME" \
        --point-in-time-recovery-specification PointInTimeRecoveryEnabled=true \
        --region "$REGION"
    done
  fi
done

echo "Waiting for global table replication to complete..."
for TABLE_NAME in $TABLES; do
  until aws dynamodb describe-global-table \
    --global-table-name "$TABLE_NAME" \
    --region "$PRIMARY_REGION" | jq -r '.GlobalTableDescription.GlobalTableStatus' | grep -q "ACTIVE"; do
    echo "Waiting for $TABLE_NAME to become ACTIVE..."
    sleep 10
  done
  echo "Global table $TABLE_NAME is ACTIVE"
done

echo "Global table configuration completed successfully"