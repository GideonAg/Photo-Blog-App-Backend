package com.photoblog.photos;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.photoblog.models.Photo;
import com.photoblog.utils.DynamoDBUtil;
import com.photoblog.utils.HeadersUtil;

public class PhotoShareHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setHeaders(HeadersUtil.getHeaders());
        try {
            Map<String, Object> authorizeMap = requestEvent.getRequestContext().getAuthorizer();

            @SuppressWarnings("unchecked")
            Map<String, String> claims = (Map<String, String>) authorizeMap.get("claims");
            String userId = claims.get("sub");
            String photoId = requestEvent.getPathParameters().get("photoId");
            // check if user is authorised to share the photo ie user owns the photo
            Photo photo = DynamoDBUtil.getPhotoById(userId, photoId);
            return null;
        } catch (Exception e) {
            context.getLogger().log(e.getMessage() + " INSIDE THE CATCH BLOCK");
            return null;
        }
    }
}
