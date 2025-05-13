package com.photoblog.photos;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoblog.models.Photo;
import com.photoblog.utils.AuthorizerClaims;
import com.photoblog.utils.DynamoDBUtil;
import com.photoblog.utils.HeadersUtil;
import com.photoblog.utils.S3Util;

import java.util.List;
import java.util.Map;

public class PhotoQueryHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final S3Util s3Util = new S3Util();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context){
        context.getLogger().log("Received Request to query photos");

        try {
            Map<String, String> claims = AuthorizerClaims.extractCognitoClaims(input);
            String userId = claims.get("userId");
            if(userId == null){
                context.getLogger().log("Unauthorized: Missing usrId in claims");
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(401)
                        .withHeaders(HeadersUtil.getHeaders())
                        .withBody("{\"error\": \"Unauthorized: Missing userId\"}");

            }
            context.getLogger().log("Authenticated user: " + userId);

            List<Photo> photos = DynamoDBUtil.getActivePhotosByUserId(userId);
            if(photos.isEmpty()){
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(HeadersUtil.getHeaders())
                        .withBody("{\"message\": \"No active photos found\"}");
            }
            context.getLogger().log("Retrieved " + photos.size() + " active photos for user: " + userId);

            List<String> photoUrls = photos.stream()
                    .map(photo -> s3Util.getImage(userId, photo.getPhotoId()))
                    .toList();

            context.getLogger().log("Generated presigned URLs for " + photoUrls.size() + " photos");
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(HeadersUtil.getHeaders())
                    .withBody(objectMapper.writeValueAsString(photoUrls));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(401)
                    .withHeaders(HeadersUtil.getHeaders())
                    .withBody("{\"error\": \"Unauthorized: " + e.getMessage() + "\"}");
        } catch (Exception e){
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(HeadersUtil.getHeaders())
                    .withBody("{\"error\": \"Failed to retrieve photos: " + e.getMessage() + "\"}");
        }
    }
}
