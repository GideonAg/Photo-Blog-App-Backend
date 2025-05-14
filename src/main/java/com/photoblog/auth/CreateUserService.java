package com.photoblog.auth;

import com.photoblog.utils.EmailTemplateLoader;
import com.photoblog.utils.SESUtil;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;

import java.io.IOException;

public class CreateUserService {
    private final CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
            .region(Region.of(System.getenv("PRIMARY_REGION")))
            .build();
    private final CognitoIdentityProviderClient backupCognitoClient = CognitoIdentityProviderClient.builder()
            .region(Region.of(System.getenv("BACKUP_REGION")))
            .build();
    private final String userPoolId = System.getenv("USER_POOL_ID");
    private final String backupUserPoolId = System.getenv("BACKUP_USER_POOL_ID");
    private static final String EMAIL_TEMPLATE_PATH = "welcome.html";
    private String emailHtmlTemplate;

    public CreateUserService() {
        try {
            this.emailHtmlTemplate = EmailTemplateLoader.loadResourceFile(EMAIL_TEMPLATE_PATH);
        } catch (IOException e) {
            this.emailHtmlTemplate = "<h2>Welcome to our web application!</h2><p>Thank you for signing up.</p>";
            System.err.println("Failed to load email template: " + e.getMessage());
        }
    }

    public CreateUserResponse createUser(CreateUserRequest request) {
        var email = request.getEmail();
        var password = request.getPassword();
        var firstName = request.getFirstName();
        var lastName = request.getLastName();

        var createUserRequest = AdminCreateUserRequest.builder()
                .userPoolId(userPoolId)
                .username(email)
                .userAttributes(
                        AttributeType.builder().name("email").value(email).build(),
                        AttributeType.builder().name("email_verified").value("true").build(),
                        AttributeType.builder().name("custom:firstName").value(firstName).build(),
                        AttributeType.builder().name("custom:lastName").value(lastName).build()
                )
                .messageAction(MessageActionType.SUPPRESS)
                .build();
        cognitoClient.adminCreateUser(createUserRequest);

        var setPasswordRequest = AdminSetUserPasswordRequest.builder()
                .userPoolId(userPoolId)
                .username(email)
                .password(password)
                .permanent(true)
                .build();
        cognitoClient.adminSetUserPassword(setPasswordRequest);

        var createBackedUpUserRequest = AdminCreateUserRequest.builder()
                .userPoolId(backupUserPoolId)
                .username(email)
                .userAttributes(
                        AttributeType.builder().name("email").value(email).build(),
                        AttributeType.builder().name("email_verified").value("true").build(),
                        AttributeType.builder().name("custom:firstName").value(firstName).build(),
                        AttributeType.builder().name("custom:lastName").value(lastName).build()
                )
                .messageAction(MessageActionType.SUPPRESS)
                .build();
        backupCognitoClient.adminCreateUser(createBackedUpUserRequest);

        var backupSetPasswordRequest = AdminSetUserPasswordRequest.builder()
                .userPoolId(backupUserPoolId)
                .username(email)
                .password(password)
                .permanent(true)
                .build();
        backupCognitoClient.adminSetUserPassword(backupSetPasswordRequest);

        sendWelcomeEmail(email, firstName);
        return CreateUserResponse.builder()
                .success(true)
                .message("User created successfully")
                .build();
    }

    private void sendWelcomeEmail(String recipientEmail, String firstName) {
        String htmlBody = createWelcomeHtmlTemplate(firstName);
        SESUtil.sendEmail(recipientEmail, "Welcome to Our Service", htmlBody);
    }

    private String createWelcomeHtmlTemplate(String firstName) {
        return emailHtmlTemplate
                .replace("{{fullName}}", firstName)
                .replace("{{currentYear}}", String.valueOf(java.time.Year.now().getValue()));
    }
}
