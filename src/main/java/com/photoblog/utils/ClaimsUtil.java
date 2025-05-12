package com.photoblog.utils;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;

import java.util.HashMap;
import java.util.Map;

public class ClaimsUtil {
    public Map<String, String> getClaims(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> claims = new HashMap<>();
        try {
            if (input.getRequestContext() != null &&
                    input.getRequestContext().getAuthorizer() != null &&
                    input.getRequestContext().getAuthorizer().get("claims") != null) {
                @SuppressWarnings("unchecked")
                Map<String, String> authClaims = (Map<String, String>) input.getRequestContext().getAuthorizer().get("claims");
                claims.putAll(authClaims);
                context.getLogger().log("Found claims in request "+ claims.size());
            } else {
                context.getLogger().log("No claims found in request context");
            }
        } catch (Exception e) {
            context.getLogger().log("Error extracting claims from request "+ e);
        }
        return claims;
    }
}
