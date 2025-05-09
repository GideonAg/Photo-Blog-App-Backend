package com.photoblog.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.crac.Core;
import org.crac.Resource;

import java.util.HashMap;
import java.util.Map;

public class AuthenticationHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>, Resource {
    private final ObjectMapper mapper;
    private AuthService authService;

    public AuthenticationHandler() {
        Core.getGlobalContext().register(this);
        mapper = new ObjectMapper();
        initializeResources();
    }

    private void initializeResources() {
        authService = new AuthService();

    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            SignInRequest request = mapper.readValue(input.getBody(), SignInRequest.class);


            var authResponse = authService.login(request.email(), request.password());
            // Create response
            SignInResponse response = SignInResponse.builder()
                    .success(true)
                    .message(authResponse.get("message"))
                    .idToken(authResponse.get("idToken"))
                    .build();

            // Return API Gateway response
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Access-Control-Allow-Origin", "*");

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(mapper.writeValueAsString(response));

        } catch (Exception e) {
            // Handle errors
            SignInResponse response = SignInResponse.builder()
                    .success(false)
                    .message("Authentication failed: " + e.getMessage())
                    .build();

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Access-Control-Allow-Origin", "*");

            try {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(401)
                        .withHeaders(headers)
                        .withBody(mapper.writeValueAsString(response));
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public void beforeCheckpoint(org.crac.Context<? extends Resource> context) throws Exception {

    }

    @Override
    public void afterRestore(org.crac.Context<? extends Resource> context) throws Exception {
        initializeResources();
    }
}
