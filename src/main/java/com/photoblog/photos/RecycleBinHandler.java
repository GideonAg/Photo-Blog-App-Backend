package com.photoblog.photos;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.photoblog.models.Photo;
import com.photoblog.utils.S3Util;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class RecycleBinHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDBMapper dynamoDBMapper;
    private final S3Util s3Util;

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        // This method is not used directly; specific methods are called by SAM
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(400)
                .withBody("Invalid operation");
    }

    public APIGatewayProxyResponseEvent listDeletedPhotos(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String userId = input.getPathParameters().get("userId");
            String photoId = input.getPathParameters().get("photoId");
            String s3Key = userId + "/" + photoId;
            if (userId == null) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(401)
                        .withBody("Unauthorized: Missing user ID");
            }

            // Query deleted photos
            Photo key = new Photo();
            key.setUserId(userId);
            DynamoDBQueryExpression<Photo> queryExpression = new DynamoDBQueryExpression<Photo>()
                    .withHashKeyValues(key)
                    .withFilterExpression("status = :status")
                    .withExpressionAttributeValues(Map.of(":status", new com.amazonaws.services.dynamodbv2.model.AttributeValue().withS("DELETED")));

            List<Photo> deletedPhotos = dynamoDBMapper.query(Photo.class, queryExpression);

            // Generate presigned URLs for deleted photos
            List<Map<String, String>> response = deletedPhotos.stream().map(photo -> {
                Map<String, String> photoInfo = new HashMap<>();
                photoInfo.put("photoId", photo.getPhotoId());
                photoInfo.put("imageName", photo.getImageName());
                photoInfo.put("versionId", photo.getVersionId());
                photoInfo.put("presignedUrl", s3Util.getDeletedImage(userId, photo.getPhotoId(), photo.getVersionId()));
                return photoInfo;
            }).collect(Collectors.toList());

            Gson gson = new Gson();
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(gson.toJson(response));
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("Error listing deleted images: " + e.getMessage());
        }
    }

    public APIGatewayProxyResponseEvent restorePhoto(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String userId = input.getPathParameters().get("userId");
            String photoId = input.getPathParameters().get("photoId");
            String s3Key = userId + "/" + photoId;
            if (userId == null) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(401)
                        .withBody("Unauthorized: Missing user ID");
            }

            // Load the Photo object
            Photo photo = dynamoDBMapper.load(Photo.class, userId, photoId);
            if (photo == null || photo.getStatus() != Photo.Status.DELETED) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(404)
                        .withBody("Deleted photo not found");
            }

            // Restore the image
            s3Util.restoreImage(userId, photoId, photo.getVersionId());

            // Update Photo status
            photo.setStatus(Photo.Status.ACTIVE);
            photo.setUpdatedAt(Instant.now().toString());
            dynamoDBMapper.save(photo);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody("Image restored");
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("Error restoring image: " + e.getMessage());
        }
    }

    public APIGatewayProxyResponseEvent permanentlyDeletePhoto(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String userId = input.getPathParameters().get("userId");
            String photoId = input.getPathParameters().get("photoId");
            String s3Key = userId + "/" + photoId;
            if (userId == null) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(401)
                        .withBody("Unauthorized: Missing user ID");
            }

            // Load the Photo object
            Photo photo = dynamoDBMapper.load(Photo.class, userId, photoId);
            if (photo == null || photo.getStatus() != Photo.Status.DELETED) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(404)
                        .withBody("Deleted photo not found");
            }

            // Delete all versions and delete markers from S3
            ListObjectVersionsRequest listVersionsRequest = ListObjectVersionsRequest.builder()
                    .bucket(s3Util.getMainBucket())
                    .prefix(s3Key)
                    .build();
            ListObjectVersionsResponse versionsResponse = s3Util.getS3Client().listObjectVersions(listVersionsRequest);

            // Delete all versions
            for (var version : versionsResponse.versions()) {
                DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                        .bucket(s3Util.getMainBucket())
                        .key(s3Key)
                        .versionId(version.versionId())
                        .build();
                s3Util.getS3Client().deleteObject(deleteRequest);
            }

            // Delete all delete markers
            for (var deleteMarker : versionsResponse.deleteMarkers()) {
                DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                        .bucket(s3Util.getMainBucket())
                        .key(s3Key)
                        .versionId(deleteMarker.versionId())
                        .build();
                s3Util.getS3Client().deleteObject(deleteRequest);
            }

            // Delete the Photo object
            dynamoDBMapper.delete(photo);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody("Image permanently deleted");
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("Error permanently deleting image: " + e.getMessage());
        }
    }
}