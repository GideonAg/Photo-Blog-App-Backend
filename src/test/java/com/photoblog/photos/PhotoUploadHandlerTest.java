package com.photoblog.photos;
// import com.amazonaws.services.lambda.runtime.Context;
// import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
// import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
// import com.fasterxml.jackson.core.JsonProcessingException;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.photoblog.utils.AuthorizerClaims;
// import com.photoblog.utils.QueueUtil;
// import com.photoblog.utils.UploadUtil;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import com.amazonaws.services.lambda.runtime.LambdaLogger;
// import org.mockito.Mock;
// import org.mockito.MockedStatic;
// import org.mockito.Mockito;
// import org.mockito.junit.jupiter.MockitoExtension;
// import software.amazon.awssdk.regions.Region;
// import software.amazon.awssdk.services.s3.S3Client;
// import software.amazon.awssdk.services.s3.model.PutObjectResponse;
// import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
// import static org.mockito.Mockito.lenient;
// import java.lang.reflect.Field;
// import java.time.LocalDateTime;
// import java.util.*;

// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.ArgumentMatchers.*;
// import static org.mockito.Mockito.*;

// @ExtendWith(MockitoExtension.class)
public class PhotoUploadHandlerTest {

    // @Mock
    // private S3Client s3Client;

    // @Mock
    // private Context context;

    // @Mock
    // private UploadUtil uploadUtil;

    // @Mock
    // private QueueUtil queueUtil;

    // private PhotoUploadHandler handler;
    // private ObjectMapper objectMapper;
    // private APIGatewayProxyRequestEvent request;
    // @BeforeEach
    // public void setUp() throws Exception {
    //     objectMapper = new ObjectMapper();

    //     // Create mocks for AWS clients
    //     s3Client = mock(S3Client.class);
    //     uploadUtil = mock(UploadUtil.class);
    //     queueUtil = mock(QueueUtil.class);

    //     s3Client = S3Client.builder()
    //             .region(Region.EU_WEST_1)
    //             .build();

    //     handler = spy(new PhotoUploadHandler());

    //     // Using reflection to inject mocks into the handler
    //     Field s3ClientField = PhotoUploadHandler.class.getDeclaredField("s3Client");
    //     s3ClientField.setAccessible(true);
    //     s3ClientField.set(handler, s3Client);

    //     Field uploadUtilField = PhotoUploadHandler.class.getDeclaredField("uploadUtil");
    //     uploadUtilField.setAccessible(true);
    //     uploadUtilField.set(handler, uploadUtil);

    //     Field queueUtilField = PhotoUploadHandler.class.getDeclaredField("queueUtil");

    //     queueUtilField.setAccessible(true);
    //     queueUtilField.set(handler, queueUtil);

    //     // Create a basic request
    //     request = new APIGatewayProxyRequestEvent();
    //     request.setHeaders(new HashMap<>());

    //     // Mock the logger with lenient stubbing
    //     LambdaLogger logger = mock(LambdaLogger.class);
    //     lenient().when(context.getLogger()).thenReturn(logger);
    // }
    // @Test
    // public void testHandleRequest_Success() throws JsonProcessingException {
    //     // Setup test data
    //     String base64Image = Base64.getEncoder().encodeToString("test image content".getBytes());
    //     String contentType = "image/jpeg";
    //     String fileName = "test_image";
    //     String fileNameWithExtension = fileName + ".jpeg";
    //     String userId = "user123";
    //     String userEmail = "user@example.com";
    //     String firstName = "John";
    //     String lastName = "Doe";


    //     Map<String, String> claims = new HashMap<>();
    //     claims.put("email", userEmail);
    //     claims.put("userId", userId);
    //     claims.put("firstName", firstName);
    //     claims.put("lastName", lastName);


    //     Map<String, Object> requestBody = new HashMap<>();
    //     requestBody.put("image", base64Image);
    //     requestBody.put("contentType", contentType);
    //     requestBody.put("fileName", fileName);
    //     request.setBody(objectMapper.writeValueAsString(requestBody));


    //     try (MockedStatic<AuthorizerClaims> mockedClaims = Mockito.mockStatic(AuthorizerClaims.class)) {
    //         mockedClaims.when(() -> AuthorizerClaims.extractCognitoClaims(any())).thenReturn(claims);


    //         PutObjectResponse putObjectResponse = PutObjectResponse.builder().eTag("test-etag").build();
    //         when(uploadUtil.uploadToS3(eq(fileNameWithExtension), any(byte[].class), eq(contentType))).thenReturn(putObjectResponse);

