package com.photoblog.auth;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;

import java.util.Map;

import static software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType.USER_PASSWORD_AUTH;

public class AuthService {
    private static final CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
            .region(Region.of(System.getenv("PRIMARY_REGION")))
            .build();
    private static final String CLIENT_ID = System.getenv("USER_POOL_CLIENT_ID");

    public Map<String, String> login(String email, String password) {

        var authParams = Map.of(
                "USERNAME", email,
                "PASSWORD", password
        );

        var authRequest = InitiateAuthRequest.builder()
                .clientId(CLIENT_ID)
                .authFlow(USER_PASSWORD_AUTH)
                .authParameters(authParams)
                .build();

        long start = System.currentTimeMillis();
        var authResponse = cognitoClient.initiateAuth(authRequest);
        long end = System.currentTimeMillis();
        System.out.println("Cognito initiateAuth took: " + (end - start) + "ms");


        return Map.of(
                "message", "Login successful",
                "idToken", authResponse.authenticationResult().idToken()
        );
    }
}