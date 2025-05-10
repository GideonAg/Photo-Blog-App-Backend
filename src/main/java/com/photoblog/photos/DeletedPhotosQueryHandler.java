//package com.photoblog.photos;
//
//import com.amazonaws.services.lambda.runtime.Context;
//import com.amazonaws.services.lambda.runtime.RequestHandler;
//import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
//import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
//import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
//import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
//import com.photoblog.models.Photo;
//import com.photoblog.utils.S3Util;
//import com.google.gson.Gson;
//import lombok.RequiredArgsConstructor;
//
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@RequiredArgsConstructor
//public class DeletedPhotosQueryHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
//    private final DynamoDBMapper dynamoDBMapper;
//    private final S3Util s3Util;
//
//    @Override
//    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
//        try {
//            String userId = input.getPathParameters().get("userId");
//
//            // Query deleted photos
//            Photo key = new Photo();
//            key.setUserId(userId);
//            DynamoDBQueryExpression<Photo> queryExpression = new DynamoDBQueryExpression<Photo>()
//                    .withHashKeyValues(key)
//                    .withFilterExpression("status = :status")
//                    .withExpressionAttributeValues(Map.of(":status", new com.amazonaws.services.dynamodbv2.model.AttributeValue().withS("DELETED")));
//
//            List<Photo> deletedPhotos = dynamoDBMapper.query(Photo.class, queryExpression);
//
//            // Generate presigned URLs for deleted photos
//            List<Map<String, String>> response = deletedPhotos.stream().map(photo -> {
//                Map<String, String> photoInfo = new HashMap<>();
//                photoInfo.put("photoId", photo.getPhotoId());
//                photoInfo.put("imageName", photo.getImageName());
//                photoInfo.put("versionId", photo.getVersionId());
//                photoInfo.put("presignedUrl", s3Util.getDeletedImage(userId, photo.getPhotoId(), photo.getVersionId()));
//                return photoInfo;
//            }).collect(Collectors.toList());
//
//            Gson gson = new Gson();
//            return new APIGatewayProxyResponseEvent()
//                    .withStatusCode(200)
//                    .withBody(gson.toJson(response));
//        } catch (Exception e) {
//            context.getLogger().log("Error: " + e.getMessage());
//            return new APIGatewayProxyResponseEvent()
//                    .withStatusCode(500)
//                    .withBody("Error listing deleted images: " + e.getMessage());
//        }
//    }
//}