    //         SendMessageResponse sqsResponse = SendMessageResponse.builder().messageId("test-message-id").build();
    //         when(queueUtil.sendToQueue(eq(fileNameWithExtension), eq(userId), eq(userEmail), eq(firstName), eq(lastName), any(LocalDateTime.class)))
    //                 .thenReturn(sqsResponse);
    //         APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
    //         assertEquals(200, response.getStatusCode());

    //         Map<String, String> responseBody = objectMapper.readValue(response.getBody(), Map.class);
    //         assertEquals("success", responseBody.get("status"));
    //         assertEquals("Image uploaded successfully", responseBody.get("message"));
    //         assertEquals(fileNameWithExtension, responseBody.get("fileName"));
    //         assertTrue(responseBody.get("url").contains(fileNameWithExtension));
    //         assertEquals("test-etag", responseBody.get("etag"));
    //         assertEquals("test-message-id", responseBody.get("sqsMessageId"));


    //         verify(uploadUtil).uploadToS3(eq(fileNameWithExtension), any(byte[].class), eq(contentType));
    //         verify(queueUtil).sendToQueue(eq(fileNameWithExtension), eq(userId), eq(userEmail), eq(firstName), eq(lastName), any(LocalDateTime.class));
    //     } catch (Exception e) {
    //         throw new RuntimeException(e);
    //     }
    // }

    // @Test
    // public void testHandleRequest_EmptyRequestBody() {

    //     request.setBody(null);

    //     try (MockedStatic<AuthorizerClaims> mockedClaims = Mockito.mockStatic(AuthorizerClaims.class)) {
    //         Map<String, String> claims = new HashMap<>();
    //         claims.put("email", "user@example.com");
    //         claims.put("userId", "user123");
    //         mockedClaims.when(() -> AuthorizerClaims.extractCognitoClaims(any())).thenReturn(claims);

    //         APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
    //         assertEquals(400, response.getStatusCode());
    //         assertTrue(response.getBody().contains("Request body is empty"));
    //     } catch (RuntimeException e) {
    //         throw new RuntimeException(e);
    //     }
    // }

    // @Test
    // public void testHandleRequest_MissingRequiredFields() throws JsonProcessingException {
    //     // Setup request body with missing fields
    //     Map<String, Object> requestBody = new HashMap<>();
    //     requestBody.put("image", ""); // Empty image
    //     requestBody.put("contentType", "image/jpeg");
    //     requestBody.put("fileName", "test");
    //     request.setBody(objectMapper.writeValueAsString(requestBody));

    //     try (MockedStatic<AuthorizerClaims> mockedClaims = Mockito.mockStatic(AuthorizerClaims.class)) {
    //         Map<String, String> claims = new HashMap<>();
    //         claims.put("email", "user@example.com");
    //         claims.put("userId", "user123");
    //         mockedClaims.when(() -> AuthorizerClaims.extractCognitoClaims(any())).thenReturn(claims);

    //         APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
    //         assertEquals(400, response.getStatusCode());
    //         assertTrue(response.getBody().contains("Missing required fields"));
    //     } catch (RuntimeException e) {
    //         throw new RuntimeException(e);
    //     }
    // }

    // @Test
    // public void testHandleRequest_UnsupportedContentType() throws JsonProcessingException {
    //     String base64Image = Base64.getEncoder().encodeToString("test image content".getBytes());
    //     Map<String, Object> requestBody = new HashMap<>();
    //     requestBody.put("image", base64Image);
    //     requestBody.put("contentType", "application/pdf");
    //     requestBody.put("fileName", "test");
    //     request.setBody(objectMapper.writeValueAsString(requestBody));

    //     try (MockedStatic<AuthorizerClaims> mockedClaims = Mockito.mockStatic(AuthorizerClaims.class)) {
    //         Map<String, String> claims = new HashMap<>();
    //         claims.put("email", "user@example.com");
    //         claims.put("userId", "user123");
    //         mockedClaims.when(() -> AuthorizerClaims.extractCognitoClaims(any())).thenReturn(claims);
    //         APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

    //         assertEquals(400, response.getStatusCode());
    //         assertTrue(response.getBody().contains("Unsupported content type"));
    //     }
    // }

    // @Test
    // public void testHandleRequest_ImageSizeExceedsLimit() throws JsonProcessingException {
    //     byte[] largeImage = new byte[7 * 1024 * 1024]; // 7MB, exceeds 6MB limit
    //     new Random().nextBytes(largeImage);
    //     String base64Image = Base64.getEncoder().encodeToString(largeImage);

    //     Map<String, Object> requestBody = new HashMap<>();
    //     requestBody.put("image", base64Image);
    //     requestBody.put("contentType", "image/jpeg");
    //     requestBody.put("fileName", "test");
    //     request.setBody(objectMapper.writeValueAsString(requestBody));
    //     try (MockedStatic<AuthorizerClaims> mockedClaims = Mockito.mockStatic(AuthorizerClaims.class)) {
    //         Map<String, String> claims = new HashMap<>();
    //         claims.put("email", "user@example.com");
    //         claims.put("userId", "user123");
    //         mockedClaims.when(() -> AuthorizerClaims.extractCognitoClaims(any())).thenReturn(claims);

    //         APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
    //         assertEquals(400, response.getStatusCode());
    //         assertTrue(response.getBody().contains("exceeds maximum allowed size"));
    //     }
    // }

    // @Test
    // public void testHandleRequest_Unauthorized() {
    //     try (MockedStatic<AuthorizerClaims> mockedClaims = Mockito.mockStatic(AuthorizerClaims.class)) {
    //         mockedClaims.when(() -> AuthorizerClaims.extractCognitoClaims(any())).thenReturn(Collections.emptyMap());
    //         APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
    //         assertEquals(401, response.getStatusCode());
    //         assertTrue(response.getBody().contains("Unauthorized"));
    //     }
    // }

    // @Test
    // public void testHandleRequest_MissingUserIdOrEmail() {
    //     try (MockedStatic<AuthorizerClaims> mockedClaims = Mockito.mockStatic(AuthorizerClaims.class)) {
    //         Map<String, String> claims = new HashMap<>();
    //         claims.put("email", "user@example.com");
    //         mockedClaims.when(() -> AuthorizerClaims.extractCognitoClaims(any())).thenReturn(claims);
    //         APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

    //         assertEquals(401, response.getStatusCode());
    //         assertTrue(response.getBody().contains("Unauthorized"));
    //     }
    // }

    // @Test
    // public void testHandleRequest_SQSError() throws Exception {
    //     String base64Image = Base64.getEncoder().encodeToString("test image content".getBytes());
    //     String contentType = "image/jpeg";
    //     String fileName = "test_image";
    //     String fileNameWithExtension = fileName + ".jpeg";

    //     Map<String, Object> requestBody = new HashMap<>();
    //     requestBody.put("image", base64Image);
    //     requestBody.put("contentType", contentType);
    //     requestBody.put("fileName", fileName);
    //     request.setBody(objectMapper.writeValueAsString(requestBody));

    //     try (MockedStatic<AuthorizerClaims> mockedClaims = Mockito.mockStatic(AuthorizerClaims.class)) {
    //         Map<String, String> claims = new HashMap<>();
    //         claims.put("email", "user@example.com");
    //         claims.put("userId", "user123");
    //         claims.put("firstName", "John");
    //         claims.put("lastName", "Doe");
    //         mockedClaims.when(() -> AuthorizerClaims.extractCognitoClaims(any())).thenReturn(claims);

    //         PutObjectResponse putObjectResponse = PutObjectResponse.builder().eTag("test-etag").build();
    //         when(uploadUtil.uploadToS3(eq(fileNameWithExtension), any(byte[].class), eq(contentType))).thenReturn(putObjectResponse);

    //         // Mock SQS to throw an exception
    //         when(queueUtil.sendToQueue(anyString(), anyString(), anyString(), anyString(), anyString(), any(LocalDateTime.class)))
    //                 .thenThrow(new RuntimeException("SQS error"));

    //         APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

    //         assertEquals(500, response.getStatusCode());
    //         assertTrue(response.getBody().contains("SQS error"));
    //         verify(uploadUtil).uploadToS3(eq(fileNameWithExtension), any(byte[].class), eq(contentType));
    //         verify(queueUtil).sendToQueue(anyString(), anyString(), anyString(), anyString(), anyString(), any(LocalDateTime.class));
    //     }
    // }

    // @Test
    // public void testHandleRequest_InvalidBase64() throws JsonProcessingException {
    //     // Setup request body with invalid base64
    //     Map<String, Object> requestBody = new HashMap<>();
    //     requestBody.put("image", "invalid base64 string");
    //     requestBody.put("contentType", "image/jpeg");
    //     requestBody.put("fileName", "test");
    //     request.setBody(objectMapper.writeValueAsString(requestBody));

    //     try (MockedStatic<AuthorizerClaims> mockedClaims = Mockito.mockStatic(AuthorizerClaims.class)) {
    //         Map<String, String> claims = new HashMap<>();
    //         claims.put("email", "user@example.com");
    //         claims.put("userId", "user123");
    //         mockedClaims.when(() -> AuthorizerClaims.extractCognitoClaims(any())).thenReturn(claims);

    //         APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);
    //         assertEquals(400, response.getStatusCode());
    //         assertTrue(response.getBody().contains("Invalid base64 encoding"));
    //     }
    // }

}
