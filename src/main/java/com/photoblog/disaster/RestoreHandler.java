package com.photoblog.disaster;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoblog.utils.SNSUtil;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.GetBucketLocationRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class RestoreHandler implements RequestHandler<Map<String, String>, Map<String, String>> {

    private final S3Client s3Client;
    private final DynamoDbClient dynamoDbClient;
    private final CognitoIdentityProviderClient cognitoClient;
    private final SNSUtil snsUtil = new SNSUtil();

    private final String mainBucket = System.getenv("MAIN_BUCKET");
    private final String backupBucketArn = System.getenv("BACKUP_BUCKET_ARN");
    private final String photosTable = System.getenv("PHOTOS_TABLE");
    private final String backupUserPoolId = System.getenv("BACKUP_USER_POOL_ID");
    private final String systemAlertTopic = System.getenv("BACKUP_ALERT_TOPIC");
    private final String backupRegion = System.getenv("BACKUP_REGION");

    public RestoreHandler() {
        if (backupRegion == null) {
            throw new IllegalStateException("BACKUP_REGION environment variable is not set");
        }
        this.s3Client = S3Client.builder().region(Region.of(backupRegion)).build();
        this.dynamoDbClient = DynamoDbClient.builder().region(Region.of(backupRegion)).build();
        this.cognitoClient = CognitoIdentityProviderClient.builder().region(Region.of(backupRegion)).build();
    }

    @Override
    public Map<String, String> handleRequest(Map<String, String> input, Context context) {
        Map<String, String> response = new HashMap<>();
        boolean restoreStatus = true;

        try {
            String action = input.getOrDefault("action", "");
            if (!"restore".equalsIgnoreCase(action)) {
                response.put("status", "error");
                response.put("errorMessage", "Invalid action: " + action);
                return response;
            }

            GetBucketLocationRequest locationRequest = GetBucketLocationRequest.builder()
                    .bucket(mainBucket)
                    .build();
            String bucketRegion = s3Client.getBucketLocation(locationRequest).locationConstraintAsString();
            if (!backupRegion.equals(bucketRegion)) {
                context.getLogger().log("Error: MainBucket is in " + bucketRegion + ", expected " + backupRegion);
                response.put("status", "error");
                response.put("errorMessage", "MainBucket is not in " + backupRegion);
                return response;
            }

            String backupBucket = backupBucketArn.replace("arn:aws:s3:::", "");
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(backupBucket)
                    .build();
            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
            restoreStatus = isMainbucketRestoreComplete(context, listResponse, backupBucket, response, restoreStatus);

            restoreStatus = isPhotosTableRestoreComplete(context, response, restoreStatus);

            int usersRestored = 0;
            int usersSkipped = 0;
            try {
                ListUsersRequest listUsersRequest = ListUsersRequest.builder()
                        .userPoolId(backupUserPoolId)
                        .build();

                for (UserType user : cognitoClient.listUsers(listUsersRequest).users()) {
                    String userId = user.username();
                    try {
                        AdminSetUserPasswordRequest setPasswordRequest = AdminSetUserPasswordRequest.builder()
                                .userPoolId(backupUserPoolId)
                                .username(userId)
                                .password("TempPassword123!")
                                .permanent(false)
                                .build();
                        cognitoClient.adminSetUserPassword(setPasswordRequest);
                        usersRestored++;
                        context.getLogger().log("Set temporary password for user: " + userId + " in BackupUserPool");
                    } catch (Exception e) {
                        usersSkipped++;
                        context.getLogger().log("Skipping user " + userId + " in BackupUserPool: " + e.getMessage());
                    }
                }
                response.put("cognitoRestoreStatus", "Restored " + usersRestored + " users, skipped " + usersSkipped);
                context.getLogger().log("Restored " + usersRestored + " users and skipped " + usersSkipped + " users in BackupUserPool");
            } catch (Exception e) {
                response.put("cognitoRestoreStatus", "Failed: " + e.getMessage());
                context.getLogger().log("Failed to restore Cognito users: " + e.getMessage());
                restoreStatus = false;
            }

            Map<String, String> snsMessage = new HashMap<>();
            snsMessage.put("event", restoreStatus ? "restoration_completed" : "restoration_issue");
            snsMessage.put("timestamp", Instant.now().toString());
            snsMessage.put("s3Status", response.get("s3RestoreStatus"));
            snsMessage.put("photosTableStatus", response.get("photosTableStatus"));
            snsMessage.put("cognitoRestoreStatus", response.get("cognitoRestoreStatus"));
            snsUtil.publishMessage(systemAlertTopic, snsMessage, context);
            context.getLogger().log("Published restoration notification to: " + systemAlertTopic);

            response.put("status", restoreStatus ? "success" : "warning");
            return response;

        } catch (Exception e) {
            context.getLogger().log("Error in restoration: " + e.getMessage());
            response.put("status", "error");
            response.put("errorMessage", e.getMessage());
            Map<String, String> snsMessage = new HashMap<>();
            snsMessage.put("event", "restoration_error");
            snsMessage.put("error", e.getMessage());
            snsUtil.publishMessage(systemAlertTopic, snsMessage, context);
            return response;
        } finally {
            s3Client.close();
            dynamoDbClient.close();
            cognitoClient.close();
        }
    }

    private boolean isPhotosTableRestoreComplete(Context context, Map<String, String> response, boolean restoreSuccessful) {
        DescribeTableRequest photosTableRequest = DescribeTableRequest.builder()
                .tableName(photosTable)
                .build();
        boolean photosTableReplicaActive = dynamoDbClient.describeTable(photosTableRequest)
                .table()
                .replicas()
                .stream()
                .anyMatch(replica -> backupRegion.equals(replica.regionName()) && "ACTIVE".equals(replica.replicaStatusAsString()));
        if (photosTableReplicaActive) {
            response.put("photosTableStatus", "Active replica in " + backupRegion);
            context.getLogger().log("PhotosTable replica verified in: " + backupRegion);
        } else {
            response.put("photosTableStatus", "No active replica in " + backupRegion);
            context.getLogger().log("Warning: No active PhotosTable replica in: " + backupRegion);
            restoreSuccessful = false;
        }
        return restoreSuccessful;
    }

    private boolean isMainbucketRestoreComplete(Context context, ListObjectsV2Response listResponse, String backupBucket, Map<String, String> response, boolean restoreSuccessful) throws InterruptedException {
        if (listResponse.contents().isEmpty()) {
            context.getLogger().log("Warning: BackupBucket is empty: " + backupBucket);
            response.put("s3RestoreStatus", "No objects found");
            restoreSuccessful = false;
        } else {
            int objectsCopied = 0;
            for (var object : listResponse.contents()) {
                int retries = 3;
                boolean copySuccess = false;
                while (retries > 0 && !copySuccess) {
                    try {
                        CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                                .sourceBucket(backupBucket)
                                .sourceKey(object.key())
                                .destinationBucket(mainBucket)
                                .destinationKey(object.key())
                                .build();
                        s3Client.copyObject(copyRequest);
                        copySuccess = true;
                        objectsCopied++;
                    } catch (Exception e) {
                        retries--;
                        context.getLogger().log("Retrying copy for " + object.key() + ", attempts left: " + retries);
                        if (retries == 0) {
                            throw e;
                        }
                        Thread.sleep(1000);
                    }
                }
            }
            response.put("s3RestoreStatus", "Copied " + objectsCopied + " objects to " + mainBucket);
            context.getLogger().log("Copied " + objectsCopied + " objects from " + backupBucket + " to " + mainBucket);
        }
        return restoreSuccessful;
    }
}
