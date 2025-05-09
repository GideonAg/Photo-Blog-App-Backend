package com.photoblog.photos;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.photoblog.models.Photo;
import com.photoblog.utils.S3Util;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;

import java.time.Instant;

@RequiredArgsConstructor
public class DeletePhotoHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDBMapper dynamoDBMapper;
    private final S3Util s3Util;


    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String userId = input.getPathParameters().get("userId");
            String photoId = input.getPathParameters().get("photoId");
            String s3Key = userId + "/" + photoId;

            // Load the Photo object
            Photo photo = dynamoDBMapper.load(Photo.class, userId, photoId);
            if (photo == null) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(404)
                        .withBody("Photo not found");
            }

            // Add delete marker to S3 object
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(s3Util.getMainBucket())
                    .key(s3Key)
                    .build();
            s3Util.getS3Client().deleteObject(deleteRequest);

            // Get the latest version ID (before the delete marker)
            ListObjectVersionsRequest listVersionsRequest = ListObjectVersionsRequest.builder()
                    .bucket(s3Util.getMainBucket())
                    .prefix(s3Key)
                    .build();
            ListObjectVersionsResponse versionsResponse = s3Util.getS3Client().listObjectVersions(listVersionsRequest);
            String latestVersionId = versionsResponse.versions().stream()
                    .filter(v -> !v.isLatest()) // Skip the delete marker
                    .findFirst()
                    .map(v -> v.versionId())
                    .orElse(photo.getVersionId());

            // Update Photo status and versionId
            photo.setStatus(Photo.Status.DELETED);
            photo.setVersionId(latestVersionId);
            photo.setUpdatedAt(Instant.now().toString());
            dynamoDBMapper.save(photo);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody("Image marked as deleted");
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("Error deleting image: " + e.getMessage());
        }
    }
}