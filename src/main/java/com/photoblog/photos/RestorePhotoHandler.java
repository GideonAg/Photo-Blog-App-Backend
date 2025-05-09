//package com.photoblog.photos;
//
//import com.amazonaws.services.lambda.runtime.Context;
//import com.amazonaws.services.lambda.runtime.RequestHandler;
//import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
//import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
//import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
//import com.photoblog.models.Photo;
//import com.photoblog.utils.S3Util;
//import lombok.RequiredArgsConstructor;
//
//import java.time.Instant;
//
//@RequiredArgsConstructor
//public class RestorePhotoHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
//    private final DynamoDBMapper dynamoDBMapper;
//    private final S3Util s3Util;
//
//
//    @Override
//    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
//        try {
//            String userId = input.getPathParameters().get("userId");
//            String photoId = input.getPathParameters().get("photoId");
//
//            // Load the Photo object
//            Photo photo = dynamoDBMapper.load(Photo.class, userId, photoId);
//            if (photo == null || photo.getStatus() != Photo.Status.DELETED) {
//                return new APIGatewayProxyResponseEvent()
//                        .withStatusCode(404)
//                        .withBody("Deleted photo not found");
//            }
//
//            // Restore the image
//            s3Util.restoreImage(userId, photoId, photo.getVersionId());
//
//            // Update Photo status
//            photo.setStatus(Photo.Status.ACTIVE);
//            photo.setUpdatedAt(Instant.now().toString());
//            dynamoDBMapper.save(photo);
//
//            return new APIGatewayProxyResponseEvent()
//                    .withStatusCode(200)
//                    .withBody("Image restored");
//        } catch (Exception e) {
//            context.getLogger().log("Error: " + e.getMessage());
//            return new APIGatewayProxyResponseEvent()
//                    .withStatusCode(500)
//                    .withBody("Error restoring image: " + e.getMessage());
//        }
//    }
//}