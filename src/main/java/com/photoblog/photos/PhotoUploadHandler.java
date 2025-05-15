package com.photoblog.photos;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoblog.utils.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.LocalDateTime;
import java.util.*;

public class PhotoUploadHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final S3Client s3Client;
    private final String bucketName;
    private final ObjectMapper objectMapper;
    private final Region region;
    private final UploadUtil uploadUtil;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private final Map<String, String> headers = HeadersUtil.getHeaders();
    private final QueueUtil queueUtil = new QueueUtil();
    private static final long MAX_IMAGE_SIZE_BYTES = 6 * 1024 * 1024;

    public PhotoUploadHandler() {

        this.bucketName = System.getenv("STAGING_BUCKET");
        String regionName = System.getenv("PRIMARY_REGION");
        this.region = regionName != null && !regionName.isEmpty()
                ? Region.of(regionName)
                : Region.EU_CENTRAL_1;

        this.s3Client = S3Client.builder().region(this.region).build();
        this.objectMapper = new ObjectMapper();
        this.uploadUtil = new UploadUtil(bucketName, s3Client);
    }


    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setIsBase64Encoded(false);
        response.setHeaders(headers);

        try {
            Map<String, String> claims = AuthorizerClaims.extractCognitoClaims(request);
            if (claims.isEmpty()) return buildErrorResponse(response, 401, "Unauthorized");
            String userEmail = claims.get("email");
            String userId = claims.get("userId");
            String firstName = claims.get("firstName");
            String lastName = claims.get("lastName");
            if(userEmail == null || userId == null){
                return buildErrorResponse(response, 401, "Unauthorized:");
            }
            context.getLogger().log("User email: " + userEmail + "UserId: " + userId);

            if (request.getBody() == null || request.getBody().isEmpty()) {
                return buildErrorResponse(response, 400, "Request body is empty");
            }

            Map<String, Object> requestBody = objectMapper.readValue(
                    request.getBody(),
                    new TypeReference<>() {
                    }
            );

            String base64Image = (String) requestBody.get("image");
            String contentType = (String) requestBody.get("contentType");
            String fileName = (String) requestBody.get("fileName");


            if (base64Image == null || base64Image.isEmpty() || contentType == null || contentType.isEmpty() || fileName == null || fileName.isEmpty()) {
                return buildErrorResponse(response, 400, "Missing required fields: image and/or contentType");
            }
            String fileNameWithExtension = fileName + "." + contentType.split("/")[1];
            if (!ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
                return buildErrorResponse(response, 400, "Unsupported content type: " + contentType);
            }

            byte[] imagesBytes = Base64.getDecoder().decode(base64Image);
            if (imagesBytes.length > MAX_IMAGE_SIZE_BYTES) {
                return buildErrorResponse(response, 400, "Image size exceeds maximum allowed size of 6MB");
            }

            PutObjectResponse putObjectResponse = uploadUtil.uploadToS3(fileNameWithExtension, imagesBytes, contentType);
            try {
                SendMessageResponse sqsResponse = queueUtil.sendToQueue(fileNameWithExtension, userId, userEmail, firstName, lastName, LocalDateTime.now());

                String objectUrl = "https://" + bucketName + ".s3.amazonaws.com/" + fileNameWithExtension;
                Map<String, String> responseBody = new HashMap<>();
                responseBody.put("status", "success");
                responseBody.put("message", "Image uploaded successfully");
                responseBody.put("fileName", fileNameWithExtension);
                responseBody.put("url", objectUrl);
                responseBody.put("etag", putObjectResponse.eTag());
                responseBody.put("sqsMessageId", sqsResponse.messageId());
                response.setStatusCode(200);
                response.setBody(objectMapper.writeValueAsString(responseBody));
            } catch (Exception e) {
                context.getLogger().log("Error sending SQS message: " + e.getMessage());
                return buildErrorResponse(response, 500, e.getMessage());
            }
            return response;
        } catch (IllegalArgumentException e) {
            context.getLogger().log(e.getMessage());
            return buildErrorResponse(response, 400, "Invalid base64 encoding");
        } catch (Exception e) {
            context.getLogger().log(e.getMessage());
            return buildErrorResponse(response, 500, "Error processing request");
        }
    }


    private APIGatewayProxyResponseEvent buildErrorResponse(APIGatewayProxyResponseEvent response, int statusCode, String message) {
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("status", "error");
        errorBody.put("message", message);
        response.setStatusCode(statusCode);
        try {
            response.setBody(objectMapper.writeValueAsString(errorBody));
        } catch (Exception e) {
            response.setBody("{\"status\":\"error\",\"message\":\"" + message + "\"}");
        }
        return response;
    }
}