package com.photoblog.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreateUserHandlerTest {

    private CreateUserService mockService;
    private CreateUserHandler handler;
    private final ObjectMapper mapper = new ObjectMapper();
    private Context mockContext;
    private LambdaLogger mockLogger;

    @BeforeEach
    void setUp() {
        mockService = mock(CreateUserService.class);
        handler = new CreateUserHandler(mockService);

        mockContext = mock(Context.class);
        mockLogger = mock(LambdaLogger.class);
        when(mockContext.getLogger()).thenReturn(mockLogger);
    }

    @Test
    void testOptionsRequestReturns200() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("OPTIONS");

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mockContext);

        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getHeaders());
        assertNull(response.getBody());
        verify(mockLogger).log(contains("Inside options block"));
    }

    @Test
    void testInvalidJsonReturns400() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody("{invalid json}");

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mockContext);

        assertEquals(400, response.getStatusCode());
        assertNotNull(response.getHeaders());
        assertNotNull(response.getBody());

        assertTrue(response.getBody().contains("Error during registration"));
    }

    @Test
    void testServiceThrowsExceptionReturns400() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("test@example.com");
        req.setPassword("Password1!");
        req.setFirstName("John");
        req.setLastName("Doe");

        when(mockService.createUser(any(CreateUserRequest.class)))
                .thenThrow(new RuntimeException("Service error"));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody(mapper.writeValueAsString(req));

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mockContext);

        assertEquals(400, response.getStatusCode());
        assertNotNull(response.getHeaders());
        assertNotNull(response.getBody());

        assertTrue(response.getBody().contains("Error during registration: Service error"));
        verify(mockLogger).log(contains("CATCH BLOCK"));
    }

    @Test
    void testPasswordTooShortReturns400() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("test@example.com");
        req.setPassword("Short1!");
        req.setFirstName("John");
        req.setLastName("Doe");

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody(mapper.writeValueAsString(req));

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mockContext);

        assertEquals(400, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Password must be at least 8 characters long"));
    }

    @Test
    void testPasswordMissingUppercaseReturns400() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("test@example.com");
        req.setPassword("password1!");
        req.setFirstName("John");
        req.setLastName("Doe");

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody(mapper.writeValueAsString(req));

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mockContext);

        assertEquals(400, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Password must contain at least one uppercase letter"));
    }

    @Test
    void testPasswordMissingLowercaseReturns400() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("test@example.com");
        req.setPassword("PASSWORD1!");
        req.setFirstName("John");
        req.setLastName("Doe");

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody(mapper.writeValueAsString(req));

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mockContext);

        assertEquals(400, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Password must contain at least one lowercase letter"));
    }

    @Test
    void testPasswordMissingDigitReturns400() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("test@example.com");
        req.setPassword("Password!");
        req.setFirstName("John");
        req.setLastName("Doe");

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody(mapper.writeValueAsString(req));

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mockContext);

        assertEquals(400, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Password must contain at least one digit"));
    }

    @Test
    void testPasswordMissingSpecialCharReturns400() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("test@example.com");
        req.setPassword("Password1");
        req.setFirstName("John");
        req.setLastName("Doe");

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody(mapper.writeValueAsString(req));

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mockContext);

        assertEquals(400, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Password must contain at least one special character"));
    }

    @Test
    void testValidPasswordSucceeds() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("test@example.com");
        req.setPassword("Password1!");
        req.setFirstName("John");
        req.setLastName("Doe");

        CreateUserResponse mockResponse = CreateUserResponse.builder()
                .success(true)
                .message("User created successfully")
                .build();

        when(mockService.createUser(any(CreateUserRequest.class))).thenReturn(mockResponse);

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody(mapper.writeValueAsString(req));

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mockContext);

        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("User created successfully"));
    }
}