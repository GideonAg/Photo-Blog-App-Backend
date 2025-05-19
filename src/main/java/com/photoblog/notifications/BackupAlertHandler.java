package com.photoblog.notifications;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.util.Map;

public class BackupAlertHandler implements RequestHandler<SNSEvent, Void> {

    private final SesClient sesClient = SesClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String emailSender = System.getenv("EMAIL_SENDER");
    private final String adminEmail = System.getenv("ADMIN_EMAIL");
    private final String primaryRegion = System.getenv("PRIMARY_REGION");
    private final String backupRegion = System.getenv("BACKUP_REGION");

    @Override
    public Void handleRequest(SNSEvent event, Context context) {
        if (emailSender == null || adminEmail == null) {
            context.getLogger().log("Error: EMAIL_SENDER or ADMIN_EMAIL environment variables are not set");
            return null;
        }

        for (SNSEvent.SNSRecord record : event.getRecords()) {
            try {
                String message = record.getSNS().getMessage();
                context.getLogger().log("Received SNS message: " + message);

                Map<String, String> alertMessage = objectMapper.readValue(message, Map.class);
                String eventType = alertMessage.get("event");

                String subject;
                String body;

                switch (eventType) {
                    case "region_monitor_check":
                        boolean isHealthy = Boolean.parseBoolean(alertMessage.get("healthy"));
                        String consecutiveFailures = alertMessage.getOrDefault("consecutiveFailures", "0");
                        subject = isHealthy ?
                                "[Photo Blog] Primary Region Health Check - Healthy" :
                                "[Photo Blog] Primary Region Health Check - Unhealthy";
                        body = String.format(
                                "Primary Region Health Check\n\n" +
                                        "Timestamp: %s\n" +
                                        "Primary Region: %s\n" +
                                        "Backup Region: %s\n" +
                                        "Healthy: %s\n" +
                                        "Consecutive Failures: %s\n\n" +
                                        "Please monitor the situation if unhealthy.",
                                alertMessage.get("timestamp"),
                                primaryRegion,
                                backupRegion,
                                isHealthy,
                                consecutiveFailures
                        );
                        break;

                    case "disaster_recovery_triggered":
                        subject = "[Photo Blog] Disaster Recovery Triggered";
                        body = String.format(
                                "Disaster Recovery Triggered\n\n" +
                                        "Timestamp: %s\n" +
                                        "Primary Region: %s\n" +
                                        "Backup Region: %s\n" +
                                        "Details: %s\n\n" +
                                        "Please take necessary actions to ensure service continuity.",
                                alertMessage.get("timestamp"),
                                primaryRegion,
                                backupRegion,
                                alertMessage.get("details")
                        );
                        break;

                    case "region_monitor_error":
                        subject = "[Photo Blog] Region Monitor Error";
                        body = String.format(
                                "Region Monitor Error\n\n" +
                                        "Timestamp: %s\n" +
                                        "Primary Region: %s\n" +
                                        "Backup Region: %s\n" +
                                        "Error: %s\n\n" +
                                        "Please investigate the issue.",
                                alertMessage.get("timestamp"),
                                primaryRegion,
                                backupRegion,
                                alertMessage.get("error")
                        );
                        break;

                    default:
                        subject = "Photo Blog System Alert: Unknown Event";
                        body = "Unknown event happened";
                        context.getLogger().log("Unknown event type: " + eventType);
                }

                sendEmail(subject, body, context);
                context.getLogger().log("Sent email to " + adminEmail + " for event: " + eventType);

            } catch (Exception e) {
                context.getLogger().log("Error processing SNS message: " + e.getMessage());
            }
        }

        sesClient.close();
        return null;
    }

    private void sendEmail(String subject, String bodyText, Context context) {
        try {
            SendEmailRequest request = SendEmailRequest.builder()
                    .source(emailSender)
                    .destination(Destination.builder()
                            .toAddresses(adminEmail)
                            .build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .data(subject)
                                    .build())
                            .body(Body.builder()
                                    .text(Content.builder()
                                            .data(bodyText)
                                            .build())
                                    .build())
                            .build())
                    .build();

            sesClient.sendEmail(request);
        } catch (SesException e) {
            context.getLogger().log("Failed to send email: " + e.getMessage());
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }
}