package com.photoblog.photos;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.photoblog.models.Photo;
import com.photoblog.utils.AuthorizerClaims;
import com.photoblog.utils.DynamoDBUtil;
import com.photoblog.utils.HeadersUtil;
import com.photoblog.utils.S3Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SetEnvironmentVariable(key = "PRIMARY_REGION", value = "us-east-1")
@SetEnvironmentVariable(key = "APP_NAME", value = "dev")
@SetEnvironmentVariable(key = "STAGE", value = "dev")
@SetEnvironmentVariable(key = "AWS_ACCOUNT_ID", value = "123456789123")
@SetEnvironmentVariable(key = "PHOTOS_TABLE", value = "TestPhotosTable")
@SetEnvironmentVariable(key = "AWS_REGION", value = "us-east-1")
public class PhotoShareHandlerTest {

    private S3Util mockS3Util;
    private PhotoShareHandler handler;
    private Context mockContext;
    protected S3Util s3Util;

    @BeforeEach
    void setup() {
        mockS3Util = mock(S3Util.class);
        handler = new PhotoShareHandler() {
            {
                this.s3Util = mockS3Util;
            }
        };
        mockContext = mock(Context.class);
    }

    @Test
    void testSuccessfulPhotoShare() throws Exception {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("photoId", "photo123");
        request.setPathParameters(pathParams);

        try (MockedStatic<AuthorizerClaims> mockAuth = Mockito.mockStatic(AuthorizerClaims.class);
             MockedStatic<DynamoDBUtil> mockDynamo = Mockito.mockStatic(DynamoDBUtil.class);
             MockedStatic<HeadersUtil> mockHeaders = Mockito.mockStatic(HeadersUtil.class)) {

            Map<String, String> claims = Map.of("sub", "user123");
            mockAuth.when(() -> AuthorizerClaims.extractCognitoClaims(request)).thenReturn(claims);

            Photo dummyPhoto = new Photo();
            mockDynamo.when(() -> DynamoDBUtil.getPhotoById("user123", "photo123")).thenReturn(dummyPhoto);

            mockHeaders.when(HeadersUtil::getHeaders).thenReturn(Map.of("Content-Type", "application/json"));

            when(mockS3Util.getImage("user123", "photo123")).thenReturn("https://mocked.link/photo.jpg");

            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);

            assertEquals(200, response.getStatusCode());
            assertTrue(response.getBody().contains("Share link generated successfully"));
        }
    }

    @Test
    void testWhenPhotoIsNull() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("photoId", "photo123");
        request.setPathParameters(pathParams);

        try (MockedStatic<AuthorizerClaims> mockAuth = Mockito.mockStatic(AuthorizerClaims.class);
             MockedStatic<DynamoDBUtil> mockDynamo = Mockito.mockStatic(DynamoDBUtil.class);
             MockedStatic<HeadersUtil> mockHeaders = Mockito.mockStatic(HeadersUtil.class)) {

            Map<String, String> claims = Map.of("sub", "user123");
            mockAuth.when(() -> AuthorizerClaims.extractCognitoClaims(request)).thenReturn(claims);

            mockDynamo.when(() -> DynamoDBUtil.getPhotoById("user123", "photo123")).thenReturn(null);
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);
            assertEquals(500, response.getStatusCode());
            assertTrue(response.getBody().contains("Failed to fetch link. Not authorized"));

        }
    }

    @Test
    void testWhenRuntimeExceptionOccurs() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("photoId", "photo123");
        request.setPathParameters(pathParams);

        try (MockedStatic<AuthorizerClaims> mockAuth = Mockito.mockStatic(AuthorizerClaims.class);
             MockedStatic<DynamoDBUtil> mockDynamo = Mockito.mockStatic(DynamoDBUtil.class);
             MockedStatic<HeadersUtil> mockHeaders = Mockito.mockStatic(HeadersUtil.class)) {
            Map<String, String> claims = Map.of("sub", "user123");
            mockAuth.when(() -> AuthorizerClaims.extractCognitoClaims(request)).thenReturn(claims);

            Photo dummyPhoto = new Photo();
            mockDynamo.when(() -> DynamoDBUtil.getPhotoById("user123", "photo123")).thenReturn(dummyPhoto);

            mockHeaders.when(HeadersUtil::getHeaders).thenReturn(Map.of("Content-Type", "application/json"));

            when(mockS3Util.getImage("user123", "photo123")).thenThrow(new RuntimeException());
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);
            assertEquals(500, response.getStatusCode());
            assertTrue(response.getBody().contains("There was an error while generating the link"));
        }
    }


    @Test
    void testWhenExceptionOccurs() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("photoId", "photo123");
        request.setPathParameters(pathParams);

        try (MockedStatic<AuthorizerClaims> mockAuth = Mockito.mockStatic(AuthorizerClaims.class);
             MockedStatic<DynamoDBUtil> mockDynamo = Mockito.mockStatic(DynamoDBUtil.class);
             MockedStatic<HeadersUtil> mockHeaders = Mockito.mockStatic(HeadersUtil.class)) {
            Map<String, String> claims = Map.of("sub", "user123");
            mockAuth.when(() -> AuthorizerClaims.extractCognitoClaims(request)).thenReturn(claims);

            mockDynamo.when(() -> DynamoDBUtil.getPhotoById("user123", "photo123")).thenThrow(new Exception());

            mockHeaders.when(HeadersUtil::getHeaders).thenReturn(Map.of("Content-Type", "application/json"));
            APIGatewayProxyResponseEvent response = handler.handleRequest(request, mockContext);
            assertEquals(500, response.getStatusCode());
            assertTrue(response.getBody().contains("Internal server error"));
        }
    }
}
