#!/bin/bash

# Script to configure DynamoDB global tables for Photo Blog App
# Configures PhotosTable and CognitoBackupTable with replicas in eu-central-1

# Exit on error
set -e

# Default parameters (can be overridden via environment variables or command-line arguments)
APP_NAME=${APP_NAME:-"photo-blog-application-group1"}
STAGE_NAME=${STAGE_NAME:-"dev"}
PRIMARY_REGION=${PRIMARY_REGION:-"eu-west-1"}
BACKUP_REGION=${BACKUP_REGION:-"eu-central-1"}
ACCOUNT_ID=${ACCOUNT_ID:-"711387109786"}

# Derived table names
PHOTOS_TABLE="${APP_NAME}-${STAGE_NAME}-photos"
COGNITO_BACKUP_TABLE="${APP_NAME}-${STAGE_NAME}-cognito-backup"

# Function to check if a DynamoDB table exists
check_table_exists() {
  local table_name=$1
  local region=$2
  echo "Checking if table ${table_name} exists in ${region}..."
  if aws dynamodb describe-table --table-name "${table_name}" --region "${region}" >/dev/null 2>&1; then
    echo "Table ${table_name} exists in ${region}."
    return 0
  else
    echo "Error: Table ${table_name} does not exist in ${region}."
    exit 1
  fi
}

# Function to enable point-in-time recovery (PITR)
enable_pitr() {
  local table_name=$1
  local region=$2
  echo "Enabling PITR for ${table_name} in ${region}..."
  aws dynamodb update-continuous-backups \
    --table-name "${table_name}" \
    --point-in-time-recovery-specification PointInTimeRecoveryEnabled=true \
    --region "${region}"
  echo "PITR enabled for ${table_name} in ${region}."
}

# Function to check DynamoDB Streams status
check_streams_enabled() {
  local table_name=$1
  local region=$2
  echo "Checking DynamoDB Streams for ${table_name} in ${region}..."
  stream_spec=$(aws dynamodb describe-table \
    --table-name "${table_name}" \
    --region "${region}" \
    --query 'Table.StreamSpecification.StreamEnabled' \
    --output text)
  if [ "$stream_spec" == "true" ]; then
    echo "DynamoDB Streams are enabled for ${table_name} in ${region}."
  else
    echo "Error: DynamoDB Streams are not enabled for ${table_name} in ${region}. Please enable streams in the SAM template."
    exit 1
  fi
}

# Function to create or update global table
configure_global_table() {
  local table_name=$1
  echo "Configuring global table for ${table_name}..."
  
  # Check if global table already exists
  global_table_status=$(aws dynamodb describe-global-table \
    --global-table-name "${table_name}" \
    --region "${PRIMARY_REGION}" \
    --query 'GlobalTableDescription.ReplicationGroup[].RegionName' \
    --output text 2>/dev/null || echo "none")

  if [[ "$global_table_status" == "none" ]]; then
    echo "Creating global table ${table_name} with replicas in ${PRIMARY_REGION} and ${BACKUP_REGION}..."
    aws dynamodb create-global-table \
      --global-table-name "${table_name}" \
      --replication-group RegionName="${PRIMARY_REGION}" RegionName="${BACKUP_REGION}" \
      --region "${PRIMARY_REGION}"
  else
    echo "Global table ${table_name} already exists. Checking replicas..."
    if [[ "$global_table_status" != *"${BACKUP_REGION}"* ]]; then
      echo "Adding ${BACKUP_REGION} replica to ${table_name}..."
      aws dynamodb update-global-table \
        --global-table-name "${table_name}" \
        --replica-updates "{\"Create\":{\"RegionName\":\"${BACKUP_REGION}\"}}" \
        --region "${PRIMARY_REGION}"
    else
      echo "${BACKUP_REGION} replica already exists for ${table_name}."
    fi
  fi
}

# Function to verify global table replication
verify_global_table() {
  local table_name=$1
  echo "Verifying global table replication for ${table_name}..."
  for i in {1..30}; do
    replication_status=$(aws dynamodb describe-global-table \
      --global-table-name "${table_name}" \
      --region "${PRIMARY_REGION}" \
      --query 'GlobalTableDescription.ReplicationGroup[?RegionName==`'"${BACKUP_REGION}"'`].ReplicaStatus' \
      --output text)
    if [[ "$replication_status" == "ACTIVE" ]]; then
      echo "Replication for ${table_name} in ${BACKUP_REGION} is ACTIVE."
      return 0
    fi
    echo "Waiting for replication to become ACTIVE (Attempt $i/30)..."
    sleep 10
  done
  echo "Error: Replication for ${table_name} in ${BACKUP_REGION} did not become ACTIVE."
  exit 1
}

# Main execution
echo "Starting DynamoDB global table configuration..."

# Check AWS CLI and credentials
if ! aws sts get-caller-identity >/dev/null 2>&1; then
  echo "Error: AWS CLI is not configured or credentials are invalid."
  exit 1
fi

# Process PhotosTable
check_table_exists "${PHOTOS_TABLE}" "${PRIMARY_REGION}"
check_streams_enabled "${PHOTOS_TABLE}" "${PRIMARY_REGION}"
enable_pitr "${PHOTOS_TABLE}" "${PRIMARY_REGION}"
configure_global_table "${PHOTOS_TABLE}"
verify_global_table "${PHOTOS_TABLE}"

# Process CognitoBackupTable
check_table_exists "${COGNITO_BACKUP_TABLE}" "${PRIMARY_REGION}"
check_streams_enabled "${COGNITO_BACKUP_TABLE}" "${PRIMARY_REGION}"
enable_pitr "${COGNITO_BACKUP_TABLE}" "${PRIMARY_REGION}"
configure_global_table "${COGNITO_BACKUP_TABLE}"
verify_global_table "${COGNITO_BACKUP_TABLE}"

echo "Global table configuration completed successfully for ${PHOTOS_TABLE} and ${COGNITO_BACKUP_TABLE}."
