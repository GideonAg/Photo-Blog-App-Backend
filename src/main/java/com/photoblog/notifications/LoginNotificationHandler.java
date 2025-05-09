package com.photoblog.notifications;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.photoblog.utils.EmailTemplateLoader;
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
        try {
            this.emailHtmlTemplate = EmailTemplateLoader.loadResourceFile(EMAIL_TEMPLATE_PATH);
        } catch (IOException e) {
            this.emailHtmlTemplate = "<h2>Hello {{name}},</h2><p>New login detected at {{loginTime}}.</p>";
            System.err.println("Failed to load email template: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            context.getLogger().log("Post Authentication event: " + event);

            @SuppressWarnings("unchecked")
            Map<String, Object> request = (Map<String, Object>) event.get("request");
            @SuppressWarnings("unchecked")
            Map<String, Object> userAttributes = (Map<String, Object>) request.get("userAttributes");
            String username = (String) event.get("userName");
            String userPoolId = (String) event.get("userPoolId");

            String email = null;
            String name = "User";

            if (userAttributes != null) {
                if (userAttributes.containsKey("email"))
                    email = (String) userAttributes.get("email");


                if (userAttributes.containsKey("custom:firstName")) {
                    name = (String) userAttributes.get("custom:firstName");
                    context.getLogger().log("Found firstName in event: " + name);
                }
            }

            if (email == null) {
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

                if ("User".equals(name)) {
                    name = userResponse.userAttributes().stream()
                            .filter(attr -> attr.name().equals("custom:firstName"))
                            .findFirst()
                            .map(AttributeType::value)
                            .orElse("User");
                }
            }

            sendLoginNotificationEmail(email, name);

            context.getLogger().log("Successfully sent login notification email to " + email + " with name: " + name);
        } catch (Exception e) {
            context.getLogger().log("Error sending login notification: " + e.getMessage());
        }

        return event;
    }

    private void sendLoginNotificationEmail(String email, String name) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDateTime = now.format(formatter);

        String subject = "Security Alert: New Login Detected";

        String htmlBody = emailHtmlTemplate
                .replace("{{name}}", name)
                .replace("{{loginTime}}", formattedDateTime)
                .replace("{{currentYear}}", String.valueOf(java.time.Year.now().getValue()));

        SESUtil.sendEmail(email, subject, htmlBody);
    }
}