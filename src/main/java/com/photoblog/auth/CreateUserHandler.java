package com.photoblog.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoblog.utils.HeadersUtil;
import org.crac.Core;
import org.crac.Resource;

import java.util.Map;

public class CreateUserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>, Resource {
    private final ObjectMapper mapper;
    private CreateUserService service;
    private final Map<String, String> headers = HeadersUtil.getHeaders();


    public CreateUserHandler() {
        Core.getGlobalContext().register(this);
        mapper = new ObjectMapper();
        initializeResources();

    }

    public CreateUserHandler(CreateUserService service) {
        Core.getGlobalContext().register(this);
        mapper = new ObjectMapper();
        this.service = service;
    }


    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("GOT HERE");
        try {
            if("OPTIONS".equalsIgnoreCase(input.getHttpMethod())) {
                context.getLogger().log("Inside options block");
                return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers);
            }
            CreateUserRequest request = mapper.readValue(input.getBody(), CreateUserRequest.class);
            validatePassword(request.getPassword());

            var response = service.createUser(request);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(mapper.writeValueAsString(response));

        } catch (Exception e) {
            context.getLogger().log("GOT HERE: CATCH BLOCK");
            var response = CreateUserResponse.builder()
                    .success(false)
                    .message("Error during registration: " + e.getMessage())
                    .build();

            try {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
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

    private void initializeResources() {
        if (service == null)
            service = new CreateUserService();
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }

        boolean hasUpperCase = false;
        boolean hasLowerCase = false;
        boolean hasDigit = false;
        boolean hasSpecialChar = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpperCase = true;
            } else if (Character.isLowerCase(c)) {
                hasLowerCase = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSpecialChar = true;
            }

            if (hasUpperCase && hasLowerCase && hasDigit && hasSpecialChar) {
                return;
            }
        }

        var errorMessage = buildErrorMessage(hasUpperCase, hasLowerCase, hasDigit, hasSpecialChar);

        throw new IllegalArgumentException(errorMessage.trim());
    }

    private String buildErrorMessage(boolean hasUpperCase, boolean hasLowerCase, boolean hasDigit, boolean hasSpecialChar) {
        StringBuilder errorMessage = new StringBuilder();
        if (!hasUpperCase) {
            errorMessage.append("Password must contain at least one uppercase letter. ");
        }
        if (!hasLowerCase) {
            errorMessage.append("Password must contain at least one lowercase letter. ");
        }
        if (!hasDigit) {
            errorMessage.append("Password must contain at least one digit. ");
        }
        if (!hasSpecialChar) {
            errorMessage.append("Password must contain at least one special character. ");
        }
        return errorMessage.toString();
    }
}
