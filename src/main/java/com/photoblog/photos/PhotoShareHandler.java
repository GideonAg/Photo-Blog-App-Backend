package com.photoblog.photos;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoblog.models.Photo;
import com.photoblog.utils.AuthorizerClaims;
import com.photoblog.utils.DynamoDBUtil;
import com.photoblog.utils.HeadersUtil;
import com.photoblog.utils.PhotoShareResponse;
import com.photoblog.utils.S3Util;

public class PhotoShareHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final S3Util s3Util;
    private final ObjectMapper mapper;

    public PhotoShareHandler() {
        s3Util = new S3Util();
        this.mapper = new ObjectMapper();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setHeaders(HeadersUtil.getHeaders());

        try {
            Map<String, String> authorizeMap = AuthorizerClaims.extractCognitoClaims(requestEvent);
            String userId = authorizeMap.get("sub");
            String photoId = requestEvent.getPathParameters().get("photoId");

            Photo photo = DynamoDBUtil.getPhotoById(userId, photoId);
            if(photo == null) {
                return response.withBody(mapper.writeValueAsString(new PhotoShareResponse("Failed to fetch link. Not authorizef")));
            }

            String shareLink = s3Util.getImage(userId, photoId);

            PhotoShareResponse photoShareResponse = new PhotoShareResponse("Share link generated successfully", shareLink);
            String body = mapper.writeValueAsString(photoShareResponse);

            return response.withBody(body).withStatusCode(200);
        } 
        catch (RuntimeException e) {
            context.getLogger().log(e.getMessage());
            PhotoShareResponse errorResponse = new PhotoShareResponse("ERROR: " + e.getMessage());
            try {
                response.setBody(mapper.writeValueAsString(errorResponse));
            } catch (Exception jsonEx) {
                response.setBody("{\"message\":\"Unexpected error\"}");
            }
            return response.withStatusCode(500);
        }
        catch (Exception e) {
            context.getLogger().log(e.getMessage() + " INSIDE THE EXCEPTION CATCH BLOCK");
            PhotoShareResponse errorResponse = new PhotoShareResponse("ERROR: " + e.getMessage());
            try {
                response.setBody(mapper.writeValueAsString(errorResponse));
            } catch (Exception jsonEx) {
                response.setBody("{\"message\":\"Unexpected error\"}");
            }
            return response.withStatusCode(500);
        }
    }
}
