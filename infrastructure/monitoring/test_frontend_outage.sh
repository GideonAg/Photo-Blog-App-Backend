#!/bin/bash

# Variables
STACK_NAME="photo-blog-backup"
REGION="eu-central-1"
DOMAIN_NAME="mscv2group1.link"
FRONTEND_ALARM_NAME="photo-blog-group1-dev-FrontendHealthAlarm"
BACKEND_ALARM_NAME="photo-blog-group1-dev-Backend5XXAlarm"

# Step 1: Get the Lambda function ARN from the stack
FUNCTION_ARN=$(aws cloudformation describe-stacks \
  --stack-name $STACK_NAME \
  --region $REGION \
  --query "Stacks[0].Outputs[?OutputKey=='RegionMonitorFunctionArn'].OutputValue" \
  --output text)

if [ -z "$FUNCTION_ARN" ]; then
  echo "Error: Could not find RegionMonitorFunction ARN in stack $STACK_NAME. Please verify the stack deployment."
  exit 1
fi

# Step 2: Simulate setting frontend alarm to ALARM state
echo "Setting Frontend CloudWatch Alarm to ALARM state..."
aws cloudwatch set-alarm-state \
  --alarm-name $FRONTEND_ALARM_NAME \
  --state-value ALARM \
  --state-reason "Testing disaster recovery" \
  --region $REGION

# Wait for alarm state to propagate
echo "Waiting for alarm state to propagate..."
sleep 30

# Step 3: Invoke RegionMonitorFunction twice to exceed failure threshold
echo "Invoking RegionMonitorFunction to detect unhealthy state..."
for i in {1..2}; do
  echo "Invocation $i of 2..."
  aws lambda invoke \
    --function-name $(basename $FUNCTION_ARN) \
    --payload '{}' \
    --region $REGION \
    output.json

  # Check if the output indicates a disaster recovery trigger
  TRIGGERED=$(cat output.json | grep -c "triggered")
  if [ $TRIGGERED -gt 0 ]; then
    echo "Disaster recovery triggered on invocation $i!"
    break
  fi

  echo "Waiting 30 seconds before next invocation..."
  sleep 30
done

# Step 4: Restore alarm to OK state after test
echo "Restoring Frontend CloudWatch Alarm to OK state..."
aws cloudwatch set-alarm-state \
  --alarm-name $FRONTEND_ALARM_NAME \
  --state-value OK \
  --state-reason "Testing completed" \
  --region $REGION

# Step 5: Check Logs and Email
echo "Check CloudWatch Logs for RegionMonitorFunction and BackupAlertHandler."
echo "Verify email sent to the SNS topic subscribers with [Photo Blog] Disaster Recovery Triggered."

# Cleanup output file
rm -f output.json