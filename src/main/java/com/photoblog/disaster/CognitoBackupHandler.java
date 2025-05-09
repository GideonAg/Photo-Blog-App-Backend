package com.photoblog.disaster;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import java.util.HashMap;
import java.util.Map;

public class CognitoBackupHandler implements RequestHandler<Map<String, String>, Map<String, String>> {

    private final CognitoIdentityProviderClient primaryCognitoClient;
    private final CognitoIdentityProviderClient backupCognitoClient;
    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String primaryUserPoolId = System.getenv("PRIMARY_USER_POOL_ID");
    private final String backupUserPoolId = System.getenv("BACKUP_USER_POOL_ID");
    private final String cognitoBackupTable = System.getenv("COGNITO_BACKUP_TABLE");
    private final String primaryRegion = System.getenv("PRIMARY_REGION");
    private final String backupRegion = System.getenv("BACKUP_REGION");

    public CognitoBackupHandler() {
        this.primaryCognitoClient = CognitoIdentityProviderClient.builder()
                .region(software.amazon.awssdk.regions.Region.of(primaryRegion))
                .build();
        this.backupCognitoClient = CognitoIdentityProviderClient.builder()
                .region(software.amazon.awssdk.regions.Region.of(backupRegion))
                .build();
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(software.amazon.awssdk.regions.Region.of(primaryRegion))
                .build();
    }

    @Override
    public Map<String, String> handleRequest(Map<String, String> input, Context context) {
        Map<String, String> response = new HashMap<>();
        try {
            String action = input.getOrDefault("action", "backup");

            if ("backup".equalsIgnoreCase(action)) {
                return handleBackup(context, response);
            } else if ("restore".equalsIgnoreCase(action)) {
                return handleRestore(context, response);
            } else {
                response.put("status", "error");
                response.put("errorMessage", "Invalid action: " + action);
                return response;
            }
        } catch (Exception e) {
            context.getLogger().log("Error in Cognito backup/restore: " + e.getMessage());
            response.put("status", "error");
            response.put("errorMessage", e.getMessage());
            return response;
        } finally {
            primaryCognitoClient.close();
            backupCognitoClient.close();
            dynamoDbClient.close();
        }
    }

    private Map<String, String> handleBackup(Context context, Map<String, String> response) {
        try {
            ListUsersRequest listUsersRequest = ListUsersRequest.builder()
                    .userPoolId(primaryUserPoolId)
                    .build();
            int usersSynced = 0;

            for (UserType user : primaryCognitoClient.listUsers(listUsersRequest).users()) {
                String userId = user.username();
                String email = user.attributes().stream()
                        .filter(attr -> "email".equals(attr.name()))
                        .findFirst()
                        .map(attr -> attr.value())
                        .orElse("");
                String givenName = user.attributes().stream()
                        .filter(attr -> "given_name".equals(attr.name()))
                        .findFirst()
                        .map(attr -> attr.value())
                        .orElse("");
                String familyName = user.attributes().stream()
                        .filter(attr -> "family_name".equals(attr.name()))
                        .findFirst()
                        .map(attr -> attr.value())
                        .orElse("");

                Map<String, AttributeValue> item = new HashMap<>();
                item.put("id", AttributeValue.builder().s(userId).build());
                item.put("email", AttributeValue.builder().s(email).build());
                item.put("given_name", AttributeValue.builder().s(givenName).build());
                item.put("family_name", AttributeValue.builder().s(familyName).build());
                PutItemRequest putItemRequest = PutItemRequest.builder()
                        .tableName(cognitoBackupTable)
                        .item(item)
                        .build();
                dynamoDbClient.putItem(putItemRequest);

                try {
                    AdminCreateUserRequest createUserRequest = AdminCreateUserRequest.builder()
                            .userPoolId(backupUserPoolId)
                            .username(userId)
                            .userAttributes(
                                    software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType.builder()
                                            .name("email").value(email).build(),
                                    software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType.builder()
                                            .name("given_name").value(givenName).build(),
                                    software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType.builder()
                                            .name("family_name").value(familyName).build(),
                                    software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType.builder()
                                            .name("email_verified").value("true").build()
                            )
                            .build();
                    backupCognitoClient.adminCreateUser(createUserRequest);
                    usersSynced++;
                } catch (Exception e) {
                    context.getLogger().log("Skipping duplicate user in BackupUserPool: " + userId + ", error: " + e.getMessage());
                }
            }

            response.put("status", "success");
            response.put("usersSynced", String.valueOf(usersSynced));
            context.getLogger().log("Synced " + usersSynced + " users to CognitoBackupTable and BackupUserPool");
            return response;

        } catch (Exception e) {
            throw new RuntimeException("Backup failed: " + e.getMessage(), e);
        }
    }

    private Map<String, String> handleRestore(Context context, Map<String, String> response) {
        try {
            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName(cognitoBackupTable)
                    .build();
            ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);
            int usersRestored = 0;

            for (Map<String, AttributeValue> item : scanResponse.items()) {
                String userId = item.get("id").s();
                String email = item.get("email").s();
                String givenName = item.get("given_name").s();
                String familyName = item.get("family_name").s();

                try {
                    AdminCreateUserRequest createUserRequest = AdminCreateUserRequest.builder()
                            .userPoolId(backupUserPoolId)
                            .username(userId)
                            .userAttributes(
                                    software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType.builder()
                                            .name("email").value(email).build(),
                                    software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType.builder()
                                            .name("given_name").value(givenName).build(),
                                    software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType.builder()
                                            .name("family_name").value(familyName).build(),
                                    software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType.builder()
                                            .name("email_verified").value("true").build()
                            )
                            .build();
                    backupCognitoClient.adminCreateUser(createUserRequest);

                    AdminSetUserPasswordRequest setPasswordRequest = AdminSetUserPasswordRequest.builder()
                            .userPoolId(backupUserPoolId)
                            .username(userId)
                            .password("TempPassword123!")
                            .permanent(false)
                            .build();
                    backupCognitoClient.adminSetUserPassword(setPasswordRequest);

                    usersRestored++;
                } catch (Exception e) {
                    context.getLogger().log("Skipping duplicate user in BackupUserPool: " + userId + ", error: " + e.getMessage());
                }
            }

            response.put("status", "success");
            response.put("usersRestored", String.valueOf(usersRestored));
            context.getLogger().log("Restored " + usersRestored + " users to BackupUserPool");
            return response;

        } catch (Exception e) {
            throw new RuntimeException("Restore failed: " + e.getMessage(), e);
        }
    }
}
