#!/bin/bash

# Variables
STACK_NAME="photo-blog-backup"
REGION="eu-central-1"
DOMAIN_NAME="mscv2group1.link"
HEALTH_CHECK_ID=$(aws route53 list-health-checks --region $REGION --query "HealthChecks[?HealthCheckConfig.FullyQualifiedDomainName=='$DOMAIN_NAME'].Id" --output text)

# Check if HEALTH_CHECK_ID is retrieved
if [ -z "$HEALTH_CHECK_ID" ]; then
  echo "Error: Could not find Health Check ID for $DOMAIN_NAME. Please verify the Route 53 Health Check is created."
  exit 1
fi

# Step 1: Update Health Check to simulate outage (invalid port)
echo "Simulating frontend outage by setting port to 81..."
aws route53 update-health-check \
  --health-check-id $HEALTH_CHECK_ID \
  --cli-input-json '{"HealthCheckConfig":{"Type":"HTTP","FullyQualifiedDomainName":"'$DOMAIN_NAME'","Port":81,"ResourcePath":"/","RequestInterval":30,"FailureThreshold":3}}' \
  --region $REGION

# Wait for health check to fail (approximately 90 seconds for 3 failures with 30s interval)
echo "Waiting for health check to fail..."
sleep 90

# Step 2: Get the correct Lambda function ARN from the stack
FUNCTION_ARN=$(aws cloudformation describe-stacks \
  --stack-name $STACK_NAME \
  --region $REGION \
  --query "Stacks[0].Outputs[?OutputKey=='RegionMonitorFunctionArn'].OutputValue" \
  --output text)

if [ -z "$FUNCTION_ARN" ]; then
  echo "Error: Could not find RegionMonitorFunction ARN in stack $STACK_NAME. Please verify the stack deployment."
  exit 1
fi

# Step 3: Invoke RegionMonitorFunction twice to exceed failure threshold
echo "Invoking RegionMonitorFunction..."
for i in {1..2}; do
  aws lambda invoke \
    --function-name $(basename $FUNCTION_ARN) \
    --payload '{}' \
    --region $REGION \
    output.json
  echo "Invocation $i completed. Waiting 5 seconds..."
  sleep 5
done

# Step 4: Revert Health Check to original configuration (port 80)
echo "Reverting Health Check to original configuration (port 80)..."
aws route53 update-health-check \
  --health-check-id $HEALTH_CHECK_ID \
  --cli-input-json '{"HealthCheckConfig":{"Type":"HTTP","FullyQualifiedDomainName":"'$DOMAIN_NAME'","Port":80,"ResourcePath":"/","RequestInterval":30,"FailureThreshold":3}}' \
  --region $REGION

# Step 5: Check Logs and Email
echo "Check CloudWatch Logs for RegionMonitorFunction and BackupAlertHandler."
echo "Verify email sent to gideon.agbosu@amalitech.com with [Photo Blog] Disaster Recovery Triggered."

# Cleanup output file
rm -f output.json