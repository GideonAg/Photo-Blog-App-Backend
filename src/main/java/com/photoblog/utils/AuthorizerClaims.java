package com.photoblog.utils;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import java.util.HashMap;
import java.util.Map;

public class AuthorizerClaims {

    public static Map<String, String> extractCognitoClaims(APIGatewayProxyRequestEvent request) {
        if (request == null) {
            throw new IllegalArgumentException("APIGatewayProxyRequestEvent cannot be null");
        }

        if (request.getRequestContext() == null) {
            throw new IllegalStateException("Request context is null, cannot access authorizer");
        }

        Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();
        if (authorizer == null) {
            throw new IllegalStateException("User is not authenticated, authorizer is null");
        }

        // Try "claims" first, then "jwt.claims"
        @SuppressWarnings("unchecked")
        Map<String, Object> authorizerClaims = (Map<String, Object>) authorizer.get("claims");
        if (authorizerClaims == null) {
            authorizerClaims = (Map<String, Object>) authorizer.get("jwt.claims");
        }

        Map<String, String> claimsMap = new HashMap<>();
        if (authorizerClaims != null) {
            // Extract sub as userId (required)
            String userId = (String) authorizerClaims.get("sub");
            if (userId != null) {
                claimsMap.put("userId", userId);
            }

            // Extract email (required)
            String email = (String) authorizerClaims.get("email");
            if (email == null) {
                throw new IllegalStateException("Required 'email' claim is missing");
            }
            claimsMap.put("email", email);

            // Extract custom:firstName (optional)
            String firstName = (String) authorizerClaims.get("custom:firstName");
            if (firstName != null) {
                claimsMap.put("firstName", firstName);
            }

            // Extract custom:lastName (optional)
            String lastName = (String) authorizerClaims.get("custom:lastName");
            if (lastName != null) {
                claimsMap.put("lastName", lastName);
            }
        }

        return claimsMap;
    }
}