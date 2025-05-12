package com.photoblog.utils;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public class AuthorizerClaims {

/**
    Extracts Cognito claims from an APIGatewayProxyRequestEvent, returning a map with user ID, email,
    first name, and last name. Email is required; others are optional.
    @param request the APIGatewayProxyRequestEvent with authorizer data
    @return a Map of Cognito claims ("userId", "email", "firstName", "lastName")
    @throws IllegalArgumentException if request is null
    @throws IllegalStateException if context/authorizer is null or email claim is missing 
*/
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
                
        Map<String, Object> authorizerClaims = (Map<String, Object>) authorizer.get("claims");
        if (authorizerClaims == null) {
            authorizerClaims = (Map<String, Object>) authorizer.get("jwt.claims");
        }
        
        Map<String, String> claimsMap = new HashMap<>();
        if (authorizerClaims != null) {
            String userId = (String) authorizerClaims.get("sub");
            if (userId != null) {
                claimsMap.put("userId", userId);
            }            
            String email = (String) authorizerClaims.get("email");
            if (email == null) {
                throw new IllegalStateException("Required 'email' claim is missing");
            }
            claimsMap.put("email", email);

            String firstName = (String) authorizerClaims.get("custom:firstName");
            if (firstName != null) {
                claimsMap.put("firstName", firstName);
            }            
            String lastName = (String) authorizerClaims.get("custom:lastName");
            if (lastName != null) {
                claimsMap.put("lastName", lastName);
            }
        }
        
        return claimsMap;
    }
}