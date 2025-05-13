package com.photoblog.utils;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent.ProxyRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizerClaimsTest {

    @Mock
    private APIGatewayProxyRequestEvent request;

    @Mock
    private ProxyRequestContext requestContext;

    private Map<String, Object> authorizer;
    private Map<String, Object> claims;

    @BeforeEach
    void setUp() {
        authorizer = new HashMap<>();
        claims = new HashMap<>();
    }

    @Test
    void shouldExtractAllClaimsWhenPresent() {
        // Arrange
        claims.put("sub", "f394c852-70d1-70e7-941e-ba6b19a3444e");
        claims.put("email", "kwasi.baidoo@amalitech.com");
        claims.put("custom:firstName", "Kwasi");
        claims.put("custom:lastName", "Sakyi");
        authorizer.put("claims", claims);
        when(request.getRequestContext()).thenReturn(requestContext);
        when(requestContext.getAuthorizer()).thenReturn(authorizer);

        Map<String, String> result = AuthorizerClaims.extractCognitoClaims(request);

        assertEquals(4, result.size());
        assertEquals("f394c852-70d1-70e7-941e-ba6b19a3444e", result.get("userId"));
        assertEquals("kwasi.baidoo@amalitech.com", result.get("email"));
        assertEquals("Kwasi", result.get("firstName"));
        assertEquals("Sakyi", result.get("lastName"));
    }

    @Test
    void shouldExtractClaimsFromJwtClaimsWhenClaimsIsNull() {
        // Arrange
        claims.put("sub", "f394c852-70d1-70e7-941e-ba6b19a3444e");
        claims.put("email", "kwasi.baidoo@amalitech.com");
        claims.put("custom:firstName", "Kwasi");
        claims.put("custom:lastName", "Sakyi");
        authorizer.put("jwt.claims", claims);
        when(request.getRequestContext()).thenReturn(requestContext);
        when(requestContext.getAuthorizer()).thenReturn(authorizer);

        
        Map<String, String> result = AuthorizerClaims.extractCognitoClaims(request);

        assertEquals(4, result.size());
        assertEquals("f394c852-70d1-70e7-941e-ba6b19a3444e", result.get("userId"));
        assertEquals("kwasi.baidoo@amalitech.com", result.get("email"));
        assertEquals("Kwasi", result.get("firstName"));
        assertEquals("Sakyi", result.get("lastName"));
    }

    @Test
    void shouldOmitOptionalClaimsWhenMissing() {
        claims.put("sub", "f394c852-70d1-70e7-941e-ba6b19a3444e");
        claims.put("email", "kwasi.baidoo@amalitech.com");
        authorizer.put("claims", claims);
        when(request.getRequestContext()).thenReturn(requestContext);
        when(requestContext.getAuthorizer()).thenReturn(authorizer);
        Map<String, String> result = AuthorizerClaims.extractCognitoClaims(request);
        assertEquals(2, result.size());
        assertEquals("f394c852-70d1-70e7-941e-ba6b19a3444e", result.get("userId"));
        assertEquals("kwasi.baidoo@amalitech.com", result.get("email"));
        assertNull(result.get("firstName"));
        assertNull(result.get("lastName"));
    }

    @Test
    void shouldOmitUserIdWhenSubIsMissing() {
        claims.put("email", "kwasi.baidoo@amalitech.com");
        claims.put("custom:firstName", "Kwasi");
        claims.put("custom:lastName", "Sakyi");
        authorizer.put("claims", claims);
        when(request.getRequestContext()).thenReturn(requestContext);
        when(requestContext.getAuthorizer()).thenReturn(authorizer);
        Map<String, String> result = AuthorizerClaims.extractCognitoClaims(request);
        assertEquals(3, result.size());
        assertNull(result.get("userId"));
        assertEquals("kwasi.baidoo@amalitech.com", result.get("email"));
        assertEquals("Kwasi", result.get("firstName"));
        assertEquals("Sakyi", result.get("lastName"));
    }

    @Test
    void shouldThrowExceptionWhenEmailIsMissing() {
        claims.put("sub", "f394c852-70d1-70e7-941e-ba6b19a3444e");
        claims.put("custom:firstName", "Kwasi");
        claims.put("custom:lastName", "Sakyi");
        authorizer.put("claims", claims);
        when(request.getRequestContext()).thenReturn(requestContext);
        when(requestContext.getAuthorizer()).thenReturn(authorizer);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            AuthorizerClaims.extractCognitoClaims(request));
        assertEquals("Required 'email' claim is missing", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenRequestIsNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            AuthorizerClaims.extractCognitoClaims(null));
        assertEquals("APIGatewayProxyRequestEvent cannot be null", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenRequestContextIsNull() {
        when(request.getRequestContext()).thenReturn(null);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            AuthorizerClaims.extractCognitoClaims(request));
        assertEquals("Request context is null, cannot access authorizer", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenAuthorizerIsNull() {
        when(request.getRequestContext()).thenReturn(requestContext);
        when(requestContext.getAuthorizer()).thenReturn(null);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            AuthorizerClaims.extractCognitoClaims(request));
        assertEquals("User is not authenticated, authorizer is null", exception.getMessage());
    }

    @Test
    void shouldReturnEmptyMapWhenClaimsIsNull() {
        when(request.getRequestContext()).thenReturn(requestContext);
        when(requestContext.getAuthorizer()).thenReturn(authorizer);

        Map<String, String> result = AuthorizerClaims.extractCognitoClaims(request);

        assertTrue(result.isEmpty());
    }
}