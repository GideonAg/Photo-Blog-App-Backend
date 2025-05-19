package com.photoblog.notifications;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.util.Map;

public class BackupAlertHandler implements RequestHandler<SNSEvent, Void> {

    private static SesClient sesClient;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String emailSender = System.getenv("EMAIL_SENDER");
    private final String adminEmail = System.getenv("ADMIN_EMAIL");
    private final String primaryRegion = System.getenv("PRIMARY_REGION");
    private final String backupRegion = System.getenv("BACKUP_REGION");

    private synchronized SesClient getSesClient() {
        if (sesClient == null) {
            sesClient = SesClient.builder().build();
        }
        return sesClient;
    }

    @Override
    public Void handleRequest(SNSEvent event, Context context) {
        if (emailSender == null || adminEmail == null) {
            context.getLogger().log("Error: EMAIL_SENDER or ADMIN_EMAIL environment variables are not set");
            return null;
        }

        for (SNSEvent.SNSRecord record : event.getRecords()) {
            try {
                processRecord(record, context);
            } catch (Exception e) {
                context.getLogger().log("Error processing SNS message: " + e.getMessage());
            }
        }

        return null;
    }

    private void processRecord(SNSEvent.SNSRecord record, Context context) throws Exception {
        String message = record.getSNS().getMessage();
        context.getLogger().log("Received SNS message: " + message);

        @SuppressWarnings("unchecked")
        Map<String, String> alertMessage = objectMapper.readValue(message, Map.class);
        String eventType = alertMessage.get("event");

        EmailContent emailContent = buildEmailContent(eventType, alertMessage, context);
        sendEmail(emailContent.subject, emailContent.body, context);

        context.getLogger().log("Successfully sent email to " + adminEmail + " for event: " + eventType);
    }

    private EmailContent buildEmailContent(String eventType, Map<String, String> alertMessage, Context context) {
        String subject;
        String body;

        return switch (eventType) {
            case "region_monitor_check" -> buildRegionMonitorEmail(alertMessage);
            case "disaster_recovery_triggered" -> buildDisasterRecoveryEmail(alertMessage);
            case "region_monitor_error" -> buildErrorEmail(alertMessage);
            default -> {
                context.getLogger().log("Unknown event type: " + eventType);
                yield new EmailContent(
                        "[Photo Blog] Unknown System Alert",
                        String.format("Unknown event type received: %s\n\nMessage: %s\n\nPlease investigate.",
                                eventType, alertMessage.toString())
                );
            }
        };
    }

    private EmailContent buildRegionMonitorEmail(Map<String, String> alertMessage) {
        boolean isHealthy = Boolean.parseBoolean(alertMessage.get("healthy"));
        boolean isFrontendHealthy = Boolean.parseBoolean(alertMessage.get("frontendHealthy"));
        boolean isBackendHealthy = Boolean.parseBoolean(alertMessage.get("backendHealthy"));
        String consecutiveFailures = alertMessage.getOrDefault("consecutiveFailures", "0");

        String subject = isHealthy ?
                "[Photo Blog] Primary Region Health Check - Healthy" :
                "[Photo Blog] Primary Region Health Check - Unhealthy";

        String body = String.format(
                "Primary Region Health Status Report\n" +
                        "====================================\n\n" +
                        "Timestamp: %s\n" +
                        "Primary Region: %s\n" +
                        "Backup Region: %s\n\n" +
                        "Overall Status: %s\n" +
                        "Frontend Healthy: %s\n" +
                        "Backend Healthy: %s\n" +
                        "Consecutive Failures: %s\n\n" +
                        "%s",
                alertMessage.get("timestamp"),
                primaryRegion,
                backupRegion,
                isHealthy ? "HEALTHY" : "UNHEALTHY",
                isFrontendHealthy,
                isBackendHealthy,
                consecutiveFailures,
                isHealthy ?
                        "All systems are operating normally." :
                        "⚠️ ATTENTION REQUIRED: Please monitor the situation closely. If this persists, manual intervention may be necessary."
        );

        return new EmailContent(subject, body);
    }

    private EmailContent buildDisasterRecoveryEmail(Map<String, String> alertMessage) {
        String subject = "[Photo Blog] 🚨 DISASTER RECOVERY TRIGGERED 🚨";

        String body = String.format(
                "DISASTER RECOVERY ACTIVATED\n" +
                        "===========================\n\n" +
                        "⚠️ CRITICAL: Disaster recovery has been automatically triggered due to primary region failure.\n\n" +
                        "Timestamp: %s\n" +
                        "Primary Region: %s (FAILED)\n" +
                        "Backup Region: %s (ACTIVATING)\n\n" +
                        "Details: %s\n\n" +
                        "IMMEDIATE ACTIONS REQUIRED:\n" +
                        "1. Verify backup region services are starting up\n" +
                        "2. Check DNS/traffic routing to backup region\n" +
                        "3. Monitor backup region performance\n" +
                        "4. Prepare for potential manual interventions\n" +
                        "5. Begin investigating primary region issues\n\n" +
                        "This is an automated alert. Please respond immediately.",
                alertMessage.get("timestamp"),
                primaryRegion,
                backupRegion,
                alertMessage.get("details")
        );

        return new EmailContent(subject, body);
    }

    private EmailContent buildErrorEmail(Map<String, String> alertMessage) {
        String subject = "[Photo Blog] Region Monitor Error";

        String body = String.format(
                "Region Monitor Error Report\n" +
                        "==========================\n\n" +
                        "An error occurred while monitoring the primary region.\n\n" +
                        "Timestamp: %s\n" +
                        "Primary Region: %s\n" +
                        "Backup Region: %s\n\n" +
                        "Error Details: %s\n\n" +
                        "INVESTIGATION REQUIRED:\n" +
                        "1. Check region monitor Lambda function logs\n" +
                        "2. Verify CloudWatch alarm configurations\n" +
                        "3. Ensure proper IAM permissions\n" +
                        "4. Check network connectivity\n\n" +
                        "Note: The monitoring system may be unable to detect actual outages while this error persists.",
                alertMessage.get("timestamp"),
                primaryRegion,
                backupRegion,
                alertMessage.get("error")
        );

        return new EmailContent(subject, body);
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
                                    .charset("UTF-8")
                                    .build())
                            .body(Body.builder()
                                    .text(Content.builder()
                                            .data(bodyText)
                                            .charset("UTF-8")
                                            .build())
                                    .build())
                            .build())
                    .build();

            SendEmailResponse response = getSesClient().sendEmail(request);
            context.getLogger().log("Email sent successfully. MessageId: " + response.messageId());

        } catch (SesException e) {
            context.getLogger().log("SES Error: " + e.getMessage());
            context.getLogger().log("Error Code: " + e.awsErrorDetails().errorCode());
            throw new RuntimeException("Failed to send email via SES: " + e.getMessage(), e);
        } catch (Exception e) {
            context.getLogger().log("Unexpected error sending email: " + e.getMessage());
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    private static class EmailContent {
        final String subject;
        final String body;

        EmailContent(String subject, String body) {
            this.subject = subject;
            this.body = body;
        }
    }
}