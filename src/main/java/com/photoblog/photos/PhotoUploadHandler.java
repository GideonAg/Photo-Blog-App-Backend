package com.photoblog.photos;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoblog.utils.ClaimsUtil;
import com.photoblog.utils.HeadersUtil;
import com.photoblog.utils.UploadUtil;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.LocalDateTime;
import java.util.*;

public class PhotoUploadHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final S3Client s3Client;
    private final SqsClient sqsClient;
    private final String bucketName;
    private final String sqsQueueUrl;
    private final ObjectMapper objectMapper;
    private final String userPoolId;
    private final Region region;
    private final UploadUtil uploadUtil;
    //    private final Utils utils;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private final Map<String, String> headers = HeadersUtil.getHeaders();
    private ClaimsUtil claimsUtil = null;

    public PhotoUploadHandler(){

        this.bucketName = System.getenv("STAGING_BUCKET");
        this.sqsQueueUrl = System.getenv("IMAGE_PROCESSING_QUEUE");
        this.userPoolId = System.getenv("USER_POOL_ID");
        String regionName = System.getenv("PRIMARY_REGION");
        this.region = regionName != null && !regionName.isEmpty()
                ? Region.of(regionName)
                : Region.EU_CENTRAL_1;

        this.s3Client = S3Client.builder().region(this.region).build();
        this.sqsClient = SqsClient.builder().region(this.region).build();
        this.objectMapper = new ObjectMapper();
        this.uploadUtil = new UploadUtil(bucketName, s3Client);
    }


    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setIsBase64Encoded(false);
        response.setHeaders(headers);

        try{
            Map<String, String> claims = claimsUtil.getClaims(request, context);
            if(claims.isEmpty()) return buildErrorResponse(response, 401, "Unauthorized");
            String userName = claims.get("cognito:username");
            if(userName.isEmpty()) {
                context.getLogger().log("Error:" + userName + " UserName not found");
                return buildErrorResponse(response, 404, "UserName not found");
            }
            if (request.getBody() == null || request.getBody().isEmpty()) {
                return buildErrorResponse(response, 400, "Request body is empty");
            }
            Map<String, Object> requestBody = objectMapper.readValue(
                    request.getBody(),
                    new TypeReference<Map<String, Object>>() {}
            );
            context.getLogger().log("requestBody: " + requestBody);

            String base64Image = (String) requestBody.get("image");
            String contentType = (String) requestBody.get("contentType");
            String fileName = userName + System.currentTimeMillis();

            if (base64Image == null || contentType == null) {
                return buildErrorResponse(response, 400, "Missing required fields: image and/or contentType");
            }
            if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
                return buildErrorResponse(response, 400, "Unsupported content type: " + contentType);
            }

            byte[] imagesBytes = Base64.getDecoder().decode(base64Image);

            PutObjectResponse putObjectResponse = uploadUtil.uploadToS3(fileName, imagesBytes, contentType);
            try {
                SendMessageResponse sqsResponse = sendToQueue(imagesBytes, userName, LocalDateTime.now());
                context.getLogger().log("SQS message sent with ID: " + sqsResponse.messageId());

                String objectUrl = "https://" + bucketName + ".s3.amazonaws.com/" + fileName;
                Map<String, String> responseBody = new HashMap<>();
                responseBody.put("status", "success");
                responseBody.put("message", "Image uploaded successfully");
                responseBody.put("fileName", fileName);
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
            return buildErrorResponse(response, 400, "Invalid base64 encoding") ;
        }catch (Exception e){
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
            response.setBody("{\"status\":\"error\",\"message\":\"" + message + "\"}");}
        return response;
    }

    public SendMessageResponse sendToQueue(byte[] objectKey, String userEmail, LocalDateTime uploadTimestamp) throws Exception {
        Map<String, String> messageBody = new HashMap<>();
        messageBody.put("objectKey", Arrays.toString(objectKey));
        messageBody.put("uploadDate", String.valueOf(uploadTimestamp));
        messageBody.put("uploadedBy", userEmail);
        messageBody.put("bucket", bucketName);

        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(messageBody);
        } catch (Exception e) {
            // Fallback in case serialization fails
            messageJson = String.format(
                    "{\"objectKey\":\"%s\",\"uploadDate\":\"%s\",\"uploadedBy\":\"%s\",\"bucket\":\"%s\"}",
                    Arrays.toString(objectKey), uploadTimestamp, userEmail != null ? userEmail : "unknown", bucketName
            );
        }
        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(sqsQueueUrl)
                .messageBody(messageJson)
                .build();
        return sqsClient.sendMessage(sendMessageRequest);
    }
}