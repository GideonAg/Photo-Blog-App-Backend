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
}
