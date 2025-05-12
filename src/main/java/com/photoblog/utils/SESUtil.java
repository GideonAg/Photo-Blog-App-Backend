package com.photoblog.utils;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

public class SESUtil {
    private static final String region = System.getenv("PRIMARY_REGION");
    private static final SesClient sesClient = SesClient.builder()
            .region(Region.of(region))
            .build();
    private static final String senderEmail = System.getenv("EMAIL_SENDER");;

    public static void sendEmail(String recipientEmail, String subject, String body) {
        try {
            Destination destination = Destination.builder()
                .toAddresses(recipientEmail)
                .build();

            Content subjectContent = Content.builder()
                .data(subject)
                .charset("UTF-8")
                .build();

            Content htmlBody = Content.builder()
                .data(body)
                .charset("UTF-8")
                .build();

            Content textBody = Content.builder()
                .data(stripHtml(body))
                .charset("UTF-8")
                .build();

            Body emailBody = Body.builder()
                .html(htmlBody)
                .text(textBody)
                .build();

            Message message = Message.builder()
                .subject(subjectContent)
                .body(emailBody)
                .build();

            SendEmailRequest request = SendEmailRequest.builder()
                .source(senderEmail)
                .destination(destination)
                .message(message)
                .build();

            sesClient.sendEmail(request);
        } catch (SesException e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    public void sendLoginNotification(String recipientEmail, String username, String loginTime) {
        String subject = "Photo Blog - Successful Login";
        String body = String.format(
            "<h2>Welcome Back, %s!</h2>" +
            "<p>You have successfully logged into your Photo Blog account.</p>" +
            "<p><strong>Login Time:</strong> %s</p>" +
            "<p>If this wasn't you, please contact our support team immediately.</p>" +
            "<p>Best regards,<br>Photo Blog Team</p>",
            username, loginTime
        );

        sendEmail(recipientEmail, subject, body);
    }

    public void sendProcessingStartedEmail(String recipientEmail, String photoId) {
        String subject = "Photo Blog - Processing Started";
        String body = String.format(
            "<h2>Photo Processing Started</h2>" +
            "<p>Your photo (ID: %s) has started processing.</p>" +
            "<p>You will receive a notification once the processing is complete.</p>" +
            "<p>Best regards,<br>Photo Blog Team</p>",
            photoId
        );

        sendEmail(recipientEmail, subject, body);
    }

    public void sendProcessingCompletedEmail(String recipientEmail, String photoId) {
        String subject = "Photo Blog - Processing Completed";
        String body = String.format(
            "<h2>Photo Processing Completed</h2>" +
            "<p>Your photo (ID: %s) has been successfully processed.</p>" +
            "<p>You can now view it in your Photo Blog gallery.</p>" +
            "<p>Best regards,<br>Photo Blog Team</p>",
            photoId
        );

        sendEmail(recipientEmail, subject, body);
    }

    public void sendProcessingFailedEmail(String recipientEmail, String photoId, String errorMessage) {
        String subject = "Photo Blog - Processing Failed";
        String body = String.format(
            "<h2>Photo Processing Failed</h2>" +
            "<p>We're sorry, but there was an issue processing your photo (ID: %s).</p>" +
            "<p><strong>Error:</strong> %s</p>" +
            "<p>Please try uploading the photo again or contact our support team.</p>" +
            "<p>Best regards,<br>Photo Blog Team</p>",
            photoId, errorMessage
        );

        sendEmail(recipientEmail, subject, body);
    }

    private static String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }
}