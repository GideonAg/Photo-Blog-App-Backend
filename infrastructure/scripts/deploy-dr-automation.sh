#!/bin/bash

# Script to deploy automated DR resources for Photo Blog App
# Requires AWS CLI and jq

set -e

# Configuration
APP_NAME="photo-blog-application"
STAGE="dev"
BACKUP_REGION="eu-central-1"
TEMPLATE_FILE="backup-template.yml"
STACK_NAME="${APP_NAME}-${STAGE}-backup"
MONITOR_FUNCTION_NAME="${APP_NAME}-${STAGE}-RegionMonitorFunction"
BACKUP_ALERT_TOPIC_ARN=$(aws sns list-topics --region "$BACKUP_REGION" --query "Topics[?contains(TopicArn, '${APP_NAME}-${STAGE}-backup-alerts')].TopicArn" --output text)

# Check dependencies
if ! command -v jq &> /dev/null; then
  echo "Error: jq is required but not installed"
  exit 1
fi

echo "Deploying automated DR resources in $BACKUP_REGION..."

# Deploy backup-template.yml
echo "Deploying $STACK_NAME stack..."
aws cloudformation deploy \
  --template-file "$TEMPLATE_FILE" \
  --stack-name "$STACK_NAME" \
  --region "$BACKUP_REGION" \
  --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM CAPABILITY_AUTO_EXPAND

# Verify RegionMonitorFunction
echo "Verifying $MONITOR_FUNCTION_NAME..."
MONITOR_STATUS=$(aws lambda get-function --function-name "$MONITOR_FUNCTION_NAME" --region "$BACKUP_REGION" --query "Configuration.State" --output text)
if [ "$MONITOR_STATUS" != "Active" ]; then
  echo "Error: $MONITOR_FUNCTION_NAME is not active"
  exit 1
fi
echo "$MONITOR_FUNCTION_NAME is active"

# Verify EventBridge rule
RULE_NAME="${APP_NAME}-${STAGE}-region-monitor"
echo "Verifying EventBridge rule $RULE_NAME..."
RULE_STATE=$(aws events describe-rule --name "$RULE_NAME" --region "$BACKUP_REGION" --query "State" --output text)
if [ "$RULE_STATE" != "ENABLED" ]; then
  echo "Error: EventBridge rule $RULE_NAME is not enabled"
  exit 1
fi
echo "EventBridge rule $RULE_NAME is enabled"

# Send deployment notification
echo "Sending deployment notification..."
aws sns publish \
  --topic-arn "$BACKUP_ALERT_TOPIC_ARN" \
  --region "$BACKUP_REGION" \
  --message "{\"event\":\"dr_automation_deployed\",\"timestamp\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"details\":\"Automated DR resources deployed in $BACKUP_REGION\"}" \
  --message-attributes "{\"event\":{\"DataType\":\"String\",\"StringValue\":\"dr_automation_deployed\"}}"

echo "Automated DR deployment completed successfully"
echo "RegionMonitorFunction will check eu-west-1 health every 5 minutes"