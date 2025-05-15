package com.photoblog.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthenticationHandlerTest {

    private AuthService mockAuthService;
    private AuthenticationHandler handler;
    private final ObjectMapper mapper = new ObjectMapper();
    private Context mockContext;

    @BeforeEach
    void setUp() {
        mockAuthService = mock(AuthService.class);
        handler = new AuthenticationHandler(mockAuthService);

        mockContext = mock(Context.class);
        LambdaLogger mockLogger = mock(LambdaLogger.class);
        when(mockContext.getLogger()).thenReturn(mockLogger);
    }

    @Test
    void testSuccessfulAuthentication() throws Exception {
        SignInRequest signInRequest = new SignInRequest("test@example.com", "Password123!");

        Map<String, String> authResponse = new HashMap<>();
        authResponse.put("message", "Login successful");
        authResponse.put("idToken", "sample-jwt-token");

        when(mockAuthService.login(anyString(), anyString())).thenReturn(authResponse);

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody(mapper.writeValueAsString(signInRequest));

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mockContext);

        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getHeaders());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        assertEquals("*", response.getHeaders().get("Access-Control-Allow-Origin"));

        SignInResponse signInResponse = mapper.readValue(response.getBody(), SignInResponse.class);
        assertTrue(signInResponse.isSuccess());
        assertEquals("Login successful", signInResponse.getMessage());
        assertEquals("sample-jwt-token", signInResponse.getIdToken());

        verify(mockAuthService).login("test@example.com", "Password123!");
    }

    @Test
    void testFailedAuthentication() throws Exception {
        SignInRequest signInRequest = new SignInRequest("test@example.com", "WrongPassword123!");

        when(mockAuthService.login(anyString(), anyString()))
                .thenThrow(new RuntimeException("Invalid credentials"));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody(mapper.writeValueAsString(signInRequest));

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mockContext);

        assertEquals(401, response.getStatusCode());
        assertNotNull(response.getHeaders());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        assertEquals("*", response.getHeaders().get("Access-Control-Allow-Origin"));

        SignInResponse signInResponse = mapper.readValue(response.getBody(), SignInResponse.class);
        assertFalse(signInResponse.isSuccess());
        assertEquals("Authentication failed: Invalid credentials", signInResponse.getMessage());
        assertNull(signInResponse.getIdToken());

        verify(mockAuthService).login("test@example.com", "WrongPassword123!");
    }

    @Test
    void testInvalidJsonReturns401() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody("{invalid json}");

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mockContext);

        assertEquals(401, response.getStatusCode());
        assertNotNull(response.getHeaders());
        assertNotNull(response.getBody());

        try {
            SignInResponse signInResponse = mapper.readValue(response.getBody(), SignInResponse.class);
            assertFalse(signInResponse.isSuccess());
            assertTrue(signInResponse.getMessage().contains("Authentication failed"));
        } catch (Exception e) {
            fail("Failed to parse response body: " + e.getMessage());
        }
    }

    @Test
    void testNullEmailOrPasswordReturns401() throws Exception {
        SignInRequest signInRequest = new SignInRequest(null, "Password123!");

        when(mockAuthService.login(isNull(), anyString()))
                .thenThrow(new IllegalArgumentException("Email cannot be null"));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody(mapper.writeValueAsString(signInRequest));

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mockContext);

        assertEquals(401, response.getStatusCode());

        SignInResponse signInResponse = mapper.readValue(response.getBody(), SignInResponse.class);
        assertFalse(signInResponse.isSuccess());
        assertEquals("Authentication failed: Email cannot be null", signInResponse.getMessage());
    }
}
