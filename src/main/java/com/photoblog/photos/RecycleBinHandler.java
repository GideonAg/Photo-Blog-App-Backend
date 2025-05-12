package com.photoblog.photos;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.photoblog.models.Photo;
import com.photoblog.utils.AuthorizerClaims;
import com.photoblog.utils.DynamoDBUtil;
import com.photoblog.utils.S3Util;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ObjectVersion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class RecycleBinHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDBUtil dynamoDBUtil;
    private final S3Util s3Util;
    private final Gson gson = new Gson();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(400)
                .withBody(gson.toJson(Map.of("error", "Invalid operation")));
    }

    protected APIGatewayProxyResponseEvent validateUserId(String userId, Context context) {
        if (userId == null || userId.isEmpty()) {
            context.getLogger().log("Validation failed: Missing user ID");
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(401)
                    .withBody(gson.toJson(Map.of("error", "Unauthorized: Missing user ID")));
        }
        return null;
    }

    public APIGatewayProxyResponseEvent listDeletedPhotos(APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Extract and validate claims
            Map<String, String> claims = AuthorizerClaims.extractCognitoClaims(input);
            String userId = claims.get("userId");
            context.getLogger().log("Extracted claims: userId=" + userId);

            APIGatewayProxyResponseEvent errorResponse = validateUserId(userId, context);
            if (errorResponse != null) {
                return errorResponse;
            }

            // Query deleted photos
            List<Photo> deletedPhotos = dynamoDBUtil.getDeletedPhotosByUserId(userId);
            if (deletedPhotos.isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withBody(gson.toJson(new ArrayList<>()));
            }

            // Fetch previous versions and generate presigned URLs
            List<Map<String, String>> response = new ArrayList<>();
            for (Photo photo : deletedPhotos) {
                String photoId = photo.getPhotoId();
                String s3Key = userId + "/" + photoId;

                // List object versions to find the previous version
                ListObjectVersionsRequest versionsRequest = ListObjectVersionsRequest.builder()
                        .bucket(s3Util.getMainBucket())
                        .prefix(s3Key)
                        .build();
                ListObjectVersionsResponse versionsResponse = s3Util.getS3Client().listObjectVersions(versionsRequest);
                List<ObjectVersion> versions = versionsResponse.versions();

                // Find the previous version (skip the latest, which may be a delete marker)
                String previousVersionId = null;
                for (ObjectVersion version : versions) {
                    if (!version.isLatest()) {
                        previousVersionId = version.versionId();
                        break;
                    }
                }

                if (previousVersionId != null) {
                    // Generate presigned URL for the previous version
                    String presignedUrl = s3Util.getDeletedImage(userId, photoId, previousVersionId);
                    Map<String, String> photoInfo = new HashMap<>();
                    photoInfo.put("photoId", photoId);
                    photoInfo.put("imageName", photo.getImageName());
                    photoInfo.put("versionId", previousVersionId);
                    photoInfo.put("presignedUrl", presignedUrl);
                    response.add(photoInfo);
                }
            }

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(gson.toJson(response));
        } catch (IllegalArgumentException | IllegalStateException e) {
            context.getLogger().log("Authorization error: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(401)
                    .withBody(gson.toJson(Map.of("error", "Authorization failed: " + e.getMessage())));
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody(gson.toJson(Map.of("error", "Error listing deleted images: " + e.getMessage())));
        }
    }

    public APIGatewayProxyResponseEvent restorePhoto(APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Extract and validate claims
            Map<String, String> claims = AuthorizerClaims.extractCognitoClaims(input);
            String userId = claims.get("userId");
            context.getLogger().log("Extracted claims: userId=" + userId);

            APIGatewayProxyResponseEvent errorResponse = validateUserId(userId, context);
            if (errorResponse != null) {
                return errorResponse;
            }

            // Extract photoId from path parameters
            String photoId = input.getPathParameters().get("photoId");
            if (photoId == null || photoId.isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody(gson.toJson(Map.of("error", "Photo ID is required")));
            }

            // Load the Photo object
            Photo photo = dynamoDBUtil.getPhotoById(userId, photoId);
            if (photo == null || photo.getStatus() != Photo.Status.DELETED) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(404)
                        .withBody(gson.toJson(Map.of("error", "Deleted photo not found")));
            }

            // Restore the image
            s3Util.restoreImage(userId, photoId, photo.getVersionId());

            // Update Photo status
            photo.setStatus(Photo.Status.ACTIVE);
            photo.setUpdatedAt(Instant.now().toString());
            dynamoDBUtil.savePhoto(photo);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(gson.toJson(Map.of("message", "Image restored")));
        } catch (IllegalArgumentException | IllegalStateException e) {
            context.getLogger().log("Authorization error: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(401)
                    .withBody(gson.toJson(Map.of("error", "Authorization failed: " + e.getMessage())));
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody(gson.toJson(Map.of("error", "Error restoring image: " + e.getMessage())));
        }
    }

    public APIGatewayProxyResponseEvent permanentlyDeletePhoto(APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Extract and validate claims
            Map<String, String> claims = AuthorizerClaims.extractCognitoClaims(input);
            String userId = claims.get("userId");
            context.getLogger().log("Extracted claims: userId=" + userId);

            APIGatewayProxyResponseEvent errorResponse = validateUserId(userId, context);
            if (errorResponse != null) {
                return errorResponse;
            }

            // Extract photoId from path parameters
            String photoId = input.getPathParameters().get("photoId");
            if (photoId == null || photoId.isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody(gson.toJson(Map.of("error", "Photo ID is required")));
            }

            // Get the Photo object
            Photo photo = dynamoDBUtil.getPhotoById(userId, photoId);
            if (photo == null || photo.getStatus() != Photo.Status.DELETED) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(404)
                        .withBody(gson.toJson(Map.of("error", "Deleted photo not found")));
            }

            // Delete all versions and delete markers from S3
            String s3Key = userId + "/" + photoId;
            ListObjectVersionsRequest listVersionsRequest = ListObjectVersionsRequest.builder()
                    .bucket(s3Util.getMainBucket())
                    .prefix(s3Key)
                    .build();
            ListObjectVersionsResponse versionsResponse = s3Util.getS3Client().listObjectVersions(listVersionsRequest);

            // Delete all versions
            for (ObjectVersion version : versionsResponse.versions()) {
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

            // Delete the Photo object from DynamoDB
            dynamoDBUtil.deletePhoto(photo);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(gson.toJson(Map.of("message", "Image permanently deleted")));
        } catch (IllegalArgumentException | IllegalStateException e) {
            context.getLogger().log("Authorization error: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(401)
                    .withBody(gson.toJson(Map.of("error", "Authorization failed: " + e.getMessage())));
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody(gson.toJson(Map.of("error", "Error permanently deleting image: " + e.getMessage())));
        }
    }
}