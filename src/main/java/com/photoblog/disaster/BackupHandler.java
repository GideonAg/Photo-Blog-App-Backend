package com.photoblog.disaster;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoblog.utils.SNSUtil;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateContinuousBackupsRequest;
import software.amazon.awssdk.services.dynamodb.model.PointInTimeRecoverySpecification;
import software.amazon.awssdk.services.dynamodb.model.PointInTimeRecoveryStatus;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetBucketReplicationRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ReplicationConfiguration;
import java.util.HashMap;
import java.util.Map;

public class BackupHandler implements RequestHandler<Map<String, String>, Map<String, String>> {

    private final S3Client s3Client = S3Client.builder().build();
    private final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
    private final SNSUtil snsUtil = new SNSUtil();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String mainBucket = System.getenv("MAIN_BUCKET");
    private final String backupBucketArn = System.getenv("BACKUP_BUCKET_ARN");
    private final String photosTable = System.getenv("PHOTOS_TABLE");
    private final String systemAlertTopic = System.getenv("SYSTEM_ALERT_TOPIC");
    private final String backupRegion = System.getenv("BACKUP_REGION");

    @Override
    public Map<String, String> handleRequest(Map<String, String> input, Context context) {
        Map<String, String> response = new HashMap<>();
        boolean backupSuccessful = true;

        try {
            GetBucketReplicationRequest replicationRequest = GetBucketReplicationRequest.builder()
                    .bucket(mainBucket)
                    .build();
            ReplicationConfiguration replicationConfig = s3Client.getBucketReplication(replicationRequest).replicationConfiguration();
            if (replicationConfig != null && !replicationConfig.rules().isEmpty()) {
                response.put("s3ReplicationStatus", "Enabled");
                context.getLogger().log("S3 replication verified for bucket: " + mainBucket);
            } else {
                response.put("s3ReplicationStatus", "Not enabled");
                context.getLogger().log("Warning: S3 replication not enabled for bucket: " + mainBucket);
                backupSuccessful = false;
            }

            String backupBucket = backupBucketArn.replace("arn:aws:s3:::", "");
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(backupBucket)
                    .maxKeys(10)
                    .build();
            int objectCount = s3Client.listObjectsV2(listRequest).contents().size();
            response.put("backupBucketStatus", "Objects found: " + objectCount);
            if (objectCount == 0) {
                context.getLogger().log("Warning: No objects found in BackupBucket: " + backupBucket);
                backupSuccessful = false;
            } else {
                context.getLogger().log("Verified " + objectCount + " objects in BackupBucket: " + backupBucket);
            }

            UpdateContinuousBackupsRequest pitrRequest = UpdateContinuousBackupsRequest.builder()
                    .tableName(photosTable)
                    .pointInTimeRecoverySpecification(PointInTimeRecoverySpecification.builder()
                            .pointInTimeRecoveryEnabled(true)
                            .build())
                    .build();
            dynamoDbClient.updateContinuousBackups(pitrRequest);
            response.put("pitrStatus", PointInTimeRecoveryStatus.ENABLED.toString());
            context.getLogger().log("DynamoDB PITR enabled for table: " + photosTable);

            DescribeTableRequest describeRequest = DescribeTableRequest.builder()
                    .tableName(photosTable)
                    .build();
            boolean hasBackupRegionReplica = dynamoDbClient.describeTable(describeRequest)
                    .table()
                    .replicas()
                    .stream()
                    .anyMatch(replica -> backupRegion.equals(replica.regionName()) && "ACTIVE".equals(replica.replicaStatusAsString()));
            if (hasBackupRegionReplica) {
                response.put("globalTableStatus", "Replica found in " + backupRegion);
                context.getLogger().log("DynamoDB global table replica verified in: " + backupRegion);
            } else {
                response.put("globalTableStatus", "No replica in " + backupRegion);
                context.getLogger().log("Warning: No global table replica found in: " + backupRegion);
                backupSuccessful = false;
            }

            if (!backupSuccessful) {
                Map<String, String> alertMessage = new HashMap<>();
                alertMessage.put("event", "backup_issue");
                alertMessage.put("s3Replication", response.get("s3ReplicationStatus"));
                alertMessage.put("backupBucket", response.get("backupBucketStatus"));
                alertMessage.put("globalTable", response.get("globalTableStatus"));
                snsUtil.publishMessage(systemAlertTopic, alertMessage, context);
                context.getLogger().log("Published backup issue alert to: " + systemAlertTopic);
            }

            response.put("status", backupSuccessful ? "success" : "warning");
            return response;

        } catch (Exception e) {
            context.getLogger().log("Error in backup: " + e.getMessage());
            response.put("status", "error");
            response.put("errorMessage", e.getMessage());
            Map<String, String> alertMessage = new HashMap<>();
            alertMessage.put("event", "backup_error");
            alertMessage.put("error", e.getMessage());
            snsUtil.publishMessage(systemAlertTopic, alertMessage, context);
            return response;
        }
    }
}
