package com.photoblog.notifications;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.photoblog.utils.FileLoaderUtil;
import com.photoblog.utils.SESUtil;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class LoginNotificationHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final CognitoIdentityProviderClient cognitoClient;
    private String emailHtmlTemplate;
    private static final String EMAIL_TEMPLATE_PATH = "email_template.html";

    public LoginNotificationHandler() {
        String region = System.getenv("PRIMARY_REGION");
        this.cognitoClient = CognitoIdentityProviderClient.builder().region(Region.of(region)).build();
        // Load HTML template during initialization
        try {
            this.emailHtmlTemplate = FileLoaderUtil.loadResourceFile(EMAIL_TEMPLATE_PATH);
        } catch (IOException e) {
            // If loading fails, fall back to a simple template
            this.emailHtmlTemplate = "<h2>Hello {{name}},</h2><p>New login detected at {{loginTime}}.</p>";
            System.err.println("Failed to load email template: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            context.getLogger().log("Post Authentication event: " + event);

            // Extract information from the event
            @SuppressWarnings("unchecked")
            Map<String, Object> request = (Map<String, Object>) event.get("request");
            @SuppressWarnings("unchecked")
            Map<String, Object> userAttributes = (Map<String, Object>) request.get("userAttributes");
            String username = (String) event.get("userName");
            String userPoolId = (String) event.get("userPoolId");

            String email = null;
            String name = "User";

            if (userAttributes != null && userAttributes.containsKey("email")) {
                email = (String) userAttributes.get("email");
                if (userAttributes.containsKey("name")) {
                    name = (String) userAttributes.get("name");
                }
            }

            // If not found in event, fetch from Cognito
            if (email == null) {
                // Get user's email
                AdminGetUserResponse userResponse = cognitoClient.adminGetUser(
                        AdminGetUserRequest.builder()
                                .username(username)
                                .userPoolId(userPoolId)
                                .build()
                );

                email = userResponse.userAttributes().stream()
                        .filter(attr -> attr.name().equals("email"))
                        .findFirst()
                        .map(AttributeType::value)
                        .orElseThrow(() -> new RuntimeException("User email not found"));

                name = userResponse.userAttributes().stream()
                        .filter(attr -> attr.name().equals("firstName"))
                        .findFirst()
                        .map(AttributeType::value)
                        .orElse("User");
            }

            // Send login notification email
            sendLoginNotificationEmail(email, name);

            context.getLogger().log("Successfully sent login notification email to " + email);
        } catch (Exception e) {
            context.getLogger().log("Error sending login notification: " + e.getMessage());
            e.printStackTrace();
        }

        return event;
    }

    private void sendLoginNotificationEmail(String email, String name) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDateTime = now.format(formatter);

        String subject = "Security Alert: New Login Detected";

        // Replace placeholders in HTML template
        String htmlBody = emailHtmlTemplate
                .replace("{{name}}", name)
                .replace("{{loginTime}}", formattedDateTime);

        SESUtil.sendEmail(email, subject, htmlBody);
    }

}
