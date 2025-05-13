package com.photoblog.notifications;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoblog.utils.SESUtil;
import java.util.Map;

public class SystemAlertHandler implements RequestHandler<SNSEvent, Void> {

    private final SESUtil sesUtil = new SESUtil();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String adminEmail = System.getenv("EMAIL_SENDER");

    @Override
    public Void handleRequest(SNSEvent event, Context context) {
        try {
            for (SNSEvent.SNSRecord record : event.getRecords()) {
                String message = record.getSNS().getMessage();
                Map<String, String> messageMap = objectMapper.readValue(message, Map.class);
                String eventType = messageMap.getOrDefault("event", "unknown");

                String subject;
                String body = switch (eventType) {
                    case "backup_issue" -> {
                        subject = "Photo Blog Backup Issue Alert";
                        yield String.format(
                                "Backup verification failed:\n" +
                                        "S3 Replication: %s\n" +
                                        "Backup Bucket: %s\n" +
                                        "Global Table: %s\n" +
                                        "Please investigate immediately.",
                                messageMap.getOrDefault("s3Replication", "Unknown"),
                                messageMap.getOrDefault("backupBucket", "Unknown"),
                                messageMap.getOrDefault("globalTable", "Unknown")
                        );
                    }
                    case "restoration_completed" -> {
                        subject = "Photo Blog Restoration Completed";
                        yield String.format(
                                "Restoration completed successfully in %s:\n" +
                                        "S3 Status: %s\n" +
                                        "PhotosTable: %s\n" +
                                        "CognitoBackupTable: %s\n" +
                                        "Timestamp: %s",
                                System.getenv("BACKUP_REGION"),
                                messageMap.getOrDefault("s3Status", "Unknown"),
                                messageMap.getOrDefault("photosTableStatus", "Unknown"),
                                messageMap.getOrDefault("cognitoTableStatus", "Unknown"),
                                messageMap.getOrDefault("timestamp", "Unknown")
                        );
                    }
                    case "restoration_issue" -> {
                        subject = "Photo Blog Restoration Issue";
                        yield String.format(
                                "Restoration encountered issues in %s:\n" +
                                        "S3 Status: %s\n" +
                                        "PhotosTable: %s\n" +
                                        "CognitoBackupTable: %s\n" +
                                        "Timestamp: %s\n" +
                                        "Please investigate.",
                                System.getenv("BACKUP_REGION"),
                                messageMap.getOrDefault("s3Status", "Unknown"),
                                messageMap.getOrDefault("photosTableStatus", "Unknown"),
                                messageMap.getOrDefault("cognitoTableStatus", "Unknown"),
                                messageMap.getOrDefault("timestamp", "Unknown")
                        );
                    }
                    case "disaster_recovery_triggered" -> {
                        subject = "Photo Blog Disaster Recovery Triggered";
                        yield String.format(
                                "Disaster recovery triggered:\n" +
                                        "Details: %s\n" +
                                        "Timestamp: %s\n" +
                                        "Restoration process initiated.",
                                messageMap.getOrDefault("triggerDetails", "Unknown"),
                                messageMap.getOrDefault("timestamp", "Unknown")
                        );
                    }
                    case "backup_error", "restoration_error", "dr_trigger_error" -> {
                        subject = "Photo Blog DR Error: " + eventType;
                        yield String.format(
                                "Error occurred during %s:\n" +
                                        "Error: %s\n" +
                                        "Please investigate immediately.",
                                eventType.replace("_error", ""),
                                messageMap.getOrDefault("error", "Unknown")
                        );
                    }
                    default -> {
                        subject = "Photo Blog System Alert: Unknown Event";
                        yield "Unknown system alert received:\n" + message;
                    }
                };

                sesUtil.sendEmail(adminEmail, subject, body);
                context.getLogger().log("Sent email to " + adminEmail + " for event: " + eventType);
            }
            return null;
        } catch (Exception e) {
            context.getLogger().log("Error processing system alert: " + e.getMessage());
            throw new RuntimeException("Failed to process system alert: " + e.getMessage(), e);
        }
    }
}
