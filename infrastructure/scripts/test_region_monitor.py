import boto3
import os
import time
import json
from botocore.exceptions import ClientError

# Configure AWS clients
lambda_client = boto3.client('lambda', region_name='eu-central-1')
sns_client = boto3.client('sns', region_name='eu-west-1')
lambda_function_name = 'photo-blog-backup-RegionMonitorFunction-W3QFi9pufMrm'
system_alert_topic_arn = 'arn:aws:sns:eu-west-1:711387109786:photo-blog-group1-dev-system-alerts'

# Set environment variables for the Lambda invocation
os.environ['PRIMARY_REGION'] = 'eu-west-1'
os.environ['SYSTEM_ALERT_TOPIC'] = system_alert_topic_arn
os.environ['API_GATEWAY_ID'] = 'yqnlkhkymh'
os.environ['AWS_REGION'] = 'eu-central-1'

def invoke_lambda():
    payload = {
        "input": "test_input"  # Optional input, can be empty for this handler
    }
    try:
        response = lambda_client.invoke(
            FunctionName=lambda_function_name,
            InvocationType='RequestResponse',
            Payload=json.dumps(payload).encode()
        )
        result = json.loads(response['Payload'].read().decode())
        print("Lambda Response:", result)
        return result
    except ClientError as e:
        print(f"Error invoking Lambda: {e}")
        return None

def check_sns_notification():
    # Wait for SNS message to be delivered (e.g., 10 seconds)
    time.sleep(10)
    try:
        # List subscriptions to verify the topic is configured
        subscriptions = sns_client.list_subscriptions_by_topic(TopicArn=system_alert_topic_arn)
        for sub in subscriptions['Subscriptions']:
            if sub['Protocol'] == 'email' and sub['SubscriptionArn'].startswith('arn:aws:sns:'):
                print(f"Subscription found: {sub['Endpoint']} will receive notification")

        # Note: Actual message content verification requires accessing email/SNS logs
        print("Check your admin email (e.g., gideon.agbosu@amalitech.com) for the notification.")
    except ClientError as e:
        print(f"Error checking SNS: {e}")

def main():
    print("Starting Region Monitor Handler test at", time.ctime())
    print("Invoking Lambda function...")
    result = invoke_lambda()

    if result and result.get('status') in ['warning', 'triggered', 'error']:
        print("Unhealthy condition detected. Checking for SNS notification...")
        check_sns_notification()
    elif result and result.get('status') == 'healthy':
        print("Primary region is healthy. No notification expected.")
    else:
        print("Test failed or no clear status returned.")

if __name__ == "__main__":
    main()