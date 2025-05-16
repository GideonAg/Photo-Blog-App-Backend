package com.photoblog.photos;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.photoblog.models.Photo;
import com.photoblog.utils.AuthorizerClaims;
import com.photoblog.utils.DynamoDBUtil;
import com.photoblog.utils.HeadersUtil;
import com.photoblog.utils.S3Util;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ObjectVersion;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

public class PhotoDeleteHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final S3Util s3Util;
    private final Gson gson = new Gson();

    public PhotoDeleteHandler() {
        this.s3Util = new S3Util();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {

            Map<String, String> claims = AuthorizerClaims.extractCognitoClaims(input);
            String userId = claims.get("userId");
            context.getLogger().log("Extracted claims: userId=" + userId);

            if (userId == null || userId.isEmpty()) {
                context.getLogger().log("Validation failed: Missing user ID");
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(401)
                        .withHeaders(HeadersUtil.getHeaders())
                        .withBody(gson.toJson(Map.of("error", "Unauthorized: Missing user ID")));
            }
            context.getLogger().log("Authenticated User "+ userId);

            // Extract photoId from path parameters
            String photoId = input.getPathParameters() != null ? input.getPathParameters().get("photoId") : null;
            if (photoId == null || photoId.isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(HeadersUtil.getHeaders())
                        .withBody(gson.toJson(Map.of("error", "Photo ID is required")));
            }

            // Validate userId from path parameters (if provided)
            String pathUserId = input.getPathParameters() != null ? input.getPathParameters().get("userId") : null;
            if (pathUserId != null && !pathUserId.equals(userId)) {
                context.getLogger().log("User ID mismatch: pathUserId=" + pathUserId + ", claimsUserId=" + userId);
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(403)
                        .withHeaders(HeadersUtil.getHeaders())
                        .withBody(gson.toJson(Map.of("error", "Forbidden: User ID mismatch")));
            }
            
            // Load the Photo object
            Photo photo = DynamoDBUtil.getPhotoById(userId, photoId);
            context.getLogger().log("USER ID:" + userId + "   PHOTO_ID" + photoId);
            if (photo == null) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(404)
                        .withHeaders(HeadersUtil.getHeaders())
                        .withBody(gson.toJson(Map.of("error", "Photo not found")));
            }
            String s3Key = photo.getImageName();

            // Add delete marker to S3 object
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(s3Util.getMainBucket())
                    .key(s3Key)
                    .build();
            s3Util.getS3Client().deleteObject(deleteRequest);


            // Extract the most recent version ID before the delete marker
            ListObjectVersionsRequest listVersionsRequest = ListObjectVersionsRequest.builder()
                    .bucket(s3Util.getMainBucket())
                    .prefix(s3Key)
                    .build();
            ListObjectVersionsResponse versionsResponse = s3Util.getS3Client().listObjectVersions(listVersionsRequest);
            String latestVersionId = versionsResponse.versions().stream()
                    .filter(v -> !v.isLatest())
                    .findFirst()
                    .map(ObjectVersion::versionId)
                    .orElse(photo.getVersionId());


            photo.setStatus(Photo.Status.DELETED);
            photo.setVersionId(latestVersionId);
            photo.setUpdatedAt(LocalDateTime.now());
            DynamoDBUtil.savePhoto(photo);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(HeadersUtil.getHeaders())
                    .withBody("Image marked as deleted");
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(HeadersUtil.getHeaders())
                    .withBody("Error deleting image: " + e.getMessage());
        }
    }
}