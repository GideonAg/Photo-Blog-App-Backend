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
//import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
//import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
//import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
//
//@RequiredArgsConstructor
//public class PermanentlyDeletePhotoHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
//    private final DynamoDBMapper dynamoDBMapper;
//    private final S3Util s3Util;
//
//    @Override
//    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
//        try {
//            String userId = input.getPathParameters().get("userId");
//            String photoId = input.getPathParameters().get("photoId");
//            String s3Key = userId + "/" + photoId;
//
//            //Get the Photo object
//            Photo photo = dynamoDBMapper.load(Photo.class, userId, photoId);
//            if (photo == null || photo.getStatus() != Photo.Status.DELETED) {
//                return new APIGatewayProxyResponseEvent()
//                        .withStatusCode(404)
//                        .withBody("Deleted photo not found");
//            }
//
//            // Delete all versions and delete markers from S3
//            ListObjectVersionsRequest listVersionsRequest = ListObjectVersionsRequest.builder()
//                    .bucket(s3Util.getMainBucket())
//                    .prefix(s3Key)
//                    .build();
//            ListObjectVersionsResponse versionsResponse = s3Util.getS3Client().listObjectVersions(listVersionsRequest);
//
//            // Delete all versions
//            for (var version : versionsResponse.versions()) {
//                DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
//                        .bucket(s3Util.getMainBucket())
//                        .key(s3Key)
//                        .versionId(version.versionId())
//                        .build();
//                s3Util.getS3Client().deleteObject(deleteRequest);
//            }
//
//            // Delete all delete markers
//            for (var deleteMarker : versionsResponse.deleteMarkers()) {
//                DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
//                        .bucket(s3Util.getMainBucket())
//                        .key(s3Key)
//                        .versionId(deleteMarker.versionId())
//                        .build();
//                s3Util.getS3Client().deleteObject(deleteRequest);
//            }
//
//            // Delete the Photo object
//            dynamoDBMapper.delete(photo);
//
//            return new APIGatewayProxyResponseEvent()
//                    .withStatusCode(200)
//                    .withBody("Image permanently deleted");
//        } catch (Exception e) {
//            context.getLogger().log("Error: " + e.getMessage());
//            return new APIGatewayProxyResponseEvent()
//                    .withStatusCode(500)
//                    .withBody("Error permanently deleting image: " + e.getMessage());
//        }
//    }
//}