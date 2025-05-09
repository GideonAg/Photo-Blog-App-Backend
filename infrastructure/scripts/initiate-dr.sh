#!/bin/bash

# Script to manually initiate disaster recovery in eu-central-1 when eu-west-1 is down
# Serves as a fallback if automated RegionMonitorFunction fails
# Requires AWS CLI and jq

set -e

# Configuration
APP_NAME="photo-blog-application-group1"
STAGE="dev"
PRIMARY_REGION="eu-west-1"
BACKUP_REGION="eu-central-1"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
DOMAIN_NAME="mscv2group1.link"
ADMIN_EMAIL="admin@mscv2group1.link" # Replace with actual admin email
EMAIL_SENDER="notifications@mscv2group1.link"

# Derived names
NEW_MAIN_BUCKET="${APP_NAME}-${STAGE}-main-${BACKUP_REGION}-${ACCOUNT_ID}"
BACKUP_BUCKET="${APP_NAME}-${STAGE}-backup-${BACKUP_REGION}-${ACCOUNT_ID}"
PHOTOS_TABLE="${APP_NAME}-${STAGE}-photos"
COGNITO_BACKUP_TABLE="${APP_NAME}-${STAGE}-cognito-backup"
RESTORE_FUNCTION_NAME="${APP_NAME}-${STAGE}-RestoreHandlerFunction"
TRIGGER_FUNCTION_NAME="${APP_NAME}-${STAGE}-DisasterRecoveryTriggerFunction"
FAILOVER_FUNCTION_NAME="${APP_NAME}-${STAGE}-Route53FailoverHandler"
BACKUP_ALERT_TOPIC_ARN=$(aws sns list-topics --region "$BACKUP_REGION" --query "Topics[?contains(TopicArn, '${APP_NAME}-${STAGE}-backup-alerts')].TopicArn" --output text)

# Check dependencies
if ! command -v jq &> /dev/null; then
  echo "Error: jq is required but not installed"
  exit 1
fi

echo "Initiating manual disaster recovery in $BACKUP_REGION..."
echo "Note: Automated DR is handled by RegionMonitorFunction. Use this script only if automation fails."

# Check RegionMonitorFunction status
echo "Checking RegionMonitorFunction status..."
MONITOR_FUNCTION_NAME="${APP_NAME}-${STAGE}-RegionMonitorFunction"
MONITOR_STATUS=$(aws lambda get-function --function-name "$MONITOR_FUNCTION_NAME" --region "$BACKUP_REGION" --query "Configuration.State" --output text 2>/dev/null || echo "NotFound")
if [ "$MONITOR_STATUS" = "Active" ]; then
  echo "Warning: RegionMonitorFunction is active. Manual DR may not be necessary."
  read -p "Continue with manual DR? (y/N): " confirm
  if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
    echo "Aborting manual DR."
    exit 0
  fi
else
  echo "RegionMonitorFunction is not active or not found. Proceeding with manual DR."
fi

# Create new MainBucket in eu-central-1
echo "Creating new MainBucket: $NEW_MAIN_BUCKET..."
aws s3api create-bucket \
  --bucket "$NEW_MAIN_BUCKET" \
  --region "$BACKUP_REGION" \
  --create-bucket-configuration LocationConstraint="$BACKUP_REGION" >/dev/null
aws s3api put-bucket-versioning \
  --bucket "$NEW_MAIN_BUCKET" \
  --versioning-configuration Status=Enabled \
  --region "$BACKUP_REGION"
aws s3api put-bucket-cors \
  --bucket "$NEW_MAIN_BUCKET" \
  --cors-configuration '{
    "CORSRules": [{
      "AllowedHeaders": ["*"],
      "AllowedMethods": ["GET"],
      "AllowedOrigins": ["https://'"$DOMAIN_NAME"'"],
      "MaxAgeSeconds": 3600
    }]
  }' \
  --region "$BACKUP_REGION"

# Get Backup API Gateway URL
BACKUP_API_URL=$(aws cloudformation describe-stacks \
  --stack-name "${APP_NAME}-${STAGE}-backup" \
  --region "$BACKUP_REGION" \
  --query "Stacks[0].Outputs[?OutputKey=='BackupApiUrl'].OutputValue" \
  --output text)

# Update RestoreHandlerFunction environment variables
echo "Updating $RESTORE_FUNCTION_NAME environment variables..."
aws lambda update-function-configuration \
  --function-name "$RESTORE_FUNCTION_NAME" \
  --region "$BACKUP_REGION" \
  --environment "Variables={
    PRIMARY_REGION=$PRIMARY_REGION,
    BACKUP_REGION=$BACKUP_REGION,
    SYSTEM_ALERT_TOPIC=$BACKUP_ALERT_TOPIC_ARN,
    BACKUP_BUCKET_ARN=arn:aws:s3:::$BACKUP_BUCKET,
    MAIN_BUCKET=$NEW_MAIN_BUCKET,
    PHOTOS_TABLE=$PHOTOS_TABLE,
    COGNITO_BACKUP_TABLE=$COGNITO_BACKUP_TABLE
  }" >/dev/null

# Invoke DisasterRecoveryTriggerFunction
echo "Invoking $TRIGGER_FUNCTION_NAME..."
aws lambda invoke \
  --function-name "$TRIGGER_FUNCTION_NAME" \
  --region "$BACKUP_REGION" \
  --payload '{"action":"trigger"}' \
  --cli-binary-format raw-in-base64-out \
  output.json >/dev/null
cat output.json
rm output.json

# Invoke Route53FailoverHandler
echo "Invoking $FAILOVER_FUNCTION_NAME to update Route53..."
aws lambda invoke \
  --function-name "$FAILOVER_FUNCTION_NAME" \
  --region "$BACKUP_REGION" \
  --payload "{\"action\":\"failover\",\"backupApiUrl\":\"$BACKUP_API_URL\"}" \
  --cli-binary-format raw-in-base64-out \
  output.json >/dev/null
cat output.json
rm output.json

# Send notification via BackupAlertTopic
echo "Sending notification via $BACKUP_ALERT_TOPIC_ARN..."
aws sns publish \
  --topic-arn "$BACKUP_ALERT_TOPIC_ARN" \
  --region "$BACKUP_REGION" \
  --message "{\"event\":\"disaster_recovery_initiated\",\"timestamp\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"details\":\"Manual DR initiated in $BACKUP_REGION, API at $BACKUP_API_URL\"}" \
  --message-attributes "{\"event\":{\"DataType\":\"String\",\"StringValue\":\"disaster_recovery_initiated\"}}"

echo "Manual disaster recovery initiated. Check $BACKUP_API_URL and $ADMIN_EMAIL for updates."
echo "Verify S3 bucket ($NEW_MAIN_BUCKET), DynamoDB tables, and Cognito users in $BACKUP_REGION